/*
 * Writer document-editing feature controller.
 *
 * Owns UNO command dispatch and find/replace SearchItem construction. Network,
 * UNO, and document access live in a WriterEditorAdapter so the controller
 * stays pure and testable (mirrors WriterAiController's bridge/adapter split).
 */

interface WriterEditorAdapterLike {
	sendUnoCommand(command: string): void;
	getToolbarCommandValues(command: string): { [key: string]: any } | undefined;
	getDocType(): string;
	getSelectedText(): string;
	sendExecuteSearch(searchCmd: { [key: string]: any }): void;
	postMobileMessage(message: string): void;
	downloadAs(name: string, format: string, options?: string, id?: string): void;
}

interface WriterFindReplaceOptions {
	caseSensitive?: boolean;
	wholeWord?: boolean;
}

type WriterEditorRunResult =
	| { dispatched: 'unocmd'; command: string }
	| { dispatched: 'save' }
	| { dispatched: 'export'; kind: 'pdf' | 'print' }
	| { dispatched: 'dialog'; dialog: WriterEditorDialogType }
	| { dispatched: 'findReplace' }
	| { dispatched: 'message'; message: string }
	| { dispatched: 'none'; reason: string };

interface WriterEditorSearchResult {
	executed: boolean;
	reason?: string;
	command?: number;
}

// SearchItem command / flag constants (mirror AndroidFindReplaceBridge).
const WriterEditorSearch = {
	CMD_FIND: 0,
	CMD_REPLACE: 2,
	CMD_REPLACE_ALL: 3,
	FLAG_MATCH_CASE: 0x00000001,
	FLAG_WHOLE_WORD: 0x00000010,
} as const;

class WriterEditorController {
	private readonly adapter: WriterEditorAdapterLike;

	constructor(adapter: WriterEditorAdapterLike = WriterEditorController.defaultAdapter()) {
		this.adapter = adapter;
	}

	static getInstance(): WriterEditorController {
		const existing = typeof window !== 'undefined'
			? (window as any).__coolWriterEditorController
			: null;
		if (existing instanceof WriterEditorController) {
			return existing;
		}
		const controller = new WriterEditorController();
		if (typeof window !== 'undefined') {
			(window as any).__coolWriterEditorController = controller;
			(window as any).WriterEditorController = controller;
		}
		return controller;
	}

	/** Runs a feature; returns what was dispatched so the DOM layer can react. */
	run(feature: WriterEditorFeature | null): WriterEditorRunResult {
		if (!feature) {
			return { dispatched: 'none', reason: 'missing_feature' };
		}
		if (!this.isWriterDocument()) {
			return { dispatched: 'none', reason: 'not_writer_document' };
		}
		switch (feature.kind) {
			case 'command':
				return this.runCommand(feature);
			case 'queryCommand':
				return this.runQueryCommand(feature);
			case 'commandWithArgs':
				return this.runCommandWithArgs(feature);
			case 'save':
				return this.runSave(feature);
			case 'export':
				return this.runExport('pdf');
			case 'print':
				return this.runExport('print');
			case 'dialog':
				return { dispatched: 'dialog', dialog: feature.dialog || 'fontName' };
			case 'findReplace':
				return { dispatched: 'findReplace' };
			default:
				return { dispatched: 'none', reason: 'unsupported_kind' };
		}
	}

	/** Dispatches an ExecuteSearch command via the adapter. */
	runFindReplace(
		query: string,
		replaceWith: string,
		replaceAll: boolean,
		options?: WriterFindReplaceOptions,
	): WriterEditorSearchResult {
		if (!query) {
			return { executed: false, reason: 'empty_query' };
		}
		if (!this.isWriterDocument()) {
			return { executed: false, reason: 'not_writer_document' };
		}
		const command = replaceAll
			? WriterEditorSearch.CMD_REPLACE_ALL
			: WriterEditorSearch.CMD_REPLACE;
		const searchCmd = WriterEditorController.buildSearchCmd(
			query,
			replaceWith || '',
			false,
			command,
			options,
		);
		this.adapter.sendExecuteSearch(searchCmd);
		return { executed: true, command };
	}

