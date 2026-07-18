package org.libreoffice.androidlib.template;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads and queries metadata from {@code templates/impress/index.json} stored
 * in Android assets.
 *
 * <p>This class provides the canonical interface for enumerating available PPT
 * templates and finding a template variant (by content-point count) that best
 * matches the AI's output plan.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * TemplateIndex index = TemplateIndex.load(context);
 * Template t = index.findById("business");
 * int variant = index.findSuitableSlide("business", 4); // returns 4
 * }</pre>
 * </p>
 *
 * <p>The index.json format is defined in
 * {@code assets/templates/impress/index.json}.</p>
 *
 * @see PptxTemplateFiller
 */
public class TemplateIndex {

    private static final String TAG = "TemplateIndex";

    /** Asset path to the impress template index. */
    private static final String INDEX_ASSET_PATH = "templates/impress/index.json";

    private final List<Template> templates;

    /**
     * Private constructor; instances are created via {@link #load(Context)}.
     */
    private TemplateIndex(List<Template> templates) {
        this.templates = templates;
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Load the template index from {@code assets/templates/impress/index.json}.
     *
     * @param context Android context (for AssetManager).
     * @return a fully-populated TemplateIndex.
     * @throws IOException  if the asset file cannot be read.
     * @throws JSONException if the JSON content is malformed or missing
     *                       required fields.
     */
    public static TemplateIndex load(Context context) throws IOException, JSONException {
        String jsonString = readAssetFile(context, INDEX_ASSET_PATH);
        JSONObject root = new JSONObject(jsonString);

        JSONArray templatesArray = root.getJSONArray("templates");
        List<Template> templateList = new ArrayList<>(templatesArray.length());

        for (int i = 0; i < templatesArray.length(); i++) {
            JSONObject obj = templatesArray.getJSONObject(i);
            Template t = new Template(obj);
            templateList.add(t);
        }

        Log.i(TAG, "Loaded " + templateList.size() + " templates from " + INDEX_ASSET_PATH);
        return new TemplateIndex(templateList);
    }

    /**
     * Returns an unmodifiable view of all loaded templates.
     */
    public List<Template> getAllTemplates() {
        return Collections.unmodifiableList(templates);
    }

    /**
     * Look up a template by its unique identifier.
     *
     * @param id the template id (e.g. {@code "business"}).
     * @return the matching Template, or {@code null} if not found.
     */
    public Template findById(String id) {
        if (id == null) return null;
        for (Template t : templates) {
            if (id.equals(t.id)) return t;
        }
        return null;
    }

    /**
     * Find the best matching variant count for a given template and number of
     * content points.
     *
     * <p>The algorithm is:</p>
     * <ol>
     *   <li>If {@code pointCount} exists in the template's {@code variants}
     *       array &rarr; return it (exact match).</li>
     *   <li>Otherwise, find the <em>smallest</em> variant that is
     *       {@code >= pointCount} (ceiling / nearest fit).</li>
     *   <li>If no variant can accommodate {@code pointCount} (i.e. it exceeds
     *       {@code maxPoints}) &rarr; return -1, signalling that the AI should
     *       reduce the number of points.</li>
     * </ol>
     *
     * @param templateId the template id (must exist in the index).
     * @param pointCount the number of content points the AI wants to generate.
     * @return the best matching variant count (positive int), or -1 if the
     *         pointCount exceeds the template's maximum capacity.
     */
    public int findSuitableSlide(String templateId, int pointCount) {
        Template t = findById(templateId);
        if (t == null) {
            Log.w(TAG, "findSuitableSlide: template '" + templateId + "' not found");
            return -1;
        }

        int[] variants = t.variants;
        if (variants == null || variants.length == 0) {
            Log.w(TAG, "findSuitableSlide: template '" + templateId + "' has no variants");
            return -1;
        }

        // 1. Exact match
        for (int v : variants) {
            if (v == pointCount) return v;
        }

        // 2. Ceiling: smallest variant >= pointCount
        int best = -1;
        for (int v : variants) {
            if (v >= pointCount) {
                if (best == -1 || v < best) {
                    best = v;
                }
            }
        }

        if (best != -1) {
            Log.i(TAG, "findSuitableSlide: template='" + templateId
                    + "' pointCount=" + pointCount + " -> variant=" + best
                    + " (ceiling match)");
            return best;
        }

        // 3. pointCount exceeds maxPoints — AI correction needed
        Log.w(TAG, "findSuitableSlide: pointCount=" + pointCount
                + " exceeds maxPoints=" + t.maxPoints
                + " for template '" + templateId + "'");
        return -1;
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    /**
     * Reads the full content of an assets file as a UTF-8 string.
     */
    private static String readAssetFile(Context context, String assetPath) throws IOException {
        InputStream is = null;
        BufferedReader reader = null;
        try {
            is = context.getAssets().open(assetPath);
            reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int len;
            while ((len = reader.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
            return sb.toString();
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException e) { /* ignore */ }
            }
            if (is != null) {
                try { is.close(); } catch (IOException e) { /* ignore */ }
            }
        }
    }

    // ---------------------------------------------------------------
    // Inner class: Template
    // ---------------------------------------------------------------

    /**
     * Metadata for a single PPT template, loaded from a JSON object in
     * {@code index.json}.
     *
     * <p>All fields are {@code public final} for direct read access.</p>
     */
    public static class Template {
        public final String id;
        public final String name;
        public final String description;
        public final String coverImage;
        public final String file;
        public final int slideCount;
        public final int maxPoints;
        public final int[] variants;

        /**
         * Construct a Template from its JSON representation.
         *
         * @param obj a JSONObject containing the template's fields.
         * @throws JSONException if a required field is missing or has an
         *                       unexpected type.
         */
        public Template(JSONObject obj) throws JSONException {
            this.id = obj.getString("id");
            this.name = obj.getString("name");
            this.description = obj.optString("description", "");
            this.coverImage = obj.optString("coverImage", "");
            this.file = obj.getString("file");
            this.slideCount = obj.getInt("slideCount");
            this.maxPoints = obj.getInt("maxPoints");

            JSONArray varArray = obj.getJSONArray("variants");
            this.variants = new int[varArray.length()];
            for (int i = 0; i < varArray.length(); i++) {
                this.variants[i] = varArray.getInt(i);
            }
        }

        @Override
        public String toString() {
            return "Template{id='" + id + "', name='" + name
                    + "', slideCount=" + slideCount
                    + ", maxPoints=" + maxPoints
                    + ", variants=" + java.util.Arrays.toString(variants)
                    + "}";
        }
    }
}