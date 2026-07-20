package org.libreoffice.androidlib.impress;

import org.libreoffice.androidlib.R;

/**
 * Impress 幻灯片切换动画目录（zip 序号 1–36 ↔ transitions_icons 行号）。
 * 由 scripts/generate-impress-transition-assets.js 生成，请勿手改。
 */
public final class ImpressTransitionCatalog {
    private ImpressTransitionCatalog() {}

    public static final class Entry {
        public final int index;
        public final String label;
        /** LO TransitionSet id；无 = null */
        public final String setId;
        /** SlideTransitionPane transitions_icons 行号（0 = 无） */
        public final int iconViewIndex;
        public final int iconResId;

        Entry(int index, String label, String setId, int iconViewIndex, int iconResId) {
            this.index = index;
            this.label = label;
            this.setId = setId;
            this.iconViewIndex = iconViewIndex;
            this.iconResId = iconResId;
        }
    }

    public static final Entry[] ENTRIES = new Entry[] {
            new Entry(1, "无", null, 0, R.drawable.lolib_ic_impress_transition_001),
            new Entry(2, "擦除", "wipe", 1, R.drawable.lolib_ic_impress_transition_002),
            new Entry(3, "滚轮", "wheel", 2, R.drawable.lolib_ic_impress_transition_003),
            new Entry(4, "揭开", "uncover", 3, R.drawable.lolib_ic_impress_transition_004),
            new Entry(5, "条形", "random-bars", 4, R.drawable.lolib_ic_impress_transition_005),
            new Entry(6, "棋盘", "checkerboard", 5, R.drawable.lolib_ic_impress_transition_006),
            new Entry(7, "形状", "shape", 6, R.drawable.lolib_ic_impress_transition_007),
            new Entry(8, "框", "box", 7, R.drawable.lolib_ic_impress_transition_008),
            new Entry(9, "楔形", "wedge", 8, R.drawable.lolib_ic_impress_transition_009),
            new Entry(10, "百叶窗", "venetian-blinds", 9, R.drawable.lolib_ic_impress_transition_010),
            new Entry(11, "淡入", "fade", 10, R.drawable.lolib_ic_impress_transition_011),
            new Entry(12, "切入", "cut", 11, R.drawable.lolib_ic_impress_transition_012),
            new Entry(13, "覆盖", "cover", 12, R.drawable.lolib_ic_impress_transition_013),
            new Entry(14, "溶解", "dissolve", 13, R.drawable.lolib_ic_impress_transition_014),
            new Entry(15, "随机", "random", 14, R.drawable.lolib_ic_impress_transition_015),
            new Entry(16, "梳动", "comb", 15, R.drawable.lolib_ic_impress_transition_016),
            new Entry(17, "推出", "push", 16, R.drawable.lolib_ic_impress_transition_017),
            new Entry(18, "拆分", "split", 17, R.drawable.lolib_ic_impress_transition_018),
            new Entry(19, "斜角方块", "diagonal-squares", 18, R.drawable.lolib_ic_impress_transition_019),
            new Entry(20, "磁贴", "tile-flip", 19, R.drawable.lolib_ic_impress_transition_020),
            new Entry(21, "立方体", "cube-turning", 20, R.drawable.lolib_ic_impress_transition_021),
            new Entry(22, "多重圆", "revolving-circles", 21, R.drawable.lolib_ic_impress_transition_022),
            new Entry(23, "螺旋", "turning-helix", 22, R.drawable.lolib_ic_impress_transition_023),
            new Entry(24, "跌落", "fall", 23, R.drawable.lolib_ic_impress_transition_024),
            new Entry(25, "翻转", "turn-around", 24, R.drawable.lolib_ic_impress_transition_025),
            new Entry(26, "光圈", "iris", 25, R.drawable.lolib_ic_impress_transition_026),
            new Entry(27, "向下转", "turn-down", 26, R.drawable.lolib_ic_impress_transition_027),
            new Entry(28, "左右互换", "rochade", 27, R.drawable.lolib_ic_impress_transition_028),
            new Entry(29, "3D百叶窗", "venetian-blinds-3d", 28, R.drawable.lolib_ic_impress_transition_029),
            new Entry(30, "静电干扰", "static", 29, R.drawable.lolib_ic_impress_transition_030),
            new Entry(31, "精细溶解", "finedissolve", 30, R.drawable.lolib_ic_impress_transition_031),
            new Entry(32, "漩涡", "vortex", 31, R.drawable.lolib_ic_impress_transition_032),
            new Entry(33, "涟漪", "ripple", 32, R.drawable.lolib_ic_impress_transition_033),
            new Entry(34, "闪耀", "glitter", 33, R.drawable.lolib_ic_impress_transition_034),
            new Entry(35, "蜂巢", "honeycomb", 34, R.drawable.lolib_ic_impress_transition_035),
            new Entry(36, "新闻快讯", "newsflash", 35, R.drawable.lolib_ic_impress_transition_036)
    };

    public static Entry byIndex(int index) {
        for (Entry entry : ENTRIES) {
            if (entry.index == index) {
                return entry;
            }
        }
        return null;
    }
}
