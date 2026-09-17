/* -*- js-indent-level: 8 -*- */
/*
 * Catalog → controller / panel routing coverage (no DOM clicks).
 * Keep *_PANEL_DIALOGS in sync with the matching *EditorPanel handlers.
 */

var assert = require('assert').strict;

/** Dialog branches in WriterEditorPanel.onFeature (kind === 'dialog'). */
var WRITER_PANEL_DIALOGS: { [key: string]: boolean } = {
	fontName: true,
	fontSize: true,
	table: true,
	margins: true,
	shape: true,
	style: true,
	watermark: true,
	paperSize: true,
	chart: true,
	image: true,
	saveAs: true,
	comment: true,
};

/** switch (feature.dialog) cases in CalcEditorPanel.openDialog. */
var CALC_PANEL_OPEN_DIALOGS: { [key: string]: boolean } = {
	fontName: true,
	fontSize: true,
	fontColor: true,
	backgroundColor: true,
	borderColor: true,
	chart: true,
	comment: true,
	calcShape: true,
	shape: true,
	dataValidation: true,
	saveAs: true,
	paperOrientation: true,
	printArea: true,
	hyperlink: true,
	image: true,
};

/** kind === 'dialog' branches in ImpressEditorPanel.onFeature. */
var IMPRESS_PANEL_DIALOGS: { [key: string]: boolean } = {
	chart: true,
	image: true,
	table: true,
	shape: true,
	comment: true,
	hyperlink: true,
	saveAs: true,
	slideFormat: true,
	slideOrientation: true,
	slideBackground: true,
	slideMaster: true,
	fontName: true,
	fontSize: true,
	fontColor: true,
	highlightColor: true,
};

function allWriterFeatures(): WriterEditorFeature[] {
	var out: WriterEditorFeature[] = [];
	WriterEditorCatalog.TABS.forEach(function (tab) {
		WriterEditorCatalog.getFeatures(tab.id).forEach(function (f) {
			out.push(f);
		});
	});
	return out;
}

function allCalcFeatures(): CalcEditorFeature[] {
	var out: CalcEditorFeature[] = [];
	CalcEditorCatalog.TABS.forEach(function (tab) {
		CalcEditorCatalog.getFeatures(tab.id).forEach(function (f) {
			out.push(f);
		});
	});
	return out;
}

function allImpressFeatures(): ImpressEditorFeature[] {
	var out: ImpressEditorFeature[] = [];
	ImpressEditorCatalog.TABS.forEach(function (tab) {
		ImpressEditorCatalog.getFeatures(tab.id).forEach(function (f) {
			out.push(f);
		});
	});
	return out;
}

function createWriterAdapter(): WriterEditorAdapterLike & {
	calls: { sendUnoCommand: string[]; downloadAs: unknown[] };
} {
	var calls = { sendUnoCommand: [] as string[], downloadAs: [] as unknown[] };
	return {
		calls: calls,
		sendUnoCommand: function (command: string) {
			calls.sendUnoCommand.push(command);
		},
		getToolbarCommandValues: function () {
			return undefined;
		},
		getDocType: function () {
			return 'text';
		},
		getSelectedText: function () {
			return '';
		},
		sendExecuteSearch: function () {},
		postMobileMessage: function () {},
		downloadAs: function () {
			calls.downloadAs.push(arguments);
		},
	};
}

