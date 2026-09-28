/*
 * Writer find / find-replace sheet (iOS).
 *
 * Preview: find-only chrome (title 「查找」, gear + close, nav buttons above IME).
 * Edit: full find/replace tabs. Dispatches through AndroidFindReplaceBridge.
 */

interface WriterFindReplaceOpenOptions {
	replaceEnabled?: boolean;
}

class WriterFindReplaceDialog {
	private static active: WriterFindReplaceDialog | null = null;

	private root: HTMLDivElement | null = null;
	private panel: HTMLDivElement | null = null;
	private mainView: HTMLDivElement | null = null;
	private settingsView: HTMLDivElement | null = null;
	private readonly replaceEnabled: boolean;
	private mode: 'find' | 'replace' = 'find';
	private ignoreCase = true;
	private caseSensitive = false;
	private wholeWord = false;
	private syncingQuery = false;

	private findQueryInput!: HTMLInputElement;
	private replaceQueryInput!: HTMLInputElement;
	private replaceWithInput!: HTMLInputElement;
	private findPrevBtn!: HTMLButtonElement;
	private findNextBtn!: HTMLButtonElement;
	private replacePrevBtn!: HTMLButtonElement;
	private replaceNextBtn!: HTMLButtonElement;
	private replaceAllBtn!: HTMLButtonElement;
	private replaceOneBtn!: HTMLButtonElement;
	private tabFindBtn!: HTMLButtonElement;
	private tabReplaceBtn!: HTMLButtonElement;
	private findPanel!: HTMLDivElement;
	private replacePanel!: HTMLDivElement;
	private tabBar!: HTMLDivElement;

	private viewportHandler: (() => void) | null = null;

	static titleForReplaceEnabled(replaceEnabled: boolean): string {
		return replaceEnabled ? '查找替换' : '查找';
	}

	static isReplaceEnabledOption(
		options?: WriterFindReplaceOpenOptions,
	): boolean {
		return !!(options && options.replaceEnabled);
	}

	static mountBridge(): void {
		(window as any).__coolWriterFindReplace = {
			open: (options?: WriterFindReplaceOpenOptions): void => {
				WriterFindReplaceDialog.openWithOptions(options);
			},
			close: (): void => {
				WriterFindReplaceDialog.closeActive();
			},
		};
		(window as any).WriterFindReplaceDialog = WriterFindReplaceDialog;
	}

	static openWithOptions(options?: WriterFindReplaceOpenOptions): void {
		if (!(window as any).ThisIsTheiOSApp) {
			return;
		}
		WriterFindReplaceDialog.closeActive();
		const dialog = new WriterFindReplaceDialog(
			WriterFindReplaceDialog.isReplaceEnabledOption(options),
		);
		dialog.show();
	}

	static closeActive(): void {
		if (WriterFindReplaceDialog.active) {
			WriterFindReplaceDialog.active.close();
		}
	}

	constructor(replaceEnabled: boolean) {
		this.replaceEnabled = replaceEnabled;
	}

	open(): void {
		this.show();
	}

	close(): void {
		this.detachViewportLift();
		if (this.root) {
			this.root.remove();
			this.root = null;
		}
		this.panel = null;
		this.mainView = null;
		this.settingsView = null;
		if (WriterFindReplaceDialog.active === this) {
			WriterFindReplaceDialog.active = null;
		}
	}

