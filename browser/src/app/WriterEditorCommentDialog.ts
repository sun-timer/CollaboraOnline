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

	constructor(
		controller: WriterEditorController,
		host?: WriterEditorInlineSubpageHost | null,
	) {
		this.controller = controller;

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
		meta.appendChild(name);

		const date = document.createElement('span');
		date.className = 'writer-comment-author__date';
		date.textContent = WriterEditorCommentDialog.formatDate(new Date());
		meta.appendChild(date);

		row.appendChild(meta);
		return row;
	}

	private resolveAuthorName(): string {
		const map = (window as any).app && (window as any).app.map;
		const viewId = map && map._viewId;
		const info = viewId != null && map._viewInfo ? map._viewInfo[viewId] : null;
		const username = info && info.username ? String(info.username).trim() : '';
		return username || '用户昵称';
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
