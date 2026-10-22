/*
 * iOS Calc data validation subpage backed by one Core dialog transaction.
 */

type CalcValidationTab = 'criteria' | 'inputHelp' | 'errorAlert';

class CalcEditorDataValidationDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly sendUno: (command: string) => void;
	private readonly host: WriterEditorInlineSubpageHost | null | undefined;
	private readonly state: CalcEditorValidationState;
	private activeTab: CalcValidationTab = 'criteria';
	private readonly root: HTMLDivElement;
	private readonly confirmButton: HTMLButtonElement;
	private readonly status: HTMLDivElement;
	private context: CalcEditorDialogContext | null = null;
	private observer: MutationObserver | null = null;
	private cancelLoad: (() => void) | null = null;
	private cancelMacroLoad: (() => void) | null = null;
	private generation = 0;
	private ready = false;
	private optionPicker: CalcEditorValidationOptionPicker | null = null;

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
	private actionSection!: HTMLElement;
	private macroBrowseButton!: HTMLButtonElement;
	private readonly checkboxSyncs: Array<() => void> = [];
	private macroHint!: HTMLDivElement;
	private macroPicker: CalcEditorMacroPicker | null = null;
	private macroDisplayName = '';
	private valueInput!: HTMLInputElement;
	private maxInput!: HTMLInputElement;
	private listInput!: HTMLTextAreaElement;
	private customFormulaInput!: HTMLInputElement;
	private inputHelpTitleInput!: HTMLInputElement;
	private inputHelpTextInput!: HTMLTextAreaElement;
	private errorTitleInput!: HTMLInputElement;
	private errorMessageInput!: HTMLTextAreaElement;

	constructor(
		sendUno: (command: string) => void,
		host?: WriterEditorInlineSubpageHost | null,
	) {
		this.sendUno = sendUno;
		this.host = host;
		this.state = calcEditorValidationDefaultState();

		const root = document.createElement('div');
		this.root = root;
		root.className = 'writer-function-calc-validation';
		root.dataset.iosCalcDialog = 'validation';

		this.status = document.createElement('div');
		this.status.className = 'writer-function-calc-validation__status';
		this.status.setAttribute('role', 'status');
		root.appendChild(this.status);
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
		this.confirmButton = confirm;
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
		this.context = calcEditorDialogContext();
		if (!this.context || this.root.isConnected) return;
		const generation = ++this.generation;
		this.ready = false;
		this.setBusy(true);
		this.status.textContent = '正在读取数据有效性…';
		this.status.hidden = false;
		this.subpage.open();
		this.observer = new MutationObserver(() => {
			if (!calcEditorSameDialogContext(this.context)) {
				this.cleanup();
				return;
			}
			if (this.root.isConnected) {
				if (this.optionPicker && !this.optionPicker.isOpen()) this.optionPicker = null;
				if (this.macroPicker && !this.macroPicker.isOpen()) {
					this.cancelMacroLoad?.();
					this.cancelMacroLoad = null;
					this.macroPicker = null;
				}
			}
			if (!this.root.isConnected && !this.optionPicker?.isOpen() && !this.macroPicker?.isOpen()) {
				this.cleanup();
			}
		});
		this.observer.observe(document.body, { childList: true, subtree: true });
		this.cancelLoad = CalcEditorValidationBridge.loadCurrent(this.state, this.sendUno, (loaded) => {
			if (generation !== this.generation || !calcEditorSameDialogContext(this.context)) return;
			this.ready = loaded;
			this.setBusy(!loaded);
			this.status.textContent = loaded ? '' : '读取失败，请返回后重试';
			this.status.hidden = loaded;
			if (!loaded) return;
			this.applyStateToInputs();
			this.refreshDynamicFields();
			this.refreshValueLabels();
		});
	}

	close(): void {
		this.optionPicker?.close();
		this.optionPicker = null;
		this.macroPicker?.close();
		this.macroPicker = null;
		if (this.root.isConnected) this.subpage.close();
		this.cleanup();
	}

	private cleanup(): void {
		++this.generation;
		this.ready = false;
		this.observer?.disconnect();
		this.observer = null;
		this.cancelLoad?.();
		this.cancelLoad = null;
		this.cancelMacroLoad?.();
		this.cancelMacroLoad = null;
		this.context = null;
	}

	private setBusy(busy: boolean): void {
		this.root.setAttribute('aria-busy', String(busy));
		this.root.querySelectorAll<HTMLButtonElement | HTMLInputElement | HTMLTextAreaElement>(
			'button, input, textarea',
		).forEach((control) => { control.disabled = busy; });
		this.confirmButton.disabled = busy;
	}

	private onConfirm(): void {
		if (!this.ready || !calcEditorSameDialogContext(this.context)) return;
		this.syncStateFromInputs();
		if (!CalcEditorValidationBridge.apply(this.state, this.sendUno)) {
			this.status.hidden = false;
			this.status.textContent = '原数据窗口已关闭，请返回后重试';
			return;
		}
		this.cancelLoad = null;
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
		this.checkboxSyncs.forEach((sync) => sync());
		if (!this.macroDisplayName && this.state.macroUrl) {
			this.macroDisplayName = this.state.macroUrl;
		}
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
		[this.criteriaBody, this.inputHelpBody, this.errorAlertBody].forEach((body, index) => {
			this.setVisible(body, tabs[index] === tab);
		});
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
		this.rangeExtraSection.appendChild(
			this.checkboxRow('升序排序', () => this.state.sortAscending, (v) => { this.state.sortAscending = v; }),
		);
		this.rangeExtraSection.appendChild(
			this.checkboxRow('区分大小写', () => this.state.caseSensitive, (v) => { this.state.caseSensitive = v; }),
		);
		scroll.appendChild(this.rangeExtraSection);

		this.customFormulaInput = this.textField('');
		this.customExtraSection = document.createElement('div');
		this.customExtraSection.className = 'writer-function-calc-validation__group';
		this.customExtraSection.appendChild(
			this.checkboxRow('区分大小写', () => this.state.caseSensitive, (v) => {
				this.state.caseSensitive = v;
			}),
		);
		this.customExtraSection.appendChild(this.formulaField(this.customFormulaInput));
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
		scroll.appendChild(this.sectionLabel('内容'));
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
				this.refreshDynamicFields();
			}),
		);

		this.errorActionValueEl = document.createElement('span');
		this.actionSection = document.createElement('div');
		this.actionSection.className = 'writer-function-calc-validation__field-block';
		this.actionSection.appendChild(this.sectionLabel('操作'));
		const actionRow = document.createElement('div');
		actionRow.className = 'writer-function-calc-validation__action-row';
		const actionPicker = document.createElement('button');
		actionPicker.type = 'button';
		actionPicker.className = 'writer-function-calc-validation__picker';
		this.errorActionValueEl.className = 'writer-function-calc-validation__picker-value';
		const chevron = document.createElement('span');
		chevron.className = 'writer-function-calc-validation__picker-chevron';
		chevron.setAttribute('aria-hidden', 'true');
		chevron.textContent = '›';
		actionPicker.appendChild(this.errorActionValueEl);
		actionPicker.appendChild(chevron);
		actionPicker.onclick = () => this.openOptionPicker('errorAction');
		this.macroBrowseButton = document.createElement('button');
		this.macroBrowseButton.type = 'button';
		this.macroBrowseButton.className = 'writer-function-calc-validation__browse';
		this.macroBrowseButton.textContent = '浏览';
		this.macroBrowseButton.setAttribute('aria-label', '浏览宏');
		this.macroBrowseButton.onclick = () => this.openMacroPicker();
		actionRow.appendChild(actionPicker);
		actionRow.appendChild(this.macroBrowseButton);
		this.actionSection.appendChild(actionRow);
		scroll.appendChild(this.actionSection);

		this.macroHint = document.createElement('div');
		this.macroHint.className = 'writer-function-calc-validation__macro-hint';
		scroll.appendChild(this.macroHint);

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
		if (!this.ready || !calcEditorSameDialogContext(this.context)) return;
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
			title = CalcEditorValidationCatalog.findErrorActionByIndex(
				this.state.errorActionIndex,
			).label;
			options = CalcEditorValidationCatalog.ERROR_ACTION_OPTIONS.map((o) => ({
				label: o.label,
				value: String(o.index),
			}));
			selected = CalcEditorValidationCatalog.findErrorActionByIndex(
				this.state.errorActionIndex,
			).label;
		}
		this.optionPicker = new CalcEditorValidationOptionPicker(
			title,
			options,
			(option) => {
				if (!this.ready || !calcEditorSameDialogContext(this.context)) return;
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

		const showDataValue = CalcEditorValidationCatalog.needsDataOperator(allow);
		this.allowBlankRow.hidden = isAny;
		this.dataSection.hidden = !showDataValue;
		this.valueSection.hidden = !(showDataValue || isRange);
		const valueLabel = this.valueSection.querySelector('.writer-function-calc-validation__field-label');
		if (valueLabel) valueLabel.textContent = isRange ? '来源' :
			CalcEditorValidationCatalog.valueLabel(this.state.dataIndex);
		this.maxSection.hidden =
			!showDataValue ||
			!CalcEditorValidationCatalog.needsBetweenValues(this.state.dataIndex);
		this.listSection.hidden = !isList;
		this.listExtraSection.hidden = !isList;
		this.rangeExtraSection.hidden = !isRange;
		this.customExtraSection.hidden = !isCustom;

		const macroAction = this.state.errorActionIndex === 3;
		const silentReject = this.state.errorActionIndex === 4;
		const showError = this.state.showErrorAlert;
		this.actionSection.hidden = !showError;
		this.macroBrowseButton.hidden = !macroAction || !showError;
		this.macroHint.hidden = !macroAction || !showError || !this.state.macroUrl;
		this.macroHint.textContent = this.macroHint.hidden ? '' : this.macroLabel();
		this.errorContentSection.hidden = !showError || silentReject || macroAction;
		[this.allowBlankRow, this.dataSection, this.valueSection, this.maxSection,
			this.listSection, this.listExtraSection, this.rangeExtraSection, this.customExtraSection,
			this.actionSection, this.macroBrowseButton, this.macroHint, this.errorContentSection,
		].forEach((element) => this.setVisible(element, !element.hidden));
		this.checkboxSyncs.forEach((sync) => sync());
	}

	private setVisible(element: HTMLElement, visible: boolean): void {
		element.hidden = !visible;
		element.style.display = visible ? '' : 'none';
	}

	private macroLabel(): string {
		if (this.macroDisplayName && this.macroDisplayName !== this.state.macroUrl) {
			return this.macroDisplayName;
		}
		const url = this.state.macroUrl || '';
		const match = url.match(/script:([^?]+)/);
		return match ? match[1] : url;
	}

	private openMacroPicker(): void {
		if (!this.ready || !calcEditorSameDialogContext(this.context)) return;
		this.cancelMacroLoad?.();
		this.cancelMacroLoad = null;
		if (this.optionPicker) {
			this.optionPicker.close();
			this.optionPicker = null;
		}
		if (this.macroPicker) {
			this.macroPicker.close();
		}
		this.macroPicker = new CalcEditorMacroPicker(
			this.host,
			this.state.macroUrl,
			(item) => {
				if (!this.ready || !calcEditorSameDialogContext(this.context)) return;
				this.cancelMacroLoad?.();
				this.cancelMacroLoad = null;
				this.state.macroUrl = item.uri;
				this.macroDisplayName = item.module
					? item.module + '.' + item.name
					: item.name;
				this.macroPicker = null;
				this.refreshDynamicFields();
			},
		);
		this.macroPicker.open();
		const picker = this.macroPicker;
		const generation = this.generation;
		this.cancelMacroLoad = CalcEditorValidationBridge.loadMacroCatalog(this.sendUno, (items) => {
			if (this.macroPicker === picker && picker.isOpen() && generation === this.generation &&
				calcEditorSameDialogContext(this.context)) {
				picker.setCatalog(items);
			}
		});
	}

	private pickerRow(
		label: string,
		valueEl: HTMLSpanElement,
		onClick: () => void,
	): HTMLDivElement {
		const wrap = document.createElement('div');
		wrap.className = 'writer-function-calc-validation__field-block writer-function-calc-validation__picker-field';
		wrap.appendChild(this.sectionLabel(label));
		const row = document.createElement('button');
		row.type = 'button';
		row.setAttribute('aria-label', label);
		row.className = 'writer-function-calc-validation__picker';
		valueEl.className = 'writer-function-calc-validation__picker-value';
		const chevron = document.createElement('span');
		chevron.className = 'writer-function-calc-validation__picker-chevron';
		chevron.setAttribute('aria-hidden', 'true');
		chevron.textContent = '›';
		row.appendChild(valueEl);
		row.appendChild(chevron);
		row.onclick = onClick;
		wrap.appendChild(row);
		return wrap;
	}

	private sectionLabel(text: string): HTMLDivElement {
		const label = document.createElement('div');
		label.className = 'writer-function-calc-validation__section-label';
		label.textContent = text;
		return label;
	}

	private formulaField(input: HTMLInputElement): HTMLDivElement {
		const wrap = this.labeledField('公式', input);
		const box = document.createElement('div');
		box.className = 'writer-function-calc-validation__fx';
		const prefix = document.createElement('span');
		prefix.className = 'writer-function-calc-validation__fx-prefix';
		prefix.textContent = 'fx';
		input.classList.add('writer-function-calc-validation__fx-input');
		box.appendChild(prefix);
		box.appendChild(input);
		const label = wrap.firstChild;
		wrap.replaceChildren();
		if (label) {
			wrap.appendChild(label);
		}
		wrap.appendChild(box);
		return wrap;
	}

	private labeledField(label: string, control: HTMLElement): HTMLDivElement {
		const wrap = document.createElement('div');
		wrap.className = 'writer-function-calc-validation__field-block';
		const lab = document.createElement('div');
		lab.className = 'writer-function-calc-validation__field-label';
		lab.textContent = label;
		control.setAttribute('aria-label', label);
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
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-calc-validation__checkbox-row';
		const text = document.createElement('span');
		text.textContent = label;
		const mark = document.createElement('span');
		mark.className = 'writer-function-calc-validation__check';
		mark.setAttribute('aria-hidden', 'true');
		const paint = () => {
			const on = getValue();
			mark.classList.toggle('writer-function-calc-validation__check--on', on);
			row.setAttribute('aria-pressed', on ? 'true' : 'false');
		};
		row.onclick = () => {
			setValue(!getValue());
			this.checkboxSyncs.forEach((sync) => sync());
		};
		paint();
		this.checkboxSyncs.push(paint);
		row.appendChild(text);
		row.appendChild(mark);
		return row;
	}
}

