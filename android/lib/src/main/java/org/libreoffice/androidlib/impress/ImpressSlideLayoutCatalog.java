package org.libreoffice.androidlib.impress;

import org.libreoffice.androidlib.R;

/**
 * Impress 幻灯片版式目录（功能面板「布局」Tab）。
 * 图标由 scripts/generate-impress-layout-assets.js 生成。
 */
public final class ImpressSlideLayoutCatalog {
    private ImpressSlideLayoutCatalog() {}

    public static final class Entry {
        public final int index;
        public final String label;
        /** LO AutoLayout / AssignLayout WhatLayout */
        public final int whatLayout;
        public final int iconResId;

        Entry(int index, String label, int whatLayout, int iconResId) {
            this.index = index;
            this.label = label;
            this.whatLayout = whatLayout;
            this.iconResId = iconResId;
        }
    }

    public static final Entry[] ENTRIES = new Entry[] {
            new Entry(1, "标题幻灯片", 0, R.drawable.lolib_ic_impress_layout_01_title),
            new Entry(2, "标题和内容", 1, R.drawable.lolib_ic_impress_layout_02_title_content),
            new Entry(3, "节标题", 2, R.drawable.lolib_ic_impress_layout_03_section),
            new Entry(4, "两栏内容", 3, R.drawable.lolib_ic_impress_layout_04_two_content),
            new Entry(5, "比较", 15, R.drawable.lolib_ic_impress_layout_05_compare),
            new Entry(6, "仅标题", 19, R.drawable.lolib_ic_impress_layout_06_title_only),
            new Entry(7, "空白", 20, R.drawable.lolib_ic_impress_layout_07_blank),
            new Entry(8, "图片与标题", 12, R.drawable.lolib_ic_impress_layout_08_picture_title),
            new Entry(9, "竖排标题与文本", 28, R.drawable.lolib_ic_impress_layout_09_vertical),
            new Entry(10, "内容", 32, R.drawable.lolib_ic_impress_layout_10_content),
            new Entry(11, "末尾幻灯片", 19, R.drawable.lolib_ic_impress_layout_11_end),
    };

    public static Entry byIndex(int index) {
        for (Entry entry : ENTRIES) {
            if (entry.index == index) {
                return entry;
            }
        }
        return null;
    }

    public static Entry byWhatLayout(int whatLayout) {
        for (Entry entry : ENTRIES) {
            if (entry.whatLayout == whatLayout) {
                return entry;
            }
        }
        return null;
    }
}
