/*
 * Writer character/paragraph quick-action floating bar (iOS).
 *
 * Mirrors Android BottomToolbarController quick panel: anchored above the
 * native bottom toolbar, horizontal scroll, ~56dp tall, white rounded shadow.
 */

interface WriterQuickActionSpec {
	label: string;
	iconHtml: string;
	command?: string;
	colorCommand?: string;
	onTap: () => void;
}

class WriterQuickActionBar {
	private readonly root: HTMLDivElement;
	private readonly scroll: HTMLDivElement;
	private readonly row: HTMLDivElement;
	private readonly toggleButtons: { [command: string]: HTMLButtonElement } = {};
	private readonly colorIcons: { [command: string]: HTMLElement } = {};
	private onStateBound: ((event: any) => void) | null = null;

	constructor() {
		this.root = document.createElement('div');
		this.root.className = 'writer-quick-action-bar';
		this.root.setAttribute('role', 'toolbar');

		this.scroll = document.createElement('div');
		this.scroll.className = 'writer-quick-action-bar__scroll';
		this.row = document.createElement('div');
		this.row.className = 'writer-quick-action-bar__row';
		this.scroll.appendChild(this.row);
		this.root.appendChild(this.scroll);
	}

	static closeAll(): void {
		const charPanel = (window as any).__coolWriterCharPanel;
		if (charPanel && typeof charPanel.close === 'function') {
			charPanel.close();
		}
		const paraPanel = (window as any).__coolWriterParaPanel;
		if (paraPanel && typeof paraPanel.close === 'function') {
			paraPanel.close();
		}
	}

	setActions(items: WriterQuickActionSpec[]): void {
		this.row.replaceChildren();
		Object.keys(this.toggleButtons).forEach(
			(key) => delete this.toggleButtons[key],
		);
		Object.keys(this.colorIcons).forEach((key) => delete this.colorIcons[key]);
		items.forEach((item) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-quick-action-bar__button';
			button.setAttribute('aria-label', item.label);
			const icon = document.createElement('span');
			icon.className = 'writer-quick-action-bar__icon';
			icon.innerHTML = item.iconHtml;
			button.appendChild(icon);
			if (item.colorCommand) this.colorIcons[item.colorCommand] = icon;
			button.onmousedown = (event) => event.preventDefault();
			button.onclick = () => {
				const map = (window as any).app?.map;
				if (map?.getDocType?.() === 'presentation') map.focus(false);
				item.onTap();
			};
			if (item.command) {
				this.toggleButtons[item.command] = button;
			}
			this.row.appendChild(button);
		});
	}

	open(): void {
		WriterQuickActionBar.closeSiblingChrome();
		if (!this.root.parentElement) {
			document.body.appendChild(this.root);
		}
		this.refreshToggles();
		this.subscribeState();
	}

	close(): void {
		this.unsubscribeState();
		this.root.remove();
	}

	isOpen(): boolean {
		return !!this.root.parentElement;
	}

	refreshToggles(): void {
		const map = (window as any).app && (window as any).app.map;
		const handler = map && map.stateChangeHandler;
		if (!handler) {
			return;
		}
		Object.keys(this.toggleButtons).forEach((command) => {
			const state = handler.getItemValue(command);
			const active = state === 'true' || (CalcEditorFormat.isActive() && state === true);
			this.setToggleActive(command, active);
			if (CalcEditorFormat.isActive()) {
				this.toggleButtons[command].disabled = state === 'disabled';
				if (state !== true && state !== false && state !== 'true' && state !== 'false') {
					this.toggleButtons[command].setAttribute('aria-pressed', 'mixed');
				}
			}
		});
		this.refreshCalcColors();
	}

	private refreshCalcColors(): void {
		if (!CalcEditorFormat.isActive()) return;
		Object.keys(this.colorIcons).forEach((command) => {
			let color = CalcEditorFormat.color(command);
			if (command === '.uno:CharBackColor') {
				color = CalcEditorFormat.highlightColor();
			}
			const icon = this.colorIcons[command];
			const marker = command === '.uno:Color' ? icon.querySelector('rect') : icon.querySelector('path[fill]');
			marker?.setAttribute('fill', command === '.uno:Color'
				? CalcEditorFormat.fontColorCss(color) : CalcEditorFormat.colorCss(color));
			icon.classList.toggle('calc-format-state-unknown', color === null);
		});
	}

	setToggleActive(command: string, active: boolean): void {
		const button = this.toggleButtons[command];
		if (!button) {
			return;
		}
		button.classList.toggle('writer-quick-action-bar__button--active', active);
		if (CalcEditorFormat.isActive()) button.setAttribute('aria-pressed', String(active));
	}

	private static closeSiblingChrome(): void {
		const menu = (window as any).__coolMobileSelectionMenu;
		if (menu && typeof menu.hide === 'function') {
			menu.hide();
		}
		const charPanel = (window as any).__coolWriterCharPanel;
		const paraPanel = (window as any).__coolWriterParaPanel;
		if (charPanel && typeof charPanel.close === 'function') {
			charPanel.close();
		}
		if (paraPanel && typeof paraPanel.close === 'function') {
			paraPanel.close();
		}
		const editorPanel = (window as any).__coolWriterEditorPanel;
		if (editorPanel && typeof editorPanel.close === 'function') {
			editorPanel.close();
		}
		if (CalcEditorFormat.isActive()) {
			(window as any).__coolCalcEditorPanel?.close();
		}
	}

	private subscribeState(): void {
		if (this.onStateBound) {
			return;
		}
		const map = (window as any).app && (window as any).app.map;
		if (!map || typeof map.on !== 'function') {
			return;
		}
		const onState = (event: any) => {
			if (CalcEditorFormat.isActive()) this.refreshCalcColors();
			if (!event || typeof event.commandName !== 'string') {
				return;
			}
			if (event.commandName in this.toggleButtons) {
				this.setToggleActive(event.commandName, event.state === 'true' ||
					(CalcEditorFormat.isActive() && event.state === true));
				if (CalcEditorFormat.isActive()) {
					this.toggleButtons[event.commandName].disabled = event.state === 'disabled';
					if (event.state !== true && event.state !== false && event.state !== 'true' && event.state !== 'false') {
						this.toggleButtons[event.commandName].setAttribute('aria-pressed', 'mixed');
					}
				}
			}
		};
		this.onStateBound = onState;
		map.on('commandstatechanged', onState);
		if (CalcEditorFormat.isActive()) map.on('calctextformatresult', onState);
	}

	private unsubscribeState(): void {
		if (!this.onStateBound) {
			return;
		}
		const map = (window as any).app && (window as any).app.map;
		if (map && typeof map.off === 'function') {
			map.off('commandstatechanged', this.onStateBound);
			map.off('calctextformatresult', this.onStateBound);
		}
		this.onStateBound = null;
	}
}
