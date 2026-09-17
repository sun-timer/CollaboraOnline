/* -*- js-indent-level: 8 -*- */

var assert = require('assert').strict;

describe('MobileAiDocumentExtractor', function () {
	it('rejects when the document map is missing', function (done) {
		const previousApp = (window as any).app;
		(window as any).app = {};
		MobileAiDocumentExtractor.extractFullText().then(
			function () {
				(window as any).app = previousApp;
				done(new Error('expected extract to fail'));
			},
			function (error: Error) {
				(window as any).app = previousApp;
				assert.equal(error.message, '文档全文提取失败');
				done();
			},
		);
	});

	it('copies after SelectAll and resolves when selection text arrives', function (done) {
		this.timeout(3000);
		const sent: string[] = [];
		const previousApp = (window as any).app;
		const previousBridge = MobileAiBridge.getInstance;
		let selectionPolls = 0;
		MobileAiBridge.getInstance = function () {
			return {
				getSelectedText() {
					selectionPolls += 1;
					return selectionPolls >= 2 ? '幻灯片正文' : '';
				},
			} as any;
		};
		delete (window as any).ThisIsTheiOSApp;
		(window as any).app = {
			map: {
				sendUnoCommand(command: string) {
					sent.push(command);
				},
				_docLayer: {
					_selectedTextContent: '',
				},
				_clip: {
					_selectionPlainTextContent: '',
				},
			},
			socket: {
				sendMessage(message: string) {
					sent.push(message);
					if (message.indexOf('gettextselection') === 0) {
						(window as any).app.map._clip._selectionPlainTextContent =
							'幻灯片正文';
					}
				},
			},
		};
		MobileAiDocumentExtractor.extractFullText().then(
			function (text: string) {
				(window as any).app = previousApp;
				MobileAiBridge.getInstance = previousBridge;
				assert.equal(text, '幻灯片正文');
				assert.ok(sent.indexOf('.uno:SelectAll') >= 0);
				assert.ok(sent.indexOf('uno .uno:Copy') >= 0);
				done();
			},
			function (error: Error) {
				(window as any).app = previousApp;
				MobileAiBridge.getInstance = previousBridge;
				done(error);
			},
		);
	});
});
