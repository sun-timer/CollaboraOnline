/*
 * Impress insert-hyperlink subpage (Android ImpressHyperlinkPickerController parity).
 *
 * Three segment tabs (互联网 / 邮件 / 文档) with per-tab fields (incl. 姓名),
 * and a document target page: 幻灯片 picker backed by the web document layer,
 * 备注/讲义/主页面 toast TODO. URL building reuses the Calc builders (same as
 * Android, which calls CalcHyperlinkPickerController.buildWebUrl/buildMailUrl).
 */

type ImpressHyperlinkTab = 'internet' | 'mail' | 'document';

class ImpressEditorHyperlinkDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly controller: WriterEditorController;
	private readonly onInserted?: () => void;

	private activeTab: ImpressHyperlinkTab = 'internet';
	private slidePickerVisible = false;
	private selectedSlideIndex: number | null = null;
	private selectedSlideLabel = '';
	private selectedSlideUrl = '';
	private activeSlideIndex = 0;
	private slideNames: string[] = [];

	private segmentButtons: HTMLButtonElement[] = [];
	private internetBody!: HTMLElement;
	private mailBody!: HTMLElement;
	private documentBody!: HTMLElement;
	private slidePickerBody!: HTMLElement;

	private webTextInput!: HTMLInputElement;
	private webLinkInput!: HTMLInputElement;
	private webNameInput!: HTMLInputElement;
	private mailRecipientInput!: HTMLInputElement;
	private mailSubjectInput!: HTMLInputElement;
	private mailBodyInput!: HTMLTextAreaElement;
	private mailNameInput!: HTMLInputElement;
	private docTextInput!: HTMLInputElement;
	private docNameInput!: HTMLInputElement;
	private slideList!: HTMLDivElement;
	private primaryButton!: HTMLButtonElement;

	constructor(
		controller: WriterEditorController,
		host?: WriterEditorInlineSubpageHost | null,
		onInserted?: () => void,
	) {
		this.controller = controller;
		this.onInserted = onInserted;

		const root = document.createElement('div');
		root.className = 'writer-function-impress-hyperlink';

		root.appendChild(this.buildSegmentControl());
		root.appendChild(this.buildBodies());

		this.primaryButton = document.createElement('button');
		this.primaryButton.type = 'button';
		this.primaryButton.className = 'writer-function-impress-hyperlink__primary';
		this.primaryButton.textContent = '添加';
		this.primaryButton.setAttribute('aria-label', '添加超链接');
		this.primaryButton.onclick = () => this.onPrimaryClicked();
		root.appendChild(this.primaryButton);

		this.subpage = writerEditorMountSubpageDialog('插入超链接', root, host);
	}

	open(): void {
		this.activeTab = 'internet';
		this.slidePickerVisible = false;
		this.webTextInput.value = '';
		this.webLinkInput.value = '';
		this.webNameInput.value = '';
		this.mailRecipientInput.value = '';
		this.mailSubjectInput.value = '';
		this.mailBodyInput.value = '';
		this.mailNameInput.value = '';
		this.docTextInput.value = '';
		this.docNameInput.value = '';
		this.refreshSlideContext();
		this.showTab('internet');
		this.subpage.open();
	}

	close(): void {
		this.subpage.close();
	}

	/** Mirrors Android fetchImpressHyperlinkContext via the web document layer. */
	private refreshSlideContext(): void {
		try {
			const map = (window as any).app && (window as any).app.map;
			const docLayer = map ? map._docLayer : null;
			const names = Array.isArray(docLayer?._partNames)
				? docLayer._partNames
				: [];
			this.slideNames = names.filter(
				(name: unknown) =>
					typeof name === 'string' && (name as string).length > 0,
			);
			const part = Number(docLayer?._selectedPart);
			this.activeSlideIndex = Math.max(0, Number.isFinite(part) ? part : 0);
		} catch (_error) {
			this.slideNames = [];
			this.activeSlideIndex = 0;
			return;
		}
		if (this.selectedSlideUrl === '' && this.slideNames.length > 0) {
			const index = Math.min(this.activeSlideIndex, this.slideNames.length - 1);
			this.selectedSlideIndex = index;
			this.selectedSlideLabel = ImpressEditorHyperlinkDialog.formatSlideLabel(
				index,
				this.slideNames[index] as string,
			);
			this.selectedSlideUrl = ImpressEditorHyperlinkDialog.buildSlideUrl(index);
		}
		this.rebuildSlideList();
	}

	private buildSegmentControl(): HTMLElement {
		const track = document.createElement('div');
		track.className = 'writer-function-impress-hyperlink__segments';
		const labels: { tab: ImpressHyperlinkTab; text: string }[] = [
			{ tab: 'internet', text: '互联网' },
			{ tab: 'mail', text: '邮件' },
			{ tab: 'document', text: '文档' },
		];
		this.segmentButtons = labels.map(({ tab, text }) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-function-impress-hyperlink__segment';
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
		bodies.className = 'writer-function-impress-hyperlink__bodies';

		this.internetBody = this.buildInternetBody();
		this.mailBody = this.buildMailBody();
		this.documentBody = this.buildDocumentBody();
		this.slidePickerBody = this.buildSlidePickerBody();
		bodies.appendChild(this.internetBody);
		bodies.appendChild(this.mailBody);
		bodies.appendChild(this.documentBody);
		bodies.appendChild(this.slidePickerBody);
		return bodies;
	}

	private buildInternetBody(): HTMLElement {
		const pane = this.buildPane();
		this.webTextInput = ImpressEditorHyperlinkDialog.textField();
		pane.appendChild(
			ImpressEditorHyperlinkDialog.wrapField('文本', this.webTextInput),
		);
		this.webLinkInput = ImpressEditorHyperlinkDialog.textField();
		pane.appendChild(
			ImpressEditorHyperlinkDialog.wrapField('链接', this.webLinkInput),
		);
		this.webNameInput = ImpressEditorHyperlinkDialog.textField();
		pane.appendChild(
			ImpressEditorHyperlinkDialog.wrapField('姓名', this.webNameInput),
		);
		return pane;
	}

	private buildMailBody(): HTMLElement {
		const pane = this.buildPane();
		this.mailRecipientInput = ImpressEditorHyperlinkDialog.textField();
		pane.appendChild(
			ImpressEditorHyperlinkDialog.wrapField('收件人', this.mailRecipientInput),
		);
		this.mailSubjectInput = ImpressEditorHyperlinkDialog.textField();
		pane.appendChild(
			ImpressEditorHyperlinkDialog.wrapField('主题', this.mailSubjectInput),
		);
		this.mailBodyInput = document.createElement('textarea');
		this.mailBodyInput.className = 'writer-function-impress-hyperlink__input';
		this.mailBodyInput.rows = 3;
		this.mailBodyInput.placeholder = '输入内容';
		this.mailBodyInput.setAttribute('aria-label', '正文');
		pane.appendChild(
			ImpressEditorHyperlinkDialog.wrapField('正文', this.mailBodyInput),
		);
		this.mailNameInput = ImpressEditorHyperlinkDialog.textField();
		pane.appendChild(
			ImpressEditorHyperlinkDialog.wrapField('姓名', this.mailNameInput),
		);
		return pane;
	}

	private buildDocumentBody(): HTMLElement {
		const pane = this.buildPane();

		pane.appendChild(ImpressEditorHyperlinkDialog.heading('目标'));

		pane.appendChild(
			ImpressEditorHyperlinkDialog.targetRow('幻灯片', () =>
				this.showSlidePicker(),
			),
		);
		pane.appendChild(
			ImpressEditorHyperlinkDialog.targetRow('备注', () =>
				ImpressEditorHyperlinkDialog.showToast('备注目标暂不支持'),
			),
		);
		pane.appendChild(
			ImpressEditorHyperlinkDialog.targetRow('讲义', () =>
				ImpressEditorHyperlinkDialog.showToast('讲义目标暂不支持'),
			),
		);
		pane.appendChild(
			ImpressEditorHyperlinkDialog.targetRow('主页面', () =>
				ImpressEditorHyperlinkDialog.showToast('主页面目标暂不支持'),
			),
		);

		this.docTextInput = ImpressEditorHyperlinkDialog.textField();
		pane.appendChild(
			ImpressEditorHyperlinkDialog.wrapField('文本', this.docTextInput),
		);
		this.docNameInput = ImpressEditorHyperlinkDialog.textField();
		pane.appendChild(
			ImpressEditorHyperlinkDialog.wrapField('姓名', this.docNameInput),
		);
		return pane;
	}

	private buildSlidePickerBody(): HTMLElement {
		const pane = this.buildPane();
		pane.appendChild(ImpressEditorHyperlinkDialog.heading('幻灯片'));
		this.slideList = document.createElement('div');
		this.slideList.className = 'writer-function-impress-hyperlink__slide-list';
		pane.appendChild(this.slideList);
		return pane;
	}

	private rebuildSlideList(): void {
		if (!this.slideList) {
			return;
		}
		this.slideList.replaceChildren();
		if (this.slideNames.length === 0) {
			const empty = document.createElement('div');
			empty.className = 'writer-function-impress-hyperlink__slide-empty';
			empty.textContent = '暂无幻灯片';
			this.slideList.appendChild(empty);
			return;
		}
		this.slideNames.forEach((name, index) => {
			const label = ImpressEditorHyperlinkDialog.formatSlideLabel(index, name);
			const row = document.createElement('button');
			row.type = 'button';
			row.className = 'writer-function-impress-hyperlink__slide-row';
			if (index === this.selectedSlideIndex) {
				row.classList.add(
					'writer-function-impress-hyperlink__slide-row--selected',
				);
			}
			row.setAttribute('aria-label', label);
			row.textContent = label;
			row.onclick = () => this.selectSlideTarget(index);
			this.slideList.appendChild(row);
		});
	}

	private selectSlideTarget(index: number): void {
		this.selectedSlideIndex = index;
		this.selectedSlideLabel = ImpressEditorHyperlinkDialog.formatSlideLabel(
			index,
			(this.slideNames[index] as string) || '',
		);
		this.selectedSlideUrl = ImpressEditorHyperlinkDialog.buildSlideUrl(index);
		this.rebuildSlideList();
	}

	private showSlidePicker(): void {
		this.slidePickerVisible = true;
		this.rebuildSlideList();
		this.updateBodiesVisibility();
		this.refreshPrimaryButton();
	}

	private hideSlidePicker(): void {
		this.slidePickerVisible = false;
		this.updateBodiesVisibility();
		this.refreshPrimaryButton();
	}

	private applySlideSelection(): void {
		if (this.selectedSlideUrl === '') {
			ImpressEditorHyperlinkDialog.showToast('请选择幻灯片');
			return;
		}
		this.hideSlidePicker();
	}

	private buildPane(): HTMLDivElement {
		const pane = document.createElement('div');
		pane.className = 'writer-function-impress-hyperlink__pane';
		pane.hidden = true;
		return pane;
	}

	private showTab(tab: ImpressHyperlinkTab): void {
		this.activeTab = tab;
		if (tab !== 'document') {
			this.slidePickerVisible = false;
		}
		const tabs: ImpressHyperlinkTab[] = ['internet', 'mail', 'document'];
		this.segmentButtons.forEach((button, i) => {
			button.classList.toggle(
				'writer-function-impress-hyperlink__segment--active',
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
		this.documentBody.hidden = !(documentTab && !this.slidePickerVisible);
		this.slidePickerBody.hidden = !(documentTab && this.slidePickerVisible);
	}

	private refreshPrimaryButton(): void {
		this.primaryButton.textContent =
			this.activeTab === 'document' && this.slidePickerVisible
				? '应用'
				: '添加';
	}

	private onPrimaryClicked(): void {
		if (this.activeTab === 'document' && this.slidePickerVisible) {
			this.applySlideSelection();
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
			ImpressEditorHyperlinkDialog.showToast('请填写链接');
			return;
		}
		const url = CalcEditorHyperlinkDialog.buildWebUrl(link);
		const display = this.resolveDisplayText(
			this.webTextInput,
			this.webNameInput,
			link,
		);
		this.insert(display, url);
	}

	private submitMail(): void {
		const recipient = this.mailRecipientInput.value.trim();
		if (!recipient) {
			ImpressEditorHyperlinkDialog.showToast('请填写收件人');
			return;
		}
		const url = CalcEditorHyperlinkDialog.buildMailUrl(
			recipient,
			this.mailSubjectInput.value.trim(),
			this.mailBodyInput.value.trim(),
		);
		let display = this.mailNameInput.value.trim();
		if (!display) {
			display = recipient;
		}
		this.insert(display, url);
	}

	private submitDocument(): void {
		if (this.selectedSlideUrl === '') {
			ImpressEditorHyperlinkDialog.showToast('请选择目标，点击「幻灯片」');
			return;
		}
		const display = this.resolveDisplayText(
			this.docTextInput,
			this.docNameInput,
			this.selectedSlideLabel,
		);
		this.insert(display, this.selectedSlideUrl);
	}

	/** 文本 → 姓名 → fallback (Android resolveDisplayText). */
	private resolveDisplayText(
		textInput: HTMLInputElement,
		nameInput: HTMLInputElement,
		fallback: string,
	): string {
		const text = textInput.value.trim();
		if (text) {
			return text;
		}
		const name = nameInput.value.trim();
		if (name) {
			return name;
		}
		return fallback;
	}

	private insert(displayText: string, url: string): void {
		this.controller.insertHyperlink(displayText, url);
		this.subpage.close();
		if (this.onInserted) {
			this.onInserted();
		}
	}

	private static textField(): HTMLInputElement {
		const input = document.createElement('input');
		input.type = 'text';
		input.className = 'writer-function-impress-hyperlink__input';
		input.placeholder = '输入内容';
		return input;
	}

	private static heading(text: string): HTMLDivElement {
		const heading = document.createElement('div');
		heading.className = 'writer-function-impress-hyperlink__heading';
		heading.textContent = text;
		return heading;
	}

	private static targetRow(
		label: string,
		onClick: () => void,
	): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-impress-hyperlink__target-row';
		row.setAttribute('aria-label', label);
		row.textContent = label;
		row.onclick = onClick;
		return row;
	}

	private static wrapField(
		labelText: string,
		control: HTMLElement,
	): HTMLDivElement {
		const wrap = document.createElement('div');
		wrap.className = 'writer-function-impress-hyperlink__field';
		const label = document.createElement('span');
		label.className = 'writer-function-impress-hyperlink__field-label';
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

	/* Slide target helpers — mirrors Android ImpressHyperlinkPickerController. */

	static buildSlideUrl(slideIndex: number): string {
		return '#Slide ' + (slideIndex + 1);
	}

	private static formatSlideLabel(
		slideIndex: number,
		slideName: string,
	): string {
		const name = (slideName || '').trim();
		if (!name) {
			return '幻灯片 ' + (slideIndex + 1);
		}
		return slideIndex + 1 + '. ' + name;
	}
}
