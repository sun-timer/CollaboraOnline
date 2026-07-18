package org.libreoffice.androidlib.template;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps outline pages + AI-generated content to an ordered list of template slides.
 */
public class ImpressSlidePlanner {

    private static final String TAG = "ImpressSlidePlanner";

    public static class PlannedSlide {
        public final int sourceTemplateSlideIndex;
        public final Map<String, String> placeholders;

        public PlannedSlide(int sourceTemplateSlideIndex, Map<String, String> placeholders) {
            this.sourceTemplateSlideIndex = sourceTemplateSlideIndex;
            this.placeholders = placeholders;
        }
    }

    /**
     * Build an ordered slide plan from outline and per-page generated content.
     *
     * @param catalog                  scanned template metadata
     * @param templateIndex            template variant lookup
     * @param templateId               selected template id
     * @param outlineSlides            full outline JSON array
     * @param generatedByOutlineIndex  AI results keyed by outline index (0-based)
     */
    public static List<PlannedSlide> build(
            TemplateSlideCatalog catalog,
            TemplateIndex templateIndex,
            String templateId,
            JSONArray outlineSlides,
            Map<Integer, JSONObject> generatedByOutlineIndex
    ) throws JSONException {
        List<PlannedSlide> plan = new ArrayList<>();
        if (outlineSlides == null) return plan;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        int chapterNumber = 0;

        for (int i = 0; i < outlineSlides.length(); i++) {
            JSONObject outlineSlide = outlineSlides.getJSONObject(i);
            String type = outlineSlide.optString("type", "section");

            JSONObject generated = generatedByOutlineIndex != null
                    ? generatedByOutlineIndex.get(i) : null;
            if (generated == null && "section_divider".equals(type)) {
                generated = outlineSlide;
            }

            if ("section_divider".equals(type)) {
                chapterNumber++;
                Map<String, String> ph = buildSectionDividerPlaceholders(
                        outlineSlide, chapterNumber);
                int srcIdx = catalog.getSlideIndex(TemplateSlideCatalog.SlideRole.SECTION_DIVIDER);
                plan.add(new PlannedSlide(srcIdx, ph));
                Log.i(TAG, "ppt_slide_planned role=section_divider chapter=" + chapterNumber
                        + " src=" + srcIdx + " title=" + outlineSlide.optString("title", ""));
                continue;
            }

            if (generated == null) {
                Log.w(TAG, "ppt_slide_planned skip index=" + i + " reason=no_generated_content");
                continue;
            }

            switch (type) {
                case "cover": {
                    Map<String, String> ph = buildCoverPlaceholders(generated, today);
                    int srcIdx = catalog.getSlideIndex(TemplateSlideCatalog.SlideRole.COVER);
                    plan.add(new PlannedSlide(srcIdx, ph));
                    Log.i(TAG, "ppt_slide_planned role=cover src=" + srcIdx);
                    break;
                }
                case "toc": {
                    Map<String, String> ph = buildTocPlaceholders(generated, outlineSlide);
                    int srcIdx = catalog.getSlideIndex(TemplateSlideCatalog.SlideRole.TOC);
                    plan.add(new PlannedSlide(srcIdx, ph));
                    Log.i(TAG, "ppt_slide_planned role=toc src=" + srcIdx);
                    break;
                }
                case "end": {
                    Map<String, String> ph = buildEndPlaceholders(generated, today);
                    int srcIdx = catalog.getSlideIndex(TemplateSlideCatalog.SlideRole.END);
                    plan.add(new PlannedSlide(srcIdx, ph));
                    Log.i(TAG, "ppt_slide_planned role=end src=" + srcIdx);
                    break;
                }
                case "section":
                default: {
                    int pointCount = countPoints(generated);
                    int slotCount = resolveContentSlotCount(catalog, templateIndex, templateId, pointCount);
                    Map<String, String> ph = buildSectionPlaceholders(generated, slotCount);
                    int srcIdx = catalog.getContentSlideIndexExact(slotCount);
                    if (srcIdx <= 0) {
                        Log.w(TAG, "ppt_slide_planned fallback src for slotCount=" + slotCount);
                        srcIdx = catalog.getSlideIndex(TemplateSlideCatalog.SlideRole.CONTENT);
                    }
                    plan.add(new PlannedSlide(srcIdx, ph));
                    Log.i(TAG, "ppt_slide_planned role=section src=" + srcIdx
                            + " aiPoints=" + pointCount + " templateSlots=" + slotCount
                            + " title=" + generated.optString("title", ""));
                    break;
                }
            }
        }

        Log.i(TAG, "ppt_slide_plan_complete slides=" + plan.size());
        return plan;
    }

