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

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
