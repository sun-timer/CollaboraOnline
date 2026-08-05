package org.libreoffice.androidlib;

/**
 * Writer 布局 Tab — 页边距 / 纸张大小选项（对齐 CO Definitions.Menu.ts）。
 */
final class WriterLayoutCatalog {

    static final class MarginOption {
        final String id;
        final String label;
        final int topHmm;
        final int bottomHmm;
        final int leftHmm;
        final int rightHmm;

        MarginOption(String id, String label, int topHmm, int bottomHmm, int leftHmm, int rightHmm) {
            this.id = id;
            this.label = label;
            this.topHmm = topHmm;
            this.bottomHmm = bottomHmm;
            this.leftHmm = leftHmm;
            this.rightHmm = rightHmm;
        }
    }

    static final class PaperSizeOption {
        final String id;
        final String label;
        final int paperFormat;

        PaperSizeOption(String id, String label, int paperFormat) {
            this.id = id;
            this.label = label;
            this.paperFormat = paperFormat;
        }
    }

    /** CO pageMarginOptions：normal / narrow / wide（单位 inch → hundredths-mm）。 */
    private static int inchToHmm(double inches) {
        return (int) Math.round(inches * 25.4 * 100);
    }

    /** CO pageMarginOptions 全量（Figma 5252:57102）：无/窄/适中/正常1.90/正常2.54/正常3.18/宽/镜像。 */
    static final MarginOption[] MARGINS = {
            new MarginOption("none", "无", 0, 0, 0, 0),
            new MarginOption("narrow", "窄",
                    inchToHmm(0.5), inchToHmm(0.5), inchToHmm(0.5), inchToHmm(0.5)),
            new MarginOption("moderate", "适中",
                    inchToHmm(1), inchToHmm(1), inchToHmm(0.75), inchToHmm(0.75)),
            new MarginOption("normal190", "正常（1.90 cm）",
                    inchToHmm(0.75), inchToHmm(0.75), inchToHmm(0.75), inchToHmm(0.75)),
            new MarginOption("normal254", "正常（2.54 cm）",
                    inchToHmm(1), inchToHmm(1), inchToHmm(1), inchToHmm(1)),
            new MarginOption("normal318", "正常（3.18 cm）",
                    inchToHmm(1.25), inchToHmm(1.25), inchToHmm(1.25), inchToHmm(1.25)),
            new MarginOption("wide", "宽",
                    inchToHmm(1), inchToHmm(1), inchToHmm(2), inchToHmm(2)),
            new MarginOption("mirrored", "镜像",
                    inchToHmm(1), inchToHmm(1), inchToHmm(2), inchToHmm(1)),
    };

    /** CO MenuPageSizesWriter / pageSizes 全量（不含 User，自定义由 stepper 处理）。 */
    static final PaperSizeOption[] PAPER_SIZES = {
            new PaperSizeOption("A6", "A6", 56),
            new PaperSizeOption("A5", "A5", 5),
            new PaperSizeOption("A4", "A4", 4),
            new PaperSizeOption("A3", "A3", 3),
            new PaperSizeOption("B6ISO", "B6（ISO）", 12),
            new PaperSizeOption("B5ISO", "B5（ISO）", 7),
            new PaperSizeOption("B4ISO", "B4（ISO）", 6),
            new PaperSizeOption("Letter", "信纸", 8),
            new PaperSizeOption("Legal", "法律专用纸", 9),
            new PaperSizeOption("LongBond", "长债券纸", 24),
            new PaperSizeOption("Tabloid", "小报", 10),
            new PaperSizeOption("B6JIS", "B6（JIS）", 36),
            new PaperSizeOption("B5JIS", "B5（JIS）", 35),
            new PaperSizeOption("B4JIS", "B4（JIS）", 34),
            new PaperSizeOption("16Kai", "16开", 31),
            new PaperSizeOption("32Kai", "32开", 32),
            new PaperSizeOption("Big32Kai", "大32开", 33),
            new PaperSizeOption("DLEnvelope", "DL 信封", 17),
            new PaperSizeOption("C6Envelope", "C6 信封", 15),
            new PaperSizeOption("C6_5Envelope", "C6/5 信封", 16),
            new PaperSizeOption("C5Envelope", "C5 信封", 14),
            new PaperSizeOption("C4Envelope", "C4 信封", 13),
            new PaperSizeOption("No6_3_4Envelope", "6¾ 号信封", 26),
            new PaperSizeOption("No7_3_4MonarchEnvelope", "7¾ 号信封", 25),
            new PaperSizeOption("No9Envelope", "9 号信封", 27),
            new PaperSizeOption("No10Envelope", "10 号信封", 28),
            new PaperSizeOption("No11Envelope", "11 号信封", 29),
            new PaperSizeOption("No12Envelope", "12 号信封", 30),
            new PaperSizeOption("JapanesePostcard", "日本明信片", 46),
    };

    static MarginOption findMarginById(String id) {
        if (id == null) {
            return MARGINS[0];
        }
        for (MarginOption option : MARGINS) {
            if (option.id.equals(id)) {
                return option;
            }
        }
        return MARGINS[0];
    }

    static MarginOption findMarginByLabel(String label) {
        if (label == null) {
            return MARGINS[0];
        }
        for (MarginOption option : MARGINS) {
            if (option.label.equals(label)) {
                return option;
            }
        }
        for (MarginOption option : MARGINS) {
            if (option.id.equals(label)) {
                return option;
            }
        }
        return MARGINS[0];
    }

    static PaperSizeOption findPaperByLabel(String label) {
        if (label == null) {
            return PAPER_SIZES[2];
        }
        for (PaperSizeOption option : PAPER_SIZES) {
            if (option.label.equals(label)) {
                return option;
            }
        }
        for (PaperSizeOption option : PAPER_SIZES) {
            if (option.id.equals(label)) {
                return option;
            }
        }
        return PAPER_SIZES[2];
    }

    static String[] marginLabels() {
        String[] labels = new String[MARGINS.length];
        for (int i = 0; i < MARGINS.length; i++) {
            labels[i] = MARGINS[i].label;
        }
        return labels;
    }

    static String[] marginIds() {
        String[] ids = new String[MARGINS.length];
        for (int i = 0; i < MARGINS.length; i++) {
            ids[i] = MARGINS[i].id;
        }
        return ids;
    }

    private WriterLayoutCatalog() {
    }
}
