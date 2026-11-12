/* Calc borders use explicit geometry and widths; dotted guide icons mean absent edges. */
class CalcEditorBorder {
	static drawingColor = 0;
	static readonly EDGES: Record<string, number[]> = {
		'calc-border-all-dashed': [0, 0, 0, 0, 0, 0],
		'calc-border-all-solid': [1, 1, 1, 1, 1, 1],
		'calc-border-outer-solid-inner-dashed': [1, 1, 1, 1, 0, 0],
		'calc-border-outer-thick': [3, 3, 3, 3, 0, 0],
		'calc-border-top': [0, 0, 0, 1, 0, 0],
		'calc-border-bottom': [0, 0, 1, 0, 0, 0],
		'calc-border-left': [1, 0, 0, 0, 0, 0],
		'calc-border-right': [0, 1, 0, 0, 0, 0],
		'calc-border-inner-vertical': [0, 0, 0, 0, 0, 1],
		'calc-border-inner-horizontal': [0, 0, 0, 0, 1, 0],
	};

	static buildPresetCommand(icon: string, rgb: number): string | null {
		const line = (width: number) => ({ type: 'com.sun.star.table.BorderLine2', value: {
			Color: { type: 'com.sun.star.util.Color', value: rgb & 0xffffff },
			InnerLineWidth: { type: 'short', value: 0 },
			OuterLineWidth: { type: 'short', value: width * 20 },
			LineDistance: { type: 'short', value: 0 },
			LineStyle: { type: 'short', value: 0 },
			LineWidth: { type: 'unsigned long', value: width * 20 },
		} });
		if (icon === 'calc-border-diag-tl-br' || icon === 'calc-border-diag-tr-bl') {
			const command = icon === 'calc-border-diag-tl-br' ? 'BorderTLBR' : 'BorderBLTR';
			return '.uno:' + command + ' ' + JSON.stringify({ [command]: { type: 'any', value: line(1) } });
		}
		const edges = CalcEditorBorder.EDGES[icon];
		if (!edges) return null;
		const zero = { type: 'long', value: 0 };
		return '.uno:SetBorderStyle ' + JSON.stringify({
			OuterBorder: { type: '[]any', value: [...edges.slice(0, 4).map(line), zero, zero, zero, zero, zero] },
			InnerBorder: { type: '[]any', value: [line(edges[4]), line(edges[5]),
				{ type: 'short', value: 0 }, { type: 'short', value: 127 }, zero] },
		});
	}
	static buildBorderColorUnoCommand(rgb: number): string {
		if (CalcEditorFormat.isActive()) {
			// Recolor existing/default frame lines without replacing border geometry.
			return '.uno:FrameLineColor ' + JSON.stringify({
				'FrameLineColor.Color': { type: 'long', value: rgb & 0xffffff },
			});
		}
		const line = (outerWidth: number) => ({
			type: 'com.sun.star.table.BorderLine2',
			value: {
				Color: { type: 'com.sun.star.util.Color', value: rgb & 0xffffff },
				InnerLineWidth: { type: 'short', value: 0 },
				OuterLineWidth: { type: 'short', value: outerWidth },
				LineDistance: { type: 'short', value: 0 },
				LineStyle: { type: 'short', value: 0 },
				LineWidth: { type: 'unsigned long', value: 0 },
			},
		});
		const params = {
			OuterBorder: {
				type: '[]any',
				value: [
					line(1),
					line(1),
					line(1),
					line(1),
					{ type: 'long', value: 0 },
					{ type: 'long', value: 0 },
					{ type: 'long', value: 0 },
					{ type: 'long', value: 0 },
					{ type: 'long', value: 0 },
				],
			},
			InnerBorder: {
				type: '[]any',
				value: [
					line(0),
					line(0),
					{ type: 'short', value: 0 },
					{ type: 'short', value: 127 },
					{ type: 'long', value: 0 },
				],
			},
		};
		return '.uno:SetBorderStyle ' + JSON.stringify(params);
	}
}