    private static Map<String, String> buildCoverPlaceholders(JSONObject slide, String today) {
        Map<String, String> ph = new HashMap<>();
        String title = slide.optString("title", "");
        String subtitle = slide.optString("subtitle", "");
        ph.put("title", title);
        ph.put("subtitle", subtitle);
        ph.put("author", subtitle.isEmpty() ? "AI Office" : subtitle);
        ph.put("date", today);
        ph.put("cover_title", title);
        return ph;
    }

    private static Map<String, String> buildTocPlaceholders(JSONObject generated, JSONObject outline) {
        Map<String, String> ph = new HashMap<>();
        String tocContent = generated.optString("content", "");
        if (tocContent.isEmpty()) {
            tocContent = outline.optString("content", "");
        }
        JSONArray contentPoints = generated.optJSONArray("content_points");
        if (tocContent.isEmpty() && contentPoints != null) {
            StringBuilder toc = new StringBuilder();
            for (int p = 0; p < contentPoints.length(); p++) {
                if (p > 0) toc.append("\n");
                toc.append(contentPoints.optString(p, ""));
            }
            tocContent = toc.toString();
        }
        ph.put("toc_content", tocContent);
        ph.put("title", generated.optString("title", outline.optString("title", "目录")));
        return ph;
    }

    private static Map<String, String> buildSectionDividerPlaceholders(
            JSONObject slide, int chapterNumber) {
        Map<String, String> ph = new HashMap<>();
        String title = slide.optString("title", "");
        String overview = slide.optString("content", "");
        ph.put("section_title", title);
        ph.put("section_number", "第" + chapterNumber + "章");
        ph.put("section_overview", overview);
        ph.put("title", title);
        return ph;
    }

    private static int resolveContentSlotCount(
            TemplateSlideCatalog catalog,
            TemplateIndex templateIndex,
            String templateId,
            int aiPointCount) {
        if (catalog.hasExactContentSlide(aiPointCount)) {
            return aiPointCount;
        }
        if (templateIndex != null && templateId != null) {
            int variant = templateIndex.findSuitableSlide(templateId, aiPointCount);
            if (variant > 0 && catalog.hasExactContentSlide(variant)) {
                Log.i(TAG, "resolveContentSlotCount aiPoints=" + aiPointCount
                        + " -> templateVariant=" + variant);
                return variant;
            }
        }
        int best = catalog.findBestSupportedPointCount(aiPointCount);
        Log.i(TAG, "resolveContentSlotCount aiPoints=" + aiPointCount + " -> bestSlots=" + best);
        return best;
    }

    private static Map<String, String> buildSectionPlaceholders(JSONObject slide, int slotCount) {
        Map<String, String> ph = new HashMap<>();
        String title = slide.optString("title", "");
        String subtitle = slide.optString("subtitle", "");
        ph.put("title", title);
        ph.put("subtitle", subtitle);
        ph.put("section_title", title);

        JSONArray contentPoints = slide.optJSONArray("content_points");
        JSONArray detailedContent = slide.optJSONArray("detailed_content");
        if (slotCount <= 0) slotCount = countPoints(slide);
        if (slotCount <= 0) slotCount = 2;

        StringBuilder cpText = new StringBuilder();
        StringBuilder dcText = new StringBuilder();
        for (int p = 0; p < slotCount; p++) {
            String cp = contentPoints != null && p < contentPoints.length()
                    ? contentPoints.optString(p, "") : "";
            String dc = detailedContent != null && p < detailedContent.length()
                    ? detailedContent.optString(p, "") : "";
            ph.put("content_points[" + p + "]", cp);
            ph.put("detailed_content[" + p + "]", dc);
            if (cpText.length() > 0) cpText.append("\n");
            cpText.append(cp);
            if (dcText.length() > 0) dcText.append("\n");
            dcText.append(dc);
        }
        ph.put("content_points", cpText.toString());
        ph.put("detailed_content", dcText.toString());
        return ph;
    }

    private static Map<String, String> buildEndPlaceholders(JSONObject slide, String today) {
        Map<String, String> ph = new HashMap<>();
        String title = slide.optString("title", "谢谢");
        String subtitle = slide.optString("subtitle", "");
        ph.put("title", title);
        ph.put("end_title", title);
        ph.put("author", subtitle.isEmpty() ? "AI Office" : subtitle);
        ph.put("date", today);
        ph.put("contact_info", subtitle);

        JSONArray contentPoints = slide.optJSONArray("content_points");
        if (subtitle.isEmpty() && contentPoints != null && contentPoints.length() > 0) {
            ph.put("contact_info", contentPoints.optString(0, ""));
        }
        return ph;
    }

    private static int countPoints(JSONObject slide) {
        JSONArray contentPoints = slide.optJSONArray("content_points");
        if (contentPoints == null) return 0;
        int count = 0;
        for (int i = 0; i < contentPoints.length(); i++) {
            if (!contentPoints.optString(i, "").trim().isEmpty()) count++;
        }
        return count;
    }
}
