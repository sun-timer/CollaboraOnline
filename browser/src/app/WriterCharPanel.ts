/*
 * Writer character quick panel (iOS).
 *
 * 粗体/斜体/下划线/删除线/字色/高亮 — Android BottomToolbarController
 * CHARACTER_QUICK_ACTION_ITEMS as a floating horizontal bar above the bottom toolbar.
 */

class WriterCharPanel {
	private readonly bar: WriterQuickActionBar;
	private readonly controller: WriterEditorController;
	private colorPicker: WriterColorPickerDialog | null = null;

	private constructor() {
		this.controller = WriterEditorController.getInstance();
		this.bar = new WriterQuickActionBar();
		this.bar.setActions(this.buildItems());
	}

	static mount(): WriterCharPanel | null {
		if (!(window as any).ThisIsTheiOSApp) {
			return null;
		}
		const existing = (window as any).__coolWriterCharPanel;
		if (existing instanceof WriterCharPanel) {
			return existing;
		}
		const panel = new WriterCharPanel();
		(window as any).__coolWriterCharPanel = panel;
		return panel;
	}

	open(): void {
		if (CalcEditorFormat.isActive()) CalcTextFormatBridge.observeSelection();
		this.closeSiblingPanels();
		this.bar.setActions(this.buildItems());
		this.bar.open();
	}

	isOpen(): boolean {
		return this.bar.isOpen() || !!this.colorPicker;
	}

	/** The 字符 toolbar button toggles: a second tap closes the panel (issue: it never closed). */
	toggle(): void {
		if (this.isOpen()) {
			this.close();
		} else {
			this.open();
		}
	}

	close(): void {
		if (this.colorPicker) {
			this.colorPicker.close();
			this.colorPicker = null;
		}
		this.bar.close();
	}

	private closeSiblingPanels(): void {
		const paraPanel = (window as any).__coolWriterParaPanel;
		if (paraPanel && typeof paraPanel.close === 'function') {
			paraPanel.close();
		}
	}

	private buildItems(): WriterQuickActionSpec[] {
		const items: WriterQuickActionSpec[] = [
			{
				label: '粗体',
				iconHtml: WriterCharPanelIcons.bold,
				command: '.uno:Bold',
				onTap: () => this.run('.uno:Bold'),
			},
			{
				label: '斜体',
				iconHtml: WriterCharPanelIcons.italic,
				command: '.uno:Italic',
				onTap: () => this.run('.uno:Italic'),
			},
			{
				label: '下划线',
				iconHtml: WriterCharPanelIcons.underline,
				command: '.uno:Underline',
				onTap: () => this.run('.uno:Underline'),
			},
			{
				label: '删除线',
				iconHtml: WriterCharPanelIcons.strikeout,
				command: '.uno:Strikeout',
				onTap: () => this.run('.uno:Strikeout'),
			},
			{
				label: '字色',
				iconHtml: CalcEditorFormat.isActive() ? WriterCharPanelIcons.calcFontColor : WriterCharPanelIcons.fontColor,
				colorCommand: '.uno:Color',
				onTap: () => this.openColor('字体颜色', 'fontColor'),
			},
			{
				label: '高亮',
				iconHtml: WriterCharPanelIcons.highlight,
				colorCommand: '.uno:CharBackColor',
				onTap: () => this.openColor('荧光颜色', 'highlight'),
			},
		];
		return CalcEditorFormat.isActive()
			? items.filter((item) => item.colorCommand !== '.uno:CharBackColor') : items;
	}

	private run(command: string): void {
		this.controller.run({
			id: 'char-quick',
			label: '',
			tab: 'default',
			icon: '',
			kind: 'command',
			unocmd: command,
		});
		this.bar.refreshToggles();
	}

	private openColor(title: string, kind: 'fontColor' | 'highlight'): void {
		const toolbarPicker = (window as any).__coolMobileToolbarColorPicker as
			| MobileToolbarColorPicker
			| undefined;
		this.close();
		if (toolbarPicker && typeof toolbarPicker.open === 'function') {
			toolbarPicker.open(kind === 'fontColor' ? 'font' : 'highlight');
			return;
		}
		if (this.colorPicker) {
			this.colorPicker.close();
		}
		const initialColor = CalcEditorFormat.isActive()
			? kind === 'fontColor' ? CalcEditorFormat.color('.uno:Color') : CalcEditorFormat.highlightColor()
			: null;
		const picker = new WriterColorPickerDialog(title, initialColor, (rgb) => {
			if (kind === 'fontColor') {
				this.controller.applyFontColor(rgb);
			} else {
				this.controller.applyHighlightColor(rgb);
			}
		});
		this.colorPicker = picker;
		picker.open();
	}
}

/** Stroke icons for the character quick actions (CO-sourced look). */
class WriterCharPanelIcons {
	static readonly bold =
		"<svg width=\"48\" height=\"48\" viewBox=\"0 0 48 48\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<path d=\"M25.3705 5.57129C31.0109 5.57129 35.583 10.1439 35.5834 15.7842C35.5834 18.5261 34.5008 21.0139 32.7426 22.8486C36.3511 24.4238 38.874 28.0219 38.8744 32.2109C38.8744 37.7535 34.4595 42.2618 28.9545 42.417V42.4268L11.1254 42.4248C10.021 42.4246 9.12545 41.5291 9.12537 40.4248V7.57129C9.12551 6.46691 10.021 5.5714 11.1254 5.57129H25.3705ZM13.1254 25.998V38.4248L28.6615 38.4258V38.4248C32.093 38.4248 34.8744 35.6424 34.8744 32.2109C34.8739 28.7799 32.0927 25.9981 28.6615 25.998H13.1254ZM13.1254 21.998H25.3705C28.802 21.998 31.5834 19.2157 31.5834 15.7842C31.583 12.353 28.8017 9.57129 25.3705 9.57129H13.1254V21.998Z\" fill=\"black\" fill-opacity=\"0.9\"/>\n</svg>";

