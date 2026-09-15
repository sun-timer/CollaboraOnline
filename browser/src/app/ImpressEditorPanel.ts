/*
 * iOS Impress edit-mode function panel: 常用 / 文件 / 插入 / 切换 / 布局 / 审阅.
 * Reuses WriterEditorSheet chrome/tokens. Does not open PreviewFunctionSheet.
 */

class ImpressEditorPanel {
	private readonly sheet: WriterEditorSheet;
	private readonly tabBar: HTMLDivElement;
	private readonly grid: HTMLDivElement;
	private activeTab: ImpressEditorTab = 'default';
	private readonly subDialogs: { close(): void }[] = [];

	private constructor() {
		this.sheet = new WriterEditorSheet('功能');
		const content = document.createElement('div');
		content.className = 'writer-function-panel';
		this.tabBar = document.createElement('div');
		this.tabBar.className = 'writer-function-tab-bar';
		content.appendChild(this.tabBar);
		this.grid = document.createElement('div');
		this.grid.className = 'writer-function-grid';
		content.appendChild(this.grid);
		this.sheet.setBody(content);
	}

	static mount(): ImpressEditorPanel | null {
		if (!(window as any).ThisIsTheiOSApp) {
			return null;
		}
		const existing = (window as any).__coolImpressEditorPanel;
		if (existing instanceof ImpressEditorPanel) {
			return existing;
		}
		const panel = new ImpressEditorPanel();
		(window as any).__coolImpressEditorPanel = panel;
		return panel;
	}

	open(): void {
		this.renderTabs();
		this.renderGrid();
		this.sheet.open();
	}

	close(): void {
		const children = this.subDialogs.splice(0);
		children.forEach((dialog) => dialog.close());
		this.sheet.close();
	}

	private presentSub(dialog: { open(): void; close(): void }): void {
		if (this.sheet.root.parentElement) {
			this.sheet.close();
		}
		this.subDialogs.push(dialog);
		dialog.open();
	}

