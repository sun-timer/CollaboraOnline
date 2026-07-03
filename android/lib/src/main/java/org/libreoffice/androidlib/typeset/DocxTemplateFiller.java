package org.libreoffice.androidlib.typeset;

import android.content.Context;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Fills a docx template with AI-generated section content.
 *
 * <p>Uses only JDK built-in APIs ({@link java.util.zip.ZipInputStream},
 * {@link javax.xml.parsers.DocumentBuilder}) — no external dependencies.</p>
 *
 * <p>Process:
 * <ol>
 *   <li>Read template .docx from res/raw as InputStream</li>
 *   <li>Unzip into memory (non-XML → byte[], XML → DOM Document)</li>
 *   <li>Walk word/document.xml paragraphs, match placeholder text, replace with AI content</li>
 *   <li>Re-zip to a temp .docx file</li>
 * </ol>
 * </p>
 */
public class DocxTemplateFiller {

    private static final String TAG = "DocxTemplateFiller";

    // OOXML namespaces
    private static final String NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    // Entries that should be stored as raw bytes (not parsed as XML)
    private static final Set<String> BINARY_EXTENSIONS = new java.util.HashSet<>();
    static {
        BINARY_EXTENSIONS.add(".png");
        BINARY_EXTENSIONS.add(".jpg");
        BINARY_EXTENSIONS.add(".jpeg");
        BINARY_EXTENSIONS.add(".gif");
        BINARY_EXTENSIONS.add(".bmp");
        BINARY_EXTENSIONS.add(".wmf");
        BINARY_EXTENSIONS.add(".emf");
    }

    /**
     * Fill a docx template with AI-generated section content.
     *
     * @param resId       Android resource ID (e.g. R.raw.typeset_template_paper)
     * @param typesetType one of "paper", "gov", "contract", "general"
     * @param sections    map of sectionKey → AI-generated text content (plain text)
     * @param context     Android context for resource access
     * @return File pointing to filled .docx in getExternalFilesDir("Documents"),
     *         or null on failure
     */
    public static File fillTemplate(int resId, String typesetType,
                                     Map<String, String> sections, Context context) {
        LinkedHashMap<String, String> sectionMap = TemplateSectionMap.getSectionMap(typesetType);
        if (sectionMap == null) {
            Log.e(TAG, "Unknown typeset type: " + typesetType);
            return null;
        }

        try (InputStream is = context.getResources().openRawResource(resId)) {
            // Step 1: Unzip template into memory
            Map<String, byte[]> rawEntries = new HashMap<>();
            Map<String, Document> xmlEntries = new HashMap<>();

            unzipTemplate(is, rawEntries, xmlEntries);

            // Step 2: Modify word/document.xml
            Document docXml = xmlEntries.get("word/document.xml");
            if (docXml == null) {
                Log.e(TAG, "word/document.xml not found in template");
                return null;
            }
            fillDocumentXml(docXml, sectionMap, sections);

            // Step 3: Re-zip to output file
            String title = sections.getOrDefault("title", "typeset_output");
            String safeName = sanitizeFilename(title);
            if (!safeName.endsWith(".docx")) safeName += ".docx";

            File outputDir = new File(context.getExternalFilesDir("Documents"), "typeset");
            if (!outputDir.exists()) outputDir.mkdirs();
            File outputFile = new File(outputDir, safeName);

            writeDocx(rawEntries, xmlEntries, outputFile);

            Log.i(TAG, "Template filled successfully: " + outputFile.getAbsolutePath());
            return outputFile;

        } catch (Exception e) {
            Log.e(TAG, "Failed to fill template: " + e.getMessage(), e);
            return null;
        }
    }

    // ---- Unzip ----

