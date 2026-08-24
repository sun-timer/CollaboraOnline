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
