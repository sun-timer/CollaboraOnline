package org.libreoffice.androidlib.template;

import android.content.Context;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Fills placeholders in a .pptx template by manipulating the ZIP+XML structure in memory.
 *
 * <p>Uses only JDK built-in APIs ({@link java.util.zip.ZipInputStream},
 * {@link javax.xml.parsers.DocumentBuilder}) — no external dependencies.</p>
 *
 * <p>Process:
 * <ol>
 *   <li>Read template .pptx from Android assets as InputStream</li>
 *   <li>Unzip into memory (non-XML &rarr; byte[], XML &rarr; DOM Document)</li>
 *   <li>For each slide in slideContents, walk {@code <a:t>} text nodes in
 *       {@code ppt/slides/slideN.xml}, match {@code {{placeholder}}} and replace</li>
 *   <li>Re-zip to a temp .pptx file</li>
 * </ol>
 * </p>
 *
 * <p>Supported placeholders:
 * <ul>
 *   <li>Simple: {@code {{title}}}, {@code {{subtitle}}}, {@code {{author}}},
 *       {@code {{date}}}, {@code {{toc_content}}}, {@code {{section_title}}},
 *       {@code {{section_number}}}, {@code {{section_overview}}}, {@code {{contact_info}}}</li>
 *   <li>Indexed: {@code {{content_points[N]}}}, {@code {{detailed_content[N]}}}
 *       where N is a 0-based integer</li>
 * </ul>
 * </p>
 */
public class PptxTemplateFiller {

    private static final String TAG = "PptxTemplateFiller";

    /**
     * Pattern to extract the index from indexed placeholders like {@code content_points[3]}.
     * The key before the brackets is in group 1 (e.g. "content_points") and the
     * numeric index is in group 2 (e.g. "3").
     */
    private static final Pattern INDEXED_PLACEHOLDER_PATTERN =
            Pattern.compile("^(.*?)\\[(\\d+)\\]$");

    /** Matches {@code {{key}}} even when split across XML runs is reconstructed at paragraph level. */
    private static final Pattern PLACEHOLDER_FULL_PATTERN =
            Pattern.compile("\\{\\{([^{}]+)\\}\\}");

    private static final Pattern SLD_ID_LIST_PATTERN =
            Pattern.compile("(<(?:p:)?sldIdLst>)(.*?)(</(?:p:)?sldIdLst>)", Pattern.DOTALL);

    private static final Pattern SLIDE_REL_PATTERN = Pattern.compile(
            "<Relationship\\s[^>]*Type=\"http://schemas\\.openxmlformats\\.org/officeDocument/2006/relationships/slide\"[^>]*/>\\s*");

    private static final Pattern RELATIONSHIP_ID_PATTERN =
            Pattern.compile("Id=\"rId(\\d+)\"");

    private static final Pattern SLD_ID_ATTR_PATTERN =
            Pattern.compile("<(?:p:)?sldId id=\"(\\d+)\"");

    private static final Pattern SLIDE_CONTENT_TYPE_OVERRIDE = Pattern.compile(
            "<Override\\s[^>]*PartName=\"/ppt/slides/(?:slide\\d+\\.xml|_rels/slide\\d+\\.xml\\.rels)\"[^>]*/>\\s*");

