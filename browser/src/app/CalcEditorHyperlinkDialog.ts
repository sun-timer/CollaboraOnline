/*
 * iOS Calc hyperlink subpages over the existing SetHyperlink command.
 */

type CalcHyperlinkTab = 'internet' | 'mail' | 'document';

interface CalcDocumentTargetGroup {
	label: string;
	items: Array<{ label: string; url: string }>;
}

interface CalcHyperlinkSelectionSnapshot {
	viewId: number;
	part: number;
	address: string;
	bounds: number[];
}

interface CalcHyperlinkTargetRequest {
	context: CalcEditorDialogContext;
	stage: 'names' | 'database';
	windowId: number | null;
	cancelled: boolean;
	timer: number;
	groups: CalcDocumentTargetGroup[];
	callback: ((groups: CalcDocumentTargetGroup[], loaded: boolean) => void) | null;
}

interface CalcHyperlinkPanelLifecycle {
	closePanel(onClosed: () => void): void;
	restorePanel(): void;
}

class CalcEditorHyperlinkDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly targetSubpage: { open(): void; close(): void };
	private readonly content: HTMLDivElement;
	private readonly targetContent: HTMLDivElement;
	private context: CalcEditorDialogContext | null = null;
	private selection: CalcHyperlinkSelectionSnapshot | null = null;
	private observer: MutationObserver | null = null;
	private pendingDispatch = false;
	private dispatchToken = 0;
	private cancelTargetLoad: (() => void) | null = null;
	private targetGroups: CalcDocumentTargetGroup[] = [];
	private static targetRequest: CalcHyperlinkTargetRequest | null = null;
	private static retiredTargetWindows = new Map<number, CalcEditorDialogContext>();
	private readonly controller: WriterEditorController;
	private readonly onInserted?: () => void;
	private readonly panelLifecycle?: CalcHyperlinkPanelLifecycle;
	private readonly segmentButtons: HTMLButtonElement[] = [];
	private readonly internetPane: HTMLDivElement;
	private readonly mailPane: HTMLDivElement;
	private readonly documentPane: HTMLDivElement;
	private readonly targetPane: HTMLDivElement;
	private readonly targetTree: HTMLDivElement;
	private readonly webText: HTMLInputElement;
	private readonly webLink: HTMLInputElement;
	private readonly mailRecipient: HTMLInputElement;
	private readonly mailSubject: HTMLInputElement;
	private readonly mailBody: HTMLTextAreaElement;
	private readonly docTarget: HTMLInputElement;
	private readonly docText: HTMLInputElement;
	private readonly submitButton: HTMLButtonElement;
	private readonly status: HTMLDivElement;
	private activeTab: CalcHyperlinkTab = 'internet';
	private documentTargetVisible = false;
	private selectedDocumentUrl = '';
	private pendingDocumentLabel = '';
	private pendingDocumentUrl = '';
	private readonly expandedGroups = new Set<string>();

	constructor(
		controller: WriterEditorController,
		host?: WriterEditorInlineSubpageHost | null,
		onInserted?: () => void,
		panelLifecycle?: CalcHyperlinkPanelLifecycle,
	) {
		this.controller = controller;
		this.onInserted = onInserted;
		this.panelLifecycle = panelLifecycle;

		const content = document.createElement('div');
		this.content = content;
		content.className = 'writer-function-calc-hyperlink-form';
		content.dataset.iosCalcDialog = 'hyperlink';
		content.appendChild(this.buildSegments());

		this.internetPane = document.createElement('div');
		this.internetPane.className = 'writer-function-calc-hyperlink-form__pane';
		this.webText = CalcEditorHyperlinkDialog.field('文本');
		this.webLink = CalcEditorHyperlinkDialog.field('链接');
		this.internetPane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('文本', this.webText),
		);
		this.internetPane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('链接', this.webLink),
		);

		this.mailPane = document.createElement('div');
		this.mailPane.className = 'writer-function-calc-hyperlink-form__pane';
		this.mailRecipient = CalcEditorHyperlinkDialog.field('收件人');
		this.mailSubject = CalcEditorHyperlinkDialog.field('主题');
		this.mailBody = document.createElement('textarea');
		this.mailBody.className = 'writer-function-calc-hyperlink-form__input';
		this.mailBody.rows = 1;
		this.mailBody.classList.add('writer-function-calc-hyperlink-form__mail-body');
		this.mailBody.oninput = () => {
			this.mailBody.style.height = 'auto';
			this.mailBody.style.height = this.mailBody.scrollHeight + 'px';
		};
		this.mailBody.placeholder = '输入内容';
		this.mailBody.setAttribute('aria-label', '正文');
		this.mailPane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('收件人', this.mailRecipient),
		);
		this.mailPane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('主题', this.mailSubject),
		);
		this.mailPane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('正文', this.mailBody),
		);

		this.documentPane = document.createElement('div');
		this.documentPane.className = 'writer-function-calc-hyperlink-form__pane';
		this.docTarget = CalcEditorHyperlinkDialog.field('目标');
		this.docTarget.readOnly = true;
		this.docText = CalcEditorHyperlinkDialog.field('文本');
		this.documentPane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('目标', this.docTarget),
		);
		const open = document.createElement('button');
		open.type = 'button';
		open.className = 'writer-function-calc-hyperlink-form__submit';
		open.textContent = '打开';
		open.setAttribute('aria-label', '打开文档中的目标');
		open.onclick = () => this.showDocumentTargetPicker();
		this.documentPane.appendChild(open);
		this.documentPane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('文本', this.docText),
		);

		this.targetPane = document.createElement('div');
		this.targetPane.className = 'writer-function-calc-hyperlink-form__pane';
		const targetTitle = document.createElement('div');
		targetTitle.className = 'writer-function-calc-hyperlink-form__target-title';
		targetTitle.textContent = '文档中的目标';
		this.targetTree = document.createElement('div');
		this.targetTree.className = 'writer-function-calc-hyperlink-form__tree';
		this.targetPane.appendChild(targetTitle);
		this.targetPane.appendChild(this.targetTree);

		content.appendChild(this.internetPane);
		content.appendChild(this.mailPane);
		content.appendChild(this.documentPane);
		this.targetContent = document.createElement('div');
		this.targetContent.className = 'writer-function-calc-hyperlink-form';
		this.targetContent.dataset.iosCalcDialog = 'hyperlink';
		this.targetContent.appendChild(this.targetPane);
		const applyTarget = document.createElement('button');
		applyTarget.type = 'button';
		applyTarget.className = 'writer-function-calc-hyperlink-form__submit';
		applyTarget.textContent = '应用';
		applyTarget.onclick = () => this.applyDocumentTarget();
		this.targetContent.appendChild(applyTarget);
		this.targetSubpage = writerEditorMountSubpageDialog('文档中的目标', this.targetContent, host);

		this.submitButton = document.createElement('button');
		this.submitButton.type = 'button';
		this.submitButton.className = 'writer-function-calc-hyperlink-form__submit';
		this.submitButton.textContent = '添加';
		this.submitButton.onclick = () => this.onPrimary();
		content.appendChild(this.submitButton);
		this.status = document.createElement('div');
		this.status.className = 'writer-function-calc-hyperlink-form__target-status';
		this.status.setAttribute('role', 'status');
		this.status.setAttribute('aria-live', 'polite');
		content.appendChild(this.status);
		[this.webText, this.webLink, this.mailRecipient, this.mailSubject, this.docText].forEach((input) => {
			input.onkeydown = (event) => {
				if (event.key === 'Enter') {
					event.preventDefault();
					this.onPrimary();
				}
			};
		});
		this.mailBody.onkeydown = (event) => {
			if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
				event.preventDefault();
				this.onPrimary();
			}
		};

		this.subpage = writerEditorMountSubpageDialog('超链接', content, host);
		this.showTab('internet');
	}

	/** Main panel may supply text captured before its close button takes focus. */
	open(
		context: CalcEditorDialogContext | null,
		selectedText = '',
		selection: CalcHyperlinkSelectionSnapshot | null,
	): void {
		if (this.content.isConnected) return;
		this.dispatchToken++;
		this.pendingDispatch = false;
		this.context = context;
		this.selection = selection;
		this.setStatus('');
		if (!calcEditorSameDialogContext(context) || !selection) {
			this.setStatus('无法确定当前单元格，请重新打开超链接面板');
			this.showTab('internet');
			this.subpage.open();
			return;
		}
		const selected = selectedText;
		this.webText.value = selected;
		this.webLink.value = '';
		this.mailRecipient.value = '';
		this.mailSubject.value = '';
		this.mailBody.value = '';
		this.mailBody.style.height = '';
		this.docTarget.value = '';
		this.docText.value = selected;
		this.selectedDocumentUrl = '';
		this.pendingDocumentLabel = '';
		this.pendingDocumentUrl = '';
		this.documentTargetVisible = false;
		this.expandedGroups.clear();
		this.targetGroups = this.readDocumentTargets(context);
		this.showTab('internet');
		this.subpage.open();
		// Do not summon the keyboard or change document focus before an input tap.
		this.observeDocumentLifecycle();
	}

	/** Reopen the same draft after a failed post-dismiss dispatch. */
	restorePreservingDraft(): void {
		if (!this.content.isConnected) this.subpage.open();
		this.observeDocumentLifecycle();
	}

	private observeDocumentLifecycle(): void {
		this.observer?.disconnect();
		this.observer = new MutationObserver(() => {
			if (!calcEditorSameDialogContext(this.context)) {
				this.close();
				return;
			}
			if (this.content.isConnected) {
				// Header back from the target picker cancels its pending choice.
				if (this.documentTargetVisible && !this.targetContent.isConnected) {
					this.documentTargetVisible = false;
					this.pendingDocumentUrl = '';
					this.pendingDocumentLabel = '';
					this.cancelTargetLoad?.();
					this.cancelTargetLoad = null;
				}
			} else if (!this.targetContent.isConnected) {
				this.cleanup();
			}
		});
		this.observer.observe(document.body, { childList: true, subtree: true });
	}

	close(): void {
		if (this.pendingDispatch) {
			this.dispatchToken++;
			this.pendingDispatch = false;
		}
		if (this.targetContent.isConnected) this.targetSubpage.close();
		if (this.content.isConnected) this.subpage.close();
		this.cleanup();
	}

	private cleanup(): void {
		this.observer?.disconnect();
		this.observer = null;
		this.cancelTargetLoad?.();
		this.cancelTargetLoad = null;
		this.context = null;
		this.selection = null;
	}

	private buildSegments(): HTMLDivElement {
		const track = document.createElement('div');
		track.className = 'writer-function-calc-hyperlink-form__segments';
		const tabs: { id: CalcHyperlinkTab; label: string }[] = [
			{ id: 'internet', label: '互联网' },
			{ id: 'mail', label: '邮件' },
			{ id: 'document', label: '文档' },
		];
		tabs.forEach((tab) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-function-calc-hyperlink-form__segment';
			button.textContent = tab.label;
			button.onclick = () => this.showTab(tab.id);
			track.appendChild(button);
			this.segmentButtons.push(button);
		});
		return track;
	}

	private showTab(tab: CalcHyperlinkTab): void {
		this.activeTab = tab;
		if (tab !== 'document') {
			this.documentTargetVisible = false;
		}
		const tabs: CalcHyperlinkTab[] = ['internet', 'mail', 'document'];
		this.segmentButtons.forEach((button, index) => {
			button.classList.toggle(
				'writer-function-calc-hyperlink-form__segment--active',
				tabs[index] === tab,
			);
		});
		[this.internetPane, this.mailPane, this.documentPane].forEach((pane, index) => {
			const visible = tabs[index] === tab;
			pane.hidden = !visible;
			// Functional visibility must also survive an author display:flex rule.
			pane.style.display = visible ? '' : 'none';
		});
		this.submitButton.textContent = '添加';
		this.submitButton.setAttribute('aria-label', this.submitButton.textContent);
	}

	private onPrimary(): void {
		if (!this.isCurrentTarget()) {
			this.setStatus('文档或单元格已更改，请重新打开超链接面板');
			return;
		}
		if (this.activeTab === 'document' && this.documentTargetVisible) {
			this.applyDocumentTarget();
			return;
		}
		if (this.activeTab === 'internet') {
			this.submitInternet();
		} else if (this.activeTab === 'mail') {
			this.submitMail();
		} else {
			this.submitDocument();
		}
	}

	private submitInternet(): void {
		const link = this.webLink.value.trim();
		if (!link) {
			this.setStatus('请输入链接');
			return;
		}
		const url = CalcEditorHyperlinkDialog.buildWebUrl(link);
		this.dispatchHyperlink(this.webText.value, url);
	}

	private submitMail(): void {
		const recipient = this.mailRecipient.value.trim();
		if (!recipient) {
			this.setStatus('请输入收件人');
			return;
		}
		const url = CalcEditorHyperlinkDialog.buildMailUrl(
			recipient,
			this.mailSubject.value,
			this.mailBody.value,
		);
		this.dispatchHyperlink(recipient, url);
	}

	private submitDocument(): void {
		if (!this.selectedDocumentUrl) {
			this.setStatus('请先选择文档中的目标');
			return;
		}
		const text = this.docText.value.trim() || this.docTarget.value.trim();
		this.dispatchHyperlink(text, this.selectedDocumentUrl);
	}

	private dispatchHyperlink(text: string, url: string): void {
		if (!this.isCurrentTarget()) {
			this.setStatus('文档或单元格已更改，请重新打开超链接面板');
			return;
		}
		if (!this.canDispatchToCurrentTarget()) {
			this.setStatus('当前文档状态无法插入超链接，请关闭其他对话框后重试');
			return;
		}
		// The input must be blurred before its containing sheet is removed. The
		// draft stays on this instance while the panel finishes dismissing.
		const active = document.activeElement as HTMLElement | null;
		if (active && this.content.contains(active)) active.blur();
		this.pendingDispatch = true;
		const token = ++this.dispatchToken;
		this.observer?.disconnect();
		this.observer = null;
		this.subpage.close();
		const afterDismiss = () => {
			if (token !== this.dispatchToken || !this.pendingDispatch) return;
			this.dispatchAfterDismiss(text, url);
		};
		if (this.panelLifecycle) this.panelLifecycle.closePanel(afterDismiss);
		else afterDismiss();
	}

	private dispatchAfterDismiss(text: string, url: string): void {
		if (!this.isCurrentTarget()) {
			this.pendingDispatch = false;
			this.restoreAfterFailedDispatch('文档或单元格已更改，请重新打开超链接面板');
			return;
		}
		try {
			const map = this.context?.map;
			if (!map || typeof map.focus !== 'function') {
				this.pendingDispatch = false;
				this.restoreAfterFailedDispatch('当前文档无法恢复焦点，请重试');
				return;
			}
			map.focus(true);
			// Recheck after focus restoration because native focus can change the
			// active view or document state while the dismissal animation runs.
			if (!this.isCurrentTarget()) {
				this.pendingDispatch = false;
				this.restoreAfterFailedDispatch('文档或单元格已更改，请重新打开超链接面板');
				return;
			}
			if (!this.canDispatchToCurrentTarget()) {
				this.pendingDispatch = false;
				this.restoreAfterFailedDispatch('当前文档状态无法插入超链接，请关闭其他对话框后重试');
				return;
			}
			const result = this.controller.insertHyperlink(text, url);
			// This confirms only that SetHyperlink was dispatched. Core does not
			// publish a command result for this UNO command.
			if (result?.dispatched !== 'unocmd') {
				this.pendingDispatch = false;
				this.restoreAfterFailedDispatch('无法发送超链接命令，请检查文档状态后重试');
				return;
			}
			this.pendingDispatch = false;
			this.cleanup();
			this.onInserted?.();
		} catch (error) {
			console.error('Failed to dispatch Calc hyperlink command', error);
			this.pendingDispatch = false;
			this.restoreAfterFailedDispatch('无法发送超链接命令，请检查文档状态后重试');
		}
	}

	private canDispatchToCurrentTarget(): boolean {
		const map = this.context?.map;
		return !!map && typeof map.isEditMode === 'function' && map.isEditMode() &&
			!map.uiManager?.isUIBlocked?.() && !map.dialog?.hasOpenedDialog?.() &&
			!map.jsdialog?.hasDialogOpened?.() && typeof map.sendUnoCommand === 'function';
	}

	private restoreAfterFailedDispatch(message: string): void {
		this.panelLifecycle?.restorePanel();
		if (!this.panelLifecycle) this.restorePreservingDraft();
		this.setStatus(message);
	}

	private setStatus(message: string): void {
		this.status.textContent = message;
		this.status.hidden = !message;
	}

	private isCurrentTarget(): boolean {
		const context = this.context;
		const selection = this.selection;
		if (!context || !calcEditorSameDialogContext(context) || !selection) return false;
		const calc = (window as any).app?.calc;
		const currentAddress = calc?.cellAddress?.toArray?.().join(':');
		const rectangle = calc?.cellCursorRectangle;
		if (context.docLayer._viewId !== selection.viewId ||
			context.docLayer._selectedPart !== selection.part || currentAddress !== selection.address) return false;
		// While editing the same cell, Core can temporarily hide the cursor; if
		// it is visible, its bounds must still match the captured selection.
		return !calc?.cellCursorVisible ||
			!!rectangle && JSON.stringify(rectangle.toArray()) === JSON.stringify(selection.bounds);
	}

	static captureSelection(context: CalcEditorDialogContext | null): CalcHyperlinkSelectionSnapshot | null {
		if (!context || !calcEditorSameDialogContext(context)) return null;
		const calc = (window as any).app?.calc;
		const address = calc?.cellAddress?.toArray?.();
		const bounds = calc?.cellCursorRectangle?.toArray?.();
		if (!Array.isArray(address) || !address.length || !Array.isArray(bounds) || bounds.length < 4 ||
			!Number.isInteger(context.docLayer._selectedPart) ||
			!Number.isInteger(context.docLayer._viewId)) return null;
		return {
			viewId: context.docLayer._viewId,
			part: context.docLayer._selectedPart,
			address: address.join(':'),
			bounds: bounds.slice(0, 4),
		};
	}

	private finish(): void {
		this.close();
		if (this.onInserted) {
			this.onInserted();
		}
	}

	private showDocumentTargetPicker(): void {
		if (!calcEditorSameDialogContext(this.context) || this.documentTargetVisible) return;
		this.documentTargetVisible = true;
		this.pendingDocumentLabel = this.docTarget.value;
		this.pendingDocumentUrl = this.selectedDocumentUrl;
		this.targetGroups = this.readDocumentTargets(this.context);
		this.expandedGroups.add('工作表');
		this.rebuildTargetTree(this.targetGroups);
		this.targetSubpage.open();
		this.targetTree.setAttribute('aria-busy', 'true');
		this.cancelTargetLoad = CalcEditorHyperlinkDialog.loadDocumentTargets(this.context, (groups, loaded) => {
			if (!this.documentTargetVisible || !calcEditorSameDialogContext(this.context)) return;
			if (groups.length) this.targetGroups = groups;
			this.rebuildTargetTree(this.targetGroups);
			this.targetTree.setAttribute('aria-busy', 'false');
			if (!loaded) {
				const status = document.createElement('div');
				status.className = 'writer-function-calc-hyperlink-form__target-status';
				status.setAttribute('role', 'status');
				status.textContent = '未能读取全部范围，请返回后重试';
				this.targetTree.appendChild(status);
			}
		});
	}

	private applyDocumentTarget(): void {
		if (!this.pendingDocumentUrl || !calcEditorSameDialogContext(this.context)) {
			this.setStatus('请先选择文档中的目标');
			return;
		}
		this.selectedDocumentUrl = this.pendingDocumentUrl;
		this.docTarget.value = this.pendingDocumentLabel;
		this.documentTargetVisible = false;
		this.cancelTargetLoad?.();
		this.cancelTargetLoad = null;
		this.targetSubpage.close();
		this.showTab('document');
	}

	private rebuildTargetTree(groups: CalcDocumentTargetGroup[]): void {
		this.targetTree.replaceChildren();
		groups.forEach((group) => {
			if (!group.items.length) return;
			const expanded = this.expandedGroups.has(group.label);
			const parent = document.createElement('button');
			parent.type = 'button';
			parent.className = 'writer-function-calc-hyperlink-form__tree-row';
			const chevron = document.createElement('span');
			chevron.className = 'writer-function-calc-hyperlink-form__tree-chevron';
			chevron.textContent = expanded ? '▾' : '▸';
			const label = document.createElement('span');
			label.textContent = group.label;
			parent.appendChild(chevron);
			parent.appendChild(label);
			parent.onclick = () => {
				if (this.expandedGroups.has(group.label)) {
					this.expandedGroups.delete(group.label);
				} else {
					this.expandedGroups.add(group.label);
				}
				this.rebuildTargetTree(groups);
			};
			this.targetTree.appendChild(parent);
			if (!expanded) {
				return;
			}
			group.items.forEach((item) => {
				const row = document.createElement('button');
				row.type = 'button';
				row.className =
					'writer-function-calc-hyperlink-form__tree-row writer-function-calc-hyperlink-form__tree-row--child';
				row.textContent = item.label;
				if (item.url && item.url === this.pendingDocumentUrl) {
					row.classList.add(
						'writer-function-calc-hyperlink-form__tree-row--selected',
					);
				}
				row.onclick = () => {
					const selected = this.pendingDocumentUrl === item.url;
					this.pendingDocumentLabel = selected ? '' : item.label;
					this.pendingDocumentUrl = selected ? '' : item.url;
					this.rebuildTargetTree(groups);
				};
				this.targetTree.appendChild(row);
			});
		});
	}

	private readDocumentTargets(context: CalcEditorDialogContext | null): CalcDocumentTargetGroup[] {
		const names = context?.docLayer._partNames;
		return [{ label: '工作表', items: Array.isArray(names) ? names
			.filter((name: unknown) => typeof name === 'string' && !!name)
			.map((name: string) => ({ label: name, url: CalcEditorHyperlinkDialog.buildDocumentSheetUrl(name) })) : [] }];
	}

	private static loadDocumentTargets(
		context: CalcEditorDialogContext | null,
		callback: (groups: CalcDocumentTargetGroup[], loaded: boolean) => void,
	): () => void {
		const old = CalcEditorHyperlinkDialog.targetRequest;
		if (old && !calcEditorSameDialogContext(old.context)) {
			CalcEditorHyperlinkDialog.finishTargetRequest(old, false);
		}
		if (!context || !calcEditorSameDialogContext(context) || CalcEditorHyperlinkDialog.targetRequest) {
			callback([], false);
			return () => undefined;
		}
		const sheets = Array.isArray(context.docLayer._partNames) ? context.docLayer._partNames : [];
		const request: CalcHyperlinkTargetRequest = {
			context, stage: 'names', windowId: null, cancelled: false, timer: 0, callback,
			groups: [{ label: '工作表', items: sheets.map((name: string) => ({
				label: name, url: CalcEditorHyperlinkDialog.buildDocumentSheetUrl(name),
			})) }],
		};
		CalcEditorHyperlinkDialog.targetRequest = request;
		CalcEditorHyperlinkDialog.requestTargetStage(request);
		return () => {
			if (CalcEditorHyperlinkDialog.targetRequest !== request) return;
			request.cancelled = true;
			request.callback = null;
			if (request.timer) window.clearTimeout(request.timer);
			request.timer = 0;
			// Keep an unbound tombstone until its late show arrives; no second
			// request of this kind can accidentally consume that response.
			if (request.windowId !== null) CalcEditorHyperlinkDialog.finishTargetRequest(request, false);
		};
	}

	private static requestTargetStage(request: CalcHyperlinkTargetRequest): void {
		// When Core disables InsertName, do not wait for a dialog it will not open.
		if (request.stage === 'names' &&
			request.context.map.stateChangeHandler?.getItemValue('.uno:InsertName') === 'disabled') {
			request.groups.push({ label: '范围名称', items: [] });
			request.stage = 'database';
		}
		request.timer = window.setTimeout(() => {
			if (CalcEditorHyperlinkDialog.targetRequest !== request) return;
			const callback = request.callback;
			request.callback = null;
			request.cancelled = true;
			request.timer = 0;
			callback?.(request.groups, false);
		}, 8000);
		request.context.socket.sendMessage('uno ' + (request.stage === 'names' ? '.uno:InsertName' : '.uno:DefineDBName'));
	}

	/** Receives raw Core JSDialog JSON through CalcEditorValidationBridge. */
	static handleDocumentTargetMessage(message: any): boolean {
		if (!calcEditorDialogContext()) return false;
		const windowId = Number(message.id);
		if (!Number.isInteger(windowId) || windowId < 0) return false;
		const retired = CalcEditorHyperlinkDialog.retiredTargetWindows.get(windowId);
		if (retired && !calcEditorSameDialogContext(retired)) {
			CalcEditorHyperlinkDialog.retiredTargetWindows.delete(windowId);
		} else if (retired) {
			if (message.action === 'close') CalcEditorHyperlinkDialog.retiredTargetWindows.delete(windowId);
			return true;
		}
		const request = CalcEditorHyperlinkDialog.targetRequest;
		if (!request || !calcEditorSameDialogContext(request.context)) return false;
		const expected = request.stage === 'names' ? 'InsertNameDialog' : 'DefineDatabaseRangeDialog';
		if (message.dialogid !== expected && request.windowId !== windowId) return false;
		if (message.action) {
			if (message.action === 'close' && request.windowId === windowId) {
				CalcEditorHyperlinkDialog.finishTargetRequest(request, false);
			}
			return true;
		}
		if (request.windowId !== null && request.windowId !== windowId) return true;
		request.windowId = windowId;
		if (request.cancelled) {
			CalcEditorHyperlinkDialog.finishTargetRequest(request, false);
			return true;
		}
		const find = (node: any, id: string): any => {
			if (node?.id === id) return node;
			for (const child of node?.children || []) {
				const match = find(child, id);
				if (match) return match;
			}
			return null;
		};
		const control = find(message, request.stage === 'names' ? 'ctrl' : 'entry-list');
		if (!control || !Array.isArray(control.entries)) {
			CalcEditorHyperlinkDialog.finishTargetRequest(request, false);
			return true;
		}
		const sheets = request.context.docLayer._partNames || [];
		const items: Array<{ label: string; url: string }> = [];
		const seen = new Set<string>();
		for (const entry of control.entries) {
			const column = (index: number): string => {
				const value = entry?.columns?.[index]?.text ?? (index === 0 ? entry?.text ?? entry : '');
				return typeof value === 'string' ? value : '';
			};
			const name = column(0);
			if (!name || name === '<dummy>') continue;
			const scope = request.stage === 'names' ? column(2) : '';
			const qualified = sheets.includes(scope)
				? CalcEditorHyperlinkDialog.formatSheetName(scope) + '.' + name : name;
			const url = '#' + qualified;
			if (seen.has(url)) continue;
			seen.add(url);
			items.push({ label: qualified, url });
		}
		request.groups.push({ label: request.stage === 'names' ? '范围名称' : '数据库范围', items });
		if (request.timer) window.clearTimeout(request.timer);
		request.timer = 0;
		if (request.stage === 'names') {
			CalcEditorHyperlinkDialog.cancelTargetWindow(request);
			request.stage = 'database';
			request.windowId = null;
			CalcEditorHyperlinkDialog.requestTargetStage(request);
		} else {
			CalcEditorHyperlinkDialog.finishTargetRequest(request, true);
		}
		return true;
	}

	private static cancelTargetWindow(request: CalcHyperlinkTargetRequest): void {
		if (request.windowId === null || !calcEditorSameDialogContext(request.context)) return;
		CalcEditorHyperlinkDialog.retiredTargetWindows.set(request.windowId, request.context);
		request.context.socket.sendMessage('dialogevent ' + request.windowId + ' ' + JSON.stringify({
			id: request.stage === 'names' ? 'close' : 'cancel', cmd: 'click', data: '0', type: 'responsebutton',
		}));
	}

	private static finishTargetRequest(request: CalcHyperlinkTargetRequest, loaded: boolean): void {
		if (request.timer) window.clearTimeout(request.timer);
		request.timer = 0;
		CalcEditorHyperlinkDialog.cancelTargetWindow(request);
		if (CalcEditorHyperlinkDialog.targetRequest === request) CalcEditorHyperlinkDialog.targetRequest = null;
		const callback = request.callback;
		request.callback = null;
		if (!request.cancelled && calcEditorSameDialogContext(request.context)) callback?.(request.groups, loaded);
	}

	private static field(label: string): HTMLInputElement {
		const input = document.createElement('input');
		input.type = 'text';
		input.placeholder = '输入内容';
		input.setAttribute('aria-label', label);
		input.className = 'writer-function-calc-hyperlink-form__input';
		return input;
	}

	private static wrapField(labelText: string, input: HTMLElement): HTMLDivElement {
		const wrap = document.createElement('div');
		wrap.className = 'writer-function-calc-hyperlink-form__field';
		const label = document.createElement('label');
		label.className = 'writer-function-calc-hyperlink-form__label';
		label.textContent = labelText;
		wrap.appendChild(label);
		wrap.appendChild(input);
		return wrap;
	}

	private static buildWebUrl(link: string): string {
		const trimmed = link.trim();
		const lower = trimmed.toLowerCase();
		let candidate = trimmed;
		if (
			lower.startsWith('http://') ||
			lower.startsWith('https://') ||
			lower.startsWith('ftp://')
		) {
			candidate = trimmed;
		} else if (lower.startsWith('mailto:') || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
			return lower.startsWith('mailto:') ? trimmed : 'mailto:' + trimmed;
		} else {
			candidate = 'http://' + trimmed;
		}
		try {
			return new URL(candidate).href;
		} catch (_error) {
			// Preserve the user's URL if a browser URL implementation rejects it.
			return candidate;
		}
	}

	/** RFC 6068: percent-encode values; '+' remains a literal plus. */
	private static buildMailUrl(recipient: string, subject: string, body: string): string {
		const addresses = recipient.replace(/^mailto:/i, '').split(/[;,]/).map((address) =>
			encodeURIComponent(address.trim()).replace(/%40/gi, '@'));
		let url = 'mailto:' + addresses.filter(Boolean).join(',');
		const query: string[] = [];
		if (subject) {
			query.push('subject=' + CalcEditorHyperlinkDialog.formEncode(subject));
		}
		if (body) {
			query.push('body=' + CalcEditorHyperlinkDialog.formEncode(body.replace(/\r\n|\r|\n/g, '\r\n')));
		}
		if (query.length) {
			url += '?' + query.join('&');
		}
		return url;
	}

	private static formEncode(value: string): string {
		return encodeURIComponent(value).replace(/[!'()*]/g, (character) =>
			'%' + character.charCodeAt(0).toString(16).toUpperCase());
	}

	private static buildDocumentSheetUrl(sheetName: string): string {
		const sheet = CalcEditorHyperlinkDialog.formatSheetName(sheetName);
		if (!sheet) {
			return '';
		}
		return '#' + sheet + '.A1';
	}

	private static formatSheetName(name: string): string {
		if (!name) {
			return '';
		}

		return "'" + name.replace(/'/g, "''") + "'";
	}
}
