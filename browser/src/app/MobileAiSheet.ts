/*
 * Shared Browser BottomSheet-like surface for mobile AI UI.
 */

interface MobileAiSheetOptions {
	title: string;
	onClose?: () => void;
}

class MobileAiSheet {
	readonly root: HTMLDivElement;
	readonly panel: HTMLDivElement;
	readonly body: HTMLDivElement;
	private readonly title: HTMLElement;
	private readonly closeButton: HTMLButtonElement;
	private readonly onClose?: () => void;

	constructor(options: MobileAiSheetOptions) {
		this.onClose = options.onClose;
		this.root = document.createElement('div');
		this.root.setAttribute('role', 'presentation');
		this.root.style.cssText =
			'position:fixed;inset:0;z-index:10000;display:flex;align-items:flex-end;' +
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
			'display:flex;flex-direction:column;width:100%;max-width:670px;max-height:92dvh;' +
			'margin:0 auto;background:var(--color-background,#fff);color:var(--color-text,#222);' +
			'border-radius:18px 18px 0 0;box-shadow:0 -6px 24px rgba(0,0,0,.24);' +
			'overflow:hidden;';
		this.root.appendChild(this.panel);

		const header = document.createElement('header');
		header.style.cssText =
			'display:flex;align-items:center;min-height:56px;padding:8px 16px;' +
			'background:linear-gradient(110deg,#c7f3ff,#f1d9ff);';
		const logo = document.createElement('span');
		logo.textContent = 'AI';
		logo.setAttribute('aria-hidden', 'true');
		logo.style.cssText = 'font-size:22px;margin-right:8px;';
		header.appendChild(logo);
		this.title = document.createElement('strong');
		this.title.textContent = options.title;
		this.title.style.cssText = 'flex:1;text-align:center;font-size:20px;';
		header.appendChild(this.title);
		this.closeButton = document.createElement('button');
		this.closeButton.type = 'button';
		this.closeButton.textContent = '关闭';
		this.closeButton.setAttribute('aria-label', '关闭 AI 面板');
		this.closeButton.onclick = () => this.close();
		header.appendChild(this.closeButton);
		this.panel.appendChild(header);

		this.body = document.createElement('div');
		this.body.style.cssText =
			'overflow:auto;padding:12px 16px calc(16px + env(safe-area-inset-bottom));';
		this.panel.appendChild(this.body);
	}

	setTitle(title: string): void {
		this.title.textContent = title;
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