/** Owns its body so header-back cancellation is visible to the parent lifetime. */
class CalcEditorValidationOptionPicker {
	private readonly root: HTMLDivElement;
	private readonly subpage: { open(): void; close(): void };

	constructor(title: string, options: WriterChooseOption[], onSelect: (option: WriterChooseOption) => void,
		selected: string, host?: WriterEditorInlineSubpageHost | null) {
		this.root = document.createElement('div');
		this.root.className = 'writer-editor-option-list writer-function-calc-validation__options';
		this.root.dataset.iosCalcDialog = 'validation';
		options.forEach((option) => {
			const row = document.createElement('button');
			row.type = 'button';
			row.className = 'writer-editor-option-row';
			row.setAttribute('aria-pressed', String(option.label === selected || option.value === selected));
			if (option.label === selected || option.value === selected) row.classList.add('writer-editor-option-row--selected');
			const label = document.createElement('span');
			label.className = 'writer-editor-option-row__label';
			label.textContent = option.label;
			const mark = document.createElement('span');
			mark.className = 'writer-editor-option-row__check';
			mark.textContent = '✓';
			mark.setAttribute('aria-hidden', 'true');
			row.append(label, mark);
			row.onclick = () => window.setTimeout(() => {
				if (!this.isOpen()) return;
				this.close();
				onSelect(option);
			}, 0);
			this.root.appendChild(row);
		});
		this.subpage = writerEditorMountSubpageDialog(title, this.root, host);
	}

