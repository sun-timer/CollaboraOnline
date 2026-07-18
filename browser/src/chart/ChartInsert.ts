/**
 * Insert Calc chart with a specific type (mobile function panel / chart type picker).
 *
 * Step 1: InsertObjectChart with typed UNO JSON (RangeList + optional ChartTemplate).
 * Step 2: ChartTemplate is applied in lo-core during insert when ChartTemplate param is set.
 */

interface ChartInsertArgs {
	RangeList: { type: 'string'; value: string };
	InNewTable: { type: 'boolean'; value: boolean };
	ChartTemplate?: { type: 'string'; value: string };
	ChartCurveStyle?: { type: 'int32'; value: number };
}

interface ChartTypeMapping {
	template: string;
	curveStyle?: number;
}

/** {@link com.sun.star.chart2.CurveStyle#CUBIC_SPLINES} */
const CURVE_STYLE_CUBIC_SPLINES = 1;

const CHART_TYPE_MAP: Record<string, ChartTypeMapping> = {
	pie: { template: 'com.sun.star.chart2.template.Pie' },
	'pie-rounded': { template: 'com.sun.star.chart2.template.Donut' },
	'pie-exploded': { template: 'com.sun.star.chart2.template.PieAllExploded' },
	line: { template: 'com.sun.star.chart2.template.LineSymbol' },
	'line-curve': {
		template: 'com.sun.star.chart2.template.LineSymbol',
		curveStyle: CURVE_STYLE_CUBIC_SPLINES,
	},
	column: { template: 'com.sun.star.chart2.template.Column' },
	bar: { template: 'com.sun.star.chart2.template.Bar' },
	'column-stacked': { template: 'com.sun.star.chart2.template.StackedColumn' },
};

namespace ChartInsert {
	export function buildInsertArgs(unoChartType: string, rangeList: string = ''): ChartInsertArgs {
		const args: ChartInsertArgs = {
			RangeList: { type: 'string', value: rangeList },
			InNewTable: { type: 'boolean', value: false },
		};
		const mapping = CHART_TYPE_MAP[unoChartType];
		if (!mapping || mapping.template === CHART_TYPE_MAP.column.template) {
			return args;
		}
		args.ChartTemplate = { type: 'string', value: mapping.template };
		if (mapping.curveStyle !== undefined && mapping.curveStyle >= 0) {
			args.ChartCurveStyle = { type: 'int32', value: mapping.curveStyle };
		}
		return args;
	}

	export function insertChartWithType(unoChartType: string, rangeList?: string): void {
		const args = buildInsertArgs(unoChartType, rangeList || '');
		app.map.sendUnoCommand('.uno:InsertObjectChart', args);
	}
}

(window as any).ChartInsert = ChartInsert;
