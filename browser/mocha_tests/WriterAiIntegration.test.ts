/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * End-to-end Writer AI flows: catalog → request → stream → accept,
 * with a fake native bridge and a document adapter that records calls.
 */

var assert = require('assert').strict;

describe('Writer AI integration', function () {
	interface AdapterCalls {
		replaceSelection: string[];
		appendAfterSelection: string[];
		insertAtEnd: string[];
		insertHtmlAtEnd: Array<{ html: string; plain: string }>;
		pastePlainText: string[];
		pasteHtml: Array<{ html: string; plain: string }>;
		copyText: string[];
	}

	interface FakeWriterAiBridge extends WriterAiBridgeLike {
		calls: {
			request: Array<{ [key: string]: any }>;
			cancel: string[];
			accept: Array<{ requestId: string; text: string }>;
		};
		emit(message: NativeBridgeEnvelope): void;
	}

	function createTrackingAdapter(): {
		calls: AdapterCalls;
		adapter: WriterAiDocumentAdapter;
	} {
		const calls: AdapterCalls = {
			replaceSelection: [],
			appendAfterSelection: [],
			insertAtEnd: [],
			insertHtmlAtEnd: [],
			pastePlainText: [],
			pasteHtml: [],
			copyText: [],
		};
		return {
			calls,
			adapter: {
				replaceSelection(text: string): boolean {
					calls.replaceSelection.push(text);
					return true;
				},
				appendAfterSelection(text: string): boolean {
					calls.appendAfterSelection.push(text);
					return true;
				},
				insertAtEnd(text: string): boolean {
					calls.insertAtEnd.push(text);
					return true;
				},
				insertHtmlAtEnd(html: string, plainText: string): boolean {
					calls.insertHtmlAtEnd.push({ html, plain: plainText });
					return true;
				},
				pastePlainText(text: string): boolean {
					calls.pastePlainText.push(text);
					return true;
				},
				pasteHtml(html: string, plainText: string): boolean {
					calls.pasteHtml.push({ html, plain: plainText });
					return true;
				},
				copyText(text: string): boolean {
					calls.copyText.push(text);
					return true;
				},
			},
		};
	}

	function createFakeBridge(selection: string): FakeWriterAiBridge {
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
				const requestId = 'request-' + nextRequestId;
				nextRequestId += 1;
				return requestId;
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

	function aiMessage(
		requestId: string,
		type: string,
		payload: { [key: string]: any },
	): NativeBridgeEnvelope {
		return {
			protocolVersion: 1,
			channel: 'native',
			type,
			requestId,
			payload,
		};
	}

	function completeRequest(
		bridge: FakeWriterAiBridge,
		requestId: string,
		fullText: string,
		streamDeltas?: string[],
	): void {
		if (streamDeltas) {
			streamDeltas.forEach(function (delta) {
				bridge.emit(aiMessage(requestId, 'ai.stream', { delta }));
			});
		}
		bridge.emit(aiMessage(requestId, 'ai.done', { fullText }));
	}

	interface WriterTaskFlowSpec {
		taskType: string;
		selection: string;
		context: { [key: string]: any };
		result: string;
		acceptMethod:
			| 'replaceSelection'
			| 'appendAfterSelection'
			| 'insertAtEnd';
	}

	const WRITER_TASK_FLOWS: WriterTaskFlowSpec[] = [
		{
			taskType: 'polish',
			selection: '原始文本',
			context: { polishStyle: 'formal' },
			result: '润色后的文本',
			acceptMethod: 'replaceSelection',
		},
		{
			taskType: 'translate',
			selection: 'Hello world',
			context: { sourceLang: 'en', targetLang: 'zh' },
			result: '你好世界',
			acceptMethod: 'replaceSelection',
		},
		{
			taskType: 'expand',
			selection: '简短描述',
			context: { requirement: '补充细节' },
			result: '更详细的描述内容',
			acceptMethod: 'replaceSelection',
		},
		{
			taskType: 'condense',
			selection: '很长的段落内容需要压缩',
			context: {},
			result: '压缩后',
			acceptMethod: 'replaceSelection',
		},
		{
			taskType: 'rewrite',
			selection: '旧表述',
			context: { requirement: '更口语化' },
			result: '新表述',
			acceptMethod: 'replaceSelection',
		},
		{
			taskType: 'continue',
			selection: '段落开头',
			context: {},
			result: '续写段落',
			acceptMethod: 'appendAfterSelection',
		},
		{
			taskType: 'summarize',
			selection: '长文正文内容',
			context: {},
			result: '摘要',
			acceptMethod: 'replaceSelection',
		},
		{
			taskType: 'outline',
			selection: '',
			context: { outlineType: 'paper', requirement: '含引言' },
			result: '1. 引言\n2. 方法',
			acceptMethod: 'insertAtEnd',
		},
		{
			taskType: 'text_extract',
			selection: '',
			context: {},
			result: '识别出的文字',
			acceptMethod: 'insertAtEnd',
		},
		{
			taskType: 'article_generate',
			selection: '',
			context: {
				template: 'leave_apply',
				variables: ['张三', '家中有事', '3天', '2026-09-03'],
			},
			result: '请假申请正文',
			acceptMethod: 'insertAtEnd',
		},
		{
			taskType: 'typeset',
			selection: '文档全文内容',
			context: {
				typesetType: 'paper',
				typesetVersion: 'v2',
				paragraphMode: false,
			},
			result: '排版后的正文',
			acceptMethod: 'insertAtEnd',
		},
	];

	describe('catalog ↔ UI alignment', function () {
		it('keeps WriterAiCatalog resultMode in sync with MobileAiUiCatalog', function () {
			Object.keys(WriterAiCatalog.TASKS).forEach(function (taskType) {
				const catalogTask = WriterAiCatalog.getTask(taskType);
				const uiEntry = MobileAiUiCatalog.getEntry(taskType);
				assert.ok(catalogTask, 'missing catalog task ' + taskType);
				assert.ok(uiEntry, 'missing UI entry for ' + taskType);
				assert.equal(uiEntry?.resultMode, catalogTask?.resultMode);
				assert.equal(uiEntry?.iosSupport, true);
				assert.equal(MobileAiUiCatalog.canRun(taskType, 'text'), true);
			});
		});

		it('validates every supported Writer task before request', function () {
			WRITER_TASK_FLOWS.forEach(function (spec) {
				const validation = WriterAiCatalog.validateRequest({
					taskType: spec.taskType,
					selection: spec.selection,
					context: spec.context,
				});
				assert.equal(
					validation.valid,
					true,
					spec.taskType + ' should validate: ' + validation.errorCode,
				);
			});
		});
	});

	describe('end-to-end task flows', function () {
		WRITER_TASK_FLOWS.forEach(function (spec) {
			it(
				'completes ' +
					spec.taskType +
					' via request → stream → accept (' +
					spec.acceptMethod +
					')',
				function () {
					const bridge = createFakeBridge(spec.selection);
					const tracking = createTrackingAdapter();
					const controller = new WriterAiController(
						bridge,
						tracking.adapter,
					);

					const requestId = controller.request(
						spec.taskType,
						spec.context,
						spec.selection,
					);
					assert.ok(requestId);
					assert.equal(controller.getState().state, 'loading');
					assert.deepEqual(bridge.calls.request[0], {
						taskType: spec.taskType,
						selection: spec.selection,
						context: spec.context,
					});

					completeRequest(bridge, requestId!, spec.result, ['流式']);
					assert.equal(controller.getState().state, 'ready');
					assert.equal(controller.getState().preview, spec.result);

					assert.equal(controller.accept(), true);
					assert.equal(controller.getState().state, 'accepted');
					assert.deepEqual(bridge.calls.accept, [
						{ requestId, text: spec.result },
					]);
					assert.equal(
						tracking.calls[spec.acceptMethod].length,
						1,
						spec.taskType + ' should use ' + spec.acceptMethod,
					);
					assert.equal(
						tracking.calls[spec.acceptMethod][0],
						spec.result,
					);
					assert.equal(tracking.calls.pastePlainText.length, 0);
				},
			);
		});

		it('inserts HTML at document end when html is provided for insertAtEnd tasks', function () {
			const bridge = createFakeBridge('');
			const tracking = createTrackingAdapter();
			const controller = new WriterAiController(bridge, tracking.adapter);

			const requestId = controller.request('outline', {
				outlineType: 'report',
			});
			completeRequest(bridge, requestId!, '大纲纯文本');
			const html = '<p>大纲 HTML</p>';
			assert.equal(controller.accept(html), true);
			assert.deepEqual(tracking.calls.insertHtmlAtEnd, [
				{ html, plain: '大纲纯文本' },
			]);
			assert.equal(tracking.calls.insertAtEnd.length, 0);
		});

		it('pastes HTML for replaceSelection when html is provided', function () {
			const bridge = createFakeBridge('选区');
			const tracking = createTrackingAdapter();
			const controller = new WriterAiController(bridge, tracking.adapter);

			const requestId = controller.request('polish', { polishStyle: 'quick' });
			completeRequest(bridge, requestId!, '润色结果');
			const html = '<strong>润色结果</strong>';
			assert.equal(controller.accept(html), true);
			assert.deepEqual(tracking.calls.pasteHtml, [
				{ html, plain: '润色结果' },
			]);
			assert.equal(tracking.calls.replaceSelection.length, 0);
		});

		it('copies preview text through the document adapter', function () {
			const bridge = createFakeBridge('选区');
			const tracking = createTrackingAdapter();
			const controller = new WriterAiController(bridge, tracking.adapter);

			const requestId = controller.request('summarize');
			completeRequest(bridge, requestId!, '摘要内容');
			assert.equal(controller.copy(), true);
			assert.deepEqual(tracking.calls.copyText, ['摘要内容']);
		});
	});

	describe('error and guard paths', function () {
		it('rejects Writer AI on spreadsheet documents', function () {
			const previousWindow = (global as any).window;
			(global as any).window = {
				app: {
					map: {
						getDocType() {
							return 'spreadsheet';
						},
					},
				},
			};
			try {
				const bridge = createFakeBridge('单元格文字');
				const controller = new WriterAiController(bridge, {
					pastePlainText() {
						return true;
					},
				});
				assert.equal(controller.request('polish'), null);
				assert.equal(controller.getState().state, 'error');
				assert.match(
					controller.getState().error || '',
					/Writer\/Impress/,
				);
			} finally {
				(global as any).window = previousWindow;
			}
		});

		it('rejects empty selection for selection-required tasks', function () {
			const bridge = createFakeBridge('   ');
			const controller = new WriterAiController(bridge, {
				pastePlainText() {
					return true;
				},
			});
			assert.equal(controller.request('polish'), null);
			assert.equal(controller.getState().error, '请先选择要处理的文字');
		});

		it('rejects typeset when document text is empty', function () {
			const bridge = createFakeBridge('');
			const controller = new WriterAiController(bridge, {
				pastePlainText() {
					return true;
				},
				insertAtEnd() {
					return true;
				},
			});
			assert.equal(
				controller.request('typeset', { typesetType: 'paper' }),
				null,
			);
			assert.equal(controller.getState().error, '文档全文提取失败');
		});

		it('blocks a second request while one is in flight', function () {
			const bridge = createFakeBridge('文本');
			const controller = new WriterAiController(bridge, {
				pastePlainText() {
					return true;
				},
			});
			assert.ok(controller.request('polish'));
			assert.equal(controller.request('expand'), null);
			assert.equal(controller.getState().error, '已有 AI 请求正在进行');
		});

		it('surfaces native ai.error messages', function () {
			const bridge = createFakeBridge('文本');
			const controller = new WriterAiController(bridge, {
				pastePlainText() {
					return true;
				},
			});
			const requestId = controller.request('polish');
			bridge.emit(
				aiMessage(requestId!, 'ai.error', {
					message: '模型服务不可用',
				}),
			);
			assert.equal(controller.getState().state, 'error');
			assert.equal(controller.getState().error, '模型服务不可用');
		});

		it('cancels an in-flight request', function () {
			const bridge = createFakeBridge('文本');
			const controller = new WriterAiController(bridge, {
				pastePlainText() {
					return true;
				},
			});
			const requestId = controller.request('rewrite');
			assert.equal(controller.cancel(), true);
			assert.deepEqual(bridge.calls.cancel, [requestId]);
			assert.equal(controller.getState().state, 'cancelled');
		});

		it('ignores bridge messages from another request session', function () {
			const bridge = createFakeBridge('文本');
			const controller = new WriterAiController(bridge, {
				pastePlainText() {
					return true;
				},
			});
			controller.request('polish');
			bridge.emit(
				aiMessage('other-request', 'ai.done', { fullText: '不应采纳' }),
			);
			assert.equal(controller.getState().state, 'loading');
			assert.equal(controller.getState().preview, '');
		});
	});

	describe('format batch local Writer flow', function () {
		it('processes selection locally and replaces via paste path', function () {
			const pasted: string[] = [];
			const selection = 'Hello, world! Are you ok?';
			const rules = new Array(FormatBatchProcessor.RULE_COUNT).fill(false);
			rules[FormatBatchProcessor.RULE_EN_TO_ZH_PUNCT] = true;

			const processed = FormatBatchProcessor.process(selection, rules);
			assert.notEqual(processed, selection);

			const clip = {
				pastePlainText(text: string): boolean {
					pasted.push(text);
					return true;
				},
			};
			assert.equal(clip.pastePlainText(processed), true);
			assert.deepEqual(pasted, [processed]);
		});

		it('exposes format_batch in UI catalog with formatBatch dialog', function () {
			const entry = MobileAiUiCatalog.getEntry('format_batch');
			assert.ok(entry);
			assert.equal(entry?.iosSupport, true);
			assert.equal(entry?.dialog, 'formatBatch');
			assert.equal(entry?.resultMode, 'replaceSelection');
			assert.equal(MobileAiUiCatalog.canRun('format_batch', 'text'), true);
		});
	});

	describe('WriterAiPanel task routing', function () {
		var jsdom = require('jsdom');

		it('opens the expected dialog type for each Writer entry', function () {
			const dom = new jsdom.JSDOM(canvasDomString(), {
				pretendToBeVisual: true,
			});
			const previousWindow = (global as any).window;
			const previousDocument = (global as any).document;
			(global as any).window = dom.window;
			(global as any).document = dom.window.document;
			(global as any).window.ThisIsTheiOSApp = true;
			(global as any).window.setTimeout = function (
				_handler: TimerHandler,
				_delay?: number,
			): number {
				return 1;
			};

			try {
				const panel = WriterAiPanel.mount();
				assert.ok(panel);

				const opened: string[] = [];
				const originalOperation = MobileAiOperationDialog.prototype.open;
				const originalTranslate = MobileAiTranslateDialog.prototype.open;
				const originalFormatBatch =
					MobileAiFormatBatchDialog.prototype.open;
				const originalTypeset = MobileAiTypesetDialog.prototype.open;

				MobileAiOperationDialog.prototype.open = function () {
					opened.push('operation:' + (this as any).taskType);
				};
				MobileAiTranslateDialog.prototype.open = function () {
					opened.push('translate');
				};
				MobileAiFormatBatchDialog.prototype.open = function () {
					opened.push('formatBatch');
				};
				MobileAiTypesetDialog.prototype.open = function () {
					opened.push('typeset');
				};

				panel!.openTask('polish');
				panel!.openTask('translate');
				panel!.openTask('format_batch');
				panel!.openTask('typeset');
				panel!.openTask('impress_outline');

				assert.deepEqual(opened, [
					'operation:polish',
					'translate',
					'formatBatch',
					'typeset',
				]);
				assert.equal(
					dom.window.document.body.querySelector('[role="status"]')?.textContent,
					'PPT 大纲：iOS 尚未支持',
				);

				MobileAiOperationDialog.prototype.open = originalOperation;
				MobileAiTranslateDialog.prototype.open = originalTranslate;
				MobileAiFormatBatchDialog.prototype.open = originalFormatBatch;
				MobileAiTypesetDialog.prototype.open = originalTypeset;
			} finally {
				(global as any).window = previousWindow;
				(global as any).document = previousDocument;
			}
		});
	});
});