	isOpen(): boolean { return this.root.isConnected; }
	open(): void { this.subpage.open(); }
	close(): void { if (this.isOpen()) this.subpage.close(); }
}

/** 宏选择器 subpage (Android CalcValidationMacroPickerController). */
class CalcEditorMacroPicker {
	private readonly subpage: { open(): void; close(): void };
	private readonly root: HTMLDivElement;
	private readonly scopeBox: HTMLDivElement;
	private readonly tree: HTMLDivElement;
	private readonly description: HTMLTextAreaElement;
	private readonly onChosen: (item: CalcEditorMacroItem) => void;
	private catalog: CalcEditorMacroItem[] = [];
	private catalogLoaded = false;
	private documentScope = true;
	private searchFilter = '';
	private readonly selectedUrl: string;

	constructor(
		host: WriterEditorInlineSubpageHost | null | undefined,
		currentMacroUrl: string,
		onChosen: (item: CalcEditorMacroItem) => void,
	) {
		this.onChosen = onChosen;
		this.selectedUrl = currentMacroUrl || '';
		if (currentMacroUrl.indexOf('location=application') >= 0) {
			this.documentScope = false;
		}

		this.root = document.createElement('div');
		this.root.className = 'writer-function-calc-macro';
		this.root.dataset.iosCalcDialog = 'validation';

		this.root.appendChild(this.sectionLabel('程序库'));
		this.scopeBox = document.createElement('div');
		this.scopeBox.className = 'writer-function-calc-macro__scope';
		this.root.appendChild(this.scopeBox);

		this.root.appendChild(this.sectionLabel('宏名称'));
		const search = document.createElement('input');
		search.type = 'text';
		search.className = 'writer-function-calc-validation__input';
		search.placeholder = '搜索';
		search.setAttribute('aria-label', '搜索宏');
		search.oninput = () => {
			this.searchFilter = search.value.trim().toLowerCase();
			this.rebuildTree();
		};
		this.root.appendChild(search);

		this.tree = document.createElement('div');
		this.tree.className = 'writer-function-calc-macro__tree';
		this.root.appendChild(this.tree);

		this.root.appendChild(this.sectionLabel('说明'));
		this.description = document.createElement('textarea');
		this.description.className = 'writer-function-calc-validation__textarea';
		this.description.rows = 3;
		this.description.readOnly = true;
		this.description.setAttribute('aria-label', '说明');
		this.root.appendChild(this.description);

		this.subpage = writerEditorMountSubpageDialog('宏选择器', this.root, host);
		this.rebuildScope();
		this.rebuildTree();
	}