	private show(): void {
		WriterFindReplaceDialog.closeActive();
		WriterFindReplaceDialog.active = this;

		this.root = document.createElement('div');
		this.root.className = 'writer-find-sheet';
		this.root.setAttribute('role', 'presentation');
		this.root.onclick = (event) => {
			if (event.target === this.root) {
				this.close();
			}
		};

		this.panel = document.createElement('div');
		this.panel.className =
			'writer-find-sheet__panel' +
			(this.replaceEnabled
				? ' writer-find-sheet__panel--edit'
				: ' writer-find-sheet__panel--preview');
		this.panel.setAttribute('role', 'dialog');
		this.panel.setAttribute('aria-modal', 'true');
		this.root.appendChild(this.panel);

		this.mainView = document.createElement('div');
		this.mainView.className = 'writer-find-sheet__main';
		this.panel.appendChild(this.mainView);
		this.mainView.appendChild(this.buildHeader());
		this.buildMainContent();
		this.settingsView = this.buildSettingsView();
		this.settingsView.style.display = 'none';
		this.panel.appendChild(this.settingsView);

		document.body.appendChild(this.root);
		this.pushOptionsToBridge();
		this.refreshButtonStates();
		this.attachViewportLift();
		window.setTimeout(() => {
			const field =
				this.mode === 'replace' && this.replaceEnabled
					? this.replaceQueryInput
					: this.findQueryInput;
			if (field) {
				field.focus();
			}
		}, 0);
	}

	private buildHeader(): HTMLElement {
		const header = document.createElement('header');
		header.className = 'writer-find-sheet__header';

		const settingsBtn = document.createElement('button');
		settingsBtn.type = 'button';
		settingsBtn.className = 'writer-find-sheet__icon-btn';
		settingsBtn.setAttribute('aria-label', '查找设置');
		settingsBtn.innerHTML = WRITER_FIND_SETTINGS_ICON;
		settingsBtn.onclick = () => this.showSettings(true);
		header.appendChild(settingsBtn);

		const title = document.createElement('h2');
		title.className = 'writer-find-sheet__title';
		title.textContent = WriterFindReplaceDialog.titleForReplaceEnabled(
			this.replaceEnabled,
		);
		header.appendChild(title);

		const closeBtn = document.createElement('button');
		closeBtn.type = 'button';
		closeBtn.className = 'writer-find-sheet__icon-btn';
		closeBtn.setAttribute('aria-label', '关闭');
		const closeIcon = WriterEditorIcons.get('close');
		if (closeIcon) {
			closeBtn.innerHTML = closeIcon;
		}
		closeBtn.onclick = () => this.close();
		header.appendChild(closeBtn);

		return header;
	}

	private buildMainContent(): void {
		if (!this.mainView) {
			return;
		}

		this.tabBar = document.createElement('div');
		this.tabBar.className = 'writer-find-sheet__tabs';
		if (!this.replaceEnabled) {
			this.tabBar.style.display = 'none';
		}
		this.tabFindBtn = this.makeTabButton('查找', true);
		this.tabReplaceBtn = this.makeTabButton('替换', false);
		this.tabBar.appendChild(this.tabFindBtn);
		this.tabBar.appendChild(this.tabReplaceBtn);
		this.mainView.appendChild(this.tabBar);

		this.findPanel = document.createElement('div');
		this.findPanel.className = 'writer-find-sheet__section';
		this.findQueryInput = this.makeSearchField('请输入查找内容');
		this.findPanel.appendChild(this.findQueryInput);
		const findNav = document.createElement('div');
		findNav.className = 'writer-find-sheet__nav-row';
		this.findPrevBtn = this.makeNavButton('上一处', () =>
			this.runFindPrevious(),
		);
		this.findNextBtn = this.makeNavButton('下一处', () =>
			this.runFindNext(this.findQueryInput),
		);
		findNav.appendChild(this.findPrevBtn);
		findNav.appendChild(this.findNextBtn);
		this.findPanel.appendChild(findNav);
		this.mainView.appendChild(this.findPanel);

		this.replacePanel = document.createElement('div');
		this.replacePanel.className = 'writer-find-sheet__section';
		this.replacePanel.style.display = 'none';
		this.replaceQueryInput = this.makeSearchField('请输入查找内容');
		this.replaceWithInput = this.makeSearchField('请输入替换内容');
		this.replacePanel.appendChild(this.replaceQueryInput);
		this.replacePanel.appendChild(this.replaceWithInput);
		const replaceNav = document.createElement('div');
		replaceNav.className =
			'writer-find-sheet__nav-row writer-find-sheet__nav-row--replace';
		this.replacePrevBtn = this.makeNavButton('上一处', () =>
			this.runFindPrevious(),
		);
		this.replaceNextBtn = this.makeNavButton('下一处', () =>
			this.runFindNext(this.replaceQueryInput),
		);
		this.replaceAllBtn = this.makeNavButton('全部替换', () =>
			this.runReplace(true),
		);
		this.replaceOneBtn = this.makeNavButton('替换', () =>
			this.runReplace(false),
		);
		this.replaceOneBtn.classList.add('writer-find-sheet__nav-btn--primary');
		replaceNav.appendChild(this.replacePrevBtn);
		replaceNav.appendChild(this.replaceNextBtn);
		replaceNav.appendChild(this.replaceAllBtn);
		replaceNav.appendChild(this.replaceOneBtn);
		this.replacePanel.appendChild(replaceNav);
		this.mainView.appendChild(this.replacePanel);

		const onQueryInput = () => {
			if (this.syncingQuery) {
				return;
			}
			if (this.mode === 'replace') {
				this.mirrorQuery(this.replaceQueryInput, this.findQueryInput);
			} else {
				this.mirrorQuery(this.findQueryInput, this.replaceQueryInput);
			}
			this.pushOptionsToBridge();
			this.refreshButtonStates();
		};
		this.findQueryInput.addEventListener('input', onQueryInput);
		this.replaceQueryInput.addEventListener('input', onQueryInput);
		this.replaceWithInput.addEventListener('input', () =>
			this.refreshButtonStates(),
		);

		this.setMode('find');
	}

