/*
 * Calc insert-shape page (Android CalcFunctionPanelController SHAPE_LABELS parity).
 *
 * Android shows a plain action list (插入形状 + 6 items + 取消); no current
 * selection exists, so rows carry no checkmark state.
 */

class CalcEditorShapeDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly onInserted?: () => void;

	constructor(
		sendUno: (command: string) => void,
		host?: WriterEditorInlineSubpageHost | null,
		onInserted?: () => void,
	) {
		this.onInserted = onInserted;

		const root = document.createElement('div');
		root.className = 'writer-function-calc-shape';

		const list = document.createElement('div');
		list.className = 'writer-function-calc-shape__list';
		CalcEditorShapeDialog.SHAPES.forEach((shape, index) => {
			const row = document.createElement('button');
			row.type = 'button';
			row.className = 'writer-function-calc-shape__row';
			row.setAttribute('aria-label', '插入' + shape.label);
			const label = document.createElement('span');
			label.textContent = shape.label;
			row.appendChild(label);
			row.onclick = () => {
				sendUno(shape.unocmd);
				this.subpage.close();
				if (this.onInserted) {
					this.onInserted();
				}
			};
			list.appendChild(row);
			if (index + 1 < CalcEditorShapeDialog.SHAPES.length) {
				const divider = document.createElement('div');
				divider.className = 'writer-function-calc-shape__divider';
				divider.setAttribute('aria-hidden', 'true');
				list.appendChild(divider);
			}
		});
		root.appendChild(list);

		const cancel = document.createElement('button');
		cancel.type = 'button';
		cancel.className = 'writer-function-calc-shape__cancel';
		cancel.textContent = '取消';
		cancel.setAttribute('aria-label', '取消');
		cancel.onclick = () => this.subpage.close();
		root.appendChild(cancel);

		this.subpage = writerEditorMountSubpageDialog('插入形状', root, host);
	}

	open(): void {
		this.subpage.open();
	}

	close(): void {
		this.subpage.close();
	}

	private static readonly SHAPES: { label: string; unocmd: string }[] = [
		{ label: '矩形', unocmd: '.uno:BasicShapes.rectangle' },
		{ label: '椭圆', unocmd: '.uno:BasicShapes.ellipse' },
		{ label: '圆角矩形', unocmd: '.uno:BasicShapes.round-rectangle' },
		{ label: '等腰三角形', unocmd: '.uno:BasicShapes.isosceles-triangle' },
		{ label: '直线', unocmd: '.uno:BasicShapes.line' },
		{ label: '箭头', unocmd: '.uno:BasicShapes.arrow' },
	];
}
