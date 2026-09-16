/*
 * Calc P1 AI dialog for formula generation and data analysis.
 */

class MobileAiCalcDialog {
	private readonly taskType: string;
	private readonly controller: CalcAiController;
	private readonly sheet: MobileAiSheet;
	private readonly layout: MobileAiTaskDialogControls;
	private readonly hint: HTMLDivElement;
	private readonly promptInput: HTMLTextAreaElement;
	private readonly unsubscribe: () => void;
	private readonly resultMode: 'insertFormula' | 'conversation' | 'mutateConfirm';

	constructor(taskType: string) {
		this.taskType = taskType;
		this.controller = CalcAiController.getInstance();
		const entry = MobileAiUiCatalog.getEntry(taskType);
		const task = CalcAiCatalog.getTask(taskType);
		this.resultMode = task?.resultMode || 'conversation';
		this.layout = MobileAiTaskDialogLayout.create({ generateLabel: '生成' });
		if (this.resultMode === 'insertFormula') {
			this.layout.applyButton.textContent = '插入单元格';
		} else if (this.resultMode === 'mutateConfirm') {
			this.layout.applyButton.textContent = '确认执行';
		}
		this.sheet = new MobileAiSheet({
			title: entry?.label || 'Calc AI',
			presentation: 'writer',
			taskType,
		});

		this.hint = document.createElement('div');
		this.hint.className = 'mobile-ai-task-dialog__status';
		this.layout.inputSlot.appendChild(this.hint);

		this.promptInput = MobileAiTaskDialogLayout.multilineInput(
			taskType === 'calc_formula'
				? '例如：计算 A1 到 A10 的平均值'
				: taskType === 'calc_cond_format'
					? '例如：把大于 100 的单元格标红'
					: taskType === 'calc_data_process'
						? '例如：按第一列升序排序'
						: taskType === 'calc_chart'
							? '例如：用选中数据做柱状图'
							: taskType === 'calc_new_table'
								? '例如：生成一份销售周报样例表'
								: '例如：总结这组数据的趋势和异常值',
			'AI 需求',
			3,
		);
		this.layout.inputSlot.appendChild(
			MobileAiTaskDialogLayout.cardField('', this.promptInput),
		);

		this.layout.generateButton.onclick = () => this.request();
		this.layout.stopButton.onclick = () => this.controller.cancel();
		this.layout.copyRow.onclick = () => this.controller.copy();
		this.layout.regenerateButton.onclick = () => this.controller.regenerate();
		if (this.resultMode === 'insertFormula' || this.resultMode === 'mutateConfirm') {
			this.layout.applyButton.onclick = () => this.controller.accept();
		} else {
			this.layout.applyButton.hidden = true;
		}

		this.sheet.setBody(this.layout.root);
		this.unsubscribe = this.controller.subscribe(() => this.render());
	}

	open(): void {
		this.refreshHint();
		this.sheet.open();
		this.render();
	}

	close(): void {
		this.controller.cancel();
		this.unsubscribe();
		this.sheet.close();
	}

	private request(): void {
		this.refreshHint();
		this.controller.request(this.taskType, this.promptInput.value);
	}

	private refreshHint(): void {
		if (this.taskType === 'calc_formula' || this.taskType === 'calc_new_table') {
			const cellAddress = CalcAiContext.getActiveCellAddress();
			this.hint.textContent = cellAddress
				? `当前单元格：${cellAddress}`
				: '未检测到活动单元格，仍可生成';
			this.hint.style.color = cellAddress ? '#188038' : '#5f6368';
			return;
		}
		const cellRange = CalcAiContext.getSelectedRange();
		this.hint.textContent = cellRange
			? `已选范围：${cellRange}`
			: '请先选择单元格区域';
		this.hint.style.color = cellRange ? '#188038' : '#d93025';
	}

	private render(): void {
		const state = this.controller.getState();
		this.layout.resultPreview.textContent = state.preview || '';
		const active = state.state === 'loading' || state.state === 'streaming';
		const ready = state.state === 'ready' && !!state.preview;
		if (ready) {
			this.layout.setStage('result');
		} else if (active) {
			this.layout.setStage('generating');
		} else {
			this.layout.setStage('input');
		}
		const canGenerate =
			!active &&
			(this.taskType === 'calc_formula' ||
				this.taskType === 'calc_new_table' ||
				!!CalcAiContext.getSelectedRange());
		this.layout.generateButton.disabled = !canGenerate;
		this.layout.stopButton.disabled = !active;
		this.layout.copyRow.disabled = !ready;
		this.layout.regenerateButton.disabled = !ready;
		if (!this.layout.applyButton.hidden) {
			this.layout.applyButton.disabled = !ready;
		}
		this.layout.status.textContent =
			state.error ||
			(active
				? state.state === 'streaming'
					? 'AI 正在输出…'
					: 'AI 正在生成…'
				: ready
					? '生成完成'
					: '');
	}
}