	private buildSettingsView(): HTMLDivElement {
		const wrap = document.createElement('div');
		wrap.className = 'writer-find-sheet__settings';

		const header = document.createElement('header');
		header.className = 'writer-find-sheet__header';
		const backBtn = document.createElement('button');
		backBtn.type = 'button';
		backBtn.className = 'writer-find-sheet__icon-btn';
		backBtn.setAttribute('aria-label', '返回');
		backBtn.innerHTML = WRITER_FIND_BACK_ICON;
		backBtn.onclick = () => this.showSettings(false);
		header.appendChild(backBtn);
		const title = document.createElement('h2');
		title.className = 'writer-find-sheet__title';
		title.textContent = '设置';
		header.appendChild(title);
		const spacer = document.createElement('span');
		spacer.className = 'writer-find-sheet__icon-btn';
		spacer.setAttribute('aria-hidden', 'true');
		header.appendChild(spacer);
		wrap.appendChild(header);

		const list = document.createElement('div');
		list.className = 'writer-find-sheet__settings-list';
		let caseToggle: HTMLInputElement | null = null;
		let fuzzyToggle: HTMLInputElement | null = null;
		const fuzzyRow = this.makeSettingsRow(
			'模糊查找',
			'根据AI匹配近义词',
			this.ignoreCase,
			(on) => {
				this.ignoreCase = on;
				if (on && caseToggle) {
					this.caseSensitive = false;
					caseToggle.checked = false;
				}
				this.pushOptionsToBridge();
			},
		);
		fuzzyToggle = fuzzyRow.querySelector('input') as HTMLInputElement;
		list.appendChild(fuzzyRow);
		const caseRow = this.makeSettingsRow(
			'区分大小写',
			'',
			this.caseSensitive,
			(on) => {
				this.caseSensitive = on;
				if (on && fuzzyToggle) {
					this.ignoreCase = false;
					fuzzyToggle.checked = false;
				}
				this.pushOptionsToBridge();
			},
		);
		caseToggle = caseRow.querySelector('input') as HTMLInputElement;
		list.appendChild(caseRow);
		list.appendChild(
			this.makeSettingsRow('全字匹配', '', this.wholeWord, (on) => {
				this.wholeWord = on;
				this.pushOptionsToBridge();
			}),
		);
		wrap.appendChild(list);
		return wrap;
	}

