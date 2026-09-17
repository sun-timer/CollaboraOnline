/*
 * Calc data validation inline subpage (Android CalcDataValidationController parity).
 */

type CalcValidationTab = 'criteria' | 'inputHelp' | 'errorAlert';

class CalcEditorDataValidationDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly sendUno: (command: string) => void;
	private readonly host: WriterEditorInlineSubpageHost | null | undefined;
	private readonly state: CalcEditorValidationState;
	private activeTab: CalcValidationTab = 'criteria';
	private loadRequested = false;
	private optionPicker: WriterEditorChooseDialog | null = null;

	private segmentButtons: HTMLButtonElement[] = [];
	private criteriaBody!: HTMLDivElement;
	private inputHelpBody!: HTMLDivElement;
	private errorAlertBody!: HTMLDivElement;
	private allowValueEl!: HTMLSpanElement;
	private dataValueEl!: HTMLSpanElement;
	private errorActionValueEl!: HTMLSpanElement;
	private allowBlankRow!: HTMLElement;
	private dataSection!: HTMLDivElement;
	private valueSection!: HTMLDivElement;
	private maxSection!: HTMLDivElement;
	private listSection!: HTMLDivElement;
	private listExtraSection!: HTMLDivElement;
	private rangeExtraSection!: HTMLDivElement;
	private customExtraSection!: HTMLDivElement;
	private errorContentSection!: HTMLDivElement;
	private macroSection!: HTMLDivElement;
	private valueInput!: HTMLInputElement;
	private maxInput!: HTMLInputElement;
	private listInput!: HTMLTextAreaElement;
	private customFormulaInput!: HTMLInputElement;
	private inputHelpTitleInput!: HTMLInputElement;
	private inputHelpTextInput!: HTMLTextAreaElement;
	private errorTitleInput!: HTMLInputElement;
	private errorMessageInput!: HTMLTextAreaElement;
	private macroUrlInput!: HTMLInputElement;

	constructor(
		sendUno: (command: string) => void,
		host?: WriterEditorInlineSubpageHost | null,
	) {
		this.sendUno = sendUno;
		this.host = host;
		this.state = calcEditorValidationDefaultState();

		const root = document.createElement('div');
		root.className = 'writer-function-calc-validation';

		root.appendChild(this.buildSegmentControl());

		const bodies = document.createElement('div');
		bodies.className = 'writer-function-calc-validation__bodies';
		this.criteriaBody = this.buildCriteriaBody();
		this.inputHelpBody = this.buildInputHelpBody();
		this.errorAlertBody = this.buildErrorAlertBody();
		bodies.appendChild(this.criteriaBody);
		bodies.appendChild(this.inputHelpBody);
		bodies.appendChild(this.errorAlertBody);
		root.appendChild(bodies);

		const confirm = document.createElement('button');
		confirm.type = 'button';
		confirm.className = 'writer-function-calc-validation__confirm';
		confirm.textContent = '确定';
		confirm.setAttribute('aria-label', '确定');
		confirm.onclick = () => this.onConfirm();
		root.appendChild(confirm);

		this.subpage = writerEditorMountSubpageDialog('数据有效性', root, host);
		this.showTab('criteria');
		this.refreshDynamicFields();
		this.refreshValueLabels();
	}

	open(): void {
		this.subpage.open();
		if (!this.loadRequested) {
			this.loadRequested = true;
			window.setTimeout(() => this.requestLoadCurrentState(), 120);
		}
	}

	close(): void {
		if (this.optionPicker) {
			this.optionPicker.close();
			this.optionPicker = null;
		}
		this.subpage.close();
	}

	private requestLoadCurrentState(): void {
		CalcEditorValidationBridge.loadCurrent(this.state, this.sendUno, () => {
			this.applyStateToInputs();
			this.refreshDynamicFields();
			this.refreshValueLabels();
		});
	}

	private onConfirm(): void {
		this.syncStateFromInputs();
		CalcEditorValidationBridge.apply(this.state, this.sendUno);
		this.close();
	}

	private syncStateFromInputs(): void {
		if (CalcEditorValidationCatalog.isCustomAllow(this.state.allowIndex)) {
			this.state.minValue = this.customFormulaInput.value;
		} else if (CalcEditorValidationCatalog.isListAllow(this.state.allowIndex)) {
			this.state.listEntries = this.listInput.value;
		} else {
			this.state.minValue = this.valueInput.value;
		}
		this.state.maxValue = this.maxInput.value;
		this.state.inputHelpTitle = this.inputHelpTitleInput.value;
		this.state.inputHelpText = this.inputHelpTextInput.value;
		this.state.errorTitle = this.errorTitleInput.value;
		this.state.errorMessage = this.errorMessageInput.value;
		this.state.macroUrl = this.macroUrlInput.value;
	}

	private applyStateToInputs(): void {
		this.valueInput.value = this.state.minValue;
		this.customFormulaInput.value = this.state.minValue;
		this.maxInput.value = this.state.maxValue;
		this.listInput.value = this.state.listEntries;
		this.inputHelpTitleInput.value = this.state.inputHelpTitle;
		this.inputHelpTextInput.value = this.state.inputHelpText;
		this.errorTitleInput.value = this.state.errorTitle;
		this.errorMessageInput.value = this.state.errorMessage;
		this.macroUrlInput.value = this.state.macroUrl || this.state.errorTitle;
	}

	private buildSegmentControl(): HTMLElement {
		const track = document.createElement('div');
		track.className = 'writer-function-calc-validation__segment-track';
		const labels: { tab: CalcValidationTab; text: string }[] = [
			{ tab: 'criteria', text: '条件' },
			{ tab: 'inputHelp', text: '输入帮助' },
			{ tab: 'errorAlert', text: '错误警告' },
		];
		this.segmentButtons = labels.map(({ tab, text }) => {
			const btn = document.createElement('button');
			btn.type = 'button';
			btn.className = 'writer-function-calc-validation__segment';
			btn.textContent = text;
			btn.onclick = () => this.showTab(tab);
			track.appendChild(btn);
			return btn;
		});
		return track;
	}

	private showTab(tab: CalcValidationTab): void {
		this.activeTab = tab;
		const tabs: CalcValidationTab[] = ['criteria', 'inputHelp', 'errorAlert'];
		this.segmentButtons.forEach((btn, i) => {
			btn.classList.toggle(
				'writer-function-calc-validation__segment--active',
				tabs[i] === tab,
			);
		});
		this.criteriaBody.hidden = tab !== 'criteria';
		this.inputHelpBody.hidden = tab !== 'inputHelp';
		this.errorAlertBody.hidden = tab !== 'errorAlert';
	}

	private buildCriteriaBody(): HTMLDivElement {
		const scroll = document.createElement('div');
		scroll.className = 'writer-function-calc-validation__scroll';

		this.allowValueEl = document.createElement('span');
		scroll.appendChild(
			this.pickerRow('允许', this.allowValueEl, () => this.openOptionPicker('allow')),
		);

		this.allowBlankRow = this.checkboxRow('允许空白单元格', () => this.state.allowEmpty, (v) => {
			this.state.allowEmpty = v;
		});
		scroll.appendChild(this.allowBlankRow);

		this.dataValueEl = document.createElement('span');
		this.dataSection = this.pickerRow('数据', this.dataValueEl, () =>
			this.openOptionPicker('data'),
		);
		scroll.appendChild(this.dataSection);

		this.valueInput = this.textField('');
		this.valueSection = this.labeledField('数值', this.valueInput);
		scroll.appendChild(this.valueSection);

		this.maxInput = this.textField('');
		this.maxSection = this.labeledField('最大值', this.maxInput);
		scroll.appendChild(this.maxSection);

		this.listInput = document.createElement('textarea');
		this.listInput.className = 'writer-function-calc-validation__textarea';
		this.listInput.rows = 4;
		this.listSection = this.labeledField('来源', this.listInput);
		scroll.appendChild(this.listSection);

		this.listExtraSection = document.createElement('div');
		this.listExtraSection.className = 'writer-function-calc-validation__group';
		this.listExtraSection.appendChild(
			this.checkboxRow('提供下拉列表', () => this.state.showDropdownList, (v) => {
				this.state.showDropdownList = v;
			}),
		);
		this.listExtraSection.appendChild(
			this.checkboxRow('升序排序', () => this.state.sortAscending, (v) => {
				this.state.sortAscending = v;
			}),
		);
		this.listExtraSection.appendChild(
			this.checkboxRow('区分大小写', () => this.state.caseSensitive, (v) => {
				this.state.caseSensitive = v;
			}),
		);
		scroll.appendChild(this.listExtraSection);

		this.rangeExtraSection = document.createElement('div');
		this.rangeExtraSection.className = 'writer-function-calc-validation__group';
		this.rangeExtraSection.appendChild(
			this.checkboxRow('提供下拉列表', () => this.state.showDropdownList, (v) => {
				this.state.showDropdownList = v;
			}),
		);
		scroll.appendChild(this.rangeExtraSection);

		this.customFormulaInput = this.textField('');
		this.customExtraSection = document.createElement('div');
		this.customExtraSection.className = 'writer-function-calc-validation__group';
		this.customExtraSection.appendChild(
			this.labeledField('公式', this.customFormulaInput),
		);
		this.customExtraSection.appendChild(
			this.checkboxRow('区分大小写', () => this.state.caseSensitive, (v) => {
				this.state.caseSensitive = v;
			}),
		);
		scroll.appendChild(this.customExtraSection);

		return scroll;
	}

	private buildInputHelpBody(): HTMLDivElement {
		const scroll = document.createElement('div');
		scroll.className = 'writer-function-calc-validation__scroll';
		scroll.appendChild(
			this.checkboxRow('选中单元格时显示输入提示', () => this.state.showInputHelp, (v) => {
				this.state.showInputHelp = v;
			}),
		);
		this.inputHelpTitleInput = this.textField('');
		scroll.appendChild(this.labeledField('标题', this.inputHelpTitleInput));
		this.inputHelpTextInput = document.createElement('textarea');
		this.inputHelpTextInput.className = 'writer-function-calc-validation__textarea';
		this.inputHelpTextInput.rows = 4;
		scroll.appendChild(this.labeledField('输入提示', this.inputHelpTextInput));
		return scroll;
	}

	private buildErrorAlertBody(): HTMLDivElement {
		const scroll = document.createElement('div');
		scroll.className = 'writer-function-calc-validation__scroll';
		scroll.appendChild(
			this.checkboxRow('处理无效值', () => this.state.showErrorAlert, (v) => {
				this.state.showErrorAlert = v;
			}),
		);

		this.errorActionValueEl = document.createElement('span');
		scroll.appendChild(
			this.pickerRow('操作', this.errorActionValueEl, () =>
				this.openOptionPicker('errorAction'),
			),
		);

		this.macroUrlInput = this.textField('');
		this.macroSection = this.labeledField('宏', this.macroUrlInput);
		scroll.appendChild(this.macroSection);

		this.errorTitleInput = this.textField('');
		this.errorMessageInput = document.createElement('textarea');
		this.errorMessageInput.className = 'writer-function-calc-validation__textarea';
		this.errorMessageInput.rows = 3;

		this.errorContentSection = document.createElement('div');
		this.errorContentSection.className = 'writer-function-calc-validation__group';
		this.errorContentSection.appendChild(this.labeledField('标题', this.errorTitleInput));
		this.errorContentSection.appendChild(
			this.labeledField('错误信息', this.errorMessageInput),
		);
		scroll.appendChild(this.errorContentSection);
		return scroll;
	}

	private openOptionPicker(kind: 'allow' | 'data' | 'errorAction'): void {
		let title = '';
		let options: WriterChooseOption[] = [];
		let selected = '';
		if (kind === 'allow') {
			title = '允许';
			options = CalcEditorValidationCatalog.ALLOW_OPTIONS.map((o) => ({
				label: o.label,
				value: String(o.index),
			}));
			selected = CalcEditorValidationCatalog.findAllowByIndex(this.state.allowIndex).label;
		} else if (kind === 'data') {
			title = '数据';
			options = CalcEditorValidationCatalog.DATA_OPTIONS.map((o) => ({
				label: o.label,
				value: String(o.index),
			}));
			selected = CalcEditorValidationCatalog.findDataByIndex(this.state.dataIndex).label;
		} else {
			title = '操作';
			options = CalcEditorValidationCatalog.ERROR_ACTION_OPTIONS.map((o) => ({
				label: o.label,
				value: String(o.index),
			}));
			selected = CalcEditorValidationCatalog.findErrorActionByIndex(
				this.state.errorActionIndex,
			).label;
		}
		this.optionPicker = new WriterEditorChooseDialog(
			title,
			options,
			(option) => {
				const index = Number.parseInt(option.value, 10);
				if (kind === 'allow') {
					this.state.allowIndex = index;
				} else if (kind === 'data') {
					this.state.dataIndex = index;
				} else {
					this.state.errorActionIndex = index;
				}
				this.refreshDynamicFields();
				this.refreshValueLabels();
				this.optionPicker = null;
			},
			selected,
			this.host,
		);
		this.optionPicker.open();
	}

	private refreshValueLabels(): void {
		this.allowValueEl.textContent = CalcEditorValidationCatalog.findAllowByIndex(
			this.state.allowIndex,
		).label;
		this.dataValueEl.textContent = CalcEditorValidationCatalog.findDataByIndex(
			this.state.dataIndex,
		).label;
		this.errorActionValueEl.textContent =
			CalcEditorValidationCatalog.findErrorActionByIndex(
				this.state.errorActionIndex,
			).label;
	}

	private refreshDynamicFields(): void {
		const allow = this.state.allowIndex;
		const isAny = allow === 0;
		const isList = CalcEditorValidationCatalog.isListAllow(allow);
		const isRange = CalcEditorValidationCatalog.isRangeAllow(allow);
		const isCustom = CalcEditorValidationCatalog.isCustomAllow(allow);
		const needsData = CalcEditorValidationCatalog.needsDataOperator(allow);

		this.allowBlankRow.hidden = isAny && !isCustom;
		this.dataSection.hidden = !needsData;
		this.valueSection.hidden = isAny || isList || isRange || isCustom;
		this.maxSection.hidden =
			!needsData ||
			isList ||
			isRange ||
			isCustom ||
			!CalcEditorValidationCatalog.needsBetweenValues(this.state.dataIndex);
		this.listSection.hidden = !isList;
		this.listExtraSection.hidden = !isList;
		this.rangeExtraSection.hidden = !isRange;
		this.customExtraSection.hidden = !isCustom;

		this.macroSection.hidden = this.state.errorActionIndex !== 3;
		this.errorContentSection.hidden =
			this.state.errorActionIndex === 3 || this.state.errorActionIndex === 4;
	}

	private pickerRow(
		label: string,
		valueEl: HTMLSpanElement,
		onClick: () => void,
	): HTMLDivElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-calc-validation__picker';
		const lab = document.createElement('span');
		lab.className = 'writer-function-calc-validation__picker-label';
		lab.textContent = label;
		valueEl.className = 'writer-function-calc-validation__picker-value';
		const chevron = document.createElement('span');
		chevron.className = 'writer-function-calc-validation__picker-chevron';
		chevron.setAttribute('aria-hidden', 'true');
		chevron.textContent = '›';
		row.appendChild(lab);
		row.appendChild(valueEl);
		row.appendChild(chevron);
		row.onclick = onClick;
		const wrap = document.createElement('div');
		wrap.className = 'writer-function-calc-validation__field-block';
		wrap.appendChild(row);
		return wrap;
	}

	private labeledField(label: string, control: HTMLElement): HTMLDivElement {
		const wrap = document.createElement('div');
		wrap.className = 'writer-function-calc-validation__field-block';
		const lab = document.createElement('div');
		lab.className = 'writer-function-calc-validation__field-label';
		lab.textContent = label;
		wrap.appendChild(lab);
		wrap.appendChild(control);
		return wrap;
	}

	private textField(placeholder: string): HTMLInputElement {
		const input = document.createElement('input');
		input.type = 'text';
		input.className = 'writer-function-calc-validation__input';
		input.placeholder = placeholder;
		return input;
	}

	private checkboxRow(
		label: string,
		getValue: () => boolean,
		setValue: (v: boolean) => void,
	): HTMLElement {
		const row = document.createElement('label');
		row.className = 'writer-function-calc-validation__checkbox-row';
		const box = document.createElement('input');
		box.type = 'checkbox';
		box.className = 'writer-function-calc-validation__checkbox';
		box.checked = getValue();
		box.onchange = () => setValue(box.checked);
		const text = document.createElement('span');
		text.textContent = label;
		row.appendChild(box);
		row.appendChild(text);
		return row;
	}
}
