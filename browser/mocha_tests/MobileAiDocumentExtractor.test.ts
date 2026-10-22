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
		const previousSelectionMenu = (window as any).AndroidSelectionMenu;
		const previousBridge = MobileAiBridge.getInstance;
		let selectionPolls = 0;
		let suppressionBegins = 0;
		let suppressionEnds = 0;
		(window as any).AndroidSelectionMenu = {
			beginProgrammaticSelection() {
				suppressionBegins++;
			},
			endProgrammaticSelection() {
				suppressionEnds++;
			},
		};
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
				(window as any).AndroidSelectionMenu = previousSelectionMenu;
				MobileAiBridge.getInstance = previousBridge;
				assert.equal(text, '幻灯片正文');
				assert.ok(sent.indexOf('.uno:SelectAll') >= 0);
				assert.ok(sent.indexOf('uno .uno:Copy') >= 0);
				assert.equal(suppressionBegins, 1);
				assert.equal(suppressionEnds, 1);
				done();
			},
			function (error: Error) {
				(window as any).app = previousApp;
				(window as any).AndroidSelectionMenu = previousSelectionMenu;
				MobileAiBridge.getInstance = previousBridge;
				done(error);
			},
		);
	});

	it('returns native successful empty text only when allowEmpty is requested', function (done) {
		const previousIOSFlag = (window as any).ThisIsTheiOSApp;
		const previousApp = (window as any).app;
		const previousGetBridge = NativeBridge.getInstance;
		let listener: ((message: any) => void) | null = null;
		let posted: any;
		const fakeBridge = {
			isAvailable: () => true,
			getDocumentSessionId: () => 'session-1',
			subscribe(callback: (message: any) => void) {
				listener = callback;
				return () => { listener = null; };
			},
			postMessage(message: any) {
				posted = message;
				window.setTimeout(() => {
					listener && listener({
						requestId: message.requestId,
						type: 'ai.doc_extract.done',
						payload: { text: '' },
					});
				}, 0);
				return true;
			},
		};
		(window as any).ThisIsTheiOSApp = true;
		(window as any).app = {};
		NativeBridge.getInstance = () => fakeBridge as any;
		MobileAiDocumentExtractor.extractFullText({ allowEmpty: true }).then(
			(text) => {
				assert.equal(text, '');
				assert.deepEqual(posted.payload, { allowEmpty: true });
				NativeBridge.getInstance = previousGetBridge;
				(window as any).app = previousApp;
				if (previousIOSFlag === undefined) delete (window as any).ThisIsTheiOSApp;
				else (window as any).ThisIsTheiOSApp = previousIOSFlag;
				done();
			},
			(error) => {
				NativeBridge.getInstance = previousGetBridge;
				(window as any).app = previousApp;
				if (previousIOSFlag === undefined) delete (window as any).ThisIsTheiOSApp;
				else (window as any).ThisIsTheiOSApp = previousIOSFlag;
				done(error);
			},
		);
	});

	it('does not treat a native extraction error as an empty document', function (done) {
		const previousIOSFlag = (window as any).ThisIsTheiOSApp;
		const previousApp = (window as any).app;
		const previousGetBridge = NativeBridge.getInstance;
		let listener: ((message: any) => void) | null = null;
		const fakeBridge = {
			isAvailable: () => true,
			getDocumentSessionId: () => 'session-1',
			subscribe(callback: (message: any) => void) {
				listener = callback;
				return () => { listener = null; };
			},
			postMessage(message: any) {
				window.setTimeout(() => {
					listener && listener({
						requestId: message.requestId,
						type: 'ai.doc_extract.error',
						payload: {},
					});
				}, 0);
				return true;
			},
		};
		(window as any).ThisIsTheiOSApp = true;
		(window as any).app = {};
		NativeBridge.getInstance = () => fakeBridge as any;
		MobileAiDocumentExtractor.extractFullText({ allowEmpty: true }).then(
			() => {
				NativeBridge.getInstance = previousGetBridge;
				(window as any).app = previousApp;
				if (previousIOSFlag === undefined) delete (window as any).ThisIsTheiOSApp;
				else (window as any).ThisIsTheiOSApp = previousIOSFlag;
				done(new Error('expected extract failure'));
			},
			(error: Error) => {
				assert.equal(error.message, '文档全文提取失败');
				NativeBridge.getInstance = previousGetBridge;
				(window as any).app = previousApp;
				if (previousIOSFlag === undefined) delete (window as any).ThisIsTheiOSApp;
				else (window as any).ThisIsTheiOSApp = previousIOSFlag;
				done();
			},
		);
	});
});
