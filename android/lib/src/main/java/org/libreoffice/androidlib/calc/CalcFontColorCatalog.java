package org.libreoffice.androidlib.calc;

import org.libreoffice.androidlib.R;

/**
 * Calc 字体颜色色板（36 色，分 24 + 12 两块）。
 * 由 scripts/generate-calc-font-color-assets.js 生成，请勿手改。
 */
public final class CalcFontColorCatalog {
    private CalcFontColorCatalog() {}

    public static final class Entry {
        public final int index;
        public final int iconResId;
        /** 0xRRGGBB，用于 .uno:Color */
        public final int rgb;

        Entry(int index, int iconResId, int rgb) {
            this.index = index;
            this.iconResId = iconResId;
            this.rgb = rgb;
        }
    }

    public static final class Block {
        public final Entry[] entries;

        Block(Entry[] entries) {
            this.entries = entries;
        }
    }

    public static final Block[] BLOCKS = new Block[] {
        new Block(new Entry[] {
            new Entry(1, R.drawable.lolib_ic_calc_font_color_001, 0x8ACFFF),
            new Entry(2, R.drawable.lolib_ic_calc_font_color_002, 0xD596FF),
            new Entry(3, R.drawable.lolib_ic_calc_font_color_003, 0xBEFFC6),
            new Entry(4, R.drawable.lolib_ic_calc_font_color_004, 0xFFC891),
            new Entry(5, R.drawable.lolib_ic_calc_font_color_005, 0xFFE4E5),
            new Entry(6, R.drawable.lolib_ic_calc_font_color_006, 0xFFFFFF),
            new Entry(7, R.drawable.lolib_ic_calc_font_color_007, 0x009CFF),
            new Entry(8, R.drawable.lolib_ic_calc_font_color_008, 0xA628FF),
            new Entry(9, R.drawable.lolib_ic_calc_font_color_009, 0x00FF47),
            new Entry(10, R.drawable.lolib_ic_calc_font_color_010, 0xFFC700),
            new Entry(11, R.drawable.lolib_ic_calc_font_color_011, 0xE65D61),
            new Entry(12, R.drawable.lolib_ic_calc_font_color_012, 0xC0C0C0),
            new Entry(13, R.drawable.lolib_ic_calc_font_color_013, 0x0000FF),
            new Entry(14, R.drawable.lolib_ic_calc_font_color_014, 0x7000D5),
            new Entry(15, R.drawable.lolib_ic_calc_font_color_015, 0x89CD00),
            new Entry(16, R.drawable.lolib_ic_calc_font_color_016, 0xFF9300),
            new Entry(17, R.drawable.lolib_ic_calc_font_color_017, 0xA62900),
            new Entry(18, R.drawable.lolib_ic_calc_font_color_018, 0x808080),
            new Entry(19, R.drawable.lolib_ic_calc_font_color_019, 0x010086),
            new Entry(20, R.drawable.lolib_ic_calc_font_color_020, 0x390069),
            new Entry(21, R.drawable.lolib_ic_calc_font_color_021, 0x008200),
            new Entry(22, R.drawable.lolib_ic_calc_font_color_022, 0xFF5700),
            new Entry(23, R.drawable.lolib_ic_calc_font_color_023, 0x8C0000),
            new Entry(24, R.drawable.lolib_ic_calc_font_color_024, 0x000000)
        }),
        new Block(new Entry[] {
            new Entry(25, R.drawable.lolib_ic_calc_font_color_025, 0xD20000),
            new Entry(26, R.drawable.lolib_ic_calc_font_color_026, 0xFFBD00),
            new Entry(27, R.drawable.lolib_ic_calc_font_color_027, 0x7ED330),
            new Entry(28, R.drawable.lolib_ic_calc_font_color_028, 0x00B3F7),
            new Entry(29, R.drawable.lolib_ic_calc_font_color_029, 0x792BA6),
            new Entry(30, R.drawable.lolib_ic_calc_font_color_030, 0xFFFFFF),
            new Entry(31, R.drawable.lolib_ic_calc_font_color_031, 0xFF0000),
            new Entry(32, R.drawable.lolib_ic_calc_font_color_032, 0xFFFF00),
            new Entry(33, R.drawable.lolib_ic_calc_font_color_033, 0x00B242),
            new Entry(34, R.drawable.lolib_ic_calc_font_color_034, 0x0073C7),
            new Entry(35, R.drawable.lolib_ic_calc_font_color_035, 0x002164),
            new Entry(36, R.drawable.lolib_ic_calc_font_color_036, 0x000000)
        })
    };
}