	private makeTabButton(label: string, isFind: boolean): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		button.className =
			'writer-find-sheet__tab' +
			(isFind ? ' writer-find-sheet__tab--active' : '');
		button.onclick = () => this.setMode(isFind ? 'find' : 'replace');
		return button;
	}

	private makeSearchField(placeholder: string): HTMLInputElement {
		const input = document.createElement('input');
		input.type = 'search';
		input.className = 'writer-find-sheet__field';
		input.placeholder = placeholder;
		input.setAttribute('enterkeyhint', 'search');
		input.addEventListener('keydown', (event) => {
			if (event.key === 'Enter') {
				event.preventDefault();
				const queryField =
					this.mode === 'replace'
						? this.replaceQueryInput
						: this.findQueryInput;
				this.runFindNext(queryField);
			}
		});
		return input;
	}

	private makeNavButton(label: string, handler: () => void): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		button.className = 'writer-find-sheet__nav-btn';
		button.onclick = handler;
		return button;
	}

	private makeSettingsRow(
		title: string,
		subtitle: string,
		initial: boolean,
		onChange: (checked: boolean) => void,
	): HTMLElement {
		const row = document.createElement('div');
		row.className = 'writer-find-sheet__settings-row';
		const text = document.createElement('div');
		text.className = 'writer-find-sheet__settings-text';
		const titleEl = document.createElement('div');
		titleEl.className = 'writer-find-sheet__settings-title';
		titleEl.textContent = title;
		text.appendChild(titleEl);
		if (subtitle) {
			const sub = document.createElement('div');
			sub.className = 'writer-find-sheet__settings-sub';
			sub.textContent = subtitle;
			text.appendChild(sub);
		}
		row.appendChild(text);
		const toggle = document.createElement('input');
		toggle.type = 'checkbox';
		toggle.className = 'writer-find-sheet__toggle';
		toggle.checked = initial;
		toggle.onchange = () => onChange(toggle.checked);
		row.appendChild(toggle);
		return row;
	}

	private setMode(mode: 'find' | 'replace'): void {
		this.mode = mode;
		const findActive = mode === 'find';
		this.findPanel.style.display = findActive ? '' : 'none';
		this.replacePanel.style.display = findActive ? 'none' : '';
		this.tabFindBtn.classList.toggle(
			'writer-find-sheet__tab--active',
			findActive,
		);
		this.tabReplaceBtn.classList.toggle(
			'writer-find-sheet__tab--active',
			!findActive,
		);
		this.refreshButtonStates();
	}

	private showSettings(show: boolean): void {
		if (!this.mainView || !this.settingsView) {
			return;
		}
		this.mainView.style.display = show ? 'none' : '';
		this.settingsView.style.display = show ? '' : 'none';
	}

	private mirrorQuery(
		source: HTMLInputElement,
		target: HTMLInputElement,
	): void {
		if (source.value === target.value) {
			return;
		}
		this.syncingQuery = true;
		try {
			target.value = source.value;
		} finally {
			this.syncingQuery = false;
		}
	}

	private hasQuery(field: HTMLInputElement): boolean {
		return field.value.trim().length > 0;
	}

	private refreshButtonStates(): void {
		const findHas = this.hasQuery(this.findQueryInput);
		const replaceHas =
			this.hasQuery(this.replaceQueryInput) ||
			this.hasQuery(this.findQueryInput);
		this.setNavEnabled(this.findPrevBtn, findHas);
		this.setNavEnabled(this.findNextBtn, findHas);
		this.setNavEnabled(this.replacePrevBtn, replaceHas);
		this.setNavEnabled(this.replaceNextBtn, replaceHas);
		this.setNavEnabled(this.replaceAllBtn, replaceHas);
		this.setNavEnabled(this.replaceOneBtn, replaceHas);
		this.findQueryInput.classList.toggle(
			'writer-find-sheet__field--filled',
			findHas,
		);
		this.replaceQueryInput.classList.toggle(
			'writer-find-sheet__field--filled',
			this.hasQuery(this.replaceQueryInput),
		);
		this.replaceWithInput.classList.toggle(
			'writer-find-sheet__field--filled',
			this.hasQuery(this.replaceWithInput),
		);
	}

	private setNavEnabled(button: HTMLButtonElement, enabled: boolean): void {
		button.disabled = !enabled;
		button.classList.toggle('writer-find-sheet__nav-btn--enabled', enabled);
	}

	private pushOptionsToBridge(): void {
		const bridge = (window as any).AndroidFindReplaceBridge;
		if (!bridge || typeof bridge.setOptions !== 'function') {
			return;
		}
		bridge.setOptions({
			ignoreCase: this.ignoreCase,
			caseSensitive: this.caseSensitive,
			wholeWord: this.wholeWord,
		});
	}

	private runFindNext(field: HTMLInputElement): void {
		const query = field.value.trim();
		if (!query) {
			return;
		}
		this.pushOptionsToBridge();
		const bridge = (window as any).AndroidFindReplaceBridge;
		if (bridge && typeof bridge.find === 'function') {
			bridge.find(query);
		}
	}

	private runFindPrevious(): void {
		const field =
			this.mode === 'replace' ? this.replaceQueryInput : this.findQueryInput;
		if (!this.hasQuery(field)) {
			return;
		}
		this.pushOptionsToBridge();
		const bridge = (window as any).AndroidFindReplaceBridge;
		if (bridge && typeof bridge.findPrevious === 'function') {
			bridge.findPrevious();
		}
	}

	private runReplace(replaceAll: boolean): void {
		const query = this.replaceQueryInput.value.trim();
		if (!query) {
			return;
		}
		this.pushOptionsToBridge();
		const bridge = (window as any).AndroidFindReplaceBridge;
		if (bridge && typeof bridge.replaceForQuery === 'function') {
			bridge.replaceForQuery(query, this.replaceWithInput.value, replaceAll);
		}
	}

	private attachViewportLift(): void {
		const vv = window.visualViewport;
		if (!vv || !this.panel) {
			return;
		}
		const onChange = (): void => {
			if (!this.panel) {
				return;
			}
			const inset = Math.max(0, window.innerHeight - vv.offsetTop - vv.height);
			this.panel.style.marginBottom = inset + 'px';
		};
		this.viewportHandler = onChange;
		vv.addEventListener('resize', onChange);
		vv.addEventListener('scroll', onChange);
		onChange();
	}

	private detachViewportLift(): void {
		const vv = window.visualViewport;
		if (vv && this.viewportHandler) {
			vv.removeEventListener('resize', this.viewportHandler);
			vv.removeEventListener('scroll', this.viewportHandler);
		}
		this.viewportHandler = null;
	}
}

