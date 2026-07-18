package org.libreoffice.androidlib.template;

import android.content.Context;
import android.util.Log;

import org.w3c.dom.Document;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans a .pptx template and classifies each slide by its {@code {{placeholder}}} keys.
 */
public class TemplateSlideCatalog {

    private static final String TAG = "TemplateSlideCatalog";

    public enum SlideRole {
        COVER,
        TOC,
        SECTION_DIVIDER,
        CONTENT,
        END,
        UNKNOWN
    }

    public static class SlideInfo {
        public final int slideIndex;
        public final SlideRole role;
        /** For CONTENT slides: number of content_points slots (1-based count). */
        public final int pointCount;
        /** Higher = simpler placeholder layout (fewer split XML runs). */
        public final int cleanScore;

        SlideInfo(int slideIndex, SlideRole role, int pointCount, int cleanScore) {
            this.slideIndex = slideIndex;
            this.role = role;
            this.pointCount = pointCount;
            this.cleanScore = cleanScore;
        }
    }

    private final List<SlideInfo> slides;
    private final int originalSlideCount;
    /** pointCount -> ordered template slide indices (may be multiple layouts per count). */
    private final Map<Integer, List<Integer>> variantToSlideIndices = new HashMap<>();
    private final Map<Integer, Integer> variantUseCounter = new HashMap<>();

    private TemplateSlideCatalog(List<SlideInfo> slides, int originalSlideCount) {
        this.slides = slides;
        this.originalSlideCount = originalSlideCount;
        for (SlideInfo info : slides) {
            if (info.role == SlideRole.CONTENT && info.pointCount > 0) {
                List<Integer> list = variantToSlideIndices.get(info.pointCount);
                if (list == null) {
                    list = new ArrayList<>();
                    variantToSlideIndices.put(info.pointCount, list);
                }
                list.add(info.slideIndex);
            }
        }
        for (List<Integer> indices : variantToSlideIndices.values()) {
            indices.sort((a, b) -> {
                int scoreA = scoreForSlide(slides, a);
                int scoreB = scoreForSlide(slides, b);
                return Integer.compare(scoreB, scoreA);
            });
        }
    }

    private static int scoreForSlide(List<SlideInfo> slides, int slideIndex) {
        for (SlideInfo info : slides) {
            if (info.slideIndex == slideIndex) return info.cleanScore;
        }
        return 0;
    }

    public int getOriginalSlideCount() {
        return originalSlideCount;
    }

    public List<SlideInfo> getSlides() {
        return Collections.unmodifiableList(slides);
    }

