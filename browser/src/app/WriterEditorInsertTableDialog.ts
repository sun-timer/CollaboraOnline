/*
 * Writer insert-table dialog (iOS).
 *
 * Row/column steppers (1-20, default 2×2) plus an insert button, replicating
 * Android ImpressInsertTablePickerController (DEFAULT 2×2 L26-27, MIN/MAX
 * 1/20 L28-29, clamp L196-198). Dispatches via WriterEditorController's
 * InsertTable Columns x Rows command.
 */

class WriterEditorInsertTableDialog {
	private static readonly MIN_COUNT = 1;
	private static readonly MAX_COUNT = 20;

	private readonly sheet: MobileAiSheet;
	private readonly controller: WriterEditorController;
	private rowCount = 2;
	private columnCount = 2;

	constructor(controller: WriterEditorController) {
		this.controller = controller;
		this.sheet = new MobileAiSheet({ title: '插入表格' });

		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:14px;';

		content.appendChild(this.stepperRow('行数',
			() => this.rowCount, (value: number) => { this.rowCount = value; }));
		content.appendChild(this.stepperRow('列数',
			() => this.columnCount, (value: number) => { this.columnCount = value; }));

		const summary = document.createElement('div');
		summary.style.cssText = 'font-size:14px;color:#5f6368;text-align:center;';
		summary.textContent = '行列范围 1 – 20';
		content.appendChild(summary);

		const actions = document.createElement('div');
		actions.style.cssText = 'display:flex;gap:10px;justify-content:flex-end;';
		const insertButton = document.createElement('button');
		insertButton.type = 'button';
		insertButton.textContent = '插入表格';
		insertButton.setAttribute('aria-label', '插入表格');
		insertButton.style.cssText =
			'padding:10px 20px;border:none;border-radius:8px;' +
			'background:linear-gradient(110deg,#c7f3ff,#f1d9ff);' +
			'font:inherit;font-size:15px;font-weight:600;cursor:pointer;';
		insertButton.onclick = () => this.insert();
		actions.appendChild(insertButton);
		content.appendChild(actions);

		this.sheet.setBody(content);
	}

	open(): void {
		this.sheet.open();
	}

	private insert(): void {
		this.controller.insertTable(this.columnCount, this.rowCount);
		this.sheet.close();
	}

	private stepperRow(
		labelText: string,
		getValue: () => number,
		setValue: (value: number) => void,
	): HTMLDivElement {
		const row = document.createElement('div');
		row.style.cssText = 'display:flex;align-items:center;gap:10px;';
		const label = document.createElement('label');
		label.textContent = labelText;
		label.style.cssText = 'font-size:14px;color:#5f6368;width:56px;';
		row.appendChild(label);

		const valueLabel = document.createElement('span');
		valueLabel.style.cssText =
			'flex:1;text-align:center;font-size:16px;font-variant-numeric:tabular-nums;color:#101010;';

		const minus = this.stepperButton('−', '减小' + labelText);
		const plus = this.stepperButton('+', '增大' + labelText);

		const refresh = () => {
			valueLabel.textContent = String(WriterEditorInsertTableDialog.clamp(getValue()));
		};
		minus.onclick = () => {
			setValue(WriterEditorInsertTableDialog.clamp(getValue() - 1));
			refresh();
		};
		plus.onclick = () => {
			setValue(WriterEditorInsertTableDialog.clamp(getValue() + 1));
			refresh();
		};

		row.appendChild(minus);
		row.appendChild(valueLabel);
		row.appendChild(plus);
		refresh();
		return row;
	}

	private stepperButton(text: string, ariaLabel: string): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = text;
		button.setAttribute('aria-label', ariaLabel);
		button.style.cssText =
			'width:44px;height:44px;border:1px solid #d8dde3;border-radius:8px;' +
			'background:#fff;font:inherit;font-size:20px;color:#1278D9;cursor:pointer;';
		return button;
	}

	private static clamp(value: number): number {
		return Math.max(
			WriterEditorInsertTableDialog.MIN_COUNT,
			Math.min(WriterEditorInsertTableDialog.MAX_COUNT, value),
		);
	}
}