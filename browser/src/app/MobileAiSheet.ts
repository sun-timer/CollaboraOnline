/*
 * Shared Browser BottomSheet-like surface for mobile AI UI.
 */

type MobileAiSheetPresentation = 'bottom' | 'writer';

interface MobileAiSheetOptions {
	title: string;
	/** Android WAI 居中圆角卡；默认 bottom 为 AI 助手/功能列表底 sheet */
	presentation?: MobileAiSheetPresentation;
	/** writer 头图（MobileAiOperationIcons），与 Android 40dp 任务图标一致 */
	taskType?: string;
	onClose?: () => void;
}

/** Android lolib_ic_ai_assistant_logo (32dp, viewport 64). */
const MOBILE_AI_LOGO_SVG =
	'<svg viewBox="0 0 64 64" width="28" height="28" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
	'<defs><linearGradient id="aiLogoGrad" x1="7.529" y1="16.941" x2="68.58" y2="30.29" gradientUnits="userSpaceOnUse">' +
	'<stop offset="0" stop-color="#1278d9"/><stop offset="1" stop-color="#30d83c"/></linearGradient></defs>' +
	'<path fill="url(#aiLogoGrad)" fill-rule="evenodd" d="M37.592,13.08C43.192,11.075 51.3,12.708 55.193,14.521C60.654,17.064 64.538,20.885 63.832,25.365C62.95,30.965 57.233,31.285 52.952,31.285C46.818,31.285 39.243,28.477 37.673,27.869C37.808,27.564 37.945,27.266 38.083,26.974C39.054,24.92 40.061,23.304 41.326,21.991C46.184,24.02 50.919,25.045 55.993,25.045C59.352,25.045 60.763,22.892 59.993,20.726C58.341,16.086 47.189,15.995 43.193,18.325C36.9,21.995 35.672,28.405 31.033,40.245C27.131,50.2 17.112,53.484 9.432,50.646C2.072,47.925 -0.248,40.813 0.433,34.755C1.22,27.755 4.9,23.606 9.432,20.886C16.604,16.582 27.765,18.167 28.68,18.306C28.59,18.441 28.496,18.578 28.405,18.719C27.524,20.071 26.582,21.678 25.514,23.599C24.892,23.545 20.754,23.211 18.073,23.606C11.62,24.555 7.244,27.136 5.432,32.245C3.62,37.355 7.353,41.525 12.153,41.045C18.712,40.389 22.003,33.775 25.592,27.125C31.032,17.045 32.9,14.76 37.592,13.08Z"/>' +
	'<path fill="none" stroke="#ec5d1f" stroke-width="3.2" d="M38.019,36.837H60.099"/>' +
	'<path fill="none" stroke="#ec5d1f" stroke-width="3.2" d="M35.46,42.917H47.3"/>' +
	'<path fill="none" stroke="#ec5d1f" stroke-width="3.2" d="M31.94,48.998H62.98"/>' +
	'</svg>';

/** Android lolib_ic_dialog_close (viewport 48). */
const MOBILE_AI_CLOSE_SVG =
	'<svg viewBox="0 0 48 48" width="24" height="24" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
	'<path fill="rgba(0,0,0,0.9)" d="M31.56,8.56C31.84,8.24 31.98,7.88 31.98,7.48C31.98,7.08 31.84,6.733 31.56,6.44C31.266,6.12 30.913,5.96 30.5,5.96C30.086,5.96 29.733,6.12 29.44,6.44L16,19.88L2.56,6.44C2.266,6.12 1.913,5.96 1.5,5.96C1.086,5.96 0.72,6.12 0.4,6.44C0.146,6.733 0.02,7.08 0.02,7.48C0.02,7.88 0.146,8.24 0.4,8.56L13.88,22L0.4,35.4C0.146,35.72 0.02,36.087 0.02,36.5C0.02,36.913 0.146,37.267 0.4,37.56C0.72,37.813 1.086,37.947 1.5,37.96C1.913,37.973 2.266,37.84 2.56,37.56L16,24.12L29.44,37.56C29.733,37.84 30.086,37.98 30.5,37.98C30.913,37.98 31.266,37.84 31.56,37.56C31.84,37.267 31.98,36.913 31.98,36.5C31.98,36.087 31.84,35.72 31.56,35.4L18.12,22L31.56,8.56Z"/>' +
	'</svg>';

