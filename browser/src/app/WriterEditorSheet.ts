/*
 * Writer function-panel bottom sheet shell (Figma / PreviewFunctionSheet aligned).
 *
 * White header, WriterAICloseButton-style icon close, 24px top radius,
 * shadow 0 -2 105px rgba(0,0,0,.28). Styles live in writer-mobile.css.
 *
 * Separate from MobileAiSheet so AI panels keep their own chrome (18px radius).
 */

class WriterEditorSheet {
	readonly root: HTMLDivElement;
	readonly panel: HTMLDivElement;
	readonly body: HTMLDivElement;
	private readonly title: HTMLElement;
	private readonly closeButton: HTMLButtonElement;
	private readonly onClose?: () => void;

	constructor(title: string, onClose?: () => void) {
		this.onClose = onClose;
		this.root = document.createElement('div');
		this.root.className = 'writer-function-sheet';
		this.root.setAttribute('role', 'presentation');
		this.root.onclick = (event) => {
			if (event.target === this.root) {
				this.close();
			}
		};

		this.panel = document.createElement('div');
		this.panel.className = 'writer-function-sheet__panel';
		this.panel.setAttribute('role', 'dialog');
		this.panel.setAttribute('aria-modal', 'true');
		this.root.appendChild(this.panel);

		const grabber = document.createElement('div');
		grabber.className = 'writer-function-sheet__grabber';
		grabber.setAttribute('aria-hidden', 'true');
		this.panel.appendChild(grabber);

		const header = document.createElement('header');
		header.className = 'writer-function-sheet__header';
		this.title = document.createElement('h2');
		this.title.className = 'writer-function-sheet__title';
		this.title.textContent = title;
		header.appendChild(this.title);

		this.closeButton = document.createElement('button');
		this.closeButton.type = 'button';
		this.closeButton.className = 'writer-function-sheet__close';
		this.closeButton.setAttribute('aria-label', '关闭功能面板');
		const closeIcon = WriterEditorIcons.get('close');
		if (closeIcon) {
			this.closeButton.innerHTML = closeIcon;
		}
		this.closeButton.onclick = () => this.close();
		header.appendChild(this.closeButton);
		this.panel.appendChild(header);

		this.body = document.createElement('div');
		this.body.className = 'writer-function-sheet__body';
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