    /**
     * Load and scan a template from assets.
     */
    public static TemplateSlideCatalog load(Context context, String assetPath) throws IOException {
        InputStream is = null;
        try {
            is = context.getAssets().open(assetPath);
            Map<String, byte[]> zipEntries = PptxTemplateFiller.unzipAllAsBytes(is);

            List<Integer> slideIndices = new ArrayList<>();
            for (String key : zipEntries.keySet()) {
                if (key.startsWith("ppt/slides/slide") && key.endsWith(".xml")
                        && !key.contains("_rels")) {
                    String num = key.substring("ppt/slides/slide".length(), key.length() - 4);
                    try {
                        slideIndices.add(Integer.parseInt(num));
                    } catch (NumberFormatException ignored) {
                        // skip non-numeric slide names
                    }
                }
            }
            Collections.sort(slideIndices);

            List<SlideInfo> infos = new ArrayList<>();
            for (int idx : slideIndices) {
                byte[] slideBytes = zipEntries.get("ppt/slides/slide" + idx + ".xml");
                if (slideBytes == null) continue;
                Document doc = PptxTemplateFiller.parseDocument(slideBytes);
                Set<String> placeholders = PptxTemplateFiller.extractPlaceholderKeys(doc);
                int pointCount = PptxTemplateFiller.countContentPointSlots(doc);
                SlideRole role = classifyRole(placeholders, pointCount, idx, slideIndices.size());
                int cleanScore = PptxTemplateFiller.scoreSlidePlaceholderCleanliness(slideBytes);
                infos.add(new SlideInfo(idx, role, pointCount, cleanScore));
                Log.i(TAG, "slide" + idx + " role=" + role + " points=" + pointCount
                        + " cleanScore=" + cleanScore);
            }

            return new TemplateSlideCatalog(infos, slideIndices.size());
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

    public int getSlideIndex(SlideRole role) {
        for (SlideInfo info : slides) {
            if (info.role == role) return info.slideIndex;
        }
        if (role == SlideRole.COVER && !slides.isEmpty()) return slides.get(0).slideIndex;
        if (role == SlideRole.END && !slides.isEmpty()) {
            return slides.get(slides.size() - 1).slideIndex;
        }
        Log.w(TAG, "getSlideIndex: no slide for role=" + role);
        return 1;
    }

    public boolean hasExactContentSlide(int pointCount) {
        List<Integer> indices = variantToSlideIndices.get(pointCount);
        return indices != null && !indices.isEmpty();
    }

    public int getPointCountForSlide(int slideIndex) {
        for (SlideInfo info : slides) {
            if (info.slideIndex == slideIndex) return info.pointCount;
        }
        return 0;
    }

    /**
     * Pick a CONTENT slide with exactly {@code pointCount} slots.
     * Rotates among multiple layouts that share the same point count.
     *
     * @return template slide index, or -1 if no exact match exists
     */
    public int getContentSlideIndexExact(int pointCount) {
        if (pointCount <= 0) pointCount = 2;
        List<Integer> indices = variantToSlideIndices.get(pointCount);
        if (indices == null || indices.isEmpty()) {
            Log.w(TAG, "getContentSlideIndexExact: no slide for pointCount=" + pointCount);
            return -1;
        }
        int use = variantUseCounter.getOrDefault(pointCount, 0);
        int slideIndex = indices.get(use % indices.size());
        variantUseCounter.put(pointCount, use + 1);
        Log.i(TAG, "getContentSlideIndexExact pointCount=" + pointCount
                + " -> slide" + slideIndex + " (variant " + (use % indices.size() + 1)
                + "/" + indices.size() + ")");
        return slideIndex;
    }

    /**
     * Nearest supported point count that does not exceed {@code pointCount}.
     */
    public int findBestSupportedPointCount(int pointCount) {
        if (pointCount <= 0) return 2;
        if (hasExactContentSlide(pointCount)) return pointCount;
        int best = -1;
        for (Integer variant : variantToSlideIndices.keySet()) {
            if (variant <= pointCount && variant > best) {
                best = variant;
            }
        }
        if (best != -1) return best;
        for (Integer variant : variantToSlideIndices.keySet()) {
            if (best == -1 || variant < best) best = variant;
        }
        return best > 0 ? best : 2;
    }

    private static SlideRole classifyRole(Set<String> ph, int pointCount,
                                          int slideIndex, int totalSlides) {
        boolean hasToc = ph.contains("toc_content");
        boolean hasDivider = ph.contains("section_number") && ph.contains("section_overview");
        boolean hasContent = pointCount > 0 || ph.contains("content_points[0]");
        boolean hasTitle = ph.contains("title");
        boolean hasSubtitle = ph.contains("subtitle");
        boolean hasAuthor = ph.contains("author");
        boolean hasDate = ph.contains("date");
        boolean hasContact = ph.contains("contact_info");

        if (hasToc) return SlideRole.TOC;
        if (hasDivider) return SlideRole.SECTION_DIVIDER;
        if (hasContent) return SlideRole.CONTENT;
        if (hasTitle && hasSubtitle) return SlideRole.COVER;
        if (hasContact || (hasAuthor && hasDate && slideIndex == totalSlides)) return SlideRole.END;
        if (hasAuthor && hasDate && !hasContent) return SlideRole.END;
        if (slideIndex == 1 && hasTitle) return SlideRole.COVER;
        if (slideIndex == totalSlides) return SlideRole.END;
        return SlideRole.UNKNOWN;
    }
}
