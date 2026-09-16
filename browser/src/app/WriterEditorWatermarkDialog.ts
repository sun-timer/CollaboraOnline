/*
 * Writer watermark settings (iOS) — layout/colors match Android
 * WatermarkSettingsController.
 */

interface WriterEditorWatermarkDialogOptions {
	text?: string;
	angle?: number;
	transparency?: number;
	font?: string;
	onApplied?: (payload: {
		text: string;
		font: string;
		angle: number;
		transparency: number;
		enabled: boolean;
	}) => void;
}

class WriterEditorWatermarkDialog {
	private static readonly MIN_ANGLE = 0;
	private static readonly MAX_ANGLE = 360;
	private static readonly MIN_TRANSPARENCY = 0;
	private static readonly MAX_TRANSPARENCY = 100;

	private readonly subpage: { open(): void; close(): void };
	private readonly host: WriterEditorInlineSubpageHost | null;
	private readonly controller: WriterEditorController;
	private readonly fontOptions: string[];
	private readonly onApplied?: WriterEditorWatermarkDialogOptions['onApplied'];
	private text = '';
	private font = '';
	private angle = 45;
	private transparency = 50;
	private textInput!: HTMLInputElement;
	private fontValueView!: HTMLSpanElement;
	private angleValueView!: HTMLSpanElement;
	private opacityValueView!: HTMLSpanElement;

	constructor(
		controller: WriterEditorController,
		fontOptions: string[],
		options?: WriterEditorWatermarkDialogOptions,
		host?: WriterEditorInlineSubpageHost | null,
	) {
		this.host = host || null;
		this.controller = controller;
		this.fontOptions =
			fontOptions && fontOptions.length ? fontOptions : ['Noto Serif CJK SC'];
		this.onApplied = options?.onApplied;
		this.text = options?.text ?? '水印文本';
		this.font =
			options?.font ||
			(this.fontOptions.indexOf('Noto Serif CJK SC') >= 0
				? 'Noto Serif CJK SC'
				: this.fontOptions[0]);
		this.angle = WriterEditorWatermarkDialog.clamp(
			options?.angle ?? 45,
			WriterEditorWatermarkDialog.MIN_ANGLE,
			WriterEditorWatermarkDialog.MAX_ANGLE,
		);
		this.transparency = WriterEditorWatermarkDialog.clamp(
			options?.transparency ?? 50,
			WriterEditorWatermarkDialog.MIN_TRANSPARENCY,
			WriterEditorWatermarkDialog.MAX_TRANSPARENCY,
		);

		const wrap = document.createElement('div');
		wrap.className = 'writer-watermark-page';
		wrap.appendChild(this.buildDivider());
		wrap.appendChild(this.buildScrollContent());
		wrap.appendChild(this.buildConfirmButton());
		this.subpage = writerEditorMountSubpageDialog('水印', wrap, host);
		this.refreshValues();
	}

	open(): void {
		this.subpage.open();
		this.textInput.focus();
	}

	close(): void {
		this.subpage.close();
	}

	private buildDivider(): HTMLDivElement {
		const divider = document.createElement('div');
		divider.className = 'writer-watermark-page__header-divider';
		divider.setAttribute('aria-hidden', 'true');
		return divider;
	}

	private buildScrollContent(): HTMLDivElement {
		const scroll = document.createElement('div');
		scroll.className = 'writer-watermark-page__scroll';

		scroll.appendChild(this.buildSection('文本', this.buildTextField()));
		scroll.appendChild(this.buildSectionSpacer());
		scroll.appendChild(this.buildSection('字体', this.buildFontRow()));
		scroll.appendChild(this.buildSectionSpacer());
		scroll.appendChild(
			this.buildSection(
				'角度',
				this.buildStepperRow(true, WRITER_WATERMARK_ANGLE_ICON, '度'),
			),
		);
		scroll.appendChild(this.buildSectionSpacer());
		scroll.appendChild(
			this.buildSection(
				'透明度',
				this.buildStepperRow(false, WRITER_WATERMARK_OPACITY_ICON, '%'),
			),
		);
		return scroll;
	}

	private buildSection(title: string, body: HTMLElement): HTMLDivElement {
		const section = document.createElement('div');
		section.className = 'writer-watermark-page__section';
		const caption = document.createElement('div');
		caption.className = 'writer-watermark-page__section-title';
		caption.textContent = title;
		section.appendChild(caption);
		section.appendChild(body);
		return section;
	}

	private buildSectionSpacer(): HTMLDivElement {
		const spacer = document.createElement('div');
		spacer.className = 'writer-watermark-page__section-spacer';
		spacer.setAttribute('aria-hidden', 'true');
		return spacer;
	}

	private buildTextField(): HTMLInputElement {
		this.textInput = document.createElement('input');
		this.textInput.type = 'text';
		this.textInput.className = 'writer-watermark-page__text-field';
		this.textInput.placeholder = '水印文本';
		this.textInput.setAttribute('aria-label', '水印文本');
		return this.textInput;
	}

