package org.libreoffice.androidlib;

/**
 * Maps App chart type ids to LibreOffice chart2 template service names.
 */
final class CalcChartTypeMapper {

    /** {@link com.sun.star.chart2.CurveStyle#CUBIC_SPLINES} */
    static final int CURVE_STYLE_CUBIC_SPLINES = 1;

    private CalcChartTypeMapper() {
    }

    static String toTemplateService(String unoChartType) {
        if (unoChartType == null || unoChartType.isEmpty()) {
            return "";
        }
        switch (unoChartType) {
            case "pie":
                return "com.sun.star.chart2.template.Pie";
            case "pie-rounded":
                return "com.sun.star.chart2.template.Donut";
            case "pie-exploded":
                return "com.sun.star.chart2.template.PieAllExploded";
            case "line":
                return "com.sun.star.chart2.template.LineSymbol";
            case "line-curve":
                return "com.sun.star.chart2.template.LineSymbol";
            case "column":
                return "com.sun.star.chart2.template.Column";
            case "bar":
                return "com.sun.star.chart2.template.Bar";
            case "column-stacked":
                return "com.sun.star.chart2.template.StackedColumn";
            default:
                return "";
        }
    }

    static int toCurveStyle(String unoChartType) {
        if ("line-curve".equals(unoChartType)) {
            return CURVE_STYLE_CUBIC_SPLINES;
        }
        return -1;
    }

    static boolean needsCustomTemplate(String unoChartType) {
        String template = toTemplateService(unoChartType);
        if (template.isEmpty()) {
            return false;
        }
        return !"com.sun.star.chart2.template.Column".equals(template)
                || toCurveStyle(unoChartType) >= 0;
    }

    static String buildInsertChartJson(String rangeList, String templateService, int curveStyle) {
        StringBuilder json = new StringBuilder();
        json.append("{\"RangeList\":{\"type\":\"string\",\"value\":\"");
        json.append(escapeJson(rangeList != null ? rangeList : ""));
        json.append("\"},\"InNewTable\":{\"type\":\"boolean\",\"value\":false}");
        json.append(",\"SynchronMode\":{\"type\":\"boolean\",\"value\":true}");
        if (templateService != null && !templateService.isEmpty()) {
            json.append(",\"ChartTemplate\":{\"type\":\"string\",\"value\":\"");
            json.append(escapeJson(templateService));
            json.append("\"}");
            if (curveStyle >= 0) {
                json.append(",\"ChartCurveStyle\":{\"type\":\"int32\",\"value\":");
                json.append(curveStyle);
                json.append("}");
            }
        }
        json.append("}");
        return json.toString();
    }

    /** AI chart ranges belong to the active sheet, whose real name comes from Core. */
    static String qualifyRange(String range, String sheetName) {
        if (sheetName == null || sheetName.isEmpty() || range == null) {
            throw new IllegalArgumentException("缺少图表数据范围或工作表名称");
        }
        String[] endpoints = range.trim().split(":", -1);
        if (endpoints.length < 1 || endpoints.length > 2) {
            throw new IllegalArgumentException("图表需要单个连续数据区域");
        }
        String start = cellAddress(endpoints[0]);
        String end = endpoints.length == 2 ? cellAddress(endpoints[1]) : start;
        String prefix = "$'" + sheetName.replace("'", "''") + "'.";
        return prefix + absoluteCell(start) + ":" + absoluteCell(end);
    }

    static String cellAddress(String reference) {
        String cell = reference.trim();
        int separator = Math.max(cell.lastIndexOf('.'), cell.lastIndexOf('!'));
        cell = cell.substring(separator + 1).replace("$", "").toUpperCase(java.util.Locale.ROOT);
        if (!cell.matches("[A-Z]{1,3}[1-9][0-9]{0,6}")) {
            throw new IllegalArgumentException("无效的单元格地址");
        }
        int split = 0;
        int column = 0;
        while (split < cell.length() && Character.isLetter(cell.charAt(split))) {
            column = column * 26 + cell.charAt(split) - 'A' + 1;
            split++;
        }
        if (column > 16384 || Integer.parseInt(cell.substring(split)) > 1048576) {
            throw new IllegalArgumentException("单元格地址超出工作表范围");
        }
        return cell;
    }

    private static String absoluteCell(String cell) {
        return "$" + cell.replaceFirst("([0-9])", "\\$$1");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
