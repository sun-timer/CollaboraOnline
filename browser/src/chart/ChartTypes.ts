/**
 * Chart type definitions for the chart type picker
 */

interface ChartTypeInfo {
  id: string;
  name: string;
  categoryName: string;
  unoChartType: string; // Maps to LibreOffice chart type
}

interface ChartCategoryInfo {
  name: string;
  chartTypes: ChartTypeInfo[];
}

const CHART_TYPES: ChartTypeInfo[] = [
  // 饼图
  { id: 'pie-basic', name: '基础饼图', categoryName: '饼图', unoChartType: 'pie' },
  { id: 'pie-rounded', name: '基础饼图(圆角)', categoryName: '饼图', unoChartType: 'pie-rounded' },
  { id: 'pie-exploded', name: '变形饼图', categoryName: '饼图', unoChartType: 'pie-exploded' },
  // 线图
  { id: 'line-basic', name: '折线图', categoryName: '线图', unoChartType: 'line' },
  { id: 'line-curve', name: '曲线折线图', categoryName: '线图', unoChartType: 'line-curve' },
  // 柱图
  { id: 'column-basic', name: '基础柱状图', categoryName: '柱图', unoChartType: 'column' },
  { id: 'bar-basic', name: '基础条形图', categoryName: '柱图', unoChartType: 'bar' },
  { id: 'column-stacked', name: '堆叠柱状图', categoryName: '柱图', unoChartType: 'column-stacked' },
];