	private buildFontRow(): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-watermark-form__font-row writer-watermark-page__card-row';
		this.fontValueView = document.createElement('span');
		this.fontValueView.className = 'writer-watermark-page__font-value';
		row.appendChild(this.fontValueView);
		const chevron = document.createElement('span');
		chevron.className = 'writer-watermark-page__chevron';
		chevron.textContent = '›';
		row.appendChild(chevron);
		row.onclick = () => this.openFontPicker();
		return row;
	}

	private buildStepperRow(
		angleRow: boolean,
		iconSvg: string,
		suffix: string,
	): HTMLDivElement {
		const track = document.createElement('div');
		track.className = 'writer-watermark-page__stepper-track';

		const valueBox = document.createElement('div');
		valueBox.className = 'writer-watermark-page__stepper-value';

		const iconWrap = document.createElement('span');
		iconWrap.className = 'writer-watermark-page__stepper-icon';
		iconWrap.innerHTML = iconSvg;
		valueBox.appendChild(iconWrap);

		const value = document.createElement('span');
		value.className = 'writer-watermark-page__stepper-number';
		if (angleRow) {
			this.angleValueView = value;
		} else {
			this.opacityValueView = value;
		}
		valueBox.appendChild(value);

		const unit = document.createElement('span');
		unit.className = 'writer-watermark-page__stepper-unit';
		unit.textContent = suffix;
		valueBox.appendChild(unit);

		const minus = this.stepperButton('减小', () =>
			this.adjustValue(angleRow, -1),
		);
		const plus = this.stepperButton('增大', () =>
			this.adjustValue(angleRow, 1),
		);

		track.appendChild(valueBox);
		track.appendChild(minus);
		track.appendChild(plus);
		return track;
	}

	private stepperButton(
		ariaLabel: string,
		handler: () => void,
	): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.className = 'writer-watermark-form__stepper-btn';
		button.setAttribute('aria-label', ariaLabel);
		button.innerHTML = WRITER_WATERMARK_STEPPER_MINUS_ICON;
		if (ariaLabel.indexOf('增大') >= 0) {
			button.innerHTML = WRITER_WATERMARK_STEPPER_PLUS_ICON;
		}
		button.onclick = handler;
		return button;
	}

	private buildConfirmButton(): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.className = 'writer-watermark-form__confirm';
		button.textContent = '确定';
		button.setAttribute('aria-label', '确定');
		button.onclick = () => this.confirm();
		return button;
	}

	private openFontPicker(): void {
		const options = this.fontOptions.map((name) => ({
			label: name,
			value: name,
		}));
		const picker = new WriterEditorChooseDialog(
			'字体',
			options,
			(option) => {
				this.font = option.value;
				this.refreshValues();
			},
			this.font,
			this.host,
		);
		picker.open();
	}

	private adjustValue(angleRow: boolean, delta: number): void {
		if (angleRow) {
			this.angle = WriterEditorWatermarkDialog.clamp(
				this.angle + delta,
				WriterEditorWatermarkDialog.MIN_ANGLE,
				WriterEditorWatermarkDialog.MAX_ANGLE,
			);
		} else {
			this.transparency = WriterEditorWatermarkDialog.clamp(
				this.transparency + delta,
				WriterEditorWatermarkDialog.MIN_TRANSPARENCY,
				WriterEditorWatermarkDialog.MAX_TRANSPARENCY,
			);
		}
		this.refreshValues();
	}

	private refreshValues(): void {
		if (this.textInput && this.textInput.value !== this.text) {
			this.textInput.value = this.text;
		}
		if (this.fontValueView) {
			this.fontValueView.textContent = this.font;
		}
		if (this.angleValueView) {
			this.angleValueView.textContent = String(this.angle);
		}
		if (this.opacityValueView) {
			this.opacityValueView.textContent = String(this.transparency);
		}
	}

	private confirm(): void {
		this.text = (this.textInput.value || '').trim();
		this.controller.applyWatermark(
			this.text,
			this.angle,
			this.transparency,
			this.font,
		);
		if (this.onApplied) {
			this.onApplied({
				text: this.text,
				font: this.font,
				angle: this.angle,
				transparency: this.transparency,
				enabled: !!this.text,
			});
		}
		this.subpage.close();
	}

	private static clamp(value: number, min: number, max: number): number {
		return Math.max(min, Math.min(max, value));
	}
}

const WRITER_WATERMARK_ANGLE_ICON =
	'<svg viewBox="0 0 48 48" width="24" height="24" aria-hidden="true" xmlns="http://www.w3.org/2000/svg">' +
	'<path d="M8 4V40H44" fill="none" stroke="#333" stroke-width="2" stroke-linecap="round"/>' +
	'<path d="M28 40C28 28.95 19.05 20 8 20V40H28Z" fill="none" stroke="#333" stroke-width="2" stroke-linecap="round"/>' +
	'</svg>';

const WRITER_WATERMARK_OPACITY_ICON =
	'<svg viewBox="0 0 48 48" width="24" height="24" aria-hidden="true" xmlns="http://www.w3.org/2000/svg">' +
	'<path d="M24 36c11.046 0 20-12 20-12s-8.954-12-20-12S4 24 4 24s8.954 12 20 12z" fill="none" stroke="#333" stroke-width="2" stroke-linejoin="round"/>' +
	'<path d="M24 29a5 5 0 1 0 0-10 5 5 0 0 0 0 10z" fill="none" stroke="#333" stroke-width="2" stroke-linejoin="round"/>' +
	'</svg>';

const WRITER_WATERMARK_STEPPER_MINUS_ICON =
	'<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M6 12h12" stroke="#333" stroke-width="2" stroke-linecap="round"/></svg>';

const WRITER_WATERMARK_STEPPER_PLUS_ICON =
	'<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M12 6v12M6 12h12" stroke="#333" stroke-width="2" stroke-linecap="round"/></svg>';