    /**
     * Fills a .pptx template with slide-specific content.
     *
     * @param context       Android context (for AssetManager)
     * @param assetPath     Path to template .pptx relative to assets/, e.g.
     *                      {@code "templates/impress/business/template.pptx"}
     * @param slideContents Map of slideIndex (1-based) &rarr; placeholder &rarr; value
     * @return File pointing to the filled temporary .pptx
     * @throws IOException if reading the template or writing the output fails
     */
    public static File fillTemplate(
            Context context,
            String assetPath,
            Map<Integer, Map<String, String>> slideContents
    ) throws IOException {
        if (context == null || assetPath == null || assetPath.isEmpty()) {
            throw new IllegalArgumentException("context and assetPath must not be null/empty");
        }
        if (slideContents == null || slideContents.isEmpty()) {
            throw new IllegalArgumentException("slideContents must not be null/empty");
        }

        InputStream is = null;
        try {
            is = context.getAssets().open(assetPath);

            // Step 1: Unzip template into memory
            Map<String, byte[]> rawEntries = new HashMap<>();
            Map<String, Document> xmlEntries = new HashMap<>();
            unzipTemplate(is, rawEntries, xmlEntries);

            // Step 2: Fill placeholders per slide
            fillPlaceholders(xmlEntries, slideContents);

            // Step 3: Re-zip to temp file
            File tempFile = File.createTempFile("ppt_filled_", ".pptx");
            rezipEntries(rawEntries, xmlEntries, tempFile);

            Log.i(TAG, "Template filled successfully: " + tempFile.getAbsolutePath());
            return tempFile;

        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close asset stream: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Fills placeholders, assembles only the planned slides in order, trims unused
     * template slides, and writes to the given output file.
     */
    public static File fillAndAssemble(
            Context context,
            String assetPath,
            List<ImpressSlidePlanner.PlannedSlide> slidePlan,
            File outputFile,
            int originalSlideCount
    ) throws IOException {
        if (context == null || assetPath == null || assetPath.isEmpty()) {
            throw new IllegalArgumentException("context and assetPath must not be null/empty");
        }
        if (slidePlan == null || slidePlan.isEmpty()) {
            throw new IllegalArgumentException("slidePlan must not be null/empty");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("outputFile must not be null");
        }

        InputStream is = null;
        try {
            is = context.getAssets().open(assetPath);
            Map<String, byte[]> zipEntries = unzipAllAsBytes(is);

            List<byte[]> outputSlideBytes = new ArrayList<>();
            List<byte[]> outputSlideRels = new ArrayList<>();

            for (ImpressSlidePlanner.PlannedSlide planned : slidePlan) {
                int srcIdx = planned.sourceTemplateSlideIndex;
                String srcName = "ppt/slides/slide" + srcIdx + ".xml";
                byte[] srcBytes = zipEntries.get(srcName);
                if (srcBytes == null) {
                    Log.w(TAG, "fillAndAssemble: source slide missing " + srcName);
                    continue;
                }
                byte[] filledBytes = fillSlideBytes(srcBytes, planned.placeholders);
                outputSlideBytes.add(filledBytes);

                String relsKey = "ppt/slides/_rels/slide" + srcIdx + ".xml.rels";
                byte[] relsBytes = zipEntries.get(relsKey);
                outputSlideRels.add(relsBytes != null ? relsBytes : new byte[0]);
            }

            int outputCount = outputSlideBytes.size();
            removeSlideEntriesFromZip(zipEntries);

            for (int i = 0; i < outputCount; i++) {
                int outIdx = i + 1;
                zipEntries.put("ppt/slides/slide" + outIdx + ".xml", outputSlideBytes.get(i));
                zipEntries.put("ppt/slides/_rels/slide" + outIdx + ".xml.rels", outputSlideRels.get(i));
            }

            byte[] presBytes = zipEntries.get("ppt/presentation.xml");
            if (presBytes != null) {
                updatePresentationSlideReferences(zipEntries, presBytes, outputCount);
            } else {
                Log.w(TAG, "fillAndAssemble: presentation.xml missing");
            }
            updateContentTypesBytes(zipEntries, outputCount);

            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            rezipAllBytes(zipEntries, outputFile);

            Log.i(TAG, "ppt_assembled outputSlides=" + outputCount
                    + " trimmedFrom=" + originalSlideCount
                    + " path=" + outputFile.getAbsolutePath());
            return outputFile;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close asset stream: " + e.getMessage());
                }
            }
        }
    }

