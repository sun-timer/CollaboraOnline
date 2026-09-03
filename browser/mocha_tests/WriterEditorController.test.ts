/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('WriterEditorController', function () {
	interface FakeWriterEditorAdapter extends WriterEditorAdapterLike {
		calls: {
			sendUnoCommand: string[];
			sendExecuteSearch: { [key: string]: any }[];
			postMobileMessage: string[];
			downloadAs: { name: string; format: string; options?: string; id?: string }[];
		};
	}

	function createFakeAdapter(docType: string): FakeWriterEditorAdapter {
		const calls: FakeWriterEditorAdapter['calls'] = {
			sendUnoCommand: [],
			sendExecuteSearch: [],
			postMobileMessage: [],
			downloadAs: [],
		};
		return {
			calls,
			sendUnoCommand(command: string): void {
				calls.sendUnoCommand.push(command);
			},
			getToolbarCommandValues(): { [key: string]: any } | undefined {
				return undefined;
			},
			getDocType(): string {
				return docType;
			},
			getSelectedText(): string {
				return '';
			},
			sendExecuteSearch(searchCmd: { [key: string]: any }): void {
				calls.sendExecuteSearch.push(searchCmd);
			},
			postMobileMessage(message: string): void {
				calls.postMobileMessage.push(message);
			},
			downloadAs(name: string, format: string, options?: string, id?: string): void {
				calls.downloadAs.push({ name, format, options, id });
			},
		};
	}

	it('dispatches a plain command via the adapter', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);
		const undo = WriterEditorCatalog.getFeature('undo');
		assert.ok(undo);

		const result = controller.run(undo);

		assert.deepEqual(result, { dispatched: 'unocmd', command: '.uno:Undo' });
		assert.deepEqual(adapter.calls.sendUnoCommand, ['.uno:Undo']);
	});

	it('does not dispatch on a non-Writer document', function () {
		const adapter = createFakeAdapter('spreadsheet');
		const controller = new WriterEditorController(adapter);
		const undo = WriterEditorCatalog.getFeature('undo');
		assert.ok(undo);

		const result = controller.run(undo);

		assert.deepEqual(result, { dispatched: 'none', reason: 'not_writer_document' });
		assert.deepEqual(adapter.calls.sendUnoCommand, []);
	});

	it('exports a PDF via downloadAs', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);
		const exportPdf = WriterEditorCatalog.getFeature('export-pdf');
		assert.ok(exportPdf);

		const result = controller.run(exportPdf);

		assert.deepEqual(result, { dispatched: 'export', kind: 'pdf' });
		assert.equal(adapter.calls.downloadAs.length, 1);
		assert.equal(adapter.calls.downloadAs[0].name, 'document.pdf');
		assert.equal(adapter.calls.downloadAs[0].format, 'pdf');
	});

	it('prints through the print download id', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);
		const print = WriterEditorCatalog.getFeature('print');
		assert.ok(print);

		const result = controller.run(print);

		assert.deepEqual(result, { dispatched: 'export', kind: 'print' });
		assert.equal(adapter.calls.downloadAs.length, 1);
		assert.equal(adapter.calls.downloadAs[0].id, 'print');
	});

	it('reports a dialog for dialog-kind features without dispatching', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);
		const fontName = WriterEditorCatalog.getFeature('font-name');
		assert.ok(fontName);

		const result = controller.run(fontName);

		assert.deepEqual(result, { dispatched: 'dialog', dialog: 'fontName' });
		assert.deepEqual(adapter.calls.sendUnoCommand, []);
	});

	it('reports findReplace for the find-replace feature', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);
		const findReplace = WriterEditorCatalog.getFeature('find-replace');
		assert.ok(findReplace);

		const result = controller.run(findReplace);

		assert.deepEqual(result, { dispatched: 'findReplace' });
	});

	it('dispatches .uno:Save for the save feature', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);
		const save = WriterEditorCatalog.getFeature('save');
		assert.ok(save);

		const result = controller.run(save);

		assert.deepEqual(result, { dispatched: 'save' });
		assert.deepEqual(adapter.calls.sendUnoCommand, ['.uno:Save']);
	});

	it('returns missing_feature for a null feature', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);
		assert.deepEqual(controller.run(null), {
			dispatched: 'none',
			reason: 'missing_feature',
		});
	});

	it('builds a valid search payload for a replace', function () {
		const searchCmd = WriterEditorController.buildSearchCmd(
			'abc',
			'def',
			false,
		WriterEditorSearch.CMD_REPLACE,
		);
		assert.equal(searchCmd['SearchItem.SearchString'].value, 'abc');
		assert.equal(searchCmd['SearchItem.ReplaceString'].value, 'def');
		assert.equal(searchCmd['SearchItem.Command'].value, WriterEditorSearch.CMD_REPLACE);
		assert.equal(searchCmd['SearchItem.SearchFlags'].value, 0);
		assert.equal(searchCmd['SearchItem.Backward'].value, false);
	});

	it('observes case-sensitive and whole-word flags', function () {
		const searchCmd = WriterEditorController.buildSearchCmd(
			'abc',
			'',
			false,
			WriterEditorSearch.CMD_REPLACE_ALL,
			{ caseSensitive: true, wholeWord: true },
		);
		assert.equal(
			searchCmd['SearchItem.SearchFlags'].value,
			WriterEditorSearch.FLAG_MATCH_CASE | WriterEditorSearch.FLAG_WHOLE_WORD,
		);
		assert.equal(searchCmd['SearchItem.Command'].value, WriterEditorSearch.CMD_REPLACE_ALL);
	});

	it('executes a find and replace over the adapter', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.runFindReplace('abc', 'def', false);

		assert.equal(result.executed, true);
		assert.equal(adapter.calls.sendExecuteSearch.length, 1);
		assert.equal(adapter.calls.sendExecuteSearch[0]['SearchItem.SearchString'].value, 'abc');
	});

	it('rejects an empty query without dispatching', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.runFindReplace('', 'def', false);

		assert.equal(result.executed, false);
		assert.equal(result.reason, 'empty_query');
		assert.equal(adapter.calls.sendExecuteSearch.length, 0);
	});

	it('rejects find/replace on a non-Writer document', function () {
		const adapter = createFakeAdapter('presentation');
		const controller = new WriterEditorController(adapter);

		const result = controller.runFindReplace('abc', 'def', false);

		assert.equal(result.executed, false);
		assert.equal(result.reason, 'not_writer_document');
		assert.equal(adapter.calls.sendExecuteSearch.length, 0);
	});
	it('applies a font name via the CharFontName command', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.applyFontName('Arial');

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command, '.uno:CharFontName {"CharFontName.FamilyName":{"type":"string","value":"Arial"}}');
		assert.equal(adapter.calls.sendUnoCommand[0], command);
	});

	it('applies a font size via the FontHeight command', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.applyFontSize('12');

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command, '.uno:FontHeight {"FontHeight.Height":{"type":"float","value":"12"}}');
		assert.equal(adapter.calls.sendUnoCommand[0], command);
	});

	it('rejects an empty font name without dispatching', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.applyFontName('');

		assert.deepEqual(result, { dispatched: 'none', reason: 'empty_font' });
		assert.equal(adapter.calls.sendUnoCommand.length, 0);
	});
	it('inserts a table via the InsertTable command', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.insertTable(3, 4);

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command, '.uno:InsertTable {"Columns":{"type":"long","value":3},"Rows":{"type":"long","value":4}}');
		assert.equal(adapter.calls.sendUnoCommand[0], command);
	});

	it('applies page margins via PageLRMargin and PageULMargin', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.applyMargins(2540, 2540, 2540, 2540);

		assert.equal(result.dispatched, 'unocmd');
		assert.deepEqual(adapter.calls.sendUnoCommand, [
			'.uno:PageLRMargin?Page.Left:long=2540&Page.Right:long=2540',
			'.uno:PageULMargin?Page.Upper:long=2540&Page.Lower:long=2540',
		]);
	});

	it('rejects an invalid table size without dispatching', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.insertTable(0, 3);

		assert.deepEqual(result, { dispatched: 'none', reason: 'invalid_table' });
		assert.equal(adapter.calls.sendUnoCommand.length, 0);
	});
	it('inserts a basic shape via the BasicShapes command', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.insertShape('rectangle');

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command, '.uno:BasicShapes.rectangle');
		assert.equal(adapter.calls.sendUnoCommand[0], command);
	});

	it('rejects an empty shape name without dispatching', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.insertShape('');

		assert.deepEqual(result, { dispatched: 'none', reason: 'empty_shape' });
		assert.equal(adapter.calls.sendUnoCommand.length, 0);
	});
	it('applies a paragraph style via StyleApply', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.applyStyle('Heading 1');

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command, '.uno:StyleApply {"Style":{"type":"string","value":"Heading 1"},"FamilyName":{"type":"string","value":"ParagraphStyles"}}');
	});

	it('applies a watermark with clamped angle and transparency', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.applyWatermark('内部', 400, 120);

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command, '.uno:Watermark {"Text":{"type":"string","value":"内部"},"Font":{"type":"string","value":"Noto Serif CJK SC"},"Angle":{"type":"long","value":360},"Transparency":{"type":"long","value":100},"Color":{"type":"long","value":12632256}}');
	});

	it('applies a paper format via AttributePageSize', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.applyPaperFormat('4');

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command, '.uno:AttributePageSize?PaperFormat:short=4');
	});

	it('inserts an image over the mobile insertfile channel', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.insertImage('pic.png', 'AAAA');

		assert.deepEqual(result, {
			dispatched: 'message',
			message: 'insertfile name=pic.png type=graphic data=AAAA',
		});
		assert.deepEqual(adapter.calls.postMobileMessage, ['insertfile name=pic.png type=graphic data=AAAA']);
	});

	it('saves as via a downloadas message', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.saveAs('pdf');

		assert.equal(result.dispatched, 'message');
		assert.equal(adapter.calls.postMobileMessage[0], 'downloadas name=document.pdf format=pdf id=saveas');
	});

	it('rejects an empty image or format without dispatching', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		assert.deepEqual(controller.insertImage('a.png', ''), { dispatched: 'none', reason: 'empty_image' });
		assert.deepEqual(controller.saveAs(''), { dispatched: 'none', reason: 'empty_format' });
		assert.equal(adapter.calls.postMobileMessage.length, 0);
	});
	it('runs a forward find over the adapter with CMD_FIND', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.runFind('abc', false);

		assert.equal(result.executed, true);
		assert.equal(result.command, 0);
		assert.equal(adapter.calls.sendExecuteSearch.length, 1);
		assert.equal(adapter.calls.sendExecuteSearch[0]['SearchItem.SearchString'].value, 'abc');
		assert.equal(adapter.calls.sendExecuteSearch[0]['SearchItem.Command'].value, 0);
		assert.equal(adapter.calls.sendExecuteSearch[0]['SearchItem.Backward'].value, false);
	});

	it('runs a backward find with the backward flag', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.runFind('abc', true);

		assert.equal(result.executed, true);
		assert.equal(adapter.calls.sendExecuteSearch[0]['SearchItem.Backward'].value, true);
	});

	it('rejects an empty find query without dispatching', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.runFind('', false);

		assert.equal(result.executed, false);
		assert.equal(result.reason, 'empty_query');
		assert.equal(adapter.calls.sendExecuteSearch.length, 0);
	});
	it('applies a custom paper size via AttributePageSize width/height', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.applyCustomPaperSize(21, 29.7);

		assert.equal(result.dispatched, 'unocmd');
		assert.deepEqual(adapter.calls.sendUnoCommand, [
			'.uno:AttributePageSize?AttributePageSize.Width:long=21000&AttributePageSize.Height:long=29700',
		]);
	});

	it('rounds custom paper cm to hundredths of mm', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		controller.applyCustomPaperSize(21.05, 5);

		assert.deepEqual(adapter.calls.sendUnoCommand, [
			'.uno:AttributePageSize?AttributePageSize.Width:long=21050&AttributePageSize.Height:long=5000',
		]);
	});

	it('rejects invalid custom paper sizes without dispatching', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		assert.deepEqual(controller.applyCustomPaperSize(0, 10), { dispatched: 'none', reason: 'invalid_paper_size' });
		assert.deepEqual(controller.applyCustomPaperSize(10, -1), { dispatched: 'none', reason: 'invalid_paper_size' });
		assert.equal(adapter.calls.sendUnoCommand.length, 0);
	});

	it('applies a watermark with a chosen font', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.applyWatermark('内部', 45, 50, 'Liberation Sans');

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.ok(command.indexOf('"Font":{"type":"string","value":"Liberation Sans"}') >= 0);
	});

	it('enables change tracking via TrackChangesInAllViews', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.trackChanges(true);

		assert.equal(result.dispatched, 'unocmd');
		assert.deepEqual(adapter.calls.sendUnoCommand, ['.uno:TrackChangesInAllViews']);
	});

	it('disables change tracking via TrackChanges bool=false', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.trackChanges(false);

		assert.equal(result.dispatched, 'unocmd');
		assert.deepEqual(adapter.calls.sendUnoCommand, ['.uno:TrackChanges?TrackChanges:bool=false']);
	});
	it('inserts a default chart (column) without a template override', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.insertChart('column');

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command,
			'.uno:InsertObjectChart {"RangeList":{"type":"string","value":""},"InNewTable":{"type":"boolean","value":false}}');
		assert.equal(adapter.calls.sendUnoCommand[0], command);
	});

	it('inserts a pie chart with its chart2 template', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.insertChart('pie');

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command,
			'.uno:InsertObjectChart {"RangeList":{"type":"string","value":""},"InNewTable":{"type":"boolean","value":false},"ChartTemplate":{"type":"string","value":"com.sun.star.chart2.template.Pie"}}');
	});

	it('inserts a curved line chart with template and curve style', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.insertChart('line-curve');

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command,
			'.uno:InsertObjectChart {"RangeList":{"type":"string","value":""},"InNewTable":{"type":"boolean","value":false},"ChartTemplate":{"type":"string","value":"com.sun.star.chart2.template.LineSymbol"},"ChartCurveStyle":{"type":"int32","value":1}}');
	});

	it('rejects an unknown chart type without dispatching', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		assert.deepEqual(controller.insertChart(''), { dispatched: 'none', reason: 'unknown_chart_type' });
		assert.deepEqual(controller.insertChart('nope'), { dispatched: 'none', reason: 'unknown_chart_type' });
		assert.equal(adapter.calls.sendUnoCommand.length, 0);
	});
	it('applies a font color via FontColor.Color long arg', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.applyFontColor(0xE65D61);

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command, '.uno:FontColor {"FontColor.Color":{"type":"long","value":15097185}}');
		assert.equal(adapter.calls.sendUnoCommand[0], command);
	});

	it('applies a highlight color via CharBackColor.Color long arg', function () {
		const adapter = createFakeAdapter('text');
		const controller = new WriterEditorController(adapter);

		const result = controller.applyHighlightColor(0xFFFF00);

		assert.equal(result.dispatched, 'unocmd');
		const command = result.dispatched === 'unocmd' ? result.command : '';
		assert.equal(command, '.uno:CharBackColor {"CharBackColor.Color":{"type":"long","value":16776960}}');
	});
});
