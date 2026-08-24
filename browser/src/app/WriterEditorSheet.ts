/*
 * Writer editor function-panel bottom sheet (Figma: radius 24 top corners,
 * shadow 0 -2 105.4 #00000047, adaptive max-width, safe-area padded).
 *
 * Separate from MobileAiSheet so the function panel can follow its own Figma
 * chrome (24pt radius) without affecting the b0d7fb AI panel (18px).
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
		this.root.setAttribute('role', 'presentation');
		this.root.style.cssText =
			'position:fixed;inset:0;z-index:10001;display:flex;align-items:flex-end;justify-content:center;' +
			'background:rgba(0,0,0,.32);padding-top:env(safe-area-inset-top);';
		this.root.onclick = (event) => {
			if (event.target === this.root) {
				this.close();
			}
		};

		this.panel = document.createElement('div');
		this.panel.setAttribute('role', 'dialog');
		this.panel.setAttribute('aria-modal', 'true');
		this.panel.style.cssText =
			'display:flex;flex-direction:column;width:100%;max-width:min(670px,calc(100vw - 40px));' +
			'max-height:92dvh;margin:0 auto;background:var(--color-background,#fff);color:var(--color-text,#222);' +
			'border-radius:24px 24px 0 0;box-shadow:0 -2px 105px rgba(0,0,0,.28);overflow:hidden;';
		this.root.appendChild(this.panel);

		const header = document.createElement('header');
		header.style.cssText =
			'display:flex;align-items:center;min-height:56px;padding:8px 16px;' +
			'background:linear-gradient(110deg,#f7e6ff,#dff2ff);';
		this.title = document.createElement('strong');
		this.title.textContent = title;
		this.title.style.cssText = 'flex:1;text-align:center;font-size:20px;';
		header.appendChild(this.title);
		this.closeButton = document.createElement('button');
		this.closeButton.type = 'button';
		this.closeButton.textContent = '关闭';
		this.closeButton.setAttribute('aria-label', '关闭功能面板');
		this.closeButton.onclick = () => this.close();
		header.appendChild(this.closeButton);
		this.panel.appendChild(header);

		this.body = document.createElement('div');
		this.body.style.cssText =
			'overflow:auto;padding:12px 16px calc(16px + env(safe-area-inset-bottom));';
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
