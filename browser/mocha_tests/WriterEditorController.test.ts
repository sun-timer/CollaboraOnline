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
			downloadAs: { name: string; format: string; options?: string; id?: string }[];
		};
	}

	function createFakeAdapter(docType: string): FakeWriterEditorAdapter {
		const calls: FakeWriterEditorAdapter['calls'] = {
			sendUnoCommand: [],
			sendExecuteSearch: [],
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
});
