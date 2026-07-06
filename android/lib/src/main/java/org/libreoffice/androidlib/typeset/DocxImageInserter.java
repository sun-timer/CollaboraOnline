package org.libreoffice.androidlib.typeset;

import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * Post-processes a filled docx to replace {@code [图N]} markers with real images.
 *
 * <p>Operates entirely in zip/DOM space (no JNI, no file I/O beyond the zip itself).
 * Reuses the same unzip/parse/re-zip pattern as {@link DocxTemplateFiller}.</p>
 */
public class DocxImageInserter {

    private static final String TAG = "DocxImageInserter";

    // OOXML namespaces
    private static final String NS_W   = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String NS_WP  = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final String NS_A   = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String NS_R   = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture";
    private static final String NS_REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String NS_CT  = "http://schemas.openxmlformats.org/package/2006/content-types";

    private static final String REL_TYPE_IMAGE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image";
    private static final String CT_RELS =
            "http://schemas.openxmlformats.org/package/2006/content-types";

    // Default image size when source size is unknown: 5 inches in EMU
    private static final long DEFAULT_CX = 5L * 914400;
    private static final long DEFAULT_CY = 5L * 914400;

    // Pattern to match [图N]
    private static final Pattern IMG_MARKER = Pattern.compile("\\[图(\\d+)\\]");

    private DocxImageInserter() {}

    /**
     * Replace all {@code [图N]} markers in the output docx with real images.
     *
     * @param outputDocx  filled docx file (modified in-place)
     * @param images      marker name → image entry map
     */
    public static void insertImages(File outputDocx, Map<String, TypesetImageEntry> images) {
        if (outputDocx == null || !outputDocx.exists()) {
            Log.w(TAG, "insertImages_skipped output docx missing");
            return;
        }
        if (images == null || images.isEmpty()) {
            Log.i(TAG, "insertImages_skipped no images");
            return;
        }

        try {
            // 1. Unzip into memory
            Map<String, byte[]> rawEntries = new HashMap<>();
            Map<String, Document> xmlEntries = new HashMap<>();

            try (InputStream is = new java.io.FileInputStream(outputDocx)) {
                unzipDocx(is, rawEntries, xmlEntries);
            }

            // 2. Modify word/document.xml — replace markers with drawings
            Document docXml = xmlEntries.get("word/document.xml");
            if (docXml == null) {
                Log.e(TAG, "word/document.xml not found");
                return;
            }

            // 3. Parse rels to find existing image count and avoid rId conflicts
            Document relsDoc = xmlEntries.get("word/_rels/document.xml.rels");
            if (relsDoc == null) {
                Log.e(TAG, "word/_rels/document.xml.rels not found");
                return;
            }

            int nextRId = getMaxRId(relsDoc) + 1;
            int nextDocPrId = getMaxDocPrId(docXml) + 1;
            int nextImageIndex = getMediaFileCount(rawEntries) + 1;

            // 4. Modify [Content_Types].xml if needed
            Document ctDoc = xmlEntries.get("[Content_Types].xml");

            int imagesInserted = 0;
            NodeList wTs = docXml.getElementsByTagNameNS(NS_W, "t");
            for (int i = 0; i < wTs.getLength(); i++) {
                Element wTElem = (Element) wTs.item(i);
                String text = wTElem.getTextContent();
                if (text == null) continue;

                Matcher m = IMG_MARKER.matcher(text);
                if (!m.find()) continue;

                // The marker text (e.g. "[图1]")
                String fullMarker = m.group();
                String markerNum = m.group(1);
                TypesetImageEntry entry = images.get("图" + markerNum);
                if (entry == null) {
                    Log.w(TAG, "image_marker_not_found marker=" + fullMarker);
                    continue;
                }

                Element runElem = (Element) wTElem.getParentNode();
                Element paraElem = (Element) runElem.getParentNode();

                int markerStart = m.start();
                int markerEnd = m.end();
                String beforeText = text.substring(0, markerStart);
                String afterText = text.substring(markerEnd);
                boolean beforeEmpty = beforeText.isEmpty();
                boolean afterEmpty = afterText.isEmpty();

                // RId and docPr id for this image
                String rId = "rId" + nextRId++;
                int docPrId = nextDocPrId++;

                // Image filename in media/
                String imageFileName = "image" + nextImageIndex + "." + entry.extension;
                nextImageIndex++;

                // Build the drawing element
                long cx = entry.cx > 0 ? entry.cx : DEFAULT_CX;
                long cy = entry.cy > 0 ? entry.cy : DEFAULT_CY;
                Element drawingEl = buildDrawingElement(docXml, rId, docPrId, fullMarker, cx, cy);

                // Split the run: before text + drawing + after text
                Node parent = runElem.getParentNode();
                Node refChild = runElem.getNextSibling();

                // If there's text after the marker, create a new run
                if (!afterEmpty) {
                    Element afterRun = createTextRun(docXml, runElem, afterText);
                    parent.insertBefore(afterRun, refChild);
                }

                // Insert the drawing run
                Element drawRun = createDrawingRun(docXml, runElem, drawingEl);
                parent.insertBefore(drawRun, refChild);

                // If there's text before the marker, keep current run with shortened text
                if (!beforeEmpty) {
                    wTElem.setTextContent(beforeText);
                    wTElem.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve");
                } else {
                    // No text before the marker, remove original run entirely
                    parent.removeChild(runElem);
                }

                // Add relationship to rels
                addImageRelationship(relsDoc, rId, imageFileName);

                // Add content type if needed
                addContentTypeIfNeeded(ctDoc, entry.extension, entry.mimeType);

                // Add image file to raw entries
                rawEntries.put("word/media/" + imageFileName, entry.imageData);

                imagesInserted++;
                Log.i(TAG, "image_marker_replaced marker=" + fullMarker + " rId=" + rId
                        + " file=word/media/" + imageFileName + " cx=" + cx + " cy=" + cy);
            }

            // 5. Re-zip
            writeDocx(rawEntries, xmlEntries, outputDocx);

            Log.i(TAG, "images_inserted_done count=" + imagesInserted
                    + " totalImages=" + images.size());

        } catch (Exception e) {
            Log.e(TAG, "images_insert_failed", e);
        }
    }

