/*
 * Calc P1 AI dialog for formula generation and data analysis.
 */

class MobileAiCalcDialog {
	private readonly taskType: string;
	private readonly controller: CalcAiController;
	private readonly sheet: MobileAiSheet;
	private readonly hint: HTMLDivElement;
	private readonly promptInput: HTMLTextAreaElement;
	private readonly preview: HTMLDivElement;
	private readonly status: HTMLDivElement;
	private readonly generateButton: HTMLButtonElement;
	private readonly stopButton: HTMLButtonElement;
	private readonly copyButton: HTMLButtonElement;
	private readonly regenerateButton: HTMLButtonElement;
	private readonly applyButton: HTMLButtonElement | null;
	private readonly unsubscribe: () => void;
	private readonly resultMode: 'insertFormula' | 'conversation' | 'mutateConfirm';

	constructor(taskType: string) {
		this.taskType = taskType;
		this.controller = CalcAiController.getInstance();
		const entry = MobileAiUiCatalog.getEntry(taskType);
		const task = CalcAiCatalog.getTask(taskType);
		this.resultMode = task?.resultMode || 'conversation';
		this.sheet = new MobileAiSheet({ title: entry?.label || 'Calc AI' });

		const content = document.createElement('div');
		content.style.cssText =
			'display:flex;flex-direction:column;gap:12px;min-height:300px;';

		this.hint = document.createElement('div');
		this.hint.style.cssText =
			'padding:10px;border-radius:8px;background:#e6ebf2;color:#5f6368;font-size:14px;';
		content.appendChild(this.hint);

		this.promptInput = document.createElement('textarea');
		this.promptInput.rows = 3;
		this.promptInput.placeholder =
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
								: '例如：总结这组数据的趋势和异常值';
		this.promptInput.setAttribute('aria-label', 'AI 需求');
		this.promptInput.style.cssText =
			'width:100%;box-sizing:border-box;padding:10px;border:1px solid #d8dde3;' +
			'border-radius:8px;font:inherit;resize:vertical;';
		content.appendChild(this.promptInput);

		this.status = document.createElement('div');
		this.status.setAttribute('role', 'status');
		content.appendChild(this.status);

		this.preview = document.createElement('div');
		this.preview.setAttribute('aria-live', 'polite');
		this.preview.style.cssText =
			'min-height:160px;max-height:42dvh;overflow:auto;padding:16px;' +
			'border:1px solid #d8dde3;border-radius:8px;line-height:1.6;white-space:pre-wrap;';
		content.appendChild(this.preview);

		const inputActions = document.createElement('div');
		inputActions.style.cssText = 'display:flex;gap:8px;';
		this.generateButton = this.createButton('开始生成');
		this.generateButton.onclick = () => this.request();
		inputActions.appendChild(this.generateButton);
		this.stopButton = this.createButton('停止生成');
		this.stopButton.onclick = () => this.controller.cancel();
		inputActions.appendChild(this.stopButton);
		content.appendChild(inputActions);

		const resultActions = document.createElement('div');
		resultActions.style.cssText = 'display:flex;gap:8px;';
		this.copyButton = this.createButton('复制');
		this.copyButton.onclick = () => this.controller.copy();
		resultActions.appendChild(this.copyButton);
		this.regenerateButton = this.createButton('重新生成');
		this.regenerateButton.onclick = () => this.controller.regenerate();
		resultActions.appendChild(this.regenerateButton);
		if (this.resultMode === 'insertFormula') {
			this.applyButton = this.createButton('插入单元格');
			this.applyButton.onclick = () => this.controller.accept();
			resultActions.appendChild(this.applyButton);
		} else if (this.resultMode === 'mutateConfirm') {
			this.applyButton = this.createButton('确认执行');
			this.applyButton.onclick = () => this.controller.accept();
			resultActions.appendChild(this.applyButton);
		} else {
			this.applyButton = null;
		}
		content.appendChild(resultActions);

		this.sheet.setBody(content);
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
		this.preview.textContent = state.preview || '';
		const active = state.state === 'loading' || state.state === 'streaming';
		const ready = state.state === 'ready' && !!state.preview;
		const canGenerate =
			!active &&
			(this.taskType === 'calc_formula' ||
				this.taskType === 'calc_new_table' ||
				!!CalcAiContext.getSelectedRange());
		this.generateButton.disabled = !canGenerate;
		this.stopButton.disabled = !active;
		this.copyButton.disabled = !ready;
		this.regenerateButton.disabled = !ready;
		if (this.applyButton) {
			this.applyButton.disabled = !ready;
		}
		this.status.textContent =
			state.error ||
			(active
				? state.state === 'streaming'
					? 'AI 正在输出...'
					: 'AI 正在生成...'
				: ready
					? '生成完成'
					: '');
	}

	private createButton(label: string): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		return button;
	}
}
