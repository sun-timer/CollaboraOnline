/*
 * iOS HTML selection menu.
 *
 * Renders the text-selection action bar for iOS (Android keeps its native
 * popup). It subscribes to the fabric selection events broadcast by the
 * shared AndroidSelectionMenu gesture state machine and routes taps into the
 * existing WriterAiPanel AI dialogs, so no new request plumbing is needed.
 */

class MobileSelectionMenu {
	static readonly MENU_CLASS = 'fabric-selection-menu';

	private container: HTMLDivElement | null = null;
	private readonly onShow: (event: Event) => void;
	private readonly onHide: (event: Event) => void;

	constructor() {
		this.onShow = (event: Event) => this.show(event as CustomEvent);
		this.onHide = () => this.hide();
		window.addEventListener(MobileSelectionEvents.SHOW_EVENT, this.onShow);
		window.addEventListener(MobileSelectionEvents.HIDE_EVENT, this.onHide);
	}

	/** 选区型 + iOS 可执行 + 操作单内的 AI 入口（本地批处理工具除外）。 */
	static menuTaskTypes(): string[] {
		return MobileAiUiCatalog.ENTRIES.filter(
			(entry) =>
				entry.iosSupport &&
				entry.selectionRequired &&
				entry.includeInOperationSheet &&
				entry.dialog !== 'formatBatch',
		).map((entry) => entry.taskType);
	}

	static install(): MobileSelectionMenu | null {
		if (typeof window === 'undefined' || !(window as any).ThisIsTheiOSApp) {
			return null;
		}
		const existing = (window as any).__coolMobileSelectionMenu;
		if (existing instanceof MobileSelectionMenu) {
			return existing;
		}
		const menu = new MobileSelectionMenu();
		(window as any).__coolMobileSelectionMenu = menu;
		return menu;
	}

	dispose(): void {
		window.removeEventListener(MobileSelectionEvents.SHOW_EVENT, this.onShow);
		window.removeEventListener(MobileSelectionEvents.HIDE_EVENT, this.onHide);
		this.hide();
	}

	private show(event: CustomEvent): void {
		const detail = event.detail;
		if (!detail || typeof detail.anchorX !== 'number' || typeof detail.text !== 'string') {
			return;
		}
		this.hide();
		const docType = (window as any).app?.map?.getDocType?.() || 'text';
		const taskTypes = MobileSelectionMenu.menuTaskTypes().filter((taskType) => {
			const entry = MobileAiUiCatalog.getEntry(taskType);
			return !!entry && entry.documentTypes.indexOf(docType) >= 0;
		});
		if (taskTypes.length === 0) {
			return;
		}

		const container = document.createElement('div');
		container.className = MobileSelectionMenu.MENU_CLASS;
		container.style.cssText =
			'position:fixed;z-index:10002;display:flex;flex-wrap:wrap;gap:8px;' +
			'padding:8px;border-radius:12px;background:#fff;' +
			'box-shadow:0 4px 16px rgba(0,0,0,0.18);max-width:90vw;';
		taskTypes.forEach((taskType) => {
			const entry = MobileAiUiCatalog.getEntry(taskType);
			const button = document.createElement('button');
			button.type = 'button';
			button.textContent = entry?.label || taskType;
			button.onclick = () => {
				this.hide();
				const panel = (window as any).__coolWriterAiPanel;
				if (panel && typeof panel.openTask === 'function') {
					panel.openTask(taskType);
				}
			};
			container.appendChild(button);
		});
		document.body.appendChild(container);

		container.style.left = this.clampX(detail.anchorX, container.offsetWidth) + 'px';
		container.style.top =
			this.clampY(detail.anchorY - container.offsetHeight - 8, container.offsetHeight) +
			'px';
		this.container = container;
	}

	private hide(): void {
		if (this.container && this.container.parentNode) {
			this.container.parentNode.removeChild(this.container);
		}
		this.container = null;
	}

	private clampX(x: number, width: number): number {
		return Math.max(8, Math.min(x, (window.innerWidth || 320) - width - 8));
	}

	private clampY(top: number, height: number): number {
		return Math.max(8, Math.min(top, (window.innerHeight || 480) - height - 8));
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	MobileSelectionMenu.install();
}