    // ---- Build drawing XML ----

    /**
     * Build a &lt;w:drawing&gt; element referencing the image by rId.
     */
    private static Element buildDrawingElement(Document doc, String rId,
                                                int docPrId, String name,
                                                long cx, long cy) {
        // Construct the drawing XML as a string for clarity
        String nsW  = NS_W;
        String nsWp = NS_WP;
        String nsA  = NS_A;
        String nsR  = NS_R;
        String nsPic = NS_PIC;

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<w:drawing xmlns:w=\"" + nsW + "\""
                + " xmlns:wp=\"" + nsWp + "\""
                + " xmlns:a=\"" + nsA + "\""
                + " xmlns:r=\"" + nsR + "\""
                + " xmlns:pic=\"" + nsPic + "\">"
                + "  <wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">"
                + "    <wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/>"
                + "    <wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>"
                + "    <wp:docPr id=\"" + docPrId + "\" name=\"" + xmlEscape(name) + "\"/>"
                + "    <wp:cNvGraphicFramePr>"
                + "      <a:graphicFrameLocks noChangeAspect=\"1\"/>"
                + "    </wp:cNvGraphicFramePr>"
                + "    <a:graphic>"
                + "      <a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
                + "        <pic:pic>"
                + "          <pic:nvPicPr>"
                + "            <pic:cNvPr id=\"" + docPrId + "\" name=\"" + xmlEscape(name) + "\"/>"
                + "            <pic:cNvPicPr/>"
                + "          </pic:nvPicPr>"
                + "          <pic:blipFill>"
                + "            <a:blip r:embed=\"" + rId + "\"/>"
                + "          </pic:blipFill>"
                + "          <pic:spPr>"
                + "            <a:xfrm>"
                + "              <a:off x=\"0\" y=\"0\"/>"
                + "              <a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/>"
                + "            </a:xfrm>"
                + "            <a:prstGeom prst=\"rect\"/>"
                + "            <a:noFill/>"
                + "          </pic:spPr>"
                + "        </pic:pic>"
                + "      </a:graphicData>"
                + "    </a:graphic>"
                + "  </wp:inline>"
                + "</w:drawing>";

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document tempDoc = builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
            return (Element) doc.importNode(tempDoc.getDocumentElement(), true);
        } catch (Exception e) {
            Log.e(TAG, "build_drawing_failed", e);
            return null;
        }
    }

    /**
     * Create a &lt;w:r&gt; containing a &lt;w:drawing&gt;.
     */
    private static Element createDrawingRun(Document doc, Element templateRun,
                                             Element drawingEl) {
        Element newRun = doc.createElementNS(NS_W, "w:r");

        // Copy run properties from template
        NodeList rPrs = templateRun.getElementsByTagNameNS(NS_W, "rPr");
        if (rPrs.getLength() > 0 && rPrs.item(0).getParentNode() == templateRun) {
            Node importedRPr = doc.importNode(rPrs.item(0), true);
            newRun.appendChild(importedRPr);
        }

        if (drawingEl != null) {
            newRun.appendChild(drawingEl);
        }

        return newRun;
    }

    /**
     * Create a &lt;w:r&gt; with simple text content, cloning rPr from a template run.
     */
    private static Element createTextRun(Document doc, Element templateRun, String text) {
        Element newRun = doc.createElementNS(NS_W, "w:r");

        // Copy run properties from template
        NodeList rPrs = templateRun.getElementsByTagNameNS(NS_W, "rPr");
        if (rPrs.getLength() > 0 && rPrs.item(0).getParentNode() == templateRun) {
            Node importedRPr = doc.importNode(rPrs.item(0), true);
            newRun.appendChild(importedRPr);
        }

        Element newText = doc.createElementNS(NS_W, "w:t");
        newText.setTextContent(text);
        newText.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve");
        newRun.appendChild(newText);

        return newRun;
    }

    // ---- Relationship helpers ----

    private static int getMaxRId(Document relsDoc) {
        int max = 0;
        NodeList rels = relsDoc.getDocumentElement().getChildNodes();
        for (int i = 0; i < rels.getLength(); i++) {
            Node n = rels.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                String id = ((Element) n).getAttribute("Id");
                if (id != null && id.startsWith("rId")) {
                    try {
                        int num = Integer.parseInt(id.substring(3));
                        if (num > max) max = num;
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return max;
    }

    private static int getMaxDocPrId(Document docXml) {
        int max = 0;
        NodeList docPrs = docXml.getElementsByTagNameNS(NS_WP, "docPr");
        for (int i = 0; i < docPrs.getLength(); i++) {
            String id = ((Element) docPrs.item(i)).getAttribute("id");
            if (id != null) {
                try {
                    int num = Integer.parseInt(id);
                    if (num > max) max = num;
                } catch (NumberFormatException ignored) {}
            }
        }
        return max;
    }

    private static int getMediaFileCount(Map<String, byte[]> rawEntries) {
        int max = 0;
        Pattern p = Pattern.compile("word/media/image(\\d+)\\.");
        for (String name : rawEntries.keySet()) {
            Matcher m = p.matcher(name);
            if (m.find()) {
                try {
                    int num = Integer.parseInt(m.group(1));
                    if (num > max) max = num;
                } catch (NumberFormatException ignored) {}
            }
        }
        return max;
    }

    private static void addImageRelationship(Document relsDoc, String rId, String target) {
        Element root = relsDoc.getDocumentElement();
        Element rel = relsDoc.createElementNS(NS_REL, "Relationship");
        rel.setAttribute("Id", rId);
        rel.setAttribute("Type", REL_TYPE_IMAGE);
        rel.setAttribute("Target", "media/" + target);
        root.appendChild(rel);
    }

    // ---- Content types helpers ----

    private static void addContentTypeIfNeeded(Document ctDoc, String extension, String mimeType) {
        if (ctDoc == null) return;

        Element root = ctDoc.getDocumentElement();
        NodeList defaults = root.getChildNodes();
        for (int i = 0; i < defaults.getLength(); i++) {
            Node n = defaults.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE
                    && "Default".equals(n.getLocalName())) {
                String ext = ((Element) n).getAttribute("Extension");
                if (extension.equalsIgnoreCase(ext)) {
                    return; // Already registered
                }
            }
        }

        Element defaultEl = ctDoc.createElementNS(NS_CT, "Default");
        defaultEl.setAttribute("Extension", extension);
        defaultEl.setAttribute("ContentType", mimeType);
        root.appendChild(defaultEl);
    }

    // ---- Zip/DOM utilities (mirroring DocxTemplateFiller) ----

    static void unzipDocx(InputStream is, Map<String, byte[]> rawEntries,
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

                if (name.endsWith(".xml") || name.endsWith(".rels")) {
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document doc = builder.parse(new ByteArrayInputStream(data));
                        xmlEntries.put(name, doc);
                    } catch (Exception e) {
                        Log.w(TAG, "XML parse failed for " + name + ", storing raw: " + e.getMessage());
                        rawEntries.put(name, data);
                    }
                } else {
                    rawEntries.put(name, data);
                }
            }
        }
    }

    static void writeDocx(Map<String, byte[]> rawEntries,
                           Map<String, Document> xmlEntries,
                           File outputFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (Map.Entry<String, byte[]> entry : rawEntries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue());
                zos.closeEntry();
            }

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

    private static String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
