/*
 * In-panel subpage stack for Writer / Impress function sheets (Android option picker).
 * Keeps the main bottom sheet open; back returns to tabs instead of dismissing all.
 */

interface WriterEditorInlineSubpageHost {
	pushSubpage(title: string, body: HTMLElement): void;
	popSubpage(): void;
	clearSubpages(): void;
}

class WriterEditorInlineSubpage implements WriterEditorInlineSubpageHost {
	private layer: HTMLDivElement | null = null;
	private readonly stack: Array<{ title: string; body: HTMLElement }> = [];

	constructor(
		private readonly anchor: HTMLElement,
		private readonly hideWhenActive: HTMLElement[],
	) {
		this.anchor.classList.add('writer-function-subpage-host');
	}

	pushSubpage(title: string, body: HTMLElement): void {
		this.stack.push({ title, body });
		this.applyTop();
	}

	popSubpage(): void {
		if (!this.stack.length) {
			return;
		}
		this.stack.pop();
		if (!this.stack.length) {
			this.teardown();
			return;
		}
		this.applyTop();
	}

	clearSubpages(): void {
		this.stack.length = 0;
		this.teardown();
	}

	private applyTop(): void {
		if (!this.layer) {
			this.layer = document.createElement('div');
			this.layer.className = 'writer-function-inline-subpage';
			this.anchor.classList.add('writer-function-subpage-host--active');
			this.anchor.appendChild(this.layer);
		}
		this.hideWhenActive.forEach((element) => {
			element.classList.add('writer-function-subpage-host__hidden');
		});
		const top = this.stack[this.stack.length - 1];
		this.layer.replaceChildren(this.buildChrome(top.title, top.body));
	}

	private buildChrome(title: string, body: HTMLElement): HTMLElement {
		const frame = document.createElement('div');
		frame.className = 'writer-function-inline-subpage__frame';

		const header = document.createElement('header');
		header.className =
			'writer-function-sheet__header writer-function-sheet__header--subpage';
		const back = document.createElement('button');
		back.type = 'button';
		back.className = 'writer-function-sheet__back';
		back.setAttribute('aria-label', '返回');
		back.innerHTML =
			'<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">' +
			'<path fill="#101010" d="M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z"/></svg>';
		back.onclick = () => this.popSubpage();
		const heading = document.createElement('h2');
		heading.className = 'writer-function-sheet__title';
		heading.textContent = title;
		header.appendChild(back);
		header.appendChild(heading);
		frame.appendChild(header);

		const scroll = document.createElement('div');
		scroll.className =
			'writer-function-inline-subpage__body writer-function-sheet__body writer-function-sheet__body--edit';
		scroll.appendChild(body);
		frame.appendChild(scroll);
		return frame;
	}

	private teardown(): void {
		this.layer?.remove();
		this.layer = null;
		this.anchor.classList.remove('writer-function-subpage-host--active');
		this.hideWhenActive.forEach((element) => {
			element.classList.remove('writer-function-subpage-host__hidden');
		});
	}
}

function writerEditorMountSubpageDialog(
	title: string,
	content: HTMLElement,
	host?: WriterEditorInlineSubpageHost | null,
): { open(): void; close(): void } {
	if (host) {
		return {
			open: () => host.pushSubpage(title, content),
			close: () => host.popSubpage(),
		};
	}
	const sheet = new WriterEditorSheet(title, undefined, { subPage: true });
	sheet.setBody(content);
	return {
		open: () => sheet.open(),
		close: () => sheet.close(),
	};
}
