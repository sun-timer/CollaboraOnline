/*
 * Writer shape picker dialog (iOS).
 *
 * Grid of six basic-shape cards mirroring Android FunctionPanelController's
 * SHAPE_LABELS/SHAPE_COMMANDS (L2129-2139): 矩形/椭圆/圆角矩形/等腰三角形/
 * 直线/箭头 → `.uno:BasicShapes.*`.
 */

class WriterEditorShapeDialog {
	private readonly sheet: MobileAiSheet;
	private readonly controller: WriterEditorController;

	constructor(controller: WriterEditorController) {
		this.controller = controller;
		this.sheet = new MobileAiSheet({ title: '插入形状' });

		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;overflow-y:auto;';

		const grid = document.createElement('div');
		grid.style.cssText =
			'display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;';
		const shapes: { label: string; name: string }[] = [
			{ label: '矩形', name: 'rectangle' },
			{ label: '椭圆', name: 'ellipse' },
			{ label: '圆角矩形', name: 'round-rectangle' },
			{ label: '等腰三角形', name: 'isosceles-triangle' },
			{ label: '直线', name: 'line' },
			{ label: '箭头', name: 'arrow' },
		];
		shapes.forEach((shape) => {
			grid.appendChild(this.card(shape.label, shape.name));
		});
		content.appendChild(grid);

		this.sheet.setBody(content);
	}

	open(): void {
		this.sheet.open();
	}

	private card(label: string, name: string): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.setAttribute('aria-label', '插入' + label);
		button.style.cssText =
			'display:flex;flex-direction:column;align-items:center;justify-content:center;' +
			'min-height:88px;padding:10px 6px;border:1px solid #E6E8EB;border-radius:12px;' +
			'background:#fff;cursor:pointer;';
		const icon = document.createElement('span');
		icon.style.cssText = 'width:32px;height:32px;display:flex;align-items:center;' +
			'justify-content:center;color:#1278D9;';
		icon.innerHTML = WriterEditorShapePreview.get(name);
		button.appendChild(icon);
		const nameLabel = document.createElement('span');
		nameLabel.textContent = label;
		nameLabel.style.cssText = 'font-size:12px;color:#101010;text-align:center;margin-top:6px;';
		button.appendChild(nameLabel);
		button.onclick = () => {
			this.controller.insertShape(name);
			this.sheet.close();
		};
		return button;
	}
}

/** Stroke-style shape icons matching the six BasicShapes names. */
class WriterEditorShapePreview {
	static get(name: string): string {
		const stroke = 'stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"';
		switch (name) {
			case 'rectangle':
				return '<svg viewBox="0 0 32 32" width="32" height="32" xmlns="http://www.w3.org/2000/svg"><rect x="5" y="8" width="22" height="16" ' + stroke + '/></svg>';
			case 'ellipse':
				return '<svg viewBox="0 0 32 32" width="32" height="32" xmlns="http://www.w3.org/2000/svg"><ellipse cx="16" cy="16" rx="11" ry="8" ' + stroke + '/></svg>';
			case 'round-rectangle':
				return '<svg viewBox="0 0 32 32" width="32" height="32" xmlns="http://www.w3.org/2000/svg"><rect x="5" y="9" width="22" height="14" rx="5" ' + stroke + '/></svg>';
			case 'isosceles-triangle':
				return '<svg viewBox="0 0 32 32" width="32" height="32" xmlns="http://www.w3.org/2000/svg"><path d="M16 5 L28 26 L4 26 Z" ' + stroke + '/></svg>';
			case 'line':
				return '<svg viewBox="0 0 32 32" width="32" height="32" xmlns="http://www.w3.org/2000/svg"><path d="M5 26 L27 6" ' + stroke + '/></svg>';
			case 'arrow':
				return '<svg viewBox="0 0 32 32" width="32" height="32" xmlns="http://www.w3.org/2000/svg"><path d="M6 26 L24 8 M12 8 H24 V20" ' + stroke + '/></svg>';
			default:
				return '';
		}
	}
}