/*
 * Calc insert-shape picker (Android CalcFunctionPanelController SHAPE_LABELS).
 */

class CalcEditorShapeDialog {
	private readonly dialog: WriterEditorChooseDialog;
	private readonly onInserted?: () => void;

	constructor(
		sendUno: (command: string) => void,
		host?: WriterEditorInlineSubpageHost | null,
		onInserted?: () => void,
	) {
		this.onInserted = onInserted;
		const options: WriterChooseOption[] = CalcEditorShapeDialog.SHAPES.map((shape) => ({
			label: shape.label,
			value: shape.unocmd,
		}));
		this.dialog = new WriterEditorChooseDialog(
			'插入形状',
			options,
			(option) => {
				sendUno(option.value);
				this.dialog.close();
				if (this.onInserted) {
					this.onInserted();
				}
			},
			undefined,
			host,
		);
	}

	open(): void {
		this.dialog.open();
	}

	close(): void {
		this.dialog.close();
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
