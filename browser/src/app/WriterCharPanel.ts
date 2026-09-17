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
		this.closeSiblingPanels();
		this.bar.open();
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
		return [
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
				iconHtml: WriterCharPanelIcons.fontColor,
				onTap: () => this.openColor('字体颜色', 'fontColor'),
			},
			{
				label: '高亮',
				iconHtml: WriterCharPanelIcons.highlight,
				onTap: () => this.openColor('荧光颜色', 'highlight'),
			},
		];
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
		const picker = new WriterColorPickerDialog(title, null, (rgb) => {
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
		'<svg viewBox="0 0 48 48" width="24" height="24" xmlns="http://www.w3.org/2000/svg"><path d="M25.3706 5.57178C31.011 5.57178 35.5831 10.1444 35.5835 15.7847C35.5835 18.5265 34.501 21.0143 32.7427 22.8491C36.3512 24.4243 38.8742 28.0224 38.8745 32.2114C38.8745 37.754 34.4596 42.2623 28.9546 42.4175V42.4272L11.1255 42.4253C10.0212 42.425 9.12557 41.5296 9.12549 40.4253V7.57178C9.12563 6.4674 10.0211 5.57189 11.1255 5.57178H25.3706ZM13.1255 25.9985V38.4253L28.6616 38.4263V38.4253C32.0931 38.4253 34.8745 35.6429 34.8745 32.2114C34.874 28.7804 32.0928 25.9986 28.6616 25.9985H13.1255ZM13.1255 21.9985H25.3706C28.8021 21.9985 31.5835 19.2162 31.5835 15.7847C31.5831 12.3535 28.8019 9.57178 25.3706 9.57178H13.1255V21.9985Z" fill="#101010" fill-opacity="0.85"/></svg>';

	static readonly italic =
		'<svg viewBox="0 0 48 48" width="24" height="24" xmlns="http://www.w3.org/2000/svg"><path d="M26.5674 7.00001C26.7148 6.95417 26.8746 6.94106 27.0371 6.96973C27.0793 6.97718 27.1203 6.98765 27.1602 7.00001H32.5898C33.1421 7.00001 33.5898 7.44777 33.5898 8.00001C33.5898 8.55229 33.1421 9.00001 32.5898 9.00001H27.6934L22.3916 39.0459H26.8633C27.4155 39.046 27.8633 39.4937 27.8633 40.0459C27.863 40.5979 27.4154 41.0458 26.8633 41.0459H15.4092C14.8571 41.0459 14.4094 40.598 14.4092 40.0459C14.4092 39.4936 14.8569 39.0459 15.4092 39.0459H20.3633C20.365 39.0333 20.3659 39.0204 20.3682 39.0078L25.6641 9.00001H21.1357C20.5837 8.99976 20.1357 8.55214 20.1357 8.00001C20.1358 7.44792 20.5837 7.00025 21.1357 7.00001H26.5674Z" fill="#101010"/></svg>';

	static readonly strikeout =
		'<svg viewBox="0 0 48 48" width="24" height="24" xmlns="http://www.w3.org/2000/svg"><path d="M24 24C40 30 34 44 24 44C13.9999 44 12 36 12 36" fill="none" stroke="#101010" stroke-width="3" stroke-linecap="round"/><path d="M35.9999 12C35.9999 12 33 4 23.9999 4C14.9999 4 11.4359 11.5995 15.6096 18" fill="none" stroke="#101010" stroke-width="3" stroke-linecap="round"/><path d="M12 36C12 36 15.9999 44 24 44C32 44 36.564 36.4005 32.3903 30" fill="none" stroke="#101010" stroke-width="3" stroke-linecap="round"/><path d="M6 24H42" stroke="#101010" stroke-width="3" stroke-linecap="round"/></svg>';

	static readonly underline =
		'<svg viewBox="0 0 48 48" width="24" height="24" xmlns="http://www.w3.org/2000/svg"><path d="M8 44H40" stroke="#101010" stroke-width="3" stroke-linecap="round"/><path d="M37 6.09668C37 12.7633 37 15.333 37 21.9997C37 29.1794 31.1797 34.9997 24 34.9997C16.8203 34.9997 11 29.1794 11 21.9997C11 15.333 11 12.7633 11 6.09668" fill="none" stroke="#101010" stroke-width="3" stroke-linecap="round"/></svg>';

	static readonly highlight =
		'<svg viewBox="0 0 48 48" width="24" height="24" xmlns="http://www.w3.org/2000/svg"><path d="M11.4784 23.6969L32.1759 3L38.0896 8.91286L17.392 29.6105L10 31.0889L11.4784 23.6969Z" fill="#FFD54F" stroke="#101010" stroke-width="1.5" stroke-linejoin="round"/><path d="M28.48 6.69531L34.3936 12.6089" stroke="#101010" stroke-width="3" stroke-linecap="round"/><path d="M8 40H40" stroke="#101010" stroke-width="3" stroke-linecap="round"/></svg>';

	static readonly fontColor =
		'<svg viewBox="0 0 48 48" width="24" height="24" xmlns="http://www.w3.org/2000/svg"><path d="M17 14 L11 34 M17 14 L24 34 M17 14 L20 24 H28" fill="none" stroke="#101010" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/><path d="M32 18 L37 34 M32 18 L39 27 M37 34 L41 34 M33 28 H40" fill="none" stroke="#101010" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/><rect x="14" y="36" width="20" height="5" rx="2" fill="#1278D9"/></svg>';
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
