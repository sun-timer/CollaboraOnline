/*
 * Writer paragraph quick panel (iOS).
 *
 * 左对齐/居中/右对齐/两端对齐/项目符号/编号, mirroring Android
 * BottomToolbarController PARAGRAPH_QUICK_ACTION_ITEMS (L83-90).
 */

interface WriterParagraphQuickItem {
	label: string;
	iconKey: string;
	command: string;
}

class WriterParagraphPanel {
	private readonly sheet: WriterEditorSheet;
	private readonly controller: WriterEditorController;
	/** Toggle buttons keyed by .uno command for activation-state feedback. */
	private readonly toggleButtons: { [command: string]: HTMLButtonElement } = {};
	private onStateBound: ((event: any) => void) | null = null;

	private constructor() {
		this.controller = WriterEditorController.getInstance();
		this.sheet = new WriterEditorSheet('段落');
		this.sheet.setBody(this.buildBody());
	}

	static getQuickActionItems(): WriterParagraphQuickItem[] {
		return [
			{ label: '左对齐', iconKey: 'align-left', command: '.uno:LeftPara' },
			{ label: '居中对齐', iconKey: 'align-center', command: '.uno:CenterPara' },
			{ label: '右对齐', iconKey: 'align-right', command: '.uno:RightPara' },
			{ label: '两端对齐', iconKey: 'align-justify', command: '.uno:JustifyPara' },
			{ label: '项目符号', iconKey: 'bullet-list', command: '.uno:DefaultBullet' },
			{ label: '编号', iconKey: 'numbered-list', command: '.uno:DefaultNumbering' },
		];
	}

	static mount(): WriterParagraphPanel | null {
		if (!(window as any).ThisIsTheiOSApp) {
			return null;
		}
		const existing = (window as any).__coolWriterParaPanel;
		if (existing instanceof WriterParagraphPanel) {
			return existing;
		}
		const panel = new WriterParagraphPanel();
		(window as any).__coolWriterParaPanel = panel;
		return panel;
	}

	open(): void {
		this.closeSiblingPanels();
		this.refreshToggles();
		this.subscribeState();
		this.sheet.open();
	}

	close(): void {
		this.unsubscribeState();
		this.sheet.close();
	}

	private closeSiblingPanels(): void {
		const charPanel = (window as any).__coolWriterCharPanel;
		if (charPanel && typeof charPanel.close === 'function') {
			charPanel.close();
		}
	}

	private buildBody(): HTMLElement {
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;';

		const grid = document.createElement('div');
		grid.style.cssText =
			'display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;';
		WriterParagraphPanel.getQuickActionItems().forEach((item) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.setAttribute('aria-label', item.label);
			button.style.cssText =
				'display:flex;flex-direction:column;align-items:center;justify-content:center;' +
				'gap:6px;min-height:76px;border:1px solid #E6E8EB;border-radius:12px;' +
				'background:#fff;cursor:pointer;';
			const icon = document.createElement('span');
			icon.style.cssText =
				'width:24px;height:24px;display:flex;align-items:center;justify-content:center;';
			icon.innerHTML = WriterEditorIcons.get(item.iconKey);
			button.appendChild(icon);
			const label = document.createElement('span');
			label.textContent = item.label;
			label.style.cssText = 'font-size:12px;color:#101010;';
			button.appendChild(label);
			this.toggleButtons[item.command] = button;
			button.onclick = () => this.run(item.command);
			grid.appendChild(button);
		});
		content.appendChild(grid);
		return content;
	}

	private run(command: string): void {
		this.controller.run({
			id: 'para-quick',
			label: '',
			tab: 'default',
			icon: '',
			kind: 'command',
			unocmd: command,
		});
	}

	private refreshToggles(): void {
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

	private setToggleActive(command: string, active: boolean): void {
		const button = this.toggleButtons[command];
		if (!button) {
			return;
		}
		button.style.background = active ? '#EAF2FF' : '#fff';
		button.style.borderColor = active ? '#1278D9' : '#E6E8EB';
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	const mountPara = () => {
		try {
			WriterParagraphPanel.mount();
		} catch (_e) {
			window.setTimeout(mountPara, 0);
		}
	};
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', mountPara, { once: true });
	} else {
		window.setTimeout(mountPara, 0);
	}
}
