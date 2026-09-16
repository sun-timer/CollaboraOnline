/*
 * Writer function-panel bottom sheet shell (Figma / PreviewFunctionSheet aligned).
 *
 * White header, WriterAICloseButton-style icon close, 24px top radius,
 * shadow 0 -2 105px rgba(0,0,0,.28). Styles live in writer-mobile.css.
 *
 * Separate from MobileAiSheet so AI panels keep their own chrome (18px radius).
 */

interface WriterEditorSheetOptions {
	/** Edit-mode function panel (Figma 192-5683 / Android lolib_sheet_functions_edit). */
	editMode?: boolean;
	/** Secondary page inside the function panel (back chevron, Android option_picker). */
	subPage?: boolean;
}

class WriterEditorSheet {
	readonly root: HTMLDivElement;
	readonly panel: HTMLDivElement;
	readonly body: HTMLDivElement;
	private readonly title: HTMLElement;
	private readonly closeButton: HTMLButtonElement;
	private readonly onClose?: () => void;

	constructor(
		title: string,
		onClose?: () => void,
		options?: WriterEditorSheetOptions,
	) {
		this.onClose = onClose;
		const editMode = !!options?.editMode;
		const subPage = !!options?.subPage;
		this.root = document.createElement('div');
		this.root.className =
			'writer-function-sheet' +
			(editMode ? ' writer-function-sheet--edit' : '') +
			(subPage ? ' writer-function-sheet--overlay-sub' : '');
		this.root.setAttribute('role', 'presentation');
		this.root.onclick = (event) => {
			if (event.target === this.root) {
				this.close();
			}
		};

		this.panel = document.createElement('div');
		this.panel.className =
			'writer-function-sheet__panel' +
			(editMode ? ' writer-function-sheet__panel--edit' : '');
		this.panel.setAttribute('role', 'dialog');
		this.panel.setAttribute('aria-modal', 'true');
		this.root.appendChild(this.panel);

		if (!editMode) {
			const grabber = document.createElement('div');
			grabber.className = 'writer-function-sheet__grabber';
			grabber.setAttribute('aria-hidden', 'true');
			this.panel.appendChild(grabber);
		}

		const header = document.createElement('header');
		header.className = 'writer-function-sheet__header';
		if (editMode) {
			header.classList.add('writer-function-sheet__header--hidden');
		}
		if (subPage) {
			header.classList.add('writer-function-sheet__header--subpage');
		}
		this.closeButton = document.createElement('button');
		this.closeButton.type = 'button';
		if (subPage) {
			this.closeButton.className = 'writer-function-sheet__back';
			this.closeButton.setAttribute('aria-label', '返回');
			this.closeButton.innerHTML =
				'<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path fill="#101010" d="M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>';
		} else {
			this.closeButton.className = 'writer-function-sheet__close';
			this.closeButton.setAttribute('aria-label', '关闭功能面板');
			const closeIcon = WriterEditorIcons.get('close');
			if (closeIcon) {
				this.closeButton.innerHTML = closeIcon;
			}
		}
		this.closeButton.onclick = () => this.close();
		header.appendChild(this.closeButton);

		this.title = document.createElement('h2');
		this.title.className = 'writer-function-sheet__title';
		this.title.textContent = title;
		header.appendChild(this.title);

		if (!subPage) {
			const headerEnd = document.createElement('span');
			headerEnd.className = 'writer-function-sheet__header-spacer';
			headerEnd.setAttribute('aria-hidden', 'true');
			header.appendChild(headerEnd);
		}
		this.panel.appendChild(header);

		this.body = document.createElement('div');
		this.body.className =
			'writer-function-sheet__body' +
			(editMode ? ' writer-function-sheet__body--edit' : '');
		this.panel.appendChild(this.body);
	}

	setTitle(next: string): void {
		this.title.textContent = next;
	}

	setBody(content: HTMLElement): void {
		this.body.replaceChildren(content);
	}

	open(): void {
		if (!this.root.parentElement) {
			document.body.appendChild(this.root);
		}
		this.closeButton.focus();
	}

	close(): void {
		this.root.remove();
		if (this.onClose) {
			this.onClose();
		}
	}
}
