/*
 * Calc insert-hyperlink subpage (Android CalcHyperlinkPickerController: 互联网 / 邮件 / 文档).
 */

type CalcHyperlinkTab = 'internet' | 'mail' | 'document';

interface CalcDocumentTargetGroup {
	label: string;
	items: Array<{ label: string; url: string }>;
}

class CalcEditorHyperlinkDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly controller: WriterEditorController;
	private readonly onInserted?: () => void;
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
	) {
		this.controller = controller;
		this.onInserted = onInserted;

		const content = document.createElement('div');
		content.className = 'writer-function-calc-hyperlink-form';
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
		this.mailBody.rows = 3;
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
		content.appendChild(this.targetPane);

		this.submitButton = document.createElement('button');
		this.submitButton.type = 'button';
		this.submitButton.className = 'writer-function-calc-hyperlink-form__submit';
		this.submitButton.textContent = '添加';
		this.submitButton.onclick = () => this.onPrimary();
		content.appendChild(this.submitButton);

		this.subpage = writerEditorMountSubpageDialog('插入超链接', content, host);
		this.showTab('internet');
	}

	open(): void {
		const selected = this.controller.getSelectedText() || '';
		this.webText.value = selected;
		this.webLink.value = '';
		this.mailRecipient.value = '';
		this.mailSubject.value = '';
		this.mailBody.value = '';
		this.docTarget.value = '';
		this.docText.value = selected;
		this.selectedDocumentUrl = '';
		this.pendingDocumentLabel = '';
		this.pendingDocumentUrl = '';
		this.documentTargetVisible = false;
		this.expandedGroups.clear();
		this.showTab('internet');
		this.seedDocumentTabDefaults();
		this.subpage.open();
		this.webLink.focus();
	}

	/** Android CalcHyperlinkPickerController.onPickerShown — default sheet target. */
	private seedDocumentTabDefaults(): void {
		const groups = this.readDocumentTargets();
		const sheets = groups[0]?.items || [];
		if (!sheets.length) {
			return;
		}
		const active =
			sheets.find((item) => item.label && this.docTarget.value === item.label) ||
			sheets[0];
		if (!this.selectedDocumentUrl) {
			this.selectedDocumentUrl = active.url;
		}
		if (!this.docTarget.value.trim()) {
			this.docTarget.value = active.label;
		}
	}

	close(): void {
		this.subpage.close();
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
		this.internetPane.hidden = tab !== 'internet';
		this.mailPane.hidden = tab !== 'mail';
		const documentTab = tab === 'document';
		this.documentPane.hidden = !documentTab || this.documentTargetVisible;
		this.targetPane.hidden = !documentTab || !this.documentTargetVisible;
		this.submitButton.textContent =
			documentTab && this.documentTargetVisible ? '应用' : '添加';
		this.submitButton.setAttribute('aria-label', this.submitButton.textContent);
	}

	private onPrimary(): void {
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
			return;
		}
		const url = CalcEditorHyperlinkDialog.buildWebUrl(link);
		this.controller.insertHyperlink(this.webText.value, url);
		this.finish();
	}

	private submitMail(): void {
		const recipient = this.mailRecipient.value.trim();
		if (!recipient) {
			return;
		}
		const url = CalcEditorHyperlinkDialog.buildMailUrl(
			recipient,
			this.mailSubject.value.trim(),
			this.mailBody.value.trim(),
		);
		this.controller.insertHyperlink(recipient, url);
		this.finish();
	}

	private submitDocument(): void {
		if (!this.selectedDocumentUrl) {
			return;
		}
		const text = this.docText.value.trim() || this.docTarget.value.trim();
		this.controller.insertHyperlink(text, this.selectedDocumentUrl);
		this.finish();
	}

	private finish(): void {
		this.subpage.close();
		if (this.onInserted) {
			this.onInserted();
		}
	}

	private showDocumentTargetPicker(): void {
		this.documentTargetVisible = true;
		this.pendingDocumentLabel = this.docTarget.value.trim();
		this.pendingDocumentUrl = this.selectedDocumentUrl;
		const groups = this.readDocumentTargets();
		if (groups[0] && groups[0].items.length) {
			this.expandedGroups.add(groups[0].label);
		}
		this.rebuildTargetTree(groups);
		this.showTab('document');
	}

	private applyDocumentTarget(): void {
		if (!this.pendingDocumentUrl) {
			return;
		}
		this.selectedDocumentUrl = this.pendingDocumentUrl;
		this.docTarget.value = this.pendingDocumentLabel;
		this.documentTargetVisible = false;
		this.showTab('document');
	}

	private rebuildTargetTree(groups: CalcDocumentTargetGroup[]): void {
		this.targetTree.replaceChildren();
		groups.forEach((group) => {
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
					this.pendingDocumentLabel = item.label;
					this.pendingDocumentUrl = item.url;
					this.rebuildTargetTree(groups);
				};
				this.targetTree.appendChild(row);
			});
		});
	}

	private readDocumentTargets(): CalcDocumentTargetGroup[] {
		const docLayer = (window as any).app?.map?._docLayer;
		const map = (window as any).app?.map;
		const sheets = CalcEditorHyperlinkDialog.stringList(docLayer?._partNames).map(
			(name) => ({
				label: name,
				url: CalcEditorHyperlinkDialog.buildDocumentSheetUrl(name),
			}),
		);
		const namedRanges = CalcEditorHyperlinkDialog.stringList(
			docLayer?.namedRanges,
			docLayer?._namedRanges,
			map?.namedRanges,
		).map((name) => ({
			label: name,
			url: '#' + CalcEditorHyperlinkDialog.formatSheetName(name),
		}));
		const databaseRanges = CalcEditorHyperlinkDialog.stringList(
			docLayer?.databaseRanges,
			docLayer?._databaseRanges,
			map?.databaseRanges,
		).map((name) => ({
			label: name,
			url: '#' + CalcEditorHyperlinkDialog.formatSheetName(name),
		}));
		return [
			{ label: '工作表', items: sheets },
			{ label: '范围名称', items: namedRanges },
			{ label: '数据库范围', items: databaseRanges },
		];
	}

	private static stringList(...values: unknown[]): string[] {
		for (const value of values) {
			if (!Array.isArray(value)) {
				continue;
			}
			const names = value
				.map((item) => {
					if (typeof item === 'string') {
						return item.trim();
					}
					if (item && typeof item === 'object' && typeof (item as any).name === 'string') {
						return String((item as any).name).trim();
					}
					return '';
				})
				.filter((name) => !!name);
			if (names.length) {
				return names;
			}
		}
		return [];
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

	/** Android CalcHyperlinkPickerController.buildMailUrl (form encoding). */
	private static buildMailUrl(recipient: string, subject: string, body: string): string {
		let url = 'mailto:' + recipient.trim();
		const query: string[] = [];
		if (subject) {
			query.push('subject=' + CalcEditorHyperlinkDialog.formEncode(subject));
		}
		if (body) {
			query.push('body=' + CalcEditorHyperlinkDialog.formEncode(body));
		}
		if (query.length) {
			url += '?' + query.join('&');
		}
		return url;
	}

	private static formEncode(value: string): string {
		return encodeURIComponent(value).replace(/%20/g, '+');
	}

	private static buildDocumentSheetUrl(sheetName: string): string {
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
		return "'" + name.replace(/'/g, "''") + "'";
	}
}
