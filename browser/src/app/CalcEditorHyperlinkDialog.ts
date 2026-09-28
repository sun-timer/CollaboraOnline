/*
 * Calc insert-hyperlink subpage (Android CalcHyperlinkPickerController parity).
 *
 * Three segment tabs (互联网 / 邮件 / 文档), per-tab fields, and a document
 * target tree (工作表 expandable; 范围名称/数据库范围 toast TODO). URL building
 * mirrors Android buildWebUrl / buildMailUrl / buildDocumentSheetUrl.
 */

type CalcHyperlinkTab = 'internet' | 'mail' | 'document';

class CalcEditorHyperlinkDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly controller: WriterEditorController;
	private readonly onInserted?: () => void;

	private activeTab: CalcHyperlinkTab = 'internet';
	private documentTargetPickerVisible = false;
	private worksheetTreeExpanded = false;
	private pendingDocumentTargetUrl = '';
	private pendingDocumentTargetLabel = '';
	private selectedDocumentUrl = '';
	private activeSheetName = '';
	private sheetNames: string[] = [];

	private segmentButtons: HTMLButtonElement[] = [];
	private internetBody!: HTMLElement;
	private mailBody!: HTMLElement;
	private documentBody!: HTMLElement;
	private targetBody!: HTMLElement;

	private webTextInput!: HTMLInputElement;
	private webLinkInput!: HTMLInputElement;
	private mailRecipientInput!: HTMLInputElement;
	private mailSubjectInput!: HTMLInputElement;
	private mailBodyInput!: HTMLTextAreaElement;
	private docTargetInput!: HTMLInputElement;
	private docTextInput!: HTMLInputElement;
	private worksheetChildren!: HTMLDivElement;
	private worksheetChevron!: HTMLSpanElement;
	private primaryButton!: HTMLButtonElement;

	constructor(
		controller: WriterEditorController,
		host?: WriterEditorInlineSubpageHost | null,
		onInserted?: () => void,
	) {
		this.controller = controller;
		this.onInserted = onInserted;

		const root = document.createElement('div');
		root.className = 'writer-function-calc-hyperlink';

		root.appendChild(this.buildSegmentControl());
		root.appendChild(this.buildBodies());

		this.primaryButton = document.createElement('button');
		this.primaryButton.type = 'button';
		this.primaryButton.className = 'writer-function-calc-hyperlink__primary';
		this.primaryButton.textContent = '添加';
		this.primaryButton.setAttribute('aria-label', '添加超链接');
		this.primaryButton.onclick = () => this.onPrimaryClicked();
		root.appendChild(this.primaryButton);

		this.subpage = writerEditorMountSubpageDialog('插入超链接', root, host);
	}

	open(): void {
		this.activeTab = 'internet';
		this.documentTargetPickerVisible = false;
		this.webTextInput.value = '';
		this.webLinkInput.value = '';
		this.mailRecipientInput.value = '';
		this.mailSubjectInput.value = '';
		this.mailBodyInput.value = '';
		this.docTextInput.value = '';
		this.refreshSheetContext();
		this.showTab('internet');
		this.refreshPrimaryButton();
		this.subpage.open();
	}

	close(): void {
		this.subpage.close();
	}

	/** Mirrors Android fetchCalcHyperlinkContext via the web document layer. */
	private refreshSheetContext(): void {
		try {
			const map = (window as any).app && (window as any).app.map;
			const docLayer = map ? map._docLayer : null;
			const names = Array.isArray(docLayer?._partNames)
				? docLayer._partNames
				: [];
			this.sheetNames = names.filter(
				(name: unknown) =>
					typeof name === 'string' && (name as string).length > 0,
			);
			const part = Number(docLayer?._selectedPart);
			const activePart = Number.isFinite(part) ? part : 0;
			this.activeSheetName =
				typeof this.sheetNames[activePart] === 'string'
					? (this.sheetNames[activePart] as string)
					: '';
		} catch (_error) {
			this.sheetNames = [];
			this.activeSheetName = '';
			return;
		}
		if (
			this.docTargetInput &&
			!this.docTargetInput.value &&
			this.activeSheetName
		) {
			this.selectedDocumentUrl =
				CalcEditorHyperlinkDialog.buildDocumentSheetUrl(this.activeSheetName);
			this.docTargetInput.value = this.activeSheetName;
		}
		this.rebuildWorksheetChildren();
	}

	private buildSegmentControl(): HTMLElement {
		const track = document.createElement('div');
		track.className = 'writer-function-calc-hyperlink__segments';
		const labels: { tab: CalcHyperlinkTab; text: string }[] = [
			{ tab: 'internet', text: '互联网' },
			{ tab: 'mail', text: '邮件' },
			{ tab: 'document', text: '文档' },
		];
		this.segmentButtons = labels.map(({ tab, text }) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-function-calc-hyperlink__segment';
			button.textContent = text;
			button.setAttribute('aria-label', text);
			button.onclick = () => this.showTab(tab);
			track.appendChild(button);
			return button;
		});
		return track;
	}

	private buildBodies(): HTMLElement {
		const bodies = document.createElement('div');
		bodies.className = 'writer-function-calc-hyperlink__bodies';

		this.internetBody = this.buildInternetBody();
		this.mailBody = this.buildMailBody();
		this.documentBody = this.buildDocumentBody();
		this.targetBody = this.buildTargetPickerBody();
		bodies.appendChild(this.internetBody);
		bodies.appendChild(this.mailBody);
		bodies.appendChild(this.documentBody);
		bodies.appendChild(this.targetBody);
		return bodies;
	}

	private buildInternetBody(): HTMLElement {
		const pane = this.buildPane();
		this.webTextInput = CalcEditorHyperlinkDialog.textField('输入内容');
		pane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('文本', this.webTextInput),
		);
		this.webLinkInput = CalcEditorHyperlinkDialog.textField('输入内容');
		pane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('链接', this.webLinkInput),
		);
		return pane;
	}

	private buildMailBody(): HTMLElement {
		const pane = this.buildPane();
		this.mailRecipientInput = CalcEditorHyperlinkDialog.textField('输入内容');
		pane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('收件人', this.mailRecipientInput),
		);
		this.mailSubjectInput = CalcEditorHyperlinkDialog.textField('输入内容');
		pane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('主题', this.mailSubjectInput),
		);
		this.mailBodyInput = document.createElement('textarea');
		this.mailBodyInput.className = 'writer-function-calc-hyperlink__input';
		this.mailBodyInput.rows = 3;
		this.mailBodyInput.placeholder = '输入内容';
		this.mailBodyInput.setAttribute('aria-label', '正文');
		pane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('正文', this.mailBodyInput),
		);
		return pane;
	}

	private buildDocumentBody(): HTMLElement {
		const pane = this.buildPane();
		this.docTargetInput = CalcEditorHyperlinkDialog.textField('输入内容');
		this.docTargetInput.readOnly = true;
		pane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('目标', this.docTargetInput),
		);

		const open = document.createElement('button');
		open.type = 'button';
		open.className = 'writer-function-calc-hyperlink__open';
		open.textContent = '打开';
		open.setAttribute('aria-label', '打开文档目标');
		open.onclick = () => this.showDocumentTargetPicker();
		pane.appendChild(open);

		this.docTextInput = CalcEditorHyperlinkDialog.textField('输入内容');
		pane.appendChild(
			CalcEditorHyperlinkDialog.wrapField('文本', this.docTextInput),
		);
		return pane;
	}

	private buildTargetPickerBody(): HTMLElement {
		const pane = this.buildPane();

		const titleRow = document.createElement('div');
		titleRow.className = 'writer-function-calc-hyperlink__tree-title';
		const title = document.createElement('span');
		title.textContent = '文档中的目标';
		const chevron = document.createElement('span');
		chevron.textContent = '▾';
		chevron.setAttribute('aria-hidden', 'true');
		titleRow.appendChild(title);
		titleRow.appendChild(chevron);
		pane.appendChild(titleRow);

		const tree = document.createElement('div');
		tree.className = 'writer-function-calc-hyperlink__tree';

		const worksheetRow = document.createElement('button');
		worksheetRow.type = 'button';
		worksheetRow.className = 'writer-function-calc-hyperlink__tree-row';
		this.worksheetChevron = document.createElement('span');
		this.worksheetChevron.className =
			'writer-function-calc-hyperlink__tree-chevron';
		this.worksheetChevron.setAttribute('aria-hidden', 'true');
		worksheetRow.appendChild(this.worksheetChevron);
		const worksheetIcon = document.createElement('span');
		worksheetIcon.className = 'writer-function-calc-hyperlink__tree-icon';
		worksheetIcon.innerHTML = CalcEditorHyperlinkDialog.WORKSHEET_ICON;
		worksheetIcon.setAttribute('aria-hidden', 'true');
		worksheetRow.appendChild(worksheetIcon);
		const worksheetLabel = document.createElement('span');
		worksheetLabel.textContent = '工作表';
		worksheetRow.appendChild(worksheetLabel);
		worksheetRow.onclick = () => this.toggleWorksheetTree();
		tree.appendChild(worksheetRow);

		this.worksheetChildren = document.createElement('div');
		this.worksheetChildren.className =
			'writer-function-calc-hyperlink__tree-children';
		this.worksheetChildren.hidden = true;
		tree.appendChild(this.worksheetChildren);

		tree.appendChild(
			this.buildTreeRow(
				'范围名称',
				CalcEditorHyperlinkDialog.NAMED_RANGE_ICON,
				() => this.selectDocumentTargetCategory('范围名称', ''),
			),
		);
		tree.appendChild(
			this.buildTreeRow(
				'数据库范围',
				CalcEditorHyperlinkDialog.DATABASE_RANGE_ICON,
				() => this.selectDocumentTargetCategory('数据库范围', ''),
			),
		);
		pane.appendChild(tree);
		return pane;
	}

	private buildTreeRow(
		label: string,
		icon: string,
		onClick: () => void,
		childIndent?: string,
	): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-calc-hyperlink__tree-row';
		if (childIndent) {
			row.classList.add(childIndent);
		}
		if (icon) {
			const iconSpan = document.createElement('span');
			iconSpan.className = 'writer-function-calc-hyperlink__tree-icon';
			iconSpan.innerHTML = icon;
			iconSpan.setAttribute('aria-hidden', 'true');
			row.appendChild(iconSpan);
		}
		const text = document.createElement('span');
		text.textContent = label;
		row.appendChild(text);
		row.onclick = onClick;
		return row;
	}

	private rebuildWorksheetChildren(): void {
		if (!this.worksheetChildren) {
			return;
		}
		this.worksheetChildren.replaceChildren();
		for (const sheet of this.sheetNames) {
			const row = this.buildTreeRow(
				sheet,
				'',
				() =>
					this.selectDocumentTargetCategory(
						sheet,
						CalcEditorHyperlinkDialog.buildDocumentSheetUrl(sheet),
					),
				'writer-function-calc-hyperlink__tree-row--child',
			);
			this.worksheetChildren.appendChild(row);
		}
	}

	private toggleWorksheetTree(): void {
		this.worksheetTreeExpanded = !this.worksheetTreeExpanded;
		this.worksheetChildren.hidden = !this.worksheetTreeExpanded;
		this.refreshWorksheetArrow();
	}

	private refreshWorksheetArrow(): void {
		if (!this.worksheetChevron) {
			return;
		}
		this.worksheetChevron.innerHTML = this.worksheetTreeExpanded
			? CalcEditorHyperlinkDialog.CHEVRON_DOWN_ICON
			: CalcEditorHyperlinkDialog.CHEVRON_RIGHT_ICON;
	}

	private selectDocumentTargetCategory(label: string, url: string): void {
		this.pendingDocumentTargetLabel = label;
		this.pendingDocumentTargetUrl = url;
		if (!url) {
			CalcEditorHyperlinkDialog.showToast(label + '暂不支持，请选择工作表');
		}
	}

	private showDocumentTargetPicker(): void {
		this.documentTargetPickerVisible = true;
		this.pendingDocumentTargetLabel = this.docTargetInput.value.trim();
		this.pendingDocumentTargetUrl = this.selectedDocumentUrl;
		this.worksheetTreeExpanded = this.sheetNames.length > 0;
		this.rebuildWorksheetChildren();
		this.worksheetChildren.hidden = !this.worksheetTreeExpanded;
		this.refreshWorksheetArrow();
		this.updateBodiesVisibility();
		this.refreshPrimaryButton();
	}

	private hideDocumentTargetPicker(): void {
		this.documentTargetPickerVisible = false;
		this.updateBodiesVisibility();
		this.refreshPrimaryButton();
	}

	private applyDocumentTargetSelection(): void {
		if (!this.pendingDocumentTargetUrl) {
			CalcEditorHyperlinkDialog.showToast('请选择目标');
			return;
		}
		this.selectedDocumentUrl = this.pendingDocumentTargetUrl;
		this.docTargetInput.value = this.pendingDocumentTargetLabel;
		this.hideDocumentTargetPicker();
	}

	private buildPane(): HTMLDivElement {
		const pane = document.createElement('div');
		pane.className = 'writer-function-calc-hyperlink__pane';
		pane.hidden = true;
		return pane;
	}

	private showTab(tab: CalcHyperlinkTab): void {
		this.activeTab = tab;
		if (tab !== 'document') {
			this.documentTargetPickerVisible = false;
		}
		const tabs: CalcHyperlinkTab[] = ['internet', 'mail', 'document'];
		this.segmentButtons.forEach((button, i) => {
			button.classList.toggle(
				'writer-function-calc-hyperlink__segment--active',
				tabs[i] === tab,
			);
		});
		this.updateBodiesVisibility();
		this.refreshPrimaryButton();
	}

	private updateBodiesVisibility(): void {
		this.internetBody.hidden = this.activeTab !== 'internet';
		this.mailBody.hidden = this.activeTab !== 'mail';
		const documentTab = this.activeTab === 'document';
		this.documentBody.hidden = !(
			documentTab && !this.documentTargetPickerVisible
		);
		this.targetBody.hidden = !(documentTab && this.documentTargetPickerVisible);
	}

	private refreshPrimaryButton(): void {
		this.primaryButton.textContent =
			this.activeTab === 'document' && this.documentTargetPickerVisible
				? '应用'
				: '添加';
	}

	private onPrimaryClicked(): void {
		if (this.activeTab === 'document' && this.documentTargetPickerVisible) {
			this.applyDocumentTargetSelection();
			return;
		}
		this.onAddClicked();
	}

	private onAddClicked(): void {
		switch (this.activeTab) {
			case 'internet':
				this.submitInternet();
				break;
			case 'mail':
				this.submitMail();
				break;
			case 'document':
				this.submitDocument();
				break;
		}
	}

	private submitInternet(): void {
		const link = this.webLinkInput.value.trim();
		if (!link) {
			CalcEditorHyperlinkDialog.showToast('请填写链接');
			return;
		}
		const text = this.webTextInput.value.trim();
		const display = text || link;
		this.insert(display, CalcEditorHyperlinkDialog.buildWebUrl(link));
	}

	private submitMail(): void {
		const recipient = this.mailRecipientInput.value.trim();
		if (!recipient) {
			CalcEditorHyperlinkDialog.showToast('请填写收件人');
			return;
		}
		const url = CalcEditorHyperlinkDialog.buildMailUrl(
			recipient,
			this.mailSubjectInput.value.trim(),
			this.mailBodyInput.value.trim(),
		);
		this.insert(recipient, url);
	}

	private submitDocument(): void {
		if (!this.selectedDocumentUrl) {
			CalcEditorHyperlinkDialog.showToast('请选择目标');
			return;
		}
		const text = this.docTextInput.value.trim();
		const display =
			text ||
			this.pendingDocumentTargetLabel ||
			this.docTargetInput.value.trim();
		this.insert(display, this.selectedDocumentUrl);
	}

	private insert(displayText: string, url: string): void {
		this.controller.insertHyperlink(displayText, url);
		this.subpage.close();
		if (this.onInserted) {
			this.onInserted();
		}
	}

	private static textField(placeholder: string): HTMLInputElement {
		const input = document.createElement('input');
		input.type = 'text';
		input.className = 'writer-function-calc-hyperlink__input';
		input.placeholder = placeholder;
		return input;
	}

	private static wrapField(
		labelText: string,
		control: HTMLElement,
	): HTMLDivElement {
		const wrap = document.createElement('div');
		wrap.className = 'writer-function-calc-hyperlink__field';
		const label = document.createElement('span');
		label.className = 'writer-function-calc-hyperlink__field-label';
		label.textContent = labelText;
		wrap.appendChild(label);
		wrap.appendChild(control);
		return wrap;
	}

	private static showToast(text: string): void {
		const toast = document.createElement('div');
		toast.textContent = text;
		toast.style.cssText =
			'position:fixed;left:50%;bottom:calc(24px + env(safe-area-inset-bottom));' +
			'transform:translateX(-50%);z-index:10002;padding:10px 16px;border-radius:8px;' +
			'background:rgba(30,30,30,.88);color:#fff;font-size:14px;pointer-events:none;';
		document.body.appendChild(toast);
		window.setTimeout(() => toast.remove(), 2200);
	}

	/* URL builders — mirrors Android CalcHyperlinkPickerController statics. */

	static buildWebUrl(link: string): string {
		const trimmed = link.trim();
		if (!trimmed) {
			return '';
		}
		const lower = trimmed.toLowerCase();
		if (
			lower.startsWith('http://') ||
			lower.startsWith('https://') ||
			lower.startsWith('ftp://') ||
			lower.startsWith('mailto:')
		) {
			return trimmed;
		}
		if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
			return 'mailto:' + trimmed;
		}
		return 'http://' + trimmed;
	}

	static buildMailUrl(
		recipient: string,
		subject: string,
		body: string,
	): string {
		let url = 'mailto:' + recipient.trim();
		let hasQuery = false;
		if (subject) {
			url += '?subject=' + encodeURIComponent(subject);
			hasQuery = true;
		}
		if (body) {
			url += (hasQuery ? '&' : '?') + 'body=' + encodeURIComponent(body);
		}
		return url;
	}

	static buildDocumentSheetUrl(sheetName: string): string {
		const sheet = CalcEditorHyperlinkDialog.formatSheetName(sheetName.trim());
		if (!sheet) {
			return '';
		}
		return '#' + sheet + '.A1';
	}

	private static formatSheetName(name: string): string {
		if (!name) {
			return '';
		}
		if (name.startsWith("'") && name.endsWith("'")) {
			return name;
		}
		if (/^[A-Za-z0-9_]+$/.test(name)) {
			return name;
		}
		return "'" + name.replace("'", "''") + "'";
	}

	private static readonly WORKSHEET_ICON =
		'<svg viewBox="0 0 24 24" width="20" height="20" fill="none" aria-hidden="true">' +
		'<rect x="3.5" y="4.5" width="17" height="15" rx="1.5" stroke="#5f6368" stroke-width="1.6"/>' +
		'<path d="M3.5 9.5h17M3.5 14.5h17M9.5 4.5v15M15.5 4.5v15" stroke="#5f6368" stroke-width="1.2"/></svg>';
	private static readonly NAMED_RANGE_ICON =
		'<svg viewBox="0 0 24 24" width="20" height="20" fill="none" aria-hidden="true">' +
		'<path d="M4 5.5A1.5 1.5 0 0 1 5.5 4h8.6a2 2 0 0 1 1.42.59l4.06 4.06a2 2 0 0 1 0 2.83l-6.02 6.02a2 2 0 0 1-2.83 0L4.6 12.4A2 2 0 0 1 4 10.97V5.5Z" stroke="#5f6368" stroke-width="1.6"/>' +
		'<circle cx="9" cy="9" r="1.4" fill="#5f6368"/></svg>';
	private static readonly DATABASE_RANGE_ICON =
		'<svg viewBox="0 0 24 24" width="20" height="20" fill="none" aria-hidden="true">' +
		'<ellipse cx="12" cy="6" rx="7.5" ry="3" stroke="#5f6368" stroke-width="1.6"/>' +
		'<path d="M4.5 6v12c0 1.66 3.36 3 7.5 3s7.5-1.34 7.5-3V6" stroke="#5f6368" stroke-width="1.6"/>' +
		'<path d="M4.5 12c0 1.66 3.36 3 7.5 3s7.5-1.34 7.5-3" stroke="#5f6368" stroke-width="1.6"/></svg>';
	private static readonly CHEVRON_DOWN_ICON =
		'<svg viewBox="0 0 24 24" width="16" height="16" fill="none" aria-hidden="true">' +
		'<path d="M6 9.5 12 15.5 18 9.5" stroke="#80868b" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
	private static readonly CHEVRON_RIGHT_ICON =
		'<svg viewBox="0 0 24 24" width="16" height="16" fill="none" aria-hidden="true">' +
		'<path d="M9.5 6 15.5 12 9.5 18" stroke="#80868b" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
}