const WRITER_FIND_SETTINGS_ICON =
	'<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" xmlns="http://www.w3.org/2000/svg">' +
	'<path fill="#333" d="M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7zm8.94 4.5a7.9 7.9 0 0 0 .06-.98 7.9 7.9 0 0 0-.06-.98l2.03-1.58a.5.5 0 0 0 .12-.64l-1.92-3.32a.5.5 0 0 0-.6-.22l-2.39.96a7.2 7.2 0 0 0-1.7-.98l-.36-2.54A.5.5 0 0 0 14 2h-4a.5.5 0 0 0-.5.42l-.36 2.54c-.62.25-1.2.58-1.7.98l-2.39-.96a.5.5 0 0 0-.6.22L2.53 8.5a.5.5 0 0 0 .12.64L4.68 10.7a7.9 7.9 0 0 0 0 1.96l-2.03 1.58a.5.5 0 0 0-.12.64l1.92 3.32a.5.5 0 0 0 .6.22l2.39-.96c.5.4 1.08.73 1.7.98l.36 2.54A.5.5 0 0 0 10 22h4a.5.5 0 0 0 .5-.42l.36-2.54c.62-.25 1.2-.58 1.7-.98l2.39.96a.5.5 0 0 0 .6-.22l1.92-3.32a.5.5 0 0 0-.12-.64L20.94 13z"/></svg>';

const WRITER_FIND_BACK_ICON =
	'<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" xmlns="http://www.w3.org/2000/svg">' +
	'<path fill="#333" d="M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>';

if (typeof window !== 'undefined') {
	WriterFindReplaceDialog.mountBridge();
}
