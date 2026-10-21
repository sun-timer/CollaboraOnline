/*
 * Writer insert-comment dialog (iOS).
 *
 * Avatar + multiline input + cancel/save, mirroring Android
 * ImpressCommentPickerController. Dispatches InsertAnnotation with Author/Text.
 */

class WriterEditorCommentDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly controller: WriterEditorController;
	private readonly input: HTMLTextAreaElement;
	private authorNameEl!: HTMLSpanElement;

	private readonly onInserted?: () => void;

	constructor(
		controller: WriterEditorController,
		host?: WriterEditorInlineSubpageHost | null,
		onInserted?: () => void,
	) {
		this.controller = controller;
		this.onInserted = onInserted;

		const content = document.createElement('div');
		content.className = 'writer-comment-sheet';

		const card = document.createElement('div');
		card.className = 'writer-comment-card';
		card.appendChild(this.buildAuthorRow());

		this.input = document.createElement('textarea');
		this.input.className = 'writer-comment-input';
		this.input.placeholder = '描述内容';
		this.input.setAttribute('aria-label', '批注内容');
		card.appendChild(this.input);
		content.appendChild(card);

		const actions = document.createElement('div');
		actions.className = 'writer-comment-actions';

		const cancel = document.createElement('button');
		cancel.type = 'button';
		cancel.className = 'writer-comment-btn writer-comment-btn--cancel';
		cancel.textContent = '取消';
		cancel.setAttribute('aria-label', '取消');
		cancel.onclick = () => this.subpage.close();
		actions.appendChild(cancel);

		const save = document.createElement('button');
		save.type = 'button';
		save.className = 'writer-comment-btn writer-comment-btn--save';
		save.textContent = '保存';
		save.setAttribute('aria-label', '保存批注');
		save.onclick = () => this.save();
		actions.appendChild(save);

		content.appendChild(actions);
		this.subpage = writerEditorMountSubpageDialog('批注', content, host);
	}

	open(): void {
		this.input.value = '';
		this.authorNameEl.textContent = this.resolveAuthorName();
		this.subpage.open();
		this.input.focus();
	}

	close(): void {
		this.subpage.close();
	}

	private save(): void {
		const text = this.input.value.trim();
		if (!text) {
			return;
		}
		this.controller.insertComment(text, this.resolveAuthorName());
		this.subpage.close();
		if (this.onInserted) {
			this.onInserted();
		}
	}

	private buildAuthorRow(): HTMLElement {
		const row = document.createElement('div');
		row.className = 'writer-comment-author';

		const avatar = document.createElement('span');
		avatar.className = 'writer-comment-author__avatar';
		const icon = WriterEditorIcons.get('comment');
		if (icon) {
			avatar.innerHTML = icon;
		}
		row.appendChild(avatar);

		const meta = document.createElement('div');
		meta.className = 'writer-comment-author__meta';

		const name = document.createElement('span');
		name.className = 'writer-comment-author__name';
		name.textContent = this.resolveAuthorName();
		this.authorNameEl = name;
		meta.appendChild(name);

		const date = document.createElement('span');
		date.className = 'writer-comment-author__date';
		date.textContent = WriterEditorCommentDialog.formatDate(new Date());
		meta.appendChild(date);

		row.appendChild(meta);
		return row;
	}

	private resolveAuthorName(): string {
		return WriterEditorCommentDialog.resolveAuthorNameFrom(window);
	}

	/**
	 * Author precedence:
	 * 1. window.__coolIOSUserName: the Settings nickname (USER_PROFILE_NAME) that
	 *    the iOS shell injects on document ready and re-injects when it is saved.
	 *    A non-empty settings name is never replaced by the core view name.
	 * 2. The core view name of this view (set at document load; can be stale).
	 * 3. The placeholder.
	 *
	 * LocalUser#N is a LOK placeholder, not a nickname. It never wins over a
	 * settings name and is not shown as a fallback.
	 */
	static isGeneratedLocalUserName(name: string): boolean {
		return /^LocalUser#\d+$/.test(name);
	}

	static resolveAuthorNameFrom(win: any): string {
		const settingsName =
			win && typeof win.__coolIOSUserName === 'string' ? win.__coolIOSUserName.trim() : '';
		if (settingsName && !WriterEditorCommentDialog.isGeneratedLocalUserName(settingsName)) {
			return settingsName;
		}

		const map = win && win.app && win.app.map;
		const docLayer = map && map._docLayer;
		const viewId = docLayer ? docLayer._viewId : undefined;
		const name =
			map && typeof map.getViewName === 'function' && Number.isInteger(viewId)
				? map.getViewName(viewId)
				: '';
		const username = typeof name === 'string' ? name.trim() : '';
		if (username && !WriterEditorCommentDialog.isGeneratedLocalUserName(username)) {
			return username;
		}
		return '用户昵称';
	}

	private static formatDate(date: Date): string {
		const pad = (value: number) => (value < 10 ? '0' + value : String(value));
		return (
			date.getFullYear() +
			'-' +
			pad(date.getMonth() + 1) +
			'-' +
			pad(date.getDate()) +
			' ' +
			pad(date.getHours()) +
			':' +
			pad(date.getMinutes())
		);
	}
}
