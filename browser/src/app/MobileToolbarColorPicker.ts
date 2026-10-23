/*
 * Bottom-toolbar / quick-action color pickers (Android CalcFontColorPickerController).
 * Full 36-swatch secondary page with back header; pick applies UNO without closing.
 */

type MobileToolbarColorKind =
	| 'font'
	| 'highlight'
	| 'background'
	| 'border';

class MobileToolbarColorPicker {
	private active: { close(): void } | null = null;

	static mount(): MobileToolbarColorPicker | null {
		if (!(window as any).ThisIsTheiOSApp) {
			return null;
		}
		const existing = (window as any).__coolMobileToolbarColorPicker;
		if (existing instanceof MobileToolbarColorPicker) {
			return existing;
		}
		const picker = new MobileToolbarColorPicker();
		(window as any).__coolMobileToolbarColorPicker = picker;
		return picker;
	}

	open(kind: MobileToolbarColorKind): void {
		this.close();
		if (kind === 'highlight' && CalcEditorFormat.isActive()) return;
		let initialColor: number | null = null;
		if (CalcEditorFormat.isActive()) {
			CalcTextFormatBridge.observeSelection();
			const command = { font: '.uno:Color', highlight: '.uno:CharBackColor',
				background: '.uno:BackgroundColor', border: '.uno:FrameLineColor' }[kind];
			initialColor = CalcEditorFormat.color(command);
			if (kind === 'highlight') {
				initialColor = CalcEditorFormat.highlightColor();
			}
		}
		const title = MobileToolbarColorPicker.titleFor(kind);
		const dialog = new WriterColorPickerDialog(
			title,
			initialColor,
			(rgb) => MobileToolbarColorPicker.apply(kind, rgb),
			null,
		);
		this.active = dialog;
		dialog.open();
	}

	close(): void {
		if (this.active) {
			this.active.close();
			this.active = null;
		}
	}

	private static titleFor(kind: MobileToolbarColorKind): string {
		switch (kind) {
			case 'font':
				return '字体颜色';
			case 'highlight':
				return '荧光颜色';
			case 'background':
				return '背景颜色';
			case 'border':
				return '边框颜色';
		}
	}

	private static apply(kind: MobileToolbarColorKind, rgb: number): void {
		const map = (window as any).app?.map;
		const docType =
			map && typeof map.getDocType === 'function' ? map.getDocType() : '';
		const socket = (window as any).app?.socket;
		const send = (command: string) => {
			if (map && typeof map.sendUnoCommand === 'function') {
				map.sendUnoCommand(command);
				return;
			}
			if (socket && typeof socket.sendMessage === 'function') {
				socket.sendMessage('uno ' + command);
			}
		};
		switch (kind) {
			case 'font':
				if (docType === 'spreadsheet' || docType === 'presentation') {
					send(
						'.uno:Color {"Color.Color":{"type":"long","value":' +
							rgb +
							'}}',
					);
				} else {
					WriterEditorController.getInstance().applyFontColor(rgb);
				}
				return;
			case 'highlight':
				WriterEditorController.getInstance().applyHighlightColor(rgb);
				return;
			case 'background':
				send(
					'.uno:BackgroundColor {"BackgroundColor.Color":{"type":"long","value":' +
						rgb +
						'}}',
				);
				return;
			case 'border':
				send(CalcEditorBorder.buildBorderColorUnoCommand(rgb));
				return;
		}
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	const mount = () => {
		try {
			MobileToolbarColorPicker.mount();
		} catch (_e) {
			window.setTimeout(mount, 0);
		}
	};
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', mount, { once: true });
	} else {
		window.setTimeout(mount, 0);
	}
}
