package org.libreoffice.androidlib.impress;

import org.libreoffice.androidlib.R;

/**
 * Impress 插入形状目录（图标序号 1–197 ↔ UNO 命令）。
 * 由 scripts/generate-impress-shape-assets.js 生成，请勿手改。
 */
public final class ImpressShapeCatalog {
    private ImpressShapeCatalog() {}

    public static final class Entry {
        public final int index;
        public final int iconResId;
        public final String unoCommand;

        Entry(int index, int iconResId, String unoCommand) {
            this.index = index;
            this.iconResId = iconResId;
            this.unoCommand = unoCommand;
        }
    }

    public static final class Section {
        public final String title;
        public final Entry[] entries;

        Section(String title, Entry[] entries) {
            this.title = title;
            this.entries = entries;
        }
    }

    public static final Section[] SECTIONS = new Section[] {
        new Section("线条和箭头", new Entry[] {
            new Entry(1, R.drawable.lolib_ic_impress_shape_001, ".uno:Line"),
            new Entry(2, R.drawable.lolib_ic_impress_shape_002, ".uno:LineArrowEnd"),
            new Entry(3, R.drawable.lolib_ic_impress_shape_003, ".uno:LineCircleArrow"),
            new Entry(4, R.drawable.lolib_ic_impress_shape_004, ".uno:LineSquareArrow"),
            new Entry(5, R.drawable.lolib_ic_impress_shape_005, ".uno:LineArrows"),
            new Entry(6, R.drawable.lolib_ic_impress_shape_006, ".uno:LineArrowStart"),
            new Entry(7, R.drawable.lolib_ic_impress_shape_007, ".uno:LineArrowCircle"),
            new Entry(8, R.drawable.lolib_ic_impress_shape_008, ".uno:LineArrowSquare"),
            new Entry(9, R.drawable.lolib_ic_impress_shape_009, ".uno:MeasureLine"),
            new Entry(10, R.drawable.lolib_ic_impress_shape_010, ".uno:Line_Diagonal"),
            new Entry(11, R.drawable.lolib_ic_impress_shape_011, ".uno:Line")
        }),
        new Section("曲线和多边形", new Entry[] {
            new Entry(12, R.drawable.lolib_ic_impress_shape_012, ".uno:BezierFill"),
            new Entry(13, R.drawable.lolib_ic_impress_shape_013, ".uno:Polygon"),
            new Entry(14, R.drawable.lolib_ic_impress_shape_014, ".uno:Polygon_Diagonal"),
            new Entry(15, R.drawable.lolib_ic_impress_shape_015, ".uno:Freeline"),
            new Entry(16, R.drawable.lolib_ic_impress_shape_016, ".uno:Bezier_Unfilled"),
            new Entry(17, R.drawable.lolib_ic_impress_shape_017, ".uno:Polygon_Unfilled"),
            new Entry(18, R.drawable.lolib_ic_impress_shape_018, ".uno:Polygon_Diagonal_Unfilled"),
            new Entry(19, R.drawable.lolib_ic_impress_shape_019, ".uno:Freeline_Unfilled"),
            new Entry(20, R.drawable.lolib_ic_impress_shape_020, ".uno:Ellipse"),
            new Entry(21, R.drawable.lolib_ic_impress_shape_021, ".uno:Ellipse_Unfilled"),
            new Entry(22, R.drawable.lolib_ic_impress_shape_022, ".uno:CircleCut"),
            new Entry(23, R.drawable.lolib_ic_impress_shape_023, ".uno:Arc")
        }),
        new Section("连接符", new Entry[] {
            new Entry(24, R.drawable.lolib_ic_impress_shape_024, ".uno:ConnectorArrowEnd"),
            new Entry(25, R.drawable.lolib_ic_impress_shape_025, ".uno:ConnectorLineArrowEnd"),
            new Entry(26, R.drawable.lolib_ic_impress_shape_026, ".uno:ConnectorCurveArrowEnd"),
            new Entry(27, R.drawable.lolib_ic_impress_shape_027, ".uno:ConnectorLinesArrowEnd"),
            new Entry(28, R.drawable.lolib_ic_impress_shape_028, ".uno:Connector"),
            new Entry(29, R.drawable.lolib_ic_impress_shape_029, ".uno:ConnectorLine"),
            new Entry(30, R.drawable.lolib_ic_impress_shape_030, ".uno:ConnectorCurve"),
            new Entry(31, R.drawable.lolib_ic_impress_shape_031, ".uno:ConnectorLines"),
            new Entry(32, R.drawable.lolib_ic_impress_shape_032, ".uno:ConnectorArrows"),
            new Entry(33, R.drawable.lolib_ic_impress_shape_033, ".uno:ConnectorLineArrows"),
            new Entry(34, R.drawable.lolib_ic_impress_shape_034, ".uno:ConnectorCurveArrows"),
            new Entry(35, R.drawable.lolib_ic_impress_shape_035, ".uno:ConnectorLinesArrows")
        }),
        new Section("基本形状", new Entry[] {
            new Entry(36, R.drawable.lolib_ic_impress_shape_036, ".uno:BasicShapes.rectangle"),
            new Entry(37, R.drawable.lolib_ic_impress_shape_037, ".uno:BasicShapes.round-rectangle"),
            new Entry(38, R.drawable.lolib_ic_impress_shape_038, ".uno:BasicShapes.quadrat"),
            new Entry(39, R.drawable.lolib_ic_impress_shape_039, ".uno:BasicShapes.round-quadrat"),
            new Entry(40, R.drawable.lolib_ic_impress_shape_040, ".uno:BasicShapes.parallelogram"),
            new Entry(41, R.drawable.lolib_ic_impress_shape_041, ".uno:BasicShapes.trapezoid"),
            new Entry(42, R.drawable.lolib_ic_impress_shape_042, ".uno:BasicShapes.ellipse"),
            new Entry(43, R.drawable.lolib_ic_impress_shape_043, ".uno:BasicShapes.circle"),
            new Entry(44, R.drawable.lolib_ic_impress_shape_044, ".uno:BasicShapes.circle-pie"),
            new Entry(45, R.drawable.lolib_ic_impress_shape_045, ".uno:CircleCut"),
            new Entry(46, R.drawable.lolib_ic_impress_shape_046, ".uno:Arc"),
            new Entry(47, R.drawable.lolib_ic_impress_shape_047, ".uno:BasicShapes.block-arc"),
            new Entry(48, R.drawable.lolib_ic_impress_shape_048, ".uno:BasicShapes.isosceles-triangle"),
            new Entry(49, R.drawable.lolib_ic_impress_shape_049, ".uno:BasicShapes.right-triangle"),
            new Entry(50, R.drawable.lolib_ic_impress_shape_050, ".uno:BasicShapes.diamond"),
            new Entry(51, R.drawable.lolib_ic_impress_shape_051, ".uno:BasicShapes.pentagon"),
            new Entry(52, R.drawable.lolib_ic_impress_shape_052, ".uno:BasicShapes.hexagon"),
            new Entry(53, R.drawable.lolib_ic_impress_shape_053, ".uno:BasicShapes.octagon"),
            new Entry(54, R.drawable.lolib_ic_impress_shape_054, ".uno:BasicShapes.can"),
            new Entry(55, R.drawable.lolib_ic_impress_shape_055, ".uno:BasicShapes.cube"),
            new Entry(56, R.drawable.lolib_ic_impress_shape_056, ".uno:BasicShapes.paper"),
            new Entry(57, R.drawable.lolib_ic_impress_shape_057, ".uno:BasicShapes.cross"),
            new Entry(58, R.drawable.lolib_ic_impress_shape_058, ".uno:BasicShapes.frame"),
            new Entry(59, R.drawable.lolib_ic_impress_shape_059, ".uno:BasicShapes.ring")
        }),
        new Section("符号形状", new Entry[] {
            new Entry(60, R.drawable.lolib_ic_impress_shape_060, ".uno:SymbolShapes.smiley"),
            new Entry(61, R.drawable.lolib_ic_impress_shape_061, ".uno:SymbolShapes.heart"),
            new Entry(62, R.drawable.lolib_ic_impress_shape_062, ".uno:SymbolShapes.sun"),
            new Entry(63, R.drawable.lolib_ic_impress_shape_063, ".uno:SymbolShapes.moon"),
            new Entry(64, R.drawable.lolib_ic_impress_shape_064, ".uno:SymbolShapes.cloud"),
            new Entry(65, R.drawable.lolib_ic_impress_shape_065, ".uno:SymbolShapes.lightning"),
            new Entry(66, R.drawable.lolib_ic_impress_shape_066, ".uno:SymbolShapes.flower"),
            new Entry(67, R.drawable.lolib_ic_impress_shape_067, ".uno:SymbolShapes.forbidden"),
            new Entry(68, R.drawable.lolib_ic_impress_shape_068, ".uno:SymbolShapes.puzzle"),
            new Entry(69, R.drawable.lolib_ic_impress_shape_069, ".uno:SymbolShapes.quad-bevel"),
            new Entry(70, R.drawable.lolib_ic_impress_shape_070, ".uno:SymbolShapes.octagon-bevel"),
            new Entry(71, R.drawable.lolib_ic_impress_shape_071, ".uno:SymbolShapes.diamond-bevel"),
            new Entry(72, R.drawable.lolib_ic_impress_shape_072, ".uno:SymbolShapes.bracket-pair"),
            new Entry(73, R.drawable.lolib_ic_impress_shape_073, ".uno:SymbolShapes.left-bracket"),
            new Entry(74, R.drawable.lolib_ic_impress_shape_074, ".uno:SymbolShapes.right-bracket"),
            new Entry(75, R.drawable.lolib_ic_impress_shape_075, ".uno:SymbolShapes.brace-pair"),
            new Entry(76, R.drawable.lolib_ic_impress_shape_076, ".uno:SymbolShapes.left-brace"),
            new Entry(77, R.drawable.lolib_ic_impress_shape_077, ".uno:SymbolShapes.right-brace")
        }),
        new Section("箭头总汇", new Entry[] {
            new Entry(78, R.drawable.lolib_ic_impress_shape_078, ".uno:ArrowShapes.right-arrow"),
            new Entry(79, R.drawable.lolib_ic_impress_shape_079, ".uno:ArrowShapes.left-arrow"),
            new Entry(80, R.drawable.lolib_ic_impress_shape_080, ".uno:ArrowShapes.down-arrow"),
            new Entry(81, R.drawable.lolib_ic_impress_shape_081, ".uno:ArrowShapes.up-arrow"),
            new Entry(82, R.drawable.lolib_ic_impress_shape_082, ".uno:ArrowShapes.left-right-arrow"),
            new Entry(83, R.drawable.lolib_ic_impress_shape_083, ".uno:ArrowShapes.up-down-arrow"),
            new Entry(84, R.drawable.lolib_ic_impress_shape_084, ".uno:ArrowShapes.circular-arrow"),
            new Entry(85, R.drawable.lolib_ic_impress_shape_085, ".uno:ArrowShapes.s-sharped-arrow"),
            new Entry(86, R.drawable.lolib_ic_impress_shape_086, ".uno:ArrowShapes.split-arrow"),
            new Entry(87, R.drawable.lolib_ic_impress_shape_087, ".uno:ArrowShapes.split-round-arrow"),
            new Entry(88, R.drawable.lolib_ic_impress_shape_088, ".uno:ArrowShapes.quad-arrow"),
            new Entry(89, R.drawable.lolib_ic_impress_shape_089, ".uno:ArrowShapes.corner-right-arrow"),
            new Entry(90, R.drawable.lolib_ic_impress_shape_090, ".uno:ArrowShapes.chevron"),
            new Entry(91, R.drawable.lolib_ic_impress_shape_091, ".uno:ArrowShapes.pentagon-right"),
            new Entry(92, R.drawable.lolib_ic_impress_shape_092, ".uno:ArrowShapes.striped-right-arrow"),
            new Entry(93, R.drawable.lolib_ic_impress_shape_093, ".uno:ArrowShapes.up-right-down-arrow"),
            new Entry(94, R.drawable.lolib_ic_impress_shape_094, ".uno:ArrowShapes.notched-right-arrow"),
            new Entry(95, R.drawable.lolib_ic_impress_shape_095, ".uno:ArrowShapes.up-right-arrow"),
            new Entry(96, R.drawable.lolib_ic_impress_shape_096, ".uno:ArrowShapes.right-arrow-callout"),
            new Entry(97, R.drawable.lolib_ic_impress_shape_097, ".uno:ArrowShapes.left-arrow-callout"),
            new Entry(98, R.drawable.lolib_ic_impress_shape_098, ".uno:ArrowShapes.down-arrow-callout"),
            new Entry(99, R.drawable.lolib_ic_impress_shape_099, ".uno:ArrowShapes.up-arrow-callout"),
            new Entry(100, R.drawable.lolib_ic_impress_shape_100, ".uno:ArrowShapes.left-right-arrow-callout"),
            new Entry(101, R.drawable.lolib_ic_impress_shape_101, ".uno:ArrowShapes.up-down-arrow-callout"),
            new Entry(102, R.drawable.lolib_ic_impress_shape_102, ".uno:ArrowShapes.quad-arrow-callout"),
            new Entry(103, R.drawable.lolib_ic_impress_shape_103, ".uno:ArrowShapes.up-right-arrow-callout"),
            new Entry(104, R.drawable.lolib_ic_impress_shape_104, ".uno:ArrowShapes.up-right-down-arrow"),
            new Entry(105, R.drawable.lolib_ic_impress_shape_105, ".uno:ArrowShapes.notched-right-arrow"),
            new Entry(106, R.drawable.lolib_ic_impress_shape_106, ".uno:ArrowShapes.up-right-arrow"),
            new Entry(107, R.drawable.lolib_ic_impress_shape_107, ".uno:ArrowShapes.striped-right-arrow")
        }),
        new Section("流程图", new Entry[] {
            new Entry(108, R.drawable.lolib_ic_impress_shape_108, ".uno:FlowChartShapes.flowchart-process"),
            new Entry(109, R.drawable.lolib_ic_impress_shape_109, ".uno:FlowChartShapes.flowchart-alternate-process"),
            new Entry(110, R.drawable.lolib_ic_impress_shape_110, ".uno:FlowChartShapes.flowchart-decision"),
            new Entry(111, R.drawable.lolib_ic_impress_shape_111, ".uno:FlowChartShapes.flowchart-data"),
            new Entry(112, R.drawable.lolib_ic_impress_shape_112, ".uno:FlowChartShapes.flowchart-predefined-process"),
            new Entry(113, R.drawable.lolib_ic_impress_shape_113, ".uno:FlowChartShapes.flowchart-internal-storage"),
            new Entry(114, R.drawable.lolib_ic_impress_shape_114, ".uno:FlowChartShapes.flowchart-document"),
            new Entry(115, R.drawable.lolib_ic_impress_shape_115, ".uno:FlowChartShapes.flowchart-multidocument"),
            new Entry(116, R.drawable.lolib_ic_impress_shape_116, ".uno:FlowChartShapes.flowchart-terminator"),
            new Entry(117, R.drawable.lolib_ic_impress_shape_117, ".uno:FlowChartShapes.flowchart-preparation"),
            new Entry(118, R.drawable.lolib_ic_impress_shape_118, ".uno:FlowChartShapes.flowchart-manual-input"),
            new Entry(119, R.drawable.lolib_ic_impress_shape_119, ".uno:FlowChartShapes.flowchart-manual-operation"),
            new Entry(120, R.drawable.lolib_ic_impress_shape_120, ".uno:FlowChartShapes.flowchart-connector"),
            new Entry(121, R.drawable.lolib_ic_impress_shape_121, ".uno:FlowChartShapes.flowchart-off-page-connector"),
            new Entry(122, R.drawable.lolib_ic_impress_shape_122, ".uno:FlowChartShapes.flowchart-card"),
            new Entry(123, R.drawable.lolib_ic_impress_shape_123, ".uno:FlowChartShapes.flowchart-punched-tape"),
            new Entry(124, R.drawable.lolib_ic_impress_shape_124, ".uno:FlowChartShapes.flowchart-summing-junction"),
            new Entry(125, R.drawable.lolib_ic_impress_shape_125, ".uno:FlowChartShapes.flowchart-or"),
            new Entry(126, R.drawable.lolib_ic_impress_shape_126, ".uno:FlowChartShapes.flowchart-collate"),
            new Entry(127, R.drawable.lolib_ic_impress_shape_127, ".uno:FlowChartShapes.flowchart-sort"),
            new Entry(128, R.drawable.lolib_ic_impress_shape_128, ".uno:FlowChartShapes.flowchart-extract"),
            new Entry(129, R.drawable.lolib_ic_impress_shape_129, ".uno:FlowChartShapes.flowchart-merge"),
            new Entry(130, R.drawable.lolib_ic_impress_shape_130, ".uno:FlowChartShapes.flowchart-stored-data"),
            new Entry(131, R.drawable.lolib_ic_impress_shape_131, ".uno:FlowChartShapes.flowchart-delay"),
            new Entry(132, R.drawable.lolib_ic_impress_shape_132, ".uno:FlowChartShapes.flowchart-sequential-access"),
            new Entry(133, R.drawable.lolib_ic_impress_shape_133, ".uno:FlowChartShapes.flowchart-magnetic-disk"),
            new Entry(134, R.drawable.lolib_ic_impress_shape_134, ".uno:FlowChartShapes.flowchart-direct-access-storage"),
            new Entry(135, R.drawable.lolib_ic_impress_shape_135, ".uno:FlowChartShapes.flowchart-display"),
            new Entry(136, R.drawable.lolib_ic_impress_shape_136, ".uno:FlowChartShapes.flowchart-process"),
            new Entry(137, R.drawable.lolib_ic_impress_shape_137, ".uno:FlowChartShapes.flowchart-alternate-process")
        }),
        new Section("流程图", new Entry[] {
            new Entry(138, R.drawable.lolib_ic_impress_shape_138, ".uno:FlowChartShapes.flowchart-process"),
            new Entry(139, R.drawable.lolib_ic_impress_shape_139, ".uno:FlowChartShapes.flowchart-alternate-process"),
            new Entry(140, R.drawable.lolib_ic_impress_shape_140, ".uno:FlowChartShapes.flowchart-decision"),
            new Entry(141, R.drawable.lolib_ic_impress_shape_141, ".uno:FlowChartShapes.flowchart-data"),
            new Entry(142, R.drawable.lolib_ic_impress_shape_142, ".uno:FlowChartShapes.flowchart-predefined-process"),
            new Entry(143, R.drawable.lolib_ic_impress_shape_143, ".uno:FlowChartShapes.flowchart-internal-storage"),
            new Entry(144, R.drawable.lolib_ic_impress_shape_144, ".uno:FlowChartShapes.flowchart-document"),
            new Entry(145, R.drawable.lolib_ic_impress_shape_145, ".uno:FlowChartShapes.flowchart-multidocument"),
            new Entry(146, R.drawable.lolib_ic_impress_shape_146, ".uno:FlowChartShapes.flowchart-terminator"),
            new Entry(147, R.drawable.lolib_ic_impress_shape_147, ".uno:FlowChartShapes.flowchart-preparation"),
            new Entry(148, R.drawable.lolib_ic_impress_shape_148, ".uno:FlowChartShapes.flowchart-manual-input"),
            new Entry(149, R.drawable.lolib_ic_impress_shape_149, ".uno:FlowChartShapes.flowchart-manual-operation"),
            new Entry(150, R.drawable.lolib_ic_impress_shape_150, ".uno:FlowChartShapes.flowchart-connector"),
            new Entry(151, R.drawable.lolib_ic_impress_shape_151, ".uno:FlowChartShapes.flowchart-off-page-connector"),
            new Entry(152, R.drawable.lolib_ic_impress_shape_152, ".uno:FlowChartShapes.flowchart-card"),
            new Entry(153, R.drawable.lolib_ic_impress_shape_153, ".uno:FlowChartShapes.flowchart-punched-tape"),
            new Entry(154, R.drawable.lolib_ic_impress_shape_154, ".uno:FlowChartShapes.flowchart-summing-junction"),
            new Entry(155, R.drawable.lolib_ic_impress_shape_155, ".uno:FlowChartShapes.flowchart-or"),
            new Entry(156, R.drawable.lolib_ic_impress_shape_156, ".uno:FlowChartShapes.flowchart-collate"),
            new Entry(157, R.drawable.lolib_ic_impress_shape_157, ".uno:FlowChartShapes.flowchart-sort"),
            new Entry(158, R.drawable.lolib_ic_impress_shape_158, ".uno:FlowChartShapes.flowchart-extract"),
            new Entry(159, R.drawable.lolib_ic_impress_shape_159, ".uno:FlowChartShapes.flowchart-merge"),
            new Entry(160, R.drawable.lolib_ic_impress_shape_160, ".uno:FlowChartShapes.flowchart-stored-data"),
            new Entry(161, R.drawable.lolib_ic_impress_shape_161, ".uno:FlowChartShapes.flowchart-delay"),
            new Entry(162, R.drawable.lolib_ic_impress_shape_162, ".uno:FlowChartShapes.flowchart-sequential-access"),
            new Entry(163, R.drawable.lolib_ic_impress_shape_163, ".uno:FlowChartShapes.flowchart-magnetic-disk"),
            new Entry(164, R.drawable.lolib_ic_impress_shape_164, ".uno:FlowChartShapes.flowchart-direct-access-storage"),
            new Entry(165, R.drawable.lolib_ic_impress_shape_165, ".uno:FlowChartShapes.flowchart-display"),
            new Entry(166, R.drawable.lolib_ic_impress_shape_166, ".uno:FlowChartShapes.flowchart-process"),
            new Entry(167, R.drawable.lolib_ic_impress_shape_167, ".uno:FlowChartShapes.flowchart-alternate-process")
        }),
        new Section("标注", new Entry[] {
            new Entry(168, R.drawable.lolib_ic_impress_shape_168, ".uno:CalloutShapes.rectangular-callout"),
            new Entry(169, R.drawable.lolib_ic_impress_shape_169, ".uno:CalloutShapes.round-rectangular-callout"),
            new Entry(170, R.drawable.lolib_ic_impress_shape_170, ".uno:CalloutShapes.round-callout"),
            new Entry(171, R.drawable.lolib_ic_impress_shape_171, ".uno:CalloutShapes.cloud-callout"),
            new Entry(172, R.drawable.lolib_ic_impress_shape_172, ".uno:CalloutShapes.line-callout-1"),
            new Entry(173, R.drawable.lolib_ic_impress_shape_173, ".uno:CalloutShapes.line-callout-2"),
            new Entry(174, R.drawable.lolib_ic_impress_shape_174, ".uno:CalloutShapes.line-callout-3"),
            new Entry(175, R.drawable.lolib_ic_impress_shape_175, ".uno:ArrowShapes.right-arrow-callout"),
            new Entry(176, R.drawable.lolib_ic_impress_shape_176, ".uno:ArrowShapes.left-arrow-callout"),
            new Entry(177, R.drawable.lolib_ic_impress_shape_177, ".uno:ArrowShapes.up-arrow-callout"),
            new Entry(178, R.drawable.lolib_ic_impress_shape_178, ".uno:ArrowShapes.down-arrow-callout"),
            new Entry(179, R.drawable.lolib_ic_impress_shape_179, ".uno:ArrowShapes.left-right-arrow-callout")
        }),
        new Section("星形与旗帜", new Entry[] {
            new Entry(180, R.drawable.lolib_ic_impress_shape_180, ".uno:StarShapes.star4"),
            new Entry(181, R.drawable.lolib_ic_impress_shape_181, ".uno:StarShapes.star5"),
            new Entry(182, R.drawable.lolib_ic_impress_shape_182, ".uno:StarShapes.star6"),
            new Entry(183, R.drawable.lolib_ic_impress_shape_183, ".uno:StarShapes.star8"),
            new Entry(184, R.drawable.lolib_ic_impress_shape_184, ".uno:StarShapes.star12"),
            new Entry(185, R.drawable.lolib_ic_impress_shape_185, ".uno:StarShapes.star24"),
            new Entry(186, R.drawable.lolib_ic_impress_shape_186, ".uno:StarShapes.bang"),
            new Entry(187, R.drawable.lolib_ic_impress_shape_187, ".uno:StarShapes.vertical-scroll"),
            new Entry(188, R.drawable.lolib_ic_impress_shape_188, ".uno:StarShapes.horizontal-scroll"),
            new Entry(189, R.drawable.lolib_ic_impress_shape_189, ".uno:StarShapes.signet"),
            new Entry(190, R.drawable.lolib_ic_impress_shape_190, ".uno:StarShapes.doorplate"),
            new Entry(191, R.drawable.lolib_ic_impress_shape_191, ".uno:StarShapes.concave-star6"),
            new Entry(192, R.drawable.lolib_ic_impress_shape_192, ".uno:StarShapes.star5"),
            new Entry(193, R.drawable.lolib_ic_impress_shape_193, ".uno:StarShapes.star6"),
            new Entry(194, R.drawable.lolib_ic_impress_shape_194, ".uno:StarShapes.star8"),
            new Entry(195, R.drawable.lolib_ic_impress_shape_195, ".uno:StarShapes.star12"),
            new Entry(196, R.drawable.lolib_ic_impress_shape_196, ".uno:StarShapes.star24"),
            new Entry(197, R.drawable.lolib_ic_impress_shape_197, ".uno:StarShapes.star4")
        })
    };
}