describe('Mobile editor feature dispatch (catalog → logic)', function () {
	describe('WriterEditorController.run for every catalog feature', function () {
		allWriterFeatures().forEach(function (feature) {
			it('feature ' + feature.id + ' (' + feature.kind + ')', function () {
				var adapter = createWriterAdapter();
				var controller = new WriterEditorController(adapter);
				var result = controller.run(feature);

				switch (feature.kind) {
					case 'command':
						assert.equal(result.dispatched, 'unocmd');
						assert.equal(
							(result as { command: string }).command,
							feature.unocmd,
						);
						assert.deepEqual(adapter.calls.sendUnoCommand, [feature.unocmd]);
						break;
					case 'queryCommand': {
						var expected =
							(feature.unocmd || '') + (feature.queryParams || '');
						assert.equal(result.dispatched, 'unocmd');
						assert.equal((result as { command: string }).command, expected);
						assert.deepEqual(adapter.calls.sendUnoCommand, [expected]);
						break;
					}
					case 'commandWithArgs':
						assert.equal(result.dispatched, 'unocmd');
						assert.ok(
							(result as { command: string }).command.indexOf(
								feature.unocmd || '',
							) === 0,
						);
						assert.equal(adapter.calls.sendUnoCommand.length, 1);
						break;
					case 'save':
						assert.deepEqual(result, { dispatched: 'save' });
						assert.deepEqual(adapter.calls.sendUnoCommand, [
							feature.unocmd || '.uno:Save',
						]);
						break;
					case 'export':
						assert.deepEqual(result, { dispatched: 'export', kind: 'pdf' });
						assert.equal(adapter.calls.downloadAs.length, 1);
						break;
					case 'print':
						assert.deepEqual(result, { dispatched: 'export', kind: 'print' });
						assert.equal(adapter.calls.downloadAs.length, 1);
						break;
					case 'dialog':
						assert.deepEqual(result, {
							dispatched: 'dialog',
							dialog: feature.dialog || 'fontName',
						});
						assert.deepEqual(adapter.calls.sendUnoCommand, []);
						break;
					case 'findReplace':
						assert.deepEqual(result, { dispatched: 'findReplace' });
						break;
					case 'toggle':
						assert.equal(result.dispatched, 'toggle');
						assert.equal(
							(result as { command: string }).command,
							feature.unocmd || '',
						);
						break;
					default:
						assert.fail('unsupported kind ' + feature.kind);
				}
			});
		});
	});

	it('Writer dialog features map to WriterEditorPanel branches', function () {
		allWriterFeatures()
			.filter(function (f) {
				return f.kind === 'dialog';
			})
			.forEach(function (f) {
				assert.ok(
					f.dialog && WRITER_PANEL_DIALOGS[f.dialog],
					'Writer panel missing dialog handler for ' +
						f.id +
						' dialog=' +
						f.dialog,
				);
			});
	});

	describe('Calc catalog command and dialog wiring', function () {
		allCalcFeatures().forEach(function (feature) {
			it('feature ' + feature.id + ' (' + feature.kind + ')', function () {
				if (feature.kind === 'dialog' || feature.kind === 'iconValueRow') {
					assert.ok(
						feature.dialog,
						feature.id + ' must declare dialog',
					);
					assert.ok(
						CALC_PANEL_OPEN_DIALOGS[feature.dialog || ''],
						'Calc openDialog missing case for ' +
							feature.id +
							' dialog=' +
							feature.dialog,
					);
					return;
				}
				if (feature.kind === 'submenu') {
					assert.ok(
						feature.submenu && feature.submenu.length > 0,
						feature.id + ' submenu empty',
					);
					return;
				}
				if (
					feature.id === 'merge-cells' ||
					feature.id === 'data-validation' ||
					feature.id === 'spell-check'
				) {
					return;
				}
				if (
					feature.kind === 'save' ||
					feature.kind === 'export' ||
					feature.kind === 'print'
				) {
					return;
				}
				if (feature.kind === 'command') {
					assert.ok(
						feature.unocmd,
						feature.id + ' command feature needs unocmd',
					);
				}
			});
		});
	});

	describe('Impress catalog routing metadata', function () {
		allImpressFeatures().forEach(function (feature) {
			it('feature ' + feature.id + ' (' + feature.kind + ')', function () {
				if (feature.kind === 'dialog') {
					assert.ok(feature.dialog);
					assert.ok(
						IMPRESS_PANEL_DIALOGS[feature.dialog || ''],
						'Impress panel missing dialog for ' +
							feature.id +
							' dialog=' +
							feature.dialog,
					);
					return;
				}
				if (feature.kind === 'charTool' && feature.dialog === 'highlightColor') {
					return;
				}
				if (feature.tab === 'transition') {
					assert.ok(
						feature.iconViewIndex !== undefined &&
							feature.iconViewIndex >= 0,
						feature.id + ' transition needs iconViewIndex',
					);
					return;
				}
				if (feature.kind === 'stub') {
					return;
				}
				if (
					feature.kind === 'save' ||
					feature.kind === 'export' ||
					feature.kind === 'print' ||
					feature.kind === 'findReplace'
				) {
					return;
				}
				if (
					feature.kind === 'command' ||
					feature.kind === 'queryCommand' ||
					feature.kind === 'charTool'
				) {
					assert.ok(
						feature.unocmd,
						feature.id + ' needs unocmd',
					);
				}
			});
		});
	});

	describe('Chart insert logic', function () {
		WriterEditorCatalog.CHART_CATEGORIES.forEach(function (category) {
			category.types.forEach(function (type) {
				it('insertChart(' + type.unoType + ') dispatches InsertObjectChart', function () {
					var adapter = createWriterAdapter();
					var controller = new WriterEditorController(adapter);
					var preview = WriterEditorChartPreviewIcons.get(type.unoType);
					assert.ok(preview && preview.indexOf('<svg') >= 0, 'missing preview SVG');

					var result = controller.insertChart(type.unoType);
					assert.equal(result.dispatched, 'unocmd');
					assert.equal(adapter.calls.sendUnoCommand.length, 1);
					assert.ok(
						adapter.calls.sendUnoCommand[0].indexOf('.uno:InsertObjectChart') ===
							0,
					);
					var template = WriterEditorCatalog.chartTemplateService(type.unoType);
					assert.ok(template, 'chartTemplateService for ' + type.unoType);
					if (WriterEditorCatalog.needsChartTemplate(type.unoType)) {
						assert.ok(
							adapter.calls.sendUnoCommand[0].indexOf(template) >= 0,
						);
					}
				});
			});
		});
	});
});