	isOpen(): boolean {
		return this.root.isConnected;
	}

	open(): void {
		this.subpage.open();
	}

	close(): void {
		if (!this.root.isConnected) {
			return;
		}
		this.subpage.close();
	}

	setCatalog(items: CalcEditorMacroItem[]): void {
		this.catalogLoaded = true;
		this.catalog = items || [];
		const current = this.catalog.find((item) => item.uri === this.selectedUrl);
		if (current) {
			this.documentScope = current.documentScope;
			this.description.value = current.description || current.uri;
		}
		this.rebuildScope();
		this.rebuildTree();
	}

	private sectionLabel(text: string): HTMLDivElement {
		const label = document.createElement('div');
		label.className = 'writer-function-calc-macro__section';
		label.textContent = text;
		return label;
	}

	private rebuildScope(): void {
		this.scopeBox.replaceChildren();
		this.scopeBox.appendChild(
			this.scopeRow('我的宏', this.documentScope ? '▾' : '▸', false, () => {
				this.documentScope = !this.documentScope;
				this.rebuildScope();
				this.rebuildTree();
			}),
		);
		if (this.documentScope) {
			this.scopeBox.appendChild(this.scopeRow('Standard', '', true, () => undefined));
		}
		this.scopeBox.appendChild(
			this.scopeRow('应用程序的宏', '', false, () => {
				this.documentScope = false;
				this.rebuildScope();
				this.rebuildTree();
			}),
		);
	}

