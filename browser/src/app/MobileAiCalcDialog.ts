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
	private disposed = false;

	constructor(taskType: string, onApplySuccess?: () => void) {
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
			onClose: () => this.dispose(),
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

		this.layout.generateButton.onclick = () => {
			void this.request();
		};
		this.layout.stopButton.onclick = () => this.controller.cancel();
		this.layout.copyRow.onclick = () => this.controller.copy();
		this.layout.regenerateButton.onclick = () => this.controller.regenerate();
		if (this.resultMode === 'insertFormula' || this.resultMode === 'mutateConfirm') {
			const closeOnApply = [
				'calc_chart',
				'calc_cond_format',
				'calc_data_process',
			].includes(taskType);
			this.layout.applyButton.onclick = async () => {
				const accepted = await this.controller.accept();
				if (closeOnApply && accepted && !this.disposed) {
					this.close();
					onApplySuccess?.();
				}
			};
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
		this.sheet.close();
	}

	private dispose(): void {
		if (this.disposed) {
			return;
		}
		this.disposed = true;
		this.unsubscribe();
		this.controller.cancel();
	}

	private async request(): Promise<void> {
		this.refreshHint();
		await MobileAiBridge.getInstance().getSelectedTextAsync();
		if (this.disposed) {
			return;
		}
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
		this.layout.resultPreview.textContent = this.previewText(state.preview || '');
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
			!active && state.state !== 'applying' &&
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
			state.error || (state.state === 'applying' ? '正在执行，请稍候…' : '') ||
			(active
				? state.state === 'streaming'
					? 'AI 正在输出…'
					: 'AI 正在生成…'
				: ready
					? '生成完成'
					: '');
	}

	/** Chart results stay JSON for apply; the sheet shows the Chinese summary. */
	private previewText(preview: string): string {
		if (this.taskType !== 'calc_chart' || !preview) {
			return preview;
		}
		const plan = CalcAiCatalog.extractJsonObject(preview);
		if (!plan || typeof plan !== 'object') {
			return '正在生成图表方案…';
		}
		const summary = typeof plan.summary === 'string' ? plan.summary.trim() : '';
		const title =
			plan.chart && typeof plan.chart.title === 'string'
				? plan.chart.title.trim()
				: '';
		return summary || title || '将根据原始选区创建图表';
	}
}
