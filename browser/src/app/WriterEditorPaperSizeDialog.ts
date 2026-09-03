/*
 * Writer custom paper-size dialog (iOS).
 *
 * Width/height steppers (step 0.1 cm, range 5.0-120.0 cm) dispatching
 * `.uno:AttributePageSize?AttributePageSize.Width:long=..&Height:long=..`
 * through WriterEditorController. Replicates Android
 * PaperSizePickerController's custom section (MIN_CM/MAX_CM/STEP_CM L33-35,
 * clampCm L333-335, formatCustomLabel L318-320).
 */

class WriterEditorPaperSizeDialog {
	private static readonly MIN_CM = 5.0;
	private static readonly MAX_CM = 120.0;
	private static readonly STEP_CM = 0.1;

	private readonly sheet: MobileAiSheet;
	private readonly controller: WriterEditorController;
	private widthCm = 21.0;
	private heightCm = 29.7;

	constructor(controller: WriterEditorController) {
		this.controller = controller;
		this.sheet = new MobileAiSheet({ title: '自定义纸张' });

		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:14px;';

		content.appendChild(this.stepperRow('宽度',
			() => this.widthCm, (value: number) => { this.widthCm = value; }));
		content.appendChild(this.stepperRow('高度',
			() => this.heightCm, (value: number) => { this.heightCm = value; }));

		const summary = document.createElement('div');
		summary.style.cssText = 'font-size:14px;color:#5f6368;text-align:center;';
		summary.textContent = '尺寸范围 5.0 – 120.0 cm，步长 0.1 cm';
		content.appendChild(summary);

		const actions = document.createElement('div');
		actions.style.cssText = 'display:flex;gap:10px;justify-content:flex-end;';
		const applyButton = document.createElement('button');
		applyButton.type = 'button';
		applyButton.textContent = '应用';
		applyButton.setAttribute('aria-label', '应用自定义纸张尺寸');
		applyButton.style.cssText =
			'padding:10px 20px;border:none;border-radius:8px;' +
			'background:linear-gradient(110deg,#c7f3ff,#f1d9ff);' +
			'font:inherit;font-size:15px;font-weight:600;cursor:pointer;';
		applyButton.onclick = () => this.apply();
		actions.appendChild(applyButton);
		content.appendChild(actions);

		this.sheet.setBody(content);
	}

	open(): void {
		this.sheet.open();
	}

	close(): void {
		this.sheet.close();
	}

	private apply(): void {
		this.controller.applyCustomPaperSize(this.widthCm, this.heightCm);
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
			valueLabel.textContent =
				WriterEditorPaperSizeDialog.formatCm(
					WriterEditorPaperSizeDialog.clampCm(getValue()),
				) + ' cm';
		};
		minus.onclick = () => {
			setValue(WriterEditorPaperSizeDialog.clampCm(
				getValue() - WriterEditorPaperSizeDialog.STEP_CM,
			));
			refresh();
		};
		plus.onclick = () => {
			setValue(WriterEditorPaperSizeDialog.clampCm(
				getValue() + WriterEditorPaperSizeDialog.STEP_CM,
			));
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

	private static formatCm(cm: number): string {
		return cm.toFixed(1);
	}

	private static clampCm(value: number): number {
		return Math.max(
			WriterEditorPaperSizeDialog.MIN_CM,
			Math.min(
				WriterEditorPaperSizeDialog.MAX_CM,
				Math.round(value * 10.0) / 10.0,
			),
		);
	}
}