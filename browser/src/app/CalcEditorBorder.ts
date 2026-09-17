/*
 * Calc cell border presets — mirrors Control.Toolbar.js setBorderStyle (1–12)
 * and Android CalcFunctionPanelController.buildBorderColorUnoCommand.
 */

class CalcEditorBorder {
	/** Android BORDER_ICONS order → desktop mobile border selector index. */
	static readonly PRESET_BY_ICON: Record<string, number> = {
		'calc-border-all-dashed': 1,
		'calc-border-all-solid': 12,
		'calc-border-outer-solid-inner-dashed': 10,
		'calc-border-outer-thick': 8,
		'calc-border-top': 5,
		'calc-border-bottom': 6,
		'calc-border-left': 2,
		'calc-border-right': 3,
		'calc-border-inner-vertical': 11,
		'calc-border-inner-horizontal': 9,
		'calc-border-diag-tl-br': 12,
		'calc-border-diag-tr-bl': 12,
	};

	static applyPreset(iconKey: string, colorRgb: number): void {
		const preset = CalcEditorBorder.PRESET_BY_ICON[iconKey];
		if (preset === undefined) {
			return;
		}
		const hex =
			'#' +
			(colorRgb & 0xffffff).toString(16).toUpperCase().padStart(6, '0');
		const setBorderStyle = (window as any).setBorderStyle;
		if (typeof setBorderStyle === 'function') {
			setBorderStyle(preset, hex);
			return;
		}
		const build = (window as any).getBorderStyleUNOCommand;
		if (typeof build !== 'function') {
			return;
		}
		const map = (window as any).app && (window as any).app.map;
		if (!map || typeof map.sendUnoCommand !== 'function') {
			return;
		}
		const color = colorRgb & 0xffffff;
		const args = CalcEditorBorder.presetToEdges(preset);
		if (!args) {
			return;
		}
		map.sendUnoCommand(
			build(args.left, args.right, args.bottom, args.top, args.horiz, args.vert, color),
		);
	}

	private static presetToEdges(preset: number): {
		left: number;
		right: number;
		bottom: number;
		top: number;
		horiz: number;
		vert: number;
	} | null {
		switch (preset) {
			case 1:
				return { left: 0, right: 0, bottom: 0, top: 0, horiz: 0, vert: 0 };
			case 2:
				return { left: 1, right: 0, bottom: 0, top: 0, horiz: 0, vert: 0 };
			case 3:
				return { left: 0, right: 1, bottom: 0, top: 0, horiz: 0, vert: 0 };
			case 4:
				return { left: 1, right: 1, bottom: 0, top: 0, horiz: 0, vert: 0 };
			case 5:
				return { left: 0, right: 0, bottom: 0, top: 1, horiz: 0, vert: 0 };
			case 6:
				return { left: 0, right: 0, bottom: 1, top: 0, horiz: 0, vert: 0 };
			case 7:
				return { left: 0, right: 0, bottom: 1, top: 1, horiz: 0, vert: 0 };
			case 8:
				return { left: 1, right: 1, bottom: 1, top: 1, horiz: 0, vert: 0 };
			case 9:
				return { left: 0, right: 0, bottom: 1, top: 1, horiz: 1, vert: 0 };
			case 10:
				return { left: 1, right: 1, bottom: 1, top: 1, horiz: 1, vert: 0 };
			case 11:
				return { left: 1, right: 1, bottom: 1, top: 1, horiz: 0, vert: 1 };
			case 12:
				return { left: 1, right: 1, bottom: 1, top: 1, horiz: 1, vert: 1 };
			default:
				return null;
		}
	}

	static buildBorderColorUnoCommand(rgb: number): string {
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
