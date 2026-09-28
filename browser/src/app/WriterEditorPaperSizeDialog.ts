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

	private readonly subpage: { open(): void; close(): void };
	private readonly controller: WriterEditorController;
	private widthCm = 21.0;
	private heightCm = 29.7;
	private widthValueView!: HTMLSpanElement;
	private heightValueView!: HTMLSpanElement;

	constructor(
		controller: WriterEditorController,
		host?: WriterEditorInlineSubpageHost | null,
	) {
		this.controller = controller;

		const wrap = document.createElement('div');
		wrap.className = 'writer-subpage-form';

		const scroll = document.createElement('div');
		scroll.className = 'writer-subpage-form__scroll';
		scroll.appendChild(this.buildStepperSection('宽度', true));
		scroll.appendChild(this.sectionSpacer());
		scroll.appendChild(this.buildStepperSection('高度', false));

		const hint = document.createElement('p');
		hint.className = 'writer-subpage-form__hint';
		hint.textContent = '尺寸范围 5.0 – 120.0 cm，步长 0.1 cm';
		scroll.appendChild(hint);
		wrap.appendChild(scroll);

		const applyButton = document.createElement('button');
		applyButton.type = 'button';
		applyButton.className = 'writer-subpage-form__confirm';
		applyButton.textContent = '应用';
		applyButton.setAttribute('aria-label', '应用自定义纸张尺寸');
		applyButton.onclick = () => this.apply();
		wrap.appendChild(applyButton);

		this.subpage = writerEditorMountSubpageDialog('自定义纸张', wrap, host);
		this.refreshValues();
	}

	open(): void {
		this.subpage.open();
	}

	close(): void {
		this.subpage.close();
	}

	private apply(): void {
		this.controller.applyCustomPaperSize(this.widthCm, this.heightCm);
		this.subpage.close();
	}

	private sectionSpacer(): HTMLDivElement {
		const spacer = document.createElement('div');
		spacer.className = 'writer-subpage-form__section-spacer';
		spacer.setAttribute('aria-hidden', 'true');
		return spacer;
	}

	private buildStepperSection(labelText: string, widthRow: boolean): HTMLDivElement {
		const section = document.createElement('div');
		const caption = document.createElement('div');
		caption.className = 'writer-subpage-form__section-title';
		caption.textContent = labelText;
		section.appendChild(caption);
		section.appendChild(this.buildStepperTrack(widthRow));
		return section;
	}

	private buildStepperTrack(widthRow: boolean): HTMLDivElement {
		const track = document.createElement('div');
		track.className = 'writer-subpage-form__stepper-track';

		const valueBox = document.createElement('div');
		valueBox.className = 'writer-subpage-form__stepper-value';

		const valueLabel = document.createElement('span');
		valueLabel.className = 'writer-subpage-form__stepper-number';
		if (widthRow) {
			this.widthValueView = valueLabel;
		} else {
			this.heightValueView = valueLabel;
		}
		valueBox.appendChild(valueLabel);

		const minus = this.stepperButton('减小' + (widthRow ? '宽度' : '高度'), () => {
			this.adjust(widthRow, -WriterEditorPaperSizeDialog.STEP_CM);
		});
		const plus = this.stepperButton('增大' + (widthRow ? '宽度' : '高度'), () => {
			this.adjust(widthRow, WriterEditorPaperSizeDialog.STEP_CM);
		});

		track.appendChild(valueBox);
		track.appendChild(minus);
		track.appendChild(plus);
		return track;
	}

	private adjust(widthRow: boolean, delta: number): void {
		if (widthRow) {
			this.widthCm = WriterEditorPaperSizeDialog.clampCm(this.widthCm + delta);
		} else {
			this.heightCm = WriterEditorPaperSizeDialog.clampCm(this.heightCm + delta);
		}
		this.refreshValues();
	}

	private refreshValues(): void {
		if (this.widthValueView) {
			this.widthValueView.textContent =
				WriterEditorPaperSizeDialog.formatCm(this.widthCm) + ' cm';
		}
		if (this.heightValueView) {
			this.heightValueView.textContent =
				WriterEditorPaperSizeDialog.formatCm(this.heightCm) + ' cm';
		}
	}

	private stepperButton(ariaLabel: string, handler: () => void): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.className = 'writer-subpage-form__stepper-btn';
		button.setAttribute('aria-label', ariaLabel);
		button.innerHTML =
			ariaLabel.indexOf('增大') >= 0
				? '<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M12 6v12M6 12h12" stroke="#333" stroke-width="2" stroke-linecap="round"/></svg>'
				: '<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M6 12h12" stroke="#333" stroke-width="2" stroke-linecap="round"/></svg>';
		button.onclick = handler;
		return button;
	}

	private static formatCm(cm: number): string {
		return cm.toFixed(1);
	}

	private static clampCm(value: number): number {
		return Math.max(
			WriterEditorPaperSizeDialog.MIN_CM,
			Math.min(WriterEditorPaperSizeDialog.MAX_CM, value),
		);
	}
}