	private renderTabs(): void {
		this.tabBar.replaceChildren();
		const track = document.createElement('div');
		track.className = 'writer-function-tab-track';
		const scroll = document.createElement('div');
		scroll.className = 'writer-function-tab-scroll';
		ImpressEditorCatalog.TABS.forEach((tab) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.textContent = tab.label;
			button.setAttribute('aria-label', tab.label);
			const active = tab.id === this.activeTab;
			button.className =
				'writer-function-tab' + (active ? ' writer-function-tab--active' : '');
			button.onclick = () => {
				this.activeTab = tab.id;
				this.renderTabs();
				this.renderGrid();
			};
			scroll.appendChild(button);
		});
		track.appendChild(scroll);
		this.tabBar.appendChild(track);
	}

	private renderGrid(): void {
		this.grid.replaceChildren();
		this.grid.className =
			this.activeTab === 'layout' || this.activeTab === 'transition'
				? 'writer-function-grid writer-function-grid--common'
				: 'writer-function-grid';
		const features = ImpressEditorCatalog.getFeatures(this.activeTab);
		features.forEach((feature) => {
			if (feature.kind === 'section') {
				const title = document.createElement('h3');
				title.textContent = feature.label;
				title.className = 'writer-function-section-title';
				this.grid.appendChild(title);
				return;
			}
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-function-tile';
			button.setAttribute('aria-label', feature.label);
			const icon = WriterEditorIcons.get(feature.icon);
			if (icon) {
				const iconWrap = document.createElement('span');
				iconWrap.className = 'writer-function-tile__icon';
				iconWrap.innerHTML = icon;
				button.appendChild(iconWrap);
			}
			const tileLabel = document.createElement('span');
			tileLabel.textContent = feature.label;
			tileLabel.className = 'writer-function-tile__label';
			button.appendChild(tileLabel);
			button.onclick = () => this.onFeature(feature);
			this.grid.appendChild(button);
		});
	}

	private onFeature(feature: ImpressEditorFeature): void {
		if (feature.kind === 'dialog') {
			if (feature.dialog === 'image') {
				this.presentSub(new WriterEditorImageDialog(WriterEditorController.getInstance()));
			} else if (feature.dialog === 'table') {
				this.presentSub(new WriterEditorInsertTableDialog(WriterEditorController.getInstance()));
			} else if (feature.dialog === 'shape') {
				this.presentSub(new WriterEditorShapeDialog(WriterEditorController.getInstance()));
			} else if (feature.dialog === 'comment') {
				this.presentSub(new WriterEditorCommentDialog(WriterEditorController.getInstance()));
			} else if (feature.dialog === 'hyperlink') {
				this.presentSub(new ImpressEditorHyperlinkDialog(WriterEditorController.getInstance()));
			} else if (feature.dialog === 'saveAs') {
				this.openSaveAsDialog();
			}
			return;
		}
		if (feature.kind === 'findReplace') {
			this.presentSub(new WriterFindReplaceDialog(WriterEditorController.getInstance()));
			return;
		}
		if (feature.kind === 'save') {
			this.sendUno('.uno:Save');
			this.sheet.close();
			return;
		}
		if (feature.kind === 'export') {
			this.openExportDialog();
			return;
		}
		if (feature.kind === 'print') {
			if (typeof (window as any).postMobileMessage === 'function') {
				(window as any).postMobileMessage('PRINT');
			}
			this.sheet.close();
			return;
		}
		if (feature.kind === 'queryCommand' && feature.unocmd) {
			this.sendUno(feature.unocmd + (feature.queryParams || ''));
			this.sheet.close();
			return;
		}
		if (feature.tab === 'transition') {
			this.applyTransition(feature.iconViewIndex || 0);
			return;
		}
		if (feature.kind === 'command' && feature.unocmd) {
			this.sendUno(feature.unocmd);
			this.sheet.close();
		}
	}

	private sendUno(command: string): void {
		const map = (window as any).app?.map;
		if (map && typeof map.sendUnoCommand === 'function') {
			map.sendUnoCommand(command);
			return;
		}
		const socket = (window as any).app?.socket;
		if (socket && typeof socket.sendMessage === 'function') {
			socket.sendMessage('uno ' + command);
		}
	}

	/** Android ImpressTransitionApplier: Sidebar iconview select+activate. */
	private applyTransition(iconViewIndex: number): void {
		const socket = (window as any).app?.socket;
		if (!socket || typeof socket.sendMessage !== 'function') {
			return;
		}
		socket.sendMessage('uno .uno:SidebarShow');
		socket.sendMessage('uno .uno:SlideChangeWindow');
		const map = (window as any).app?.map;
		if (map && map.sidebar && typeof map.sidebar.setupTargetDeck === 'function') {
			try {
				map.sidebar.setupTargetDeck('.uno:SlideChangeWindow');
			} catch (_err) {
				// Deck may be unavailable on first open; dialogevent still reaches Core.
			}
		}
		let windowId = -1;
		if ((window as any).sidebarId !== undefined && (window as any).sidebarId !== null) {
			windowId = (window as any).sidebarId;
		}
		const send = (cmd: string, data: string) => {
			socket.sendMessage(
				'dialogevent ' +
					windowId +
					' {"id":"transitions_icons", "cmd": "' +
					cmd +
					'", "data": "' +
					data +
					'", "type": "iconview"}',
			);
		};
		send('select', String(iconViewIndex));
		send('activate', String(iconViewIndex));
	}

	private openSaveAsDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: 'ODF 演示文稿 (.odp)', value: 'odp' },
			{ label: 'PowerPoint (.pptx)', value: 'pptx' },
			{ label: 'PDF (.pdf)', value: 'pdf' },
		];
		this.presentSub(new WriterEditorChooseDialog('另存为', options, (option) => {
			if (typeof (window as any).postMobileMessage === 'function') {
				(window as any).postMobileMessage(
					'downloadas name=export.' + option.value + ' format=' + option.value,
				);
			}
		}));
	}

	private openExportDialog(): void {
		const options: WriterChooseOption[] = [
			{ label: 'PDF (.pdf)', value: 'pdf' },
			{ label: 'ODF 演示文稿 (.odp)', value: 'odp' },
			{ label: 'PowerPoint (.pptx)', value: 'pptx' },
		];
		this.presentSub(new WriterEditorChooseDialog('导出为', options, (option) => {
			if (typeof (window as any).postMobileMessage === 'function') {
				(window as any).postMobileMessage(
					'downloadas name=export.' + option.value + ' format=' + option.value,
				);
			}
		}));
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	const mount = () => {
		if (ImpressEditorPanel.mount()) {
			return;
		}
		window.setTimeout(mount, 250);
	};
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', mount, { once: true });
	} else {
		mount();
	}
}