	/** Dispatches a forward/backward find over ExecuteSearch (CMD_FIND). */
	runFind(
		query: string,
		backward: boolean,
		options?: WriterFindReplaceOptions,
	): WriterEditorSearchResult {
		if (!query) {
			return { executed: false, reason: 'empty_query' };
		}
		if (!this.isWriterDocument()) {
			return { executed: false, reason: 'not_writer_document' };
		}
		const searchCmd = WriterEditorController.buildSearchCmd(
			query,
			'',
			backward,
			WriterEditorSearch.CMD_FIND,
			options,
		);
		this.adapter.sendExecuteSearch(searchCmd);
		return { executed: true, command: WriterEditorSearch.CMD_FIND };
	}

	/** Pure SearchItem payload builder (test seam). */
	/** Applies a font-family by dispatching the CharFontName UNO command. */
	applyFontName(fontName: string): WriterEditorRunResult {
		if (!fontName) {
			return { dispatched: 'none', reason: 'empty_font' };
		}
		const command =
			'.uno:CharFontName {"CharFontName.FamilyName":{"type":"string","value":' +
			JSON.stringify(fontName) + '}}';
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	/** Applies a font size (pt) by dispatching the FontHeight UNO command. */
	applyFontSize(sizePt: string): WriterEditorRunResult {
		if (!sizePt) {
			return { dispatched: 'none', reason: 'empty_size' };
		}
		const command =
			'.uno:FontHeight {"FontHeight.Height":{"type":"float","value":' +
			JSON.stringify(sizePt) + '}}';
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
		}

	/** Inserts a table (columns x rows) via the InsertTable UNO command. */
	insertTable(columns: number, rows: number): WriterEditorRunResult {
		if (!columns || !rows || columns < 1 || rows < 1) {
			return { dispatched: 'none', reason: 'invalid_table' };
		}
		const command =
			'.uno:InsertTable {"Columns":{"type":"long","value":' + columns +
			'},"Rows":{"type":"long","value":' + rows + '}}';
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	/** Applies page margins (HMM) via PageLRMargin + PageULMargin. */
	applyMargins(left: number, right: number, top: number, bottom: number): WriterEditorRunResult {
		const cmdLR =
			'.uno:PageLRMargin?Page.Left:long=' + left + '&Page.Right:long=' + right;
		const cmdUL =
			'.uno:PageULMargin?Page.Upper:long=' + top + '&Page.Lower:long=' + bottom;
		this.adapter.sendUnoCommand(cmdLR);
		this.adapter.sendUnoCommand(cmdUL);
		return { dispatched: 'unocmd', command: cmdLR };
	}

	/** Inserts a basic shape via the BasicShapes UNO command. */
	insertShape(name: string): WriterEditorRunResult {
		if (!name) {
			return { dispatched: 'none', reason: 'empty_shape' };
		}
		const command = '.uno:BasicShapes.' + name;
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	/** Applies a Writer paragraph style via StyleApply (FamilyName ParagraphStyles). */
	applyStyle(styleName: string): WriterEditorRunResult {
		if (!styleName) {
			return { dispatched: 'none', reason: 'empty_style' };
		}
		const command =
			'.uno:StyleApply {"Style":{"type":"string","value":' + JSON.stringify(styleName) +
			'},"FamilyName":{"type":"string","value":"ParagraphStyles"}}';
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	/** Applies a page watermark. Empty text removes it (matches Android). */
	applyWatermark(
		text: string,
		angle: number,
		transparency: number,
		font?: string,
	): WriterEditorRunResult {
		const safeText = text || '';
		const safeAngle = Math.max(0, Math.min(360, angle | 0));
		const safeTransparency = Math.max(0, Math.min(100, transparency | 0));
		const safeFont = font || 'Noto Serif CJK SC';
		const command =
			'.uno:Watermark {"Text":{"type":"string","value":' + JSON.stringify(safeText) +
			'},"Font":{"type":"string","value":' + JSON.stringify(safeFont) +
			'},"Angle":{"type":"long","value":' +
			safeAngle + '},"Transparency":{"type":"long","value":' + safeTransparency +
			'},"Color":{"type":"long","value":12632256}}';
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	/** Applies a paper format preset via AttributePageSize PaperFormat:short. */
	applyPaperFormat(formatShort: string): WriterEditorRunResult {
		if (!formatShort) {
			return { dispatched: 'none', reason: 'empty_paper' };
		}
		const command = '.uno:AttributePageSize?PaperFormat:short=' + formatShort;
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	/**
	 * Applies a custom paper size via AttributePageSize width/height (HMM).
	 * cm is rounded like Android PaperSizePickerController.cmToHmm (L322-324).
	 */
	applyCustomPaperSize(widthCm: number, heightCm: number): WriterEditorRunResult {
		if (!widthCm || !heightCm || widthCm <= 0 || heightCm <= 0) {
			return { dispatched: 'none', reason: 'invalid_paper_size' };
		}
		const widthHmm = Math.round(widthCm * 1000);
		const heightHmm = Math.round(heightCm * 1000);
		const command =
			'.uno:AttributePageSize?AttributePageSize.Width:long=' + widthHmm +
			'&AttributePageSize.Height:long=' + heightHmm;
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	/** Enables or disables change tracking (Android TOGGLE semantics). */
	trackChanges(enabled: boolean): WriterEditorRunResult {
		const command = enabled
			? '.uno:TrackChangesInAllViews'
			: '.uno:TrackChanges?TrackChanges:bool=false';
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	/**
	 * Inserts a chart of the given unoChartType via InsertObjectChart.
	 * JSON args mirror Android CalcChartTypeMapper.buildInsertChartJson
	 * (L56-73): empty RangeList, ChartTemplate when required, curve style for
	 * the curved-line variant.
	 */
	insertChart(unoChartType: string): WriterEditorRunResult {
		if (!unoChartType || !WriterEditorCatalog.chartTemplateService(unoChartType)) {
			return { dispatched: 'none', reason: 'unknown_chart_type' };
		}
		const args: { [key: string]: any } = {
			'RangeList': { type: 'string', value: '' },
			'InNewTable': { type: 'boolean', value: false },
		};
		if (WriterEditorCatalog.needsChartTemplate(unoChartType)) {
			args['ChartTemplate'] = {
				type: 'string',
				value: WriterEditorCatalog.chartTemplateService(unoChartType),
			};
			const curveStyle = WriterEditorCatalog.chartCurveStyle(unoChartType);
			if (curveStyle >= 0) {
				args['ChartCurveStyle'] = { type: 'int32', value: curveStyle };
			}
		}
		const command = '.uno:InsertObjectChart ' + JSON.stringify(args);
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	/**
	 * Applies a font color (0xRRGGBB) via FontColor.Color long arg.
	 * Matches Android buildColorUnoCommand (BottomToolbarController L845-847).
	 */
	applyFontColor(rgb: number): WriterEditorRunResult {
		const command = '.uno:FontColor {"FontColor.Color":{"type":"long","value":' + rgb + '}}';
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	/**
	 * Applies a text-highlight/background color (0xRRGGBB) via
	 * CharBackColor.Color long arg (Android highlight color picker L750-752).
	 */
	applyHighlightColor(rgb: number): WriterEditorRunResult {
		const command =
			'.uno:CharBackColor {"CharBackColor.Color":{"type":"long","value":' + rgb + '}}';
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	/** Inserts an image as base64 over the mobile insertfile channel. */
	insertImage(fileName: string, dataBase64: string): WriterEditorRunResult {
		if (!dataBase64) {
			return { dispatched: 'none', reason: 'empty_image' };
		}
		const message = 'insertfile name=' + fileName + ' type=graphic data=' + dataBase64;
		this.adapter.postMobileMessage(message);
		return { dispatched: 'message', message };
	}

	/** Saves-as by dispatching a downloadas message (iOS picks the destination). */
	saveAs(format: string): WriterEditorRunResult {
		if (!format) {
			return { dispatched: 'none', reason: 'empty_format' };
		}
		const message = 'downloadas name=document.' + format + ' format=' + format + ' id=saveas';
		this.adapter.postMobileMessage(message);
		return { dispatched: 'message', message };
	}

	static buildSearchCmd(
		text: string,
		replaceString: string,
		backward: boolean,
		command: number,
		options?: WriterFindReplaceOptions,
	): { [key: string]: any } {
		let flags = 0;
		if (options && options.caseSensitive) {
			flags |= WriterEditorSearch.FLAG_MATCH_CASE;
		}
		if (options && options.wholeWord) {
			flags |= WriterEditorSearch.FLAG_WHOLE_WORD;
		}
		return {
			'SearchItem.SearchString': { type: 'string', value: text },
			'SearchItem.ReplaceString': { type: 'string', value: replaceString },
			'SearchItem.Backward': { type: 'boolean', value: backward },
			'SearchItem.SearchStartPointX': { type: 'long', value: 0 },
			'SearchItem.SearchStartPointY': { type: 'long', value: 0 },
			'SearchItem.Command': { type: 'long', value: command },
			'SearchItem.SearchFlags': { type: 'long', value: flags },
		};
	}

	getCommandValues(command: string): { [key: string]: any } | undefined {
		return this.adapter.getToolbarCommandValues(command);
	}

	getSelectedText(): string {
		return this.adapter.getSelectedText();
	}

	isWriterDocument(): boolean {
		return this.adapter.getDocType() === 'text';
	}

	private runCommand(feature: WriterEditorFeature): WriterEditorRunResult {
		const command = feature.unocmd || '';
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	private runQueryCommand(feature: WriterEditorFeature): WriterEditorRunResult {
		const command = (feature.unocmd || '') + (feature.queryParams || '');
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	private runCommandWithArgs(feature: WriterEditorFeature): WriterEditorRunResult {
		const command = (feature.unocmd || '') +
			(feature.args ? ' ' + JSON.stringify(feature.args) : '');
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'unocmd', command };
	}

	private runSave(feature: WriterEditorFeature): WriterEditorRunResult {
		const command = feature.unocmd || '.uno:Save';
		this.adapter.sendUnoCommand(command);
		return { dispatched: 'save' };
	}

	private runExport(kind: 'pdf' | 'print'): WriterEditorRunResult {
		if (kind === 'print') {
			this.adapter.downloadAs('print.pdf', 'pdf', '', 'print');
		} else {
			this.adapter.downloadAs('document.pdf', 'pdf');
		}
		return { dispatched: 'export', kind };
	}

	private static defaultAdapter(): WriterEditorAdapterLike {
		return {
			sendUnoCommand(command: string): void {
				const map = (window as any).app?.map;
				if (map && typeof map.sendUnoCommand === 'function') {
					map.sendUnoCommand(command);
				}
			},
			getToolbarCommandValues(command: string): { [key: string]: any } | undefined {
				const map = (window as any).app?.map;
				if (map && typeof map.getToolbarCommandValues === 'function') {
					return map.getToolbarCommandValues(command);
				}
				return undefined;
			},
			getDocType(): string {
				const map = (window as any).app?.map;
				if (map && typeof map.getDocType === 'function') {
					return map.getDocType();
				}
				return '';
			},
			getSelectedText(): string {
				if (typeof MobileAiBridge !== 'undefined') {
					return MobileAiBridge.getInstance().getSelectedText();
				}
				return '';
			},
			sendExecuteSearch(searchCmd: { [key: string]: any }): void {
				const socket = (window as any).app?.socket;
				if (socket && typeof socket.sendMessage === 'function') {
					socket.sendMessage(
						'uno .uno:ExecuteSearch ' + JSON.stringify(searchCmd),
					);
				}
			},
			postMobileMessage(message: string): void {
				const poster = (window as any).postMobileMessage;
				if (typeof poster === 'function') {
					poster(message);
				}
			},
			downloadAs(name: string, format: string, options?: string, id?: string): void {
				const map = (window as any).app?.map;
				if (map && typeof map.downloadAs === 'function') {
					map.downloadAs(name, format, options, id);
				}
			},
		};
	}
}

if (typeof window !== 'undefined') {
	WriterEditorController.getInstance();
}