    static void unzipTemplate(InputStream is, Map<String, byte[]> rawEntries,
                              Map<String, Document> xmlEntries) throws Exception {
        byte[] buffer = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = entry.getName();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                byte[] data = baos.toByteArray();

                if (isXmlEntry(name) && !isBinaryByExtension(name)) {
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document doc = builder.parse(new ByteArrayInputStream(data));
                        xmlEntries.put(name, doc);
                    } catch (Exception e) {
                        // Fall back to raw if XML parsing fails
                        Log.w(TAG, "XML parse failed for " + name + ", storing raw: " + e.getMessage());
                        rawEntries.put(name, data);
                    }
                } else {
                    rawEntries.put(name, data);
                }
            }
        }
    }

    // ---- Modify document.xml ----

    static void fillDocumentXml(Document doc, LinkedHashMap<String, String> sectionMap,
                                Map<String, String> sections) {
        Element body = findBody(doc);
        if (body == null) {
            Log.e(TAG, "No <w:body> found in document.xml");
            return;
        }

        Set<String> usedKeys = new LinkedHashSet<>();

        // Collect all paragraph elements (we'll modify the DOM during iteration,
        // so collect first, then process)
        List<Element> paragraphs = new ArrayList<>();
        NodeList children = body.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "p".equals(child.getLocalName())) {
                paragraphs.add((Element) child);
            }
        }
        Log.i(TAG, "fillDocumentXml: scanning " + paragraphs.size() + " top-level paragraphs in w:body");

        for (Element p : paragraphs) {
            String paraText = getParagraphText(p);
            if (paraText.isEmpty()) continue;

            String sectionKey = TemplateSectionMap.matchPlaceholder(paraText, sectionMap);
            if (sectionKey == null) continue;

            // Resolve duplicate placeholders (e.g. second "身份证号码：" → party_b_id)
            sectionKey = TemplateSectionMap.resolveDuplicateKey(sectionKey, usedKeys);
            usedKeys.add(sectionKey);

            String content = sections.get(sectionKey);
            if (content == null || content.isEmpty()) {
                Log.w(TAG, "No content for section key: " + sectionKey);
                continue;
            }

            // Split content into paragraphs (double-newline separator)
            String[] paraContents = content.split("\n\n");
            if (paraContents.length == 0) continue;

            // Replace first paragraph's runs with first segment
            replaceParagraphText(p, paraContents[0]);

            // Create additional paragraphs for remaining segments
            for (int j = 1; j < paraContents.length; j++) {
                String seg = paraContents[j].trim();
                if (seg.isEmpty()) continue;
                Element newP = cloneParagraphWithText(p, seg);
                Node nextSibling = p.getNextSibling();
                if (nextSibling != null) {
                    body.insertBefore(newP, nextSibling);
                } else {
                    body.appendChild(newP);
                }
                p = newP; // so subsequent paragraphs are inserted after this one
            }

            Log.i(TAG, "Filled section [" + sectionKey + "] with " + content.length() + " chars, "
                    + paraContents.length + " paragraph(s)");
        }

        // Log unfilled sections
        for (Map.Entry<String, String> sEntry : sections.entrySet()) {
            if (!usedKeys.contains(sEntry.getKey())) {
                Log.i(TAG, "Section key NOT matched to any placeholder: " + sEntry.getKey());
            }
        }
        Log.i(TAG, "fillDocumentXml: done — " + usedKeys.size() + " sections filled, "
                + (sections.size() - usedKeys.size()) + " unmatched");
    }

    // ---- DOM helpers ----

    /**
     * Find <w:body> element in document.xml, handling namespaces.
     */
    static Element findBody(Document doc) {
        NodeList bodies = doc.getElementsByTagNameNS(NS_W, "body");
        if (bodies.getLength() > 0) return (Element) bodies.item(0);

        // Fallback: search without namespace
        NodeList all = doc.getElementsByTagName("body");
        if (all.getLength() > 0) return (Element) all.item(0);

        return null;
    }

    /**
     * Get the concatenated text of all <w:t> elements in a paragraph.
     */
    static String getParagraphText(Element paragraph) {
        StringBuilder sb = new StringBuilder();
        NodeList texts = paragraph.getElementsByTagNameNS(NS_W, "t");
        if (texts.getLength() == 0) {
            // Fallback: without namespace
            texts = paragraph.getElementsByTagName("t");
        }
        for (int i = 0; i < texts.getLength(); i++) {
            Node t = texts.item(i);
            if (t.getTextContent() != null) {
                sb.append(t.getTextContent());
            }
        }
        return sb.toString().trim();
    }

    /**
     * Replace the text content of a paragraph.
     * Preserves the first run's w:rPr (font, size, bold, color, etc.) and
     * the paragraph's w:pPr (style reference).
     */
    static void replaceParagraphText(Element paragraph, String newText) {
        // Find the first run to extract its properties
        NodeList runs = getChildElementsNS(paragraph, NS_W, "r");
        Element rPrTemplate = null;
        if (runs.getLength() > 0) {
            Element firstRun = (Element) runs.item(0);
            NodeList rPrs = getChildElementsNS(firstRun, NS_W, "rPr");
            if (rPrs.getLength() > 0) {
                rPrTemplate = (Element) rPrs.item(0);
            }
        }

        // Remove all existing <w:r> children
        while (runs.getLength() > 0) {
            paragraph.removeChild(runs.item(0));
            runs = getChildElementsNS(paragraph, NS_W, "r");
        }

        // Create a new <w:r> with the replacement text
        Document doc = paragraph.getOwnerDocument();
        Element newRun = doc.createElementNS(NS_W, "w:r");

        if (rPrTemplate != null) {
            Node importedRPr = doc.importNode(rPrTemplate, true);
            newRun.appendChild(importedRPr);
        }

        Element newTextEl = doc.createElementNS(NS_W, "w:t");
        newTextEl.setTextContent(newText);
        // Preserve whitespace
        newTextEl.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve");
        newRun.appendChild(newTextEl);

        paragraph.appendChild(newRun);
    }

    /**
     * Create a new paragraph cloned from the source paragraph's w:pPr,
     * with the given text content.
     */
    static Element cloneParagraphWithText(Element sourceParagraph, String text) {
        Document doc = sourceParagraph.getOwnerDocument();
        Element newP = doc.createElementNS(NS_W, "w:p");

        // Clone paragraph properties (style reference, spacing, etc.)
        NodeList pPrs = getChildElementsNS(sourceParagraph, NS_W, "pPr");
        if (pPrs.getLength() > 0) {
            Node importedPPr = doc.importNode(pPrs.item(0), true);
            newP.appendChild(importedPPr);
        }

        // Clone run properties from first run
        NodeList runs = getChildElementsNS(sourceParagraph, NS_W, "r");
        Element newRun = doc.createElementNS(NS_W, "w:r");
        if (runs.getLength() > 0) {
            NodeList rPrs = getChildElementsNS((Element) runs.item(0), NS_W, "rPr");
            if (rPrs.getLength() > 0) {
                Node importedRPr = doc.importNode(rPrs.item(0), true);
                newRun.appendChild(importedRPr);
            }
        }

        Element newText = doc.createElementNS(NS_W, "w:t");
        newText.setTextContent(text);
        newText.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve");
        newRun.appendChild(newText);
        newP.appendChild(newRun);

        return newP;
    }

    private static NodeList getChildElementsNS(Element parent, String ns, String localName) {
        // Simple helper: collect matching child elements
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && ns.equals(child.getNamespaceURI())
                    && localName.equals(child.getLocalName())) {
                result.add((Element) child);
            }
        }
        return new SimpleNodeList(result);
    }

    // ---- Re-zip ----

    static void writeDocx(Map<String, byte[]> rawEntries,
                          Map<String, Document> xmlEntries, File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Write raw (binary) entries first
            for (Map.Entry<String, byte[]> entry : rawEntries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue());
                zos.closeEntry();
            }

            // Write XML entries (serialize DOM → bytes)
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            for (Map.Entry<String, Document> entry : xmlEntries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                transformer.transform(new DOMSource(entry.getValue()), new StreamResult(baos));
                zos.write(baos.toByteArray());
                zos.closeEntry();
            }

            zos.finish();
        }
    }

    // ---- Utilities ----

    private static boolean isXmlEntry(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".xml") || lower.endsWith(".rels");
    }

    private static boolean isBinaryByExtension(String name) {
        String lower = name.toLowerCase();
        for (String ext : BINARY_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    /**
     * Sanitize a string for use as a filename.
     */
    private static String sanitizeFilename(String name) {
        if (name == null || name.isEmpty()) return "typeset_output.docx";
        // Remove characters unsafe for filenames
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "")
                .replaceAll("\n", " ")
                .replaceAll("\r", "")
                .trim();
        if (safe.length() > 80) safe = safe.substring(0, 80);
        if (safe.isEmpty()) return "typeset_output.docx";
        return safe;
    }

    // ---- SimpleNodeList (internal helper) ----

    private static class SimpleNodeList implements NodeList {
        private final List<Element> elements;

        SimpleNodeList(List<Element> elements) {
            this.elements = elements;
        }

        @Override
        public Node item(int index) {
            return (index >= 0 && index < elements.size()) ? elements.get(index) : null;
        }

        @Override
        public int getLength() {
            return elements.size();
        }
    }
}
