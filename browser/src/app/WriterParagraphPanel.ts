/*
 * Writer paragraph quick panel (iOS).
 *
 * 左对齐/居中/右对齐/两端对齐/项目符号/编号 — Android PARAGRAPH_QUICK_ACTION_ITEMS
 * as a floating horizontal bar above the bottom toolbar.
 */

interface WriterParagraphQuickItem {
	label: string;
	iconKey: string;
	command: string;
}

class WriterParagraphPanel {
	private readonly bar: WriterQuickActionBar;
	private readonly controller: WriterEditorController;

	private constructor() {
		this.controller = WriterEditorController.getInstance();
		this.bar = new WriterQuickActionBar();
		this.bar.setActions(this.buildItems());
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
		this.bar.open();
	}

	close(): void {
		this.bar.close();
	}

	private closeSiblingPanels(): void {
		const charPanel = (window as any).__coolWriterCharPanel;
		if (charPanel && typeof charPanel.close === 'function') {
			charPanel.close();
		}
	}

	private buildItems(): WriterQuickActionSpec[] {
		return WriterParagraphPanel.getQuickActionItems().map((item) => ({
			label: item.label,
			iconHtml: WriterEditorIcons.get(item.iconKey),
			command: item.command,
			onTap: () => this.run(item.command),
		}));
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
		this.bar.refreshToggles();
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
