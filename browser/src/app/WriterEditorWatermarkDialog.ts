/*
 * Writer watermark dialog (iOS).
 *
 * Text input plus angle (0-360) and transparency (0-100) sliders. Applying an
 * empty text removes the watermark, mirroring the Android behaviour
 * (app.map.sendUnoCommand('.uno:Watermark', args)).
 */

class WriterEditorWatermarkDialog {
	private readonly sheet: MobileAiSheet;
	private readonly controller: WriterEditorController;
	private readonly textInput: HTMLInputElement;
	private readonly fontSelect: HTMLSelectElement;
	private readonly angleInput: HTMLInputElement;
	private readonly transparencyInput: HTMLInputElement;
	private readonly angleValue: HTMLSpanElement;
	private readonly transparencyValue: HTMLSpanElement;

	constructor(controller: WriterEditorController, fontOptions: string[]) {
		this.controller = controller;
		this.sheet = new MobileAiSheet({ title: '水印' });

		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:14px;';

		const label = document.createElement('label');
		label.textContent = '水印文字';
		label.style.cssText = 'font-size:14px;color:#5f6368;';
		content.appendChild(label);
		this.textInput = document.createElement('input');
		this.textInput.type = 'text';
		this.textInput.placeholder = '输入水印文字（留空移除）';
		this.textInput.style.cssText =
			'padding:10px;border:1px solid #d8dde3;border-radius:8px;font:inherit;';
		content.appendChild(this.textInput);

		const fontLabel = document.createElement('label');
		fontLabel.textContent = '字体';
		fontLabel.style.cssText = 'font-size:14px;color:#5f6368;';
		content.appendChild(fontLabel);
		this.fontSelect = document.createElement('select');
		const safeFonts = fontOptions && fontOptions.length ? fontOptions : ['Noto Serif CJK SC'];
		const defaultFont = safeFonts.indexOf('Noto Serif CJK SC') >= 0
			? 'Noto Serif CJK SC'
			: safeFonts[0];
		safeFonts.forEach((fontName) => {
			const option = document.createElement('option');
			option.value = fontName;
			option.textContent = fontName;
			if (fontName === defaultFont) {
				option.selected = true;
			}
			this.fontSelect.appendChild(option);
		});
		this.fontSelect.style.cssText =
			'padding:10px;border:1px solid #d8dde3;border-radius:8px;font:inherit;';
		content.appendChild(this.fontSelect);

		const angleRow = this.sliderRow(
			'角度',
			0,
			360,
			45,
			(value) => { this.angleValue.textContent = value + '°'; },
		);
		content.appendChild(angleRow.row);
		this.angleInput = angleRow.input;
		this.angleValue = angleRow.valueLabel;

		const transparencyRow = this.sliderRow(
			'透明度',
			0,
			100,
			50,
			(value) => { this.transparencyValue.textContent = value + '%'; },
		);
		content.appendChild(transparencyRow.row);
		this.transparencyInput = transparencyRow.input;
		this.transparencyValue = transparencyRow.valueLabel;

		const actions = document.createElement('div');
		actions.style.cssText = 'display:flex;gap:10px;justify-content:flex-end;';
		const removeButton = this.makeButton('移除水印', () => this.remove());
		const applyButton = this.makeButton('应用', () => this.apply(), true);
		actions.appendChild(removeButton);
		actions.appendChild(applyButton);
		content.appendChild(actions);

		this.sheet.setBody(content);
	}

	open(): void {
		this.sheet.open();
		this.textInput.focus();
	}

	private apply(): void {
		this.controller.applyWatermark(
			this.textInput.value,
			parseInt(this.angleInput.value, 10),
			parseInt(this.transparencyInput.value, 10),
			this.fontSelect.value,
		);
		this.sheet.close();
	}

	private remove(): void {
		this.controller.applyWatermark('', 45, 50);
		this.sheet.close();
	}

	private sliderRow(
		labelText: string,
		min: number,
		max: number,
		initial: number,
		onInput: (value: string) => void,
	): { row: HTMLDivElement; input: HTMLInputElement; valueLabel: HTMLSpanElement } {
		const row = document.createElement('div');
		row.style.cssText = 'display:flex;align-items:center;gap:10px;';
		const labelElement = document.createElement('label');
		labelElement.textContent = labelText;
		labelElement.style.cssText = 'font-size:14px;color:#5f6368;width:56px;';
		row.appendChild(labelElement);
		const input = document.createElement('input');
		input.type = 'range';
		input.min = String(min);
		input.max = String(max);
		input.value = String(initial);
		input.style.cssText = 'flex:1;';
		row.appendChild(input);
		const valueLabel = document.createElement('span');
		valueLabel.textContent = String(initial);
		valueLabel.style.cssText = 'font-size:14px;width:44px;text-align:right;color:#333;';
		row.appendChild(valueLabel);
		input.addEventListener('input', () => {
			valueLabel.textContent = input.value;
			onInput(input.value);
		});
		return { row, input, valueLabel };
	}

	private makeButton(label: string, handler: () => void, primary = false): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		button.setAttribute('aria-label', label);
		button.style.cssText = primary
			? 'padding:10px 20px;border:none;border-radius:8px;background:linear-gradient(110deg,#c7f3ff,#f1d9ff);font:inherit;font-size:15px;font-weight:600;cursor:pointer;'
			: 'padding:10px 20px;border:1px solid #d8dde3;border-radius:8px;background:#fff;font:inherit;font-size:15px;cursor:pointer;';
		button.onclick = handler;
		return button;
	}
}