	static readonly italic =
		"<svg width=\"48\" height=\"48\" viewBox=\"0 0 48 48\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<path d=\"M26.5636 6.99805C26.712 6.95134 26.8735 6.93795 27.0372 6.9668C27.0797 6.97429 27.1211 6.98559 27.1613 6.99805H32.59C33.1432 6.99805 33.5919 7.44677 33.5919 8C33.5918 8.5531 33.1431 9.00195 32.59 9.00195H27.6954L22.3937 39.043H26.8634C27.4165 39.043 27.8653 39.4918 27.8654 40.0449C27.8654 40.5981 27.4166 41.0468 26.8634 41.0469H15.4093C14.8561 41.0469 14.4073 40.5982 14.4073 40.0449C14.4074 39.4917 14.8561 39.043 15.4093 39.043H20.3615C20.3631 39.0311 20.3642 39.0187 20.3663 39.0068L25.6613 9.00195H21.1359C20.5829 9.00173 20.1341 8.55296 20.1339 8C20.1339 7.44691 20.5828 6.99827 21.1359 6.99805H26.5636Z\" fill=\"black\" fill-opacity=\"0.9\"/>\n</svg>";

	static readonly strikeout =
		"<svg width=\"48\" height=\"48\" viewBox=\"0 0 48 48\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<path d=\"M4.99988 24H42.9999\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke=\"#1278D9\"/>\n<path d=\"M24.0002 24C40.0002 30 34.0002 44 24.0002 44C14.0001 44 12.0002 36 12.0002 36\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke=\"#333333\"/>\n<path d=\"M35.9996 12C35.9996 12 32.9997 4 23.9996 4C14.9996 4 11.4356 11.5995 15.6093 18\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke=\"#101010\"/>\n<path d=\"M12.0002 36C12.0002 36 16.0001 44 24.0002 44C32.0002 44 36.5642 36.4005 32.3905 30\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke=\"#101010\"/>\n</svg>";

	static readonly underline =
		"<svg width=\"48\" height=\"48\" viewBox=\"0 0 48 48\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<path d=\"M8 44H40\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke=\"#1278D9\"/>\n<path d=\"M37 6.09668C37 12.7633 37 15.333 37 21.9997C37 29.1794 31.1797 34.9997 24 34.9997C16.8203 34.9997 11 29.1794 11 21.9997C11 15.333 11 12.7633 11 6.09668\" stroke-width=\"2\" stroke-linecap=\"round\" stroke=\"#101010\"/>\n</svg>";

	static readonly highlight =
		"<svg width=\"48\" height=\"48\" viewBox=\"0 0 48 48\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<path d=\"M28.48 6.69531L34.3936 12.6089\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke=\"#101010\"/>\n<path d=\"M11.4784 23.6969L32.1759 3L38.0896 8.91286L17.392 29.6105L10 31.0889L11.4784 23.6969Z\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke=\"#101010\"/>\n<path d=\"M28.48 6.69531L34.3936 12.6089\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke=\"#101010\"/>\n<path d=\"M12.2178 23.6963L17.3922 28.8707\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke=\"#101010\"/>\n<rect x=\"2.98328\" y=\"35.9833\" width=\"38.0334\" height=\"8.03344\" fill=\"#FFFF00\" stroke-width=\"1.96656\" stroke=\"#101010\"/>\n</svg>";

	/** Figma icon/24/工具栏/P/字符颜色: one A over an outlined color bar. */
	static readonly calcFontColor =
		'<svg viewBox="0 0 48 48" width="24" height="24" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M11 34L24 5L37 34M16 23H32" fill="none" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><rect x="6" y="35" width="36" height="7" fill="#FE3A3A" stroke="#333333" stroke-width="2"/></svg>';

	/**
	 * Figma icon/24/工具栏/P/字符颜色: one A over an outlined color bar.
	 *
	 * The previous glyph stacked two hand-drawn "A" strokes plus a blue bar, which
	 * rendered as an unreadable scribble in the iOS character bar. Use the exported
	 * Figma asset (same one Calc already uses) instead of drawing a new icon.
	 */
	static readonly fontColor =
		"<svg width=\"48\" height=\"48\" viewBox=\"0 0 48 48\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">\n<rect x=\"5.06944\" y=\"34.4866\" width=\"38.528\" height=\"8.11117\" fill=\"#FF3838\" stroke-width=\"2.02779\" stroke=\"#333333\"/>\n<path d=\"M18.2274 22.0483H29.6819L27.6819 16.9079C26.3183 13.5977 25.2274 10.5992 24.0456 7.17223H23.8638C22.7274 10.5992 21.591 13.5977 20.2729 16.9079L18.2274 22.0483ZM34.091 33.4584L30.4092 23.9175H17.5001L13.7274 33.4584H11.0001L22.591 5.06934H25.4092L37.0001 33.4584H34.091Z\" fill=\"#333333\"/>\n</svg>";
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	const mountChar = () => {
		try {
			WriterCharPanel.mount();
		} catch (_e) {
			window.setTimeout(mountChar, 0);
		}
	};
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', mountChar, { once: true });
	} else {
		window.setTimeout(mountChar, 0);
	}
}
