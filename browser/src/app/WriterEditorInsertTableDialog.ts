/*
 * Writer insert-table dialog (iOS).
 *
 * Row/column steppers (1-20, default 2×2) plus an insert button. Impress uses
 * the Figma "表格" sheet layout (PPT-编辑模式); the Writer branch is unchanged.
 * Dispatches via WriterEditorController's InsertTable Columns x Rows command.
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
		impress = false,
	) {
		this.controller = controller;
		if (impress) {
			this.subpage = writerEditorMountSubpageDialog(
				'表格',
				this.impressBody(),
				host,
			);
			return;
		}

		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:14px;';

		content.appendChild(
			this.stepperRow(
				'行数',
				() => this.rowCount,
				(value: number) => {
					this.rowCount = value;
				},
			),
		);
		content.appendChild(
			this.stepperRow(
				'列数',
				() => this.columnCount,
				(value: number) => {
					this.columnCount = value;
				},
			),
		);

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

		this.subpage = writerEditorMountSubpageDialog('插入表格', content, host);
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

	/**
	 * Impress insert-table body, matched to the Figma "表格" sheet
	 * (PPT-编辑模式, node 1689:53196). Figma px are 2x; values below are CSS px (pt).
	 * 行/列 track 343x56 #f2f3f5 r12; value box 255x40 #e5e6e8 r8; 24pt steppers;
	 * pill insert button 343x35 #ec5d1f.
	 */
	private impressBody(): HTMLElement {
		const rowIcon =
			'<svg viewBox="0 0 48 48" width="24" height="24" fill="none" stroke="#333" stroke-width="2.667" aria-hidden="true"><rect x="4" y="6" width="40" height="36" rx="2"/><path d="M4 18h40M4 30h40"/></svg>';
		const columnIcon =
			'<svg viewBox="0 0 48 48" width="24" height="24" fill="none" stroke="#333" stroke-width="2.667" aria-hidden="true"><rect x="4" y="6" width="40" height="36" rx="2"/><path d="M18 6v36M30 6v36"/></svg>';
		const minusIcon =
			'<svg viewBox="0 0 48 48" width="24" height="24" fill="none" aria-hidden="true"><path d="M5 24.5h39" stroke="#333" stroke-width="3" stroke-linecap="round"/></svg>';
		const plusIcon =
			'<svg viewBox="0 0 48 48" width="24" height="24" fill="none" aria-hidden="true"><path d="M24.5 5v39M5 24.5h39" stroke="#333" stroke-width="3" stroke-linecap="round"/></svg>';

		const body = document.createElement('div');
		body.style.cssText = 'padding:20px 16px 16px;box-sizing:border-box;';
		['行', '列'].forEach((label, index) => {
			const section = document.createElement('div');
			section.style.cssText = 'margin-bottom:15px;';
			const caption = document.createElement('div');
			caption.textContent = label;
			caption.style.cssText =
				'font-size:16px;line-height:19px;font-weight:400;color:#101010;margin-bottom:6px;';
			section.appendChild(caption);

			const track = document.createElement('div');
			track.style.cssText =
				'display:flex;align-items:center;gap:12px;box-sizing:border-box;height:56px;padding:8px;background:#f2f3f5;border-radius:12px;';
			const valueBox = document.createElement('div');
			valueBox.style.cssText =
				'display:flex;align-items:center;flex:1;box-sizing:border-box;height:40px;padding:0 12px;background:#e5e6e8;border-radius:8px;';
			const icon = document.createElement('span');
			icon.style.cssText =
				'display:block;flex-shrink:0;width:24px;height:24px;';
			icon.innerHTML = index === 0 ? rowIcon : columnIcon;
			valueBox.appendChild(icon);
			const value = document.createElement('span');
			value.style.cssText =
				'margin-left:60px;font-size:16px;font-weight:400;color:#333;';
			valueBox.appendChild(value);
			track.appendChild(valueBox);

			const minus = this.impressStepButton(minusIcon, '减少' + label);
			const plus = this.impressStepButton(plusIcon, '增加' + label);
			track.appendChild(minus);
			track.appendChild(plus);

			const refresh = () => {
				const count = index === 0 ? this.rowCount : this.columnCount;
				value.textContent = String(count);
				minus.disabled = count <= WriterEditorInsertTableDialog.MIN_COUNT;
				plus.disabled = count >= WriterEditorInsertTableDialog.MAX_COUNT;
				minus.style.opacity = minus.disabled ? '0.4' : '1';
				plus.style.opacity = plus.disabled ? '0.4' : '1';
			};
			const adjust = (delta: number) => {
				if (index === 0)
					this.rowCount = WriterEditorInsertTableDialog.clamp(
						this.rowCount + delta,
					);
				else
					this.columnCount = WriterEditorInsertTableDialog.clamp(
						this.columnCount + delta,
					);
				refresh();
			};
			minus.onclick = () => adjust(-1);
			plus.onclick = () => adjust(1);
			refresh();
			section.appendChild(track);
			body.appendChild(section);
		});
		const insert = document.createElement('button');
		insert.type = 'button';
		insert.textContent = '插入表格';
		insert.style.cssText =
			'display:block;box-sizing:border-box;width:100%;height:35px;margin-top:15px;padding:0;border:0;border-radius:40px;background:#ec5d1f;color:#fff;font:inherit;font-weight:400;font-size:16px;';
		insert.onclick = () => this.insert();
		body.appendChild(insert);
		return body;
	}

	/** Impress stepper: bare 24pt glyph, no chrome (Figma 48px minus/plus frames). */
	private impressStepButton(svg: string, ariaLabel: string): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.setAttribute('aria-label', ariaLabel);
		button.innerHTML = svg;
		button.style.cssText =
			'display:flex;align-items:center;justify-content:center;flex-shrink:0;width:24px;height:24px;padding:0;margin:0;border:0;background:transparent;';
		return button;
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
			valueLabel.textContent = String(
				WriterEditorInsertTableDialog.clamp(getValue()),
			);
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