    /** Unzip entire .pptx into a single map preserving original bytes for every entry. */
    static Map<String, byte[]> unzipAllAsBytes(InputStream is) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
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
                entries.put(name, baos.toByteArray());
            }
        }
        return entries;
    }

    private static final Pattern PARAGRAPH_BLOCK_PATTERN =
            Pattern.compile("(<a:p\\b[^>]*>)(.*?)(</a:p>)", Pattern.DOTALL);

    private static final Pattern TEXT_RUN_PATTERN =
            Pattern.compile("(<a:t(?:\\s[^>]*)?>)([^<]*)(</a:t>)");

    /**
     * Fills placeholders in slide XML while preserving original bytes/namespace structure.
     * Avoids DOM re-serialization which breaks OOXML prefixes on Android.
     */
    static byte[] fillSlideBytes(byte[] slideXml, Map<String, String> placeholders) {
        if (slideXml == null || slideXml.length == 0 || placeholders == null || placeholders.isEmpty()) {
            return slideXml;
        }
        String xml = new String(slideXml, StandardCharsets.UTF_8);
        if (!xml.contains("{{")) {
            return slideXml;
        }

        Matcher pMatcher = PARAGRAPH_BLOCK_PATTERN.matcher(xml);
        StringBuffer out = new StringBuffer();
        int updatedParagraphs = 0;
        while (pMatcher.find()) {
            String open = pMatcher.group(1);
            String body = pMatcher.group(2);
            String close = pMatcher.group(3);
            String newBody = fillParagraphBody(body, placeholders);
            if (!newBody.equals(body)) {
                updatedParagraphs++;
                pMatcher.appendReplacement(out,
                        Matcher.quoteReplacement(open + newBody + close));
            } else {
                pMatcher.appendReplacement(out, Matcher.quoteReplacement(pMatcher.group(0)));
            }
        }
        pMatcher.appendTail(out);
        Log.i(TAG, "fillSlideBytes: updated " + updatedParagraphs + " paragraph(s)");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String fillParagraphBody(String body, Map<String, String> placeholders) {
        Matcher tMatcher = TEXT_RUN_PATTERN.matcher(body);
        List<String> texts = new ArrayList<>();
        while (tMatcher.find()) {
            texts.add(tMatcher.group(2));
        }
        if (texts.isEmpty()) return body;

        StringBuilder fullText = new StringBuilder();
        for (String part : texts) {
            fullText.append(part);
        }
        String joined = fullText.toString();
        if (!joined.contains("{{")) return body;

        Matcher phMatcher = PLACEHOLDER_FULL_PATTERN.matcher(joined);
        if (!phMatcher.find()) return body;
        phMatcher.reset();

        StringBuffer replaced = new StringBuffer();
        boolean changed = false;
        while (phMatcher.find()) {
            String key = phMatcher.group(1).trim();
            String value = lookupPlaceholderValue(key, placeholders);
            phMatcher.appendReplacement(replaced, Matcher.quoteReplacement(value));
            changed = true;
        }
        phMatcher.appendTail(replaced);
        if (!changed) return body;

        String finalText = escapeXmlText(replaced.toString());
        tMatcher.reset();
        StringBuffer newBody = new StringBuffer();
        int runIndex = 0;
        while (tMatcher.find()) {
            String text = runIndex == 0 ? finalText : "";
            tMatcher.appendReplacement(newBody,
                    Matcher.quoteReplacement(tMatcher.group(1) + text + tMatcher.group(3)));
            runIndex++;
        }
        tMatcher.appendTail(newBody);
        return newBody.toString();
    }

    private static String escapeXmlText(String value) {
        if (value == null || value.isEmpty()) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Higher score means placeholders are less fragmented across {@code <a:t>} runs.
     */
    static int scoreSlidePlaceholderCleanliness(byte[] slideXml) {
        if (slideXml == null || slideXml.length == 0) return 0;
        String xml = new String(slideXml, StandardCharsets.UTF_8);
        Matcher pMatcher = PARAGRAPH_BLOCK_PATTERN.matcher(xml);
        int score = 1000;
        while (pMatcher.find()) {
            String body = pMatcher.group(2);
            Matcher tMatcher = TEXT_RUN_PATTERN.matcher(body);
            int runCount = 0;
            boolean hasPlaceholderFragment = false;
            while (tMatcher.find()) {
                runCount++;
                String text = tMatcher.group(2);
                if (text.contains("{{") || text.contains("}}")) {
                    hasPlaceholderFragment = true;
                }
            }
            if (hasPlaceholderFragment && runCount > 1) {
                score -= (runCount - 1) * 10;
            }
        }
        return score;
    }

    static Document parseDocument(byte[] data) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(data));
        } catch (Exception e) {
            throw new IOException("Failed to parse XML document: " + e.getMessage(), e);
        }
    }

    static byte[] serializeDocument(Document doc) throws IOException {
        try {
            return serializeDocumentUnchecked(doc);
        } catch (Exception e) {
            throw new IOException("Failed to serialize XML document: " + e.getMessage(), e);
        }
    }

    private static byte[] serializeDocumentUnchecked(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(baos));
        return baos.toByteArray();
    }

    // ---- Unzip ----

    private static void removeSlideEntriesFromZip(Map<String, byte[]> zipEntries) {
        Set<String> keys = new HashSet<>(zipEntries.keySet());
        for (String key : keys) {
            if (isSlideEntry(key)) {
                zipEntries.remove(key);
            }
        }
    }

    private static boolean isSlideEntry(String key) {
        return key.startsWith("ppt/slides/slide") && key.endsWith(".xml")
                || key.startsWith("ppt/slides/_rels/slide") && key.endsWith(".xml.rels");
    }

    /**
     * Rewires presentation.xml + presentation.xml.rels so each output slide gets a fresh,
     * non-conflicting relationship id. Flat template uses rId61+ for slides while rId3
     * is a slideMaster — hardcoding rId3.. breaks LibreOffice rendering (white slides).
     */
    private static void updatePresentationSlideReferences(
            Map<String, byte[]> zipEntries, byte[] presBytes, int outputCount) {
        String relsKey = "ppt/_rels/presentation.xml.rels";
        byte[] relsBytes = zipEntries.get(relsKey);
        if (relsBytes == null) {
            Log.w(TAG, "updatePresentationSlideReferences: presentation.xml.rels missing");
            return;
        }

        String relsXml = new String(relsBytes, StandardCharsets.UTF_8);
        relsXml = SLIDE_REL_PATTERN.matcher(relsXml).replaceAll("");

        int nextRIdNum = findMaxRelationshipIdNumber(relsXml) + 1;
        List<String> slideRIds = new ArrayList<>();
        StringBuilder additions = new StringBuilder();
        for (int i = 0; i < outputCount; i++) {
            String rId = "rId" + nextRIdNum;
            nextRIdNum++;
            slideRIds.add(rId);
            additions.append("<Relationship Id=\"").append(rId)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide")
                    .append(i + 1).append(".xml\"/>");
        }

        int insertAt = relsXml.lastIndexOf("</Relationships>");
        if (insertAt < 0) {
            Log.w(TAG, "updatePresentationSlideReferences: Relationships root missing");
            return;
        }
        relsXml = relsXml.substring(0, insertAt) + additions + relsXml.substring(insertAt);
        zipEntries.put(relsKey, relsXml.getBytes(StandardCharsets.UTF_8));

        String presXml = new String(presBytes, StandardCharsets.UTF_8);
        int nextSldId = findMaxSldIdNumber(presXml) + 1;
        StringBuilder inner = new StringBuilder();
        for (int i = 0; i < slideRIds.size(); i++) {
            inner.append("<p:sldId id=\"").append(nextSldId + i)
                    .append("\" r:id=\"").append(slideRIds.get(i)).append("\"/>");
        }
        Matcher matcher = SLD_ID_LIST_PATTERN.matcher(presXml);
        if (matcher.find()) {
            presXml = matcher.replaceFirst("$1" + Matcher.quoteReplacement(inner.toString()) + "$3");
        } else {
            Log.w(TAG, "updatePresentationSlideReferences: sldIdLst not found");
        }
        zipEntries.put("ppt/presentation.xml", presXml.getBytes(StandardCharsets.UTF_8));

        Log.i(TAG, "ppt_pres_updated slides=" + outputCount
                + " rIds=" + slideRIds
                + " sldIdStart=" + nextSldId);
    }

    private static int findMaxRelationshipIdNumber(String relsXml) {
        Matcher matcher = RELATIONSHIP_ID_PATTERN.matcher(relsXml);
        int max = 0;
        while (matcher.find()) {
            max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return max;
    }

    private static int findMaxSldIdNumber(String presXml) {
        Matcher matcher = SLD_ID_ATTR_PATTERN.matcher(presXml);
        int max = 255;
        while (matcher.find()) {
            max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return max;
    }

    private static void updateContentTypesBytes(Map<String, byte[]> zipEntries, int outputCount) {
        String ctKey = "[Content_Types].xml";
        byte[] ctBytes = zipEntries.get(ctKey);
        if (ctBytes == null) return;

        String xml = new String(ctBytes, StandardCharsets.UTF_8);
        xml = SLIDE_CONTENT_TYPE_OVERRIDE.matcher(xml).replaceAll("");
        int insertAt = xml.lastIndexOf("</Types>");
        if (insertAt < 0) {
            Log.w(TAG, "updateContentTypesBytes: Types root missing");
            return;
        }
        String slideCt =
                "application/vnd.openxmlformats-officedocument.presentationml.slide+xml";
        String relsCt =
                "application/vnd.openxmlformats-package.relationships+xml";
        StringBuilder additions = new StringBuilder();
        for (int i = 1; i <= outputCount; i++) {
            additions.append("<Override PartName=\"/ppt/slides/slide").append(i)
                    .append(".xml\" ContentType=\"").append(slideCt).append("\"/>");
            additions.append("<Override PartName=\"/ppt/slides/_rels/slide").append(i)
                    .append(".xml.rels\" ContentType=\"").append(relsCt).append("\"/>");
        }
        xml = xml.substring(0, insertAt) + additions + xml.substring(insertAt);
        zipEntries.put(ctKey, xml.getBytes(StandardCharsets.UTF_8));
    }

    static void rezipAllBytes(Map<String, byte[]> zipEntries, File outputFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (Map.Entry<String, byte[]> entry : zipEntries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
            zos.finish();
        }
    }

    // ---- Unzip ----

    /**
     * Unzips a template InputStream into two maps: raw (binary) entries as byte[],
     * and XML entries as DOM Document.
     */
    static void unzipTemplate(InputStream is,
                              Map<String, byte[]> rawEntries,
                              Map<String, Document> xmlEntries) throws IOException {
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

                if (isXmlEntry(name)) {
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

    // ---- Fill placeholders ----

    /**
     * Iterates over slideContents and replaces placeholders in the corresponding
     * slide XML Documents.
     */
    static void fillPlaceholders(Map<String, Document> xmlEntries,
                                 Map<Integer, Map<String, String>> slideContents) {
        for (Map.Entry<Integer, Map<String, String>> slideEntry : slideContents.entrySet()) {
            int slideIndex = slideEntry.getKey();
            Map<String, String> placeholders = slideEntry.getValue();

            if (slideIndex < 1) {
                Log.w(TAG, "Slide index must be 1-based; got " + slideIndex + ", skipping");
                continue;
            }

            String entryName = "ppt/slides/slide" + slideIndex + ".xml";
            Document doc = xmlEntries.get(entryName);
            if (doc == null) {
                // Try with leading zeros (some tools generate slide01.xml)
                String paddedName = "ppt/slides/slide" + String.format("%02d", slideIndex) + ".xml";
                doc = xmlEntries.get(paddedName);
                if (doc == null) {
                    Log.w(TAG, "Slide " + entryName + " not found in template, skipping");
                    continue;
                }
            }

            fillSlideDocument(doc, placeholders);
            Log.i(TAG, "Filled slide " + slideIndex + " with " + placeholders.size() + " placeholder(s)");
        }
    }

    /**
     * Collect all {@code {{placeholder}}} keys from a slide document.
     * Concatenates all {@code <a:t>} text so placeholders split across runs are detected.
     */
    static Set<String> extractPlaceholderKeys(Document doc) {
        Set<String> keys = new HashSet<>();
        if (doc == null) return keys;
        String fullText = collectAllTextNodeContent(doc);
        Matcher m = PLACEHOLDER_FULL_PATTERN.matcher(fullText);
        while (m.find()) {
            keys.add(m.group(1).trim());
        }
        return keys;
    }

    static int countContentPointSlots(Document doc) {
        int max = -1;
        for (String key : extractPlaceholderKeys(doc)) {
            Matcher m = INDEXED_PLACEHOLDER_PATTERN.matcher(key);
            if (m.matches() && "content_points".equals(m.group(1))) {
                int idx = Integer.parseInt(m.group(2));
                if (idx > max) max = idx;
            }
        }
        return max + 1;
    }

    private static String collectAllTextNodeContent(Document doc) {
        StringBuilder sb = new StringBuilder();
        NodeList textNodes = doc.getElementsByTagNameNS("*", "t");
        for (int i = 0; i < textNodes.getLength(); i++) {
            Node tNode = textNodes.item(i);
            if (tNode != null && tNode.getTextContent() != null) {
                sb.append(tNode.getTextContent());
            }
        }
        return sb.toString();
    }

    /**
     * Replaces placeholders in a slide XML document.
     * Handles placeholders split across multiple {@code <a:r>/<a:t>} runs by
     * processing at paragraph ({@code <a:p>}) level.
     */
    static void fillSlideDocument(Document doc, Map<String, String> placeholders) {
        if (doc == null || placeholders == null) return;

        NodeList paragraphs = doc.getElementsByTagNameNS("*", "p");
        int replaced = 0;
        for (int i = 0; i < paragraphs.getLength(); i++) {
            Node pNode = paragraphs.item(i);
            if (pNode == null) continue;
            if (replacePlaceholdersInParagraph(pNode, placeholders)) {
                replaced++;
            }
        }
        Log.i(TAG, "fillSlideDocument: updated " + replaced + " paragraph(s)");
    }

    private static boolean replacePlaceholdersInParagraph(Node pNode,
                                                          Map<String, String> placeholders) {
        List<Node> tNodes = collectTextNodesInOrder(pNode);
        if (tNodes.isEmpty()) return false;

        StringBuilder sb = new StringBuilder();
        for (Node t : tNodes) {
            sb.append(t.getTextContent() != null ? t.getTextContent() : "");
        }
        String fullText = sb.toString();
        if (!fullText.contains("{{")) return false;

        Matcher matcher = PLACEHOLDER_FULL_PATTERN.matcher(fullText);
        if (!matcher.find()) return false;
        matcher.reset();

        StringBuffer result = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String replacement = lookupPlaceholderValue(key, placeholders);
            if (replacement == null) replacement = "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            changed = true;
        }
        matcher.appendTail(result);

        if (!changed) return false;

        setTextContent(tNodes.get(0), result.toString());
        for (int i = 1; i < tNodes.size(); i++) {
            setTextContent(tNodes.get(i), "");
        }
        return true;
    }

    private static List<Node> collectTextNodesInOrder(Node root) {
        List<Node> tNodes = new ArrayList<>();
        collectTextNodesRecursive(root, tNodes);
        return tNodes;
    }

    private static void collectTextNodesRecursive(Node node, List<Node> tNodes) {
        if (node == null) return;
        if ("t".equals(node.getLocalName())) {
            tNodes.add(node);
            return;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectTextNodesRecursive(children.item(i), tNodes);
        }
    }

    private static String lookupPlaceholderValue(String key, Map<String, String> placeholders) {
        if (placeholders.containsKey(key)) {
            return placeholders.get(key);
        }
        String indexed = resolveIndexedPlaceholder(key, placeholders);
        return indexed != null ? indexed : "";
    }

    /**
     * Checks if a placeholder key matches an indexed pattern (e.g. {@code content_points[3]})
     * and tries to find a matching entry in the placeholders map.
     *
     * <p>For a placeholder like {@code content_points[3]}, this looks up the key
     * {@code content_points[3]} in the map directly. If found, returns its value.
     * This works because the caller already placed indexed keys like
     * {@code "content_points[3]"} in the placeholders map.</p>
     */
    static String resolveIndexedPlaceholder(String placeholderKey,
                                            Map<String, String> placeholders) {
        Matcher matcher = INDEXED_PLACEHOLDER_PATTERN.matcher(placeholderKey);
        if (matcher.matches()) {
            // The full key like "content_points[3]" should be in the map as-is
            return placeholders.get(placeholderKey);
        }
        return null;
    }

    /**
     * Sets the text content of a DOM text node, preserving the {@code xml:space} attribute
     * on the parent element if it exists.
     */
    private static void setTextContent(Node tNode, String value) {
        if (value == null) value = "";

        // Set text content on the <a:t> element
        tNode.setTextContent(value);

        // Ensure xml:space="preserve" is set on the parent <a:t> element
        if (tNode.getNodeType() == Node.ELEMENT_NODE) {
            Element tEl = (Element) tNode;
            if (!tEl.hasAttributeNS("http://www.w3.org/XML/1998/namespace", "space")) {
                tEl.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve");
            }
        }
    }

    // ---- Re-zip ----

    /**
     * Writes all entries (raw bytes + serialized XML Documents) back into a ZipOutputStream
     * targeting the given output file.
     */
    static void rezipEntries(Map<String, byte[]> rawEntries,
                             Map<String, Document> xmlEntries,
                             File outputFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Write raw (binary) entries first
            for (Map.Entry<String, byte[]> entry : rawEntries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue());
                zos.closeEntry();
            }

            // Write XML entries (serialize DOM &rarr; bytes)
            try {
                TransformerFactory tf = TransformerFactory.newInstance();
                Transformer transformer = tf.newTransformer();
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.setOutputProperty(OutputKeys.INDENT, "no");

                for (Map.Entry<String, Document> entry : xmlEntries.entrySet()) {
                    ZipEntry ze = new ZipEntry(entry.getKey());
                    zos.putNextEntry(ze);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    transformer.transform(new DOMSource(entry.getValue()), new StreamResult(baos));
                    zos.write(baos.toByteArray());
                    zos.closeEntry();
                }
            } catch (Exception e) {
                throw new IOException("Failed to serialize XML entries", e);
            }

            zos.finish();
        }
    }

    // ---- Utilities ----

    /**
     * Returns true if the entry name indicates an XML file (.xml or .rels).
     */
    private static boolean isXmlEntry(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".xml") || lower.endsWith(".rels");
    }
}