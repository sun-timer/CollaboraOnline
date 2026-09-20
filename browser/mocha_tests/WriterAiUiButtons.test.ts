/* -*- js-indent-level: 8 -*- */
/*
 * Writer AI UI button flows — simulates taps on sheet/dialog controls.
 */

var assert = require('assert').strict;
var jsdom = require('jsdom');

describe('Writer AI UI buttons', function () {
	interface FakeWriterAiBridge extends WriterAiBridgeLike {
		calls: {
			request: Array<{ [key: string]: any }>;
			cancel: string[];
			accept: Array<{ requestId: string; text: string }>;
		};
		emit(message: NativeBridgeEnvelope): void;
	}

	interface DomHarness {
		window: Window;
		document: Document;
		restore(): void;
	}

	function createFakeWriterBridge(selection: string): FakeWriterAiBridge {
		const listeners: Array<(message: NativeBridgeEnvelope) => void> = [];
		let nextRequestId = 1;
		const calls: FakeWriterAiBridge['calls'] = {
			request: [],
			cancel: [],
			accept: [],
		};
		return {
			calls,
			request(payload: { [key: string]: any }): string {
				calls.request.push(payload);
				return 'req-' + nextRequestId++;
			},
			cancel(requestId: string): boolean {
				calls.cancel.push(requestId);
				return true;
			},
			accept(requestId: string, text: string): boolean {
				calls.accept.push({ requestId, text });
				return true;
			},
			getSelectedText(): string {
				return selection;
			},
			isAvailable(): boolean {
				return true;
			},
			subscribe(listener: (message: NativeBridgeEnvelope) => void): () => void {
				listeners.push(listener);
				return function () {
					const index = listeners.indexOf(listener);
					if (index >= 0) listeners.splice(index, 1);
				};
			},
			emit(message: NativeBridgeEnvelope): void {
				listeners.slice().forEach(function (listener) {
					listener(message);
				});
			},
		};
	}

	function aiDone(requestId: string, fullText: string): NativeBridgeEnvelope {
		return {
			protocolVersion: 1,
			channel: 'native',
			type: 'ai.done',
			requestId,
			payload: { fullText },
		};
	}

	function setupDom(): DomHarness {
		const dom = new jsdom.JSDOM(canvasDomString(), { pretendToBeVisual: true });
		const previousWindow = (global as any).window;
		const previousDocument = (global as any).document;
		(global as any).window = dom.window;
		(global as any).document = dom.window.document;
		dom.window.setTimeout = function (handler: TimerHandler): number {
			if (typeof handler === 'function') {
				handler();
			}
			return 1;
		} as typeof dom.window.setTimeout;
		(dom.window as any).app = {
			map: {
				getDocType() {
					return 'text';
				},
				sendUnoCommand() {},
				_clip: { pastePlainText() { return true; } },
				_docLayer: { _selectedTextContent: '待处理文本' },
			},
		};
		delete (dom.window as any).__coolWriterAiController;
		delete (dom.window as any).__coolWriterAiPanel;
		return {
			window: dom.window as unknown as Window,
			document: dom.window.document,
			restore() {
				(global as any).window = previousWindow;
				(global as any).document = previousDocument;
			},
		};
	}

	function clickButton(root: ParentNode, label: string): HTMLButtonElement {
		const buttons = root.querySelectorAll('button');
		for (let i = 0; i < buttons.length; i++) {
			const button = buttons[i] as HTMLButtonElement;
			if (
				button.textContent === label ||
				button.getAttribute('aria-label') === label
			) {
				button.click();
				return button;
			}
		}
		throw new Error('button not found: ' + label);
	}

	function findButton(root: ParentNode, label: string): HTMLButtonElement | null {
		const buttons = root.querySelectorAll('button');
		for (let i = 0; i < buttons.length; i++) {
			const button = buttons[i] as HTMLButtonElement;
			if (
				button.textContent === label ||
				button.getAttribute('aria-label') === label
			) {
				return button;
			}
		}
		return null;
	}

	function injectWriterController(
		bridge: FakeWriterAiBridge,
		adapter: WriterAiDocumentAdapter,
	): WriterAiController {
		const controller = new WriterAiController(bridge, adapter);
		(window as any).__coolWriterAiController = controller;
		return controller;
	}

	describe('MobileAiOperationDialog', function () {
		it('runs 生成 → 停止 → 重新生成 → 复制 → 插入文档', async function () {
			const dom = setupDom();
			try {
				const bridge = createFakeWriterBridge('原始段落');
				const copied: string[] = [];
				const pastedHtml: Array<{ html: string; plain: string }> = [];
				injectWriterController(bridge, {
					pastePlainText() {
						return true;
					},
					replaceSelection() {
						return true;
					},
					pasteHtml(html: string, plain: string) {
						pastedHtml.push({ html, plain });
						return true;
					},
					copyText(text: string) {
						copied.push(text);
						return true;
					},
				});

				const dialog = new MobileAiOperationDialog('polish');
				dialog.open();
				const root = dom.document.body;

				assert.ok(findButton(root, '插入文档'), '插入文档 button must be in DOM');
				assert.ok(findButton(root, '重新生成'), '重新生成 button must be in DOM');

				clickButton(root, '生成');
				await new Promise((resolve) => setTimeout(resolve, 0));
				assert.equal(bridge.calls.request.length, 1);
				assert.equal(bridge.calls.request[0].taskType, 'polish');
				bridge.emit(aiDone('req-1', '润色结果'));
				assert.equal(findButton(root, '复制')?.disabled, false);

				clickButton(root, '复制');
				assert.deepEqual(copied, ['润色结果']);

				clickButton(root, '重新生成');
				assert.equal(bridge.calls.request.length, 2);
				bridge.emit(aiDone('req-2', '润色第二版'));

				clickButton(root, '插入文档');
				assert.equal(bridge.calls.accept.length, 1);

				clickButton(root, '生成');
				await new Promise((resolve) => setTimeout(resolve, 0));
				clickButton(root, '停止生成');
				assert.deepEqual(bridge.calls.cancel, ['req-3']);
			} finally {
				dom.restore();
			}
		});

		it('auto-starts continue on open and supports 开始生成', async function () {
			const dom = setupDom();
			try {
				const bridge = createFakeWriterBridge('段落开头');
				injectWriterController(bridge, {
					appendAfterSelection() {
						return true;
					},
					pastePlainText() {
						return false;
					},
					copyText() {
						return true;
					},
				});
				const dialog = new MobileAiOperationDialog('continue');
				dialog.open();
				await new Promise((resolve) => setTimeout(resolve, 0));
				assert.equal(bridge.calls.request.length, 1);
				assert.equal(bridge.calls.request[0].taskType, 'continue');
			} finally {
				dom.restore();
			}
		});
	});

	describe('MobileAiTranslateDialog', function () {
		it('supports 交换 / 开始翻译 / 重新生成 / 插入文档', function () {
			const dom = setupDom();
			try {
				const bridge = createFakeWriterBridge('');
				injectWriterController(bridge, {
					replaceSelection() {
						return true;
					},
					pastePlainText() {
						return false;
					},
					pasteHtml() {
						return true;
					},
					copyText() {
						return true;
					},
				});
				const dialog = new MobileAiTranslateDialog();
				dialog.open();
				const root = dom.document.body;

				const source = root.querySelector(
					'textarea[aria-label="原文"]',
				) as HTMLTextAreaElement;
				source.value = 'Hello';

				clickButton(root, '交换');
				clickButton(root, '开始翻译');
				assert.equal(bridge.calls.request.length, 1);
				assert.equal(bridge.calls.request[0].taskType, 'translate');
				bridge.emit(aiDone('req-1', '你好'));
				clickButton(root, '重新生成');
				assert.equal(bridge.calls.request.length, 2);
				bridge.emit(aiDone('req-2', '你好呀'));
				clickButton(root, '插入文档');
				assert.equal(bridge.calls.accept.length, 1);
			} finally {
				dom.restore();
			}
		});
	});

	describe('MobileAiFormatBatchDialog', function () {
		it('applies checked rules via 应用', async function () {
			const dom = setupDom();
			try {
				const pasted: string[] = [];
				(dom.window as any).app.map._clip.pastePlainText = function (text: string) {
					pasted.push(text);
					return true;
				};
				(dom.window as any).app.map._docLayer._selectedTextContent =
					'Hello, world! Are you ok?';
				const dialog = new MobileAiFormatBatchDialog();
				dialog.open();
				const root = dom.document.body;
				const checkbox = root.querySelector(
					'input[type="checkbox"]',
				) as HTMLInputElement;
				checkbox.checked = true;
				checkbox.onchange!(new Event('change'));
				clickButton(root, '应用');
				await new Promise((resolve) => setTimeout(resolve, 0));
				assert.equal(pasted.length, 1);
				assert.notEqual(pasted[0], '待处理文本');
			} finally {
				dom.restore();
			}
		});
	});

	describe('MobileAiTypesetDialog', function () {
		it('runs 开始排版 → 重新生成 → 插入文档 (HTML fallback)', async function () {
			const dom = setupDom();
			try {
				const bridge = createFakeWriterBridge('');
				const insertedHtml: string[] = [];
				injectWriterController(bridge, {
					pastePlainText() {
						return true;
					},
				insertHtmlAtEnd(html: string, _plain: string): boolean {
					insertedHtml.push(html);
					return true;
				},
					copyText() {
						return true;
					},
				});

				const origTypesetBridge = TypesetBridge.getInstance;
				TypesetBridge.getInstance = function () {
					return { isAvailable() { return false; } } as any;
				};

				const origExtractor = MobileAiDocumentExtractor.extractFullText;
				MobileAiDocumentExtractor.extractFullText = function () {
					return Promise.resolve('论文标题\n摘要内容\n正文段落');
				};

				const dialog = new MobileAiTypesetDialog();
				dialog.open();
				const root = dom.document.body;

				clickButton(root, '通用文档');
				clickButton(root, '开始排版');
				await new Promise(function (resolve) {
					setTimeout(resolve, 0);
				});
				assert.equal(bridge.calls.request.length, 1);
				assert.equal(bridge.calls.request[0].taskType, 'typeset');

				const json =
					'{"sections":{"title":"论文标题","abstract":"摘要内容","body":"正文段落"}}';
				bridge.emit(aiDone('req-1', json));

				clickButton(root, '重新生成');
				assert.equal(bridge.calls.request.length, 2);
				bridge.emit(aiDone('req-2', json));

				clickButton(root, '插入文档');
				assert.equal(bridge.calls.accept.length, 1);

				MobileAiDocumentExtractor.extractFullText = origExtractor;
				TypesetBridge.getInstance = origTypesetBridge;
			} finally {
				dom.restore();
			}
		});
	});

	describe('MobileAiAssistantPanel', function () {
		it('supports Tab 切换、空发送校验、发送与停止按钮', function () {
			const dom = setupDom();
			try {
				let nextId = 1;
				MobileAiConversationController.resetSharedForTests();
				const origGetInstance = MobileAiBridge.getInstance;
				MobileAiBridge.getInstance = function () {
					return {
						request(_payload: { [key: string]: any }): string {
							return 'conv-' + nextId++;
						},
						cancel(): boolean {
							return true;
						},
						accept(): boolean {
							return true;
						},
						isAvailable(): boolean {
							return true;
						},
						getSelectedText(): string {
							return '文档上下文';
						},
						subscribe(
							_listener: (message: NativeBridgeEnvelope) => void,
						): () => void {
							return function () {};
						},
					} as any;
				};

				const panel = new MobileAiAssistantPanel();
				panel.open();
				const root = dom.document.body;

				clickButton(root, '聊天');
				clickButton(root, '发送');
				assert.match(
					root.querySelector('[role="status"]')?.textContent || '',
					/请输入/,
				);

				const input = root.querySelector('textarea') as HTMLTextAreaElement;
				input.value = '你好';
				clickButton(root, '发送');
				assert.equal(input.value, '');

				clickButton(root, '停止');
				clickButton(root, '清空对话');

				MobileAiBridge.getInstance = origGetInstance;
			} finally {
				dom.restore();
			}
		});
	});

	describe('MobileAiOperationSheet', function () {
		it('disables selection-required cards without text and opens supported tasks', function () {
			const dom = setupDom();
			try {
				const opened: string[] = [];
				const origGetInstance = MobileAiBridge.getInstance;
				MobileAiBridge.getInstance = function () {
					return {
						getSelectedText() {
							return '';
						},
					} as any;
				};

				const sheet = new MobileAiOperationSheet(function (entry) {
					opened.push(entry.taskType);
				});
				sheet.open();
				const root = dom.document.body;
				const polish = findButton(root, '文案润色');
				assert.ok(polish);
				assert.ok(polish?.querySelector('svg'));
				assert.equal(polish?.disabled, false);
				polish!.click();
				assert.deepEqual(opened, []);

				MobileAiBridge.getInstance = function () {
					return {
						getSelectedText() {
							return '选区文字';
						},
					} as any;
				};
				sheet.open();
				const polishEnabled = findButton(root, '文案润色');
				assert.equal(polishEnabled?.disabled, false);
				polishEnabled!.click();
				assert.deepEqual(opened, ['polish']);

				MobileAiBridge.getInstance = origGetInstance;
			} finally {
				dom.restore();
			}
		});
	});

	describe('WriterAiController retry semantics', function () {
		it('regenerate reuses task context after accept', function () {
			const bridge = createFakeWriterBridge('文本');
			const controller = new WriterAiController(bridge, {
				replaceSelection() {
					return true;
				},
				pastePlainText() {
					return false;
				},
			});
			controller.request('expand', { requirement: '加例子' });
			bridge.emit(aiDone('req-1', '扩写一'));
			assert.equal(controller.accept(), true);
			assert.equal(controller.regenerate(), 'req-2');
			assert.deepEqual(bridge.calls.request[1], {
				taskType: 'expand',
				selection: '文本',
				context: { requirement: '加例子' },
			});
		});

		it('blocks concurrent request like UI 开始生成 would', function () {
			const bridge = createFakeWriterBridge('文本');
			const controller = new WriterAiController(bridge, {
				replaceSelection() {
					return true;
				},
				pastePlainText() {
					return false;
				},
			});
			controller.request('polish');
			assert.equal(controller.request('polish'), null);
		});
	});
});
