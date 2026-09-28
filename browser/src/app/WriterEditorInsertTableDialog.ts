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

	private readonly subpage: { open(): void; close(): void };
	private readonly controller: WriterEditorController;
	private rowCount = 2;
	private columnCount = 2;

	constructor(
		controller: WriterEditorController,
		host?: WriterEditorInlineSubpageHost | null,
	) {
		this.controller = controller;

		const wrap = document.createElement('div');
		wrap.className = 'writer-subpage-form';

		const scroll = document.createElement('div');
		scroll.className = 'writer-subpage-form__scroll';
		scroll.appendChild(
			this.buildStepperSection(
				'行',
				'impress-table-rows',
				true,
			),
		);
		scroll.appendChild(this.sectionSpacer());
		scroll.appendChild(
			this.buildStepperSection(
				'列',
				'impress-table-columns',
				false,
			),
		);
		wrap.appendChild(scroll);

		const insertButton = document.createElement('button');
		insertButton.type = 'button';
		insertButton.className = 'writer-subpage-form__confirm';
		insertButton.textContent = '插入表格';
		insertButton.setAttribute('aria-label', '插入表格');
		insertButton.onclick = () => this.insert();
		wrap.appendChild(insertButton);

		this.subpage = writerEditorMountSubpageDialog('表格', wrap, host);
	}

	open(): void {
		this.subpage.open();
	}

	close(): void {
		this.subpage.close();
	}

	private insert(): void {
		this.controller.insertTable(this.columnCount, this.rowCount);
		this.subpage.close();
	}

	private sectionSpacer(): HTMLDivElement {
		const spacer = document.createElement('div');
		spacer.className = 'writer-subpage-form__section-spacer';
		spacer.setAttribute('aria-hidden', 'true');
		return spacer;
	}

	private buildStepperSection(
		labelText: string,
		iconKey: string,
		rows: boolean,
	): HTMLDivElement {
		const section = document.createElement('div');
		const caption = document.createElement('div');
		caption.className = 'writer-subpage-form__section-title';
		caption.textContent = labelText;
		section.appendChild(caption);
		section.appendChild(this.buildStepperTrack(iconKey, rows));
		return section;
	}

	private buildStepperTrack(iconKey: string, rows: boolean): HTMLDivElement {
		const track = document.createElement('div');
		track.className = 'writer-subpage-form__stepper-track';

		const valueBox = document.createElement('div');
		valueBox.className = 'writer-subpage-form__stepper-value';

		const iconWrap = document.createElement('span');
		iconWrap.className = 'writer-subpage-form__stepper-icon';
		const icon = WriterEditorIcons.get(iconKey);
		if (icon) {
			iconWrap.innerHTML = icon;
		}
		valueBox.appendChild(iconWrap);

		const valueLabel = document.createElement('span');
		valueLabel.className = 'writer-subpage-form__stepper-number';
		valueBox.appendChild(valueLabel);

		const minus = this.stepperButton('减小', WRITER_SUBPAGE_STEPPER_MINUS_ICON, () => {
			this.adjustCount(rows, -1);
			valueLabel.textContent = String(
				WriterEditorInsertTableDialog.clamp(
					rows ? this.rowCount : this.columnCount,
				),
			);
		});
		const plus = this.stepperButton('增大', WRITER_SUBPAGE_STEPPER_PLUS_ICON, () => {
			this.adjustCount(rows, 1);
			valueLabel.textContent = String(
				WriterEditorInsertTableDialog.clamp(
					rows ? this.rowCount : this.columnCount,
				),
			);
		});

		valueLabel.textContent = String(
			WriterEditorInsertTableDialog.clamp(rows ? this.rowCount : this.columnCount),
		);

		track.appendChild(valueBox);
		track.appendChild(minus);
		track.appendChild(plus);
		return track;
	}

	private adjustCount(rows: boolean, delta: number): void {
		if (rows) {
			this.rowCount = WriterEditorInsertTableDialog.clamp(this.rowCount + delta);
		} else {
			this.columnCount = WriterEditorInsertTableDialog.clamp(
				this.columnCount + delta,
			);
		}
	}

	private stepperButton(
		ariaLabel: string,
		iconSvg: string,
		handler: () => void,
	): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.className = 'writer-subpage-form__stepper-btn';
		button.setAttribute('aria-label', ariaLabel);
		button.innerHTML = iconSvg;
		button.onclick = handler;
		return button;
	}

	private static clamp(value: number): number {
		return Math.max(
			WriterEditorInsertTableDialog.MIN_COUNT,
			Math.min(WriterEditorInsertTableDialog.MAX_COUNT, value),
		);
	}
}

const WRITER_SUBPAGE_STEPPER_MINUS_ICON =
	'<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M6 12h12" stroke="#333" stroke-width="2" stroke-linecap="round"/></svg>';

const WRITER_SUBPAGE_STEPPER_PLUS_ICON =
	'<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M12 6v12M6 12h12" stroke="#333" stroke-width="2" stroke-linecap="round"/></svg>';