class MobileAiSheet {
	readonly root: HTMLDivElement;
	readonly panel: HTMLDivElement;
	readonly body: HTMLDivElement;
	private readonly title: HTMLElement;
	readonly closeButton: HTMLButtonElement;
	private readonly onClose?: () => void;

	constructor(options: MobileAiSheetOptions) {
		this.onClose = options.onClose;
		const writer = options.presentation === 'writer';
		this.root = document.createElement('div');
		this.root.setAttribute('role', 'presentation');
		this.root.setAttribute('data-cool-mobile-ai-ui', '1');
		this.root.className =
			'mobile-ai-sheet-root' + (writer ? ' mobile-ai-sheet-root--writer' : '');
		this.root.onclick = (event) => {
			if (event.target === this.root) {
				this.close();
			}
		};

		this.panel = document.createElement('div');
		this.panel.setAttribute('role', 'dialog');
		this.panel.setAttribute('aria-modal', 'true');
		this.panel.className =
			'mobile-ai-sheet-panel' +
			(writer ? ' mobile-ai-sheet-panel--writer' : '');
		this.panel.addEventListener('mousedown', (event) => event.stopPropagation());
		this.panel.addEventListener('touchstart', (event) => event.stopPropagation(), {
			passive: true,
		});
		this.root.appendChild(this.panel);

		const header = document.createElement('header');
		header.className =
			'mobile-ai-sheet-header' +
			(writer ? ' mobile-ai-sheet-header--writer' : '');

		const headerLeading = document.createElement('span');
		headerLeading.className = 'mobile-ai-sheet-header-leading';
		headerLeading.setAttribute('aria-hidden', 'true');
		if (writer && options.taskType) {
			headerLeading.innerHTML = MobileAiOperationIcons.svgFor(options.taskType);
		}
		header.appendChild(headerLeading);

		const titleRow = document.createElement('div');
		titleRow.className = 'mobile-ai-sheet-title-row';
		if (!writer) {
			const logo = document.createElement('span');
			logo.className = 'mobile-ai-sheet-logo';
			logo.innerHTML = MOBILE_AI_LOGO_SVG;
			titleRow.appendChild(logo);
		}
		this.title = document.createElement('span');
		this.title.className = 'mobile-ai-sheet-title';
		this.title.textContent = options.title;
		titleRow.appendChild(this.title);
		header.appendChild(titleRow);

		this.closeButton = document.createElement('button');
		this.closeButton.type = 'button';
		this.closeButton.className = 'mobile-ai-sheet-close';
		this.closeButton.innerHTML = MOBILE_AI_CLOSE_SVG;
		this.closeButton.setAttribute('aria-label', '关闭');
		this.closeButton.tabIndex = -1;
		this.closeButton.onclick = () => this.close();
		header.appendChild(this.closeButton);
		this.panel.appendChild(header);

		this.body = document.createElement('div');
		this.body.className =
			'mobile-ai-sheet-body' +
			(writer ? ' mobile-ai-sheet-body--writer' : '');
		this.panel.appendChild(this.body);
	}

	setTitle(title: string): void {
		this.title.textContent = title;
	}

	setBody(content: HTMLElement): void {
		this.body.replaceChildren(content);
	}

	open(focusElement?: HTMLElement | null): void {
		if (!this.root.parentElement) {
			document.body.appendChild(this.root);
		}
		if (!focusElement) {
			return;
		}
		focusElement.focus();
		window.setTimeout(() => {
			if (this.root.parentElement && document.activeElement !== focusElement) {
				focusElement.focus();
			}
		}, 0);
	}

	close(): void {
		const active = document.activeElement as HTMLElement | null;
		if (active && this.root.contains(active) && typeof active.blur === 'function') {
			active.blur();
		}
		this.root.remove();
		if (this.onClose) {
			this.onClose();
		}
	}
}
