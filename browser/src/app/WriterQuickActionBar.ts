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
	onTap: () => void;
}

class WriterQuickActionBar {
	private readonly root: HTMLDivElement;
	private readonly scroll: HTMLDivElement;
	private readonly row: HTMLDivElement;
	private readonly toggleButtons: { [command: string]: HTMLButtonElement } = {};
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
		Object.keys(this.toggleButtons).forEach((key) => delete this.toggleButtons[key]);
		items.forEach((item) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-quick-action-bar__button';
			button.setAttribute('aria-label', item.label);
			const icon = document.createElement('span');
			icon.className = 'writer-quick-action-bar__icon';
			icon.innerHTML = item.iconHtml;
			button.appendChild(icon);
			button.onclick = item.onTap;
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
			const active = handler.getItemValue(command) === 'true';
			this.setToggleActive(command, active);
		});
	}

	setToggleActive(command: string, active: boolean): void {
		const button = this.toggleButtons[command];
		if (!button) {
			return;
		}
		button.classList.toggle('writer-quick-action-bar__button--active', active);
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
			if (!event || typeof event.commandName !== 'string') {
				return;
			}
			if (event.commandName in this.toggleButtons) {
				this.setToggleActive(event.commandName, event.state === 'true');
			}
		};
		this.onStateBound = onState;
		map.on('commandstatechanged', onState);
	}

	private unsubscribeState(): void {
		if (!this.onStateBound) {
			return;
		}
		const map = (window as any).app && (window as any).app.map;
		if (map && typeof map.off === 'function') {
			map.off('commandstatechanged', this.onStateBound);
		}
		this.onStateBound = null;
	}
}