	private scopeRow(
		label: string,
		chevron: string,
		child: boolean,
		onClick: () => void,
	): HTMLButtonElement {
		const row = document.createElement('button');
		row.type = 'button';
		row.className = 'writer-function-calc-macro__scope-row';
		if (child) {
			row.classList.add('writer-function-calc-macro__scope-row--child');
		}
		if (chevron) {
			const mark = document.createElement('span');
			mark.className = 'writer-function-calc-macro__chevron';
			mark.textContent = chevron;
			row.appendChild(mark);
		}
		const text = document.createElement('span');
		text.textContent = label;
		row.appendChild(text);
		row.onclick = onClick;
		return row;
	}

	private rebuildTree(): void {
		this.tree.replaceChildren();
		const needle = this.searchFilter;
		const items = this.catalog.filter((item) => {
			if (item.documentScope !== this.documentScope) {
				return false;
			}
			if (!needle) {
				return true;
			}
			const haystack = (item.name + ' ' + item.library + ' ' + item.module).toLowerCase();
			return haystack.indexOf(needle) >= 0;
		});
		if (!items.length) {
			const empty = document.createElement('div');
			empty.className = 'writer-function-calc-macro__empty';
			empty.textContent = !this.catalogLoaded
				? '正在读取宏…'
				: needle
					? '没有匹配的宏'
					: '没有可用的宏';
			this.tree.appendChild(empty);
			return;
		}
		items.forEach((item) => {
			const row = document.createElement('button');
			row.type = 'button';
			row.className = 'writer-function-calc-macro__row';
			const selected = item.uri === this.selectedUrl;
			if (selected) {
				row.classList.add('writer-function-calc-macro__row--selected');
			}
			const display = item.module ? item.module + '.' + item.name : item.name;
			row.textContent = display;
			if (selected) {
				const check = document.createElement('span');
				check.className = 'writer-function-calc-macro__check';
				check.textContent = '✓';
				row.appendChild(check);
			}
			row.onclick = () => {
				this.description.value = item.description || item.uri;
				this.onChosen(item);
				this.close();
			};
			this.tree.appendChild(row);
		});
	}
}
