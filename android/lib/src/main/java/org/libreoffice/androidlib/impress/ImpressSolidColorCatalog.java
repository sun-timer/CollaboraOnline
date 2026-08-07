package org.libreoffice.androidlib.impress;

import org.libreoffice.androidlib.R;

/**
 * Impress 母版幻灯片 — 纯色色板（12 + 58，6 列网格）。
 * 由 scripts/generate-impress-solid-color-assets.js 生成，请勿手改。
 */
public final class ImpressSolidColorCatalog {
    private ImpressSolidColorCatalog() {}

    public static final class Entry {
        public final int index;
        public final int iconResId;
        /** 0xRRGGBB */
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
            new Entry(1, R.drawable.lolib_ic_impress_solid_color_001, 0xA61013),
            new Entry(2, R.drawable.lolib_ic_impress_solid_color_002, 0xD51114),
            new Entry(3, R.drawable.lolib_ic_impress_solid_color_003, 0xFABD00),
            new Entry(4, R.drawable.lolib_ic_impress_solid_color_004, 0xF2FB20),
            new Entry(5, R.drawable.lolib_ic_impress_solid_color_005, 0x90C943),
            new Entry(6, R.drawable.lolib_ic_impress_solid_color_006, 0x13A55E),
            new Entry(7, R.drawable.lolib_ic_impress_solid_color_007, 0x00AAF1),
            new Entry(8, R.drawable.lolib_ic_impress_solid_color_008, 0x0E68A6),
            new Entry(9, R.drawable.lolib_ic_impress_solid_color_009, 0x01225E),
            new Entry(10, R.drawable.lolib_ic_impress_solid_color_010, 0x753195),
            new Entry(11, R.drawable.lolib_ic_impress_solid_color_011, 0xA5A5A5),
            new Entry(12, R.drawable.lolib_ic_impress_solid_color_012, 0x7E7E7E)
        }),
        new Block(new Entry[] {
            new Entry(13, R.drawable.lolib_ic_impress_solid_color_013, 0xFEFEFE),
            new Entry(14, R.drawable.lolib_ic_impress_solid_color_014, 0xF2F2F2),
            new Entry(15, R.drawable.lolib_ic_impress_solid_color_015, 0xDCDCDC),
            new Entry(16, R.drawable.lolib_ic_impress_solid_color_016, 0xBFBFBF),
            new Entry(17, R.drawable.lolib_ic_impress_solid_color_017, 0x262626),
            new Entry(18, R.drawable.lolib_ic_impress_solid_color_018, 0x0C0C0C),
            new Entry(19, R.drawable.lolib_ic_impress_solid_color_019, 0x000000),
            new Entry(20, R.drawable.lolib_ic_impress_solid_color_020, 0x7F7F7F),
            new Entry(21, R.drawable.lolib_ic_impress_solid_color_021, 0x5D5D5D),
            new Entry(22, R.drawable.lolib_ic_impress_solid_color_022, 0x3F3F3F),
            new Entry(23, R.drawable.lolib_ic_impress_solid_color_023, 0x383838),
            new Entry(24, R.drawable.lolib_ic_impress_solid_color_024, 0x161616),
            new Entry(25, R.drawable.lolib_ic_impress_solid_color_025, 0xE6E6E5),
            new Entry(26, R.drawable.lolib_ic_impress_solid_color_026, 0xCFCFCF),
            new Entry(27, R.drawable.lolib_ic_impress_solid_color_027, 0xACACAC),
            new Entry(28, R.drawable.lolib_ic_impress_solid_color_028, 0x727272),
            new Entry(29, R.drawable.lolib_ic_impress_solid_color_029, 0x333F4B),
            new Entry(30, R.drawable.lolib_ic_impress_solid_color_030, 0x232A34),
            new Entry(31, R.drawable.lolib_ic_impress_solid_color_031, 0x46556A),
            new Entry(32, R.drawable.lolib_ic_impress_solid_color_032, 0xD9DCE1),
            new Entry(33, R.drawable.lolib_ic_impress_solid_color_033, 0xACB7C9),
            new Entry(34, R.drawable.lolib_ic_impress_solid_color_034, 0x8698AB),
            new Entry(35, R.drawable.lolib_ic_impress_solid_color_035, 0x3073B8),
            new Entry(36, R.drawable.lolib_ic_impress_solid_color_036, 0x214C76),
            new Entry(37, R.drawable.lolib_ic_impress_solid_color_037, 0x5E9FE5),
            new Entry(38, R.drawable.lolib_ic_impress_solid_color_038, 0xE3EBF7),
            new Entry(39, R.drawable.lolib_ic_impress_solid_color_039, 0xBFD5EB),
            new Entry(40, R.drawable.lolib_ic_impress_solid_color_040, 0x9DC3EA),
            new Entry(47, R.drawable.lolib_ic_impress_solid_color_047, 0xC25A14),
            new Entry(48, R.drawable.lolib_ic_impress_solid_color_048, 0x853C0C),
            new Entry(49, R.drawable.lolib_ic_impress_solid_color_049, 0xE2732C),
            new Entry(50, R.drawable.lolib_ic_impress_solid_color_050, 0xF6E2D7),
            new Entry(51, R.drawable.lolib_ic_impress_solid_color_051, 0xF8CEA5),
            new Entry(52, R.drawable.lolib_ic_impress_solid_color_052, 0xF4B488),
            new Entry(53, R.drawable.lolib_ic_impress_solid_color_053, 0x7A7880),
            new Entry(54, R.drawable.lolib_ic_impress_solid_color_054, 0x4C5253),
            new Entry(55, R.drawable.lolib_ic_impress_solid_color_055, 0xA3A8A3),
            new Entry(56, R.drawable.lolib_ic_impress_solid_color_056, 0xEAECE9),
            new Entry(57, R.drawable.lolib_ic_impress_solid_color_057, 0xDFDFE1),
            new Entry(58, R.drawable.lolib_ic_impress_solid_color_058, 0xCACCC8),
            new Entry(59, R.drawable.lolib_ic_impress_solid_color_059, 0xC18E07),
            new Entry(60, R.drawable.lolib_ic_impress_solid_color_060, 0x786300),
            new Entry(61, R.drawable.lolib_ic_impress_solid_color_061, 0xEBC229),
            new Entry(62, R.drawable.lolib_ic_impress_solid_color_062, 0xFFF0CC),
            new Entry(63, R.drawable.lolib_ic_impress_solid_color_063, 0xF6E1C1),
            new Entry(64, R.drawable.lolib_ic_impress_solid_color_064, 0xF6D968),
            new Entry(65, R.drawable.lolib_ic_impress_solid_color_065, 0x305599),
            new Entry(66, R.drawable.lolib_ic_impress_solid_color_066, 0x21385D),
            new Entry(67, R.drawable.lolib_ic_impress_solid_color_067, 0x4A71BE),
            new Entry(68, R.drawable.lolib_ic_impress_solid_color_068, 0xDAE3F3),
            new Entry(69, R.drawable.lolib_ic_impress_solid_color_069, 0xB2C7DF),
            new Entry(70, R.drawable.lolib_ic_impress_solid_color_070, 0x8FADD3),
            new Entry(71, R.drawable.lolib_ic_impress_solid_color_071, 0x5D7752),
            new Entry(72, R.drawable.lolib_ic_impress_solid_color_072, 0x39522D),
            new Entry(73, R.drawable.lolib_ic_impress_solid_color_073, 0x6DAB4B),
            new Entry(74, R.drawable.lolib_ic_impress_solid_color_074, 0xE5ECDC),
            new Entry(75, R.drawable.lolib_ic_impress_solid_color_075, 0xC4E1B2),
            new Entry(76, R.drawable.lolib_ic_impress_solid_color_076, 0xAACE8D)
        })
    };
}
