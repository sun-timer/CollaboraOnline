/*
 * Android lolib_wai_article_dialog — category → subtype → dynamic fields.
 */

class MobileAiArticleGenerateDialog {
	private readonly controller: WriterAiController;
	private readonly sheet: MobileAiSheet;
	private readonly root: HTMLDivElement;
	private readonly setupGroup: HTMLDivElement;
	private readonly categoryCard: HTMLButtonElement;
	private readonly categoryLabel: HTMLSpanElement;
	private readonly subTypeCard: HTMLButtonElement;
	private readonly subTypeLabel: HTMLSpanElement;
	private readonly hintBar: HTMLParagraphElement;
	private readonly formStage: HTMLDivElement;
	private readonly formScroll: HTMLDivElement;
	private readonly formContainer: HTMLDivElement;
	private readonly formGenerateBtn: HTMLButtonElement;
	private readonly resultGroup: HTMLDivElement;
	private readonly resultPreview: HTMLDivElement;
	private readonly copyRow: HTMLButtonElement;
	private readonly regenerateBtn: HTMLButtonElement;
	private readonly insertBtn: HTMLButtonElement;
	private readonly status: HTMLDivElement;
	private readonly fieldInputs: HTMLInputElement[] = [];
	private readonly unsubscribe: () => void;
	private category: string | null = null;
	private template: WriterAiArticleTemplate | null = null;
	private stage: 'select' | 'form' | 'result' = 'select';

	constructor() {
		this.controller = WriterAiController.getInstance();
		this.sheet = new MobileAiSheet({
			title: '文案生成',
			presentation: 'writer',
			taskType: 'article_generate',
		});
		this.root = document.createElement('div');
		this.root.className = 'mobile-ai-article-dialog';

		this.setupGroup = document.createElement('div');
		this.setupGroup.className = 'mobile-ai-article-dialog__setup';

		this.categoryCard = MobileAiTaskDialogLayout.selectCard('请选择分类', () =>
			this.openCategoryPicker(),
		);
		this.categoryLabel = this.categoryCard.querySelector(
			'.mobile-ai-task-dialog__select-card-label',
		) as HTMLSpanElement;
		this.setupGroup.appendChild(this.categoryCard);

		this.subTypeCard = MobileAiTaskDialogLayout.selectCard('请选择子类', () =>
			this.openSubTypePicker(),
		);
		this.subTypeCard.hidden = true;
		this.subTypeLabel = this.subTypeCard.querySelector(
			'.mobile-ai-task-dialog__select-card-label',
		) as HTMLSpanElement;
		this.setupGroup.appendChild(this.subTypeCard);

		this.hintBar = document.createElement('p');
		this.hintBar.className = 'mobile-ai-article-dialog__hint';
		this.hintBar.textContent = '请选择文案类型后，进行文案生成';
		this.setupGroup.appendChild(this.hintBar);

		this.formStage = document.createElement('div');
		this.formStage.className = 'mobile-ai-article-dialog__form-stage';
		this.formStage.hidden = true;
		this.formScroll = document.createElement('div');
		this.formScroll.className = 'mobile-ai-article-dialog__form-scroll';
		this.formContainer = document.createElement('div');
		this.formContainer.className = 'mobile-ai-article-dialog__form-fields';
		this.formScroll.appendChild(this.formContainer);
		this.formStage.appendChild(this.formScroll);
		this.formGenerateBtn = document.createElement('button');
		this.formGenerateBtn.type = 'button';
		this.formGenerateBtn.className =
			'mobile-ai-task-dialog__btn mobile-ai-task-dialog__btn--primary';
		this.formGenerateBtn.textContent = '开始生成';
		this.formGenerateBtn.onclick = () => this.startGeneration();
		this.formStage.appendChild(this.formGenerateBtn);
		this.setupGroup.appendChild(this.formStage);

		this.root.appendChild(this.setupGroup);

		this.resultGroup = document.createElement('div');
		this.resultGroup.className = 'mobile-ai-article-dialog__result-group';
		this.resultGroup.hidden = true;
		const resultBox = document.createElement('div');
		resultBox.className = 'mobile-ai-task-dialog__result';
		this.resultPreview = document.createElement('div');
		this.resultPreview.className = 'mobile-ai-task-dialog__result-body';
		this.resultPreview.setAttribute('aria-live', 'polite');
		resultBox.appendChild(this.resultPreview);
		this.resultGroup.appendChild(resultBox);

		this.copyRow = document.createElement('button');
		this.copyRow.type = 'button';
		this.copyRow.className = 'mobile-ai-task-dialog__copy-row';
		this.copyRow.hidden = true;
		this.copyRow.innerHTML =
			'<span class="mobile-ai-task-dialog__copy-icon" aria-hidden="true"></span><span>复制</span>';
		this.copyRow.onclick = () => this.controller.copy();
		this.resultGroup.appendChild(this.copyRow);

		const resultBar = document.createElement('div');
		resultBar.className = 'mobile-ai-task-dialog__result-bar';
		this.regenerateBtn = document.createElement('button');
		this.regenerateBtn.type = 'button';
		this.regenerateBtn.className =
			'mobile-ai-task-dialog__btn mobile-ai-task-dialog__btn--secondary';
		this.regenerateBtn.textContent = '重新生成';
		this.regenerateBtn.onclick = () => {
			if (this.template) {
				this.setStage('form');
			}
		};
		resultBar.appendChild(this.regenerateBtn);
		this.insertBtn = document.createElement('button');
		this.insertBtn.type = 'button';
		this.insertBtn.className =
			'mobile-ai-task-dialog__btn mobile-ai-task-dialog__btn--primary';
		this.insertBtn.textContent = '插入文档';
		this.insertBtn.onclick = () =>
			this.controller.accept(
				MobileAiResultRenderer.toHtml(this.controller.getState().preview),
			);
		resultBar.appendChild(this.insertBtn);
		this.resultGroup.appendChild(resultBar);

		this.root.appendChild(this.resultGroup);

		this.status = document.createElement('div');
		this.status.className = 'mobile-ai-task-dialog__status';
		this.status.setAttribute('role', 'status');
		this.root.appendChild(this.status);

		this.sheet.setBody(this.root);
		this.unsubscribe = this.controller.subscribe(() => this.render());
		this.setStage('select');
	}

	open(): void {
		this.sheet.open();
		this.render();
	}

	close(): void {
		this.controller.cancel();
		MobileAiWaiDropdown.dismiss();
		this.unsubscribe();
		this.sheet.close();
	}

	private openCategoryPicker(): void {
		const categories = WriterAiArticleRegistry.getCategories();
		let selectedIndex = 0;
		if (this.category) {
			const idx = categories.indexOf(this.category);
			if (idx >= 0) {
				selectedIndex = idx;
			}
		}
		MobileAiWaiDropdown.show({
			anchor: this.categoryCard,
			labels: categories,
			selectedIndex,
			onPick: (index) => {
				this.category = categories[index];
				this.categoryLabel.textContent = this.category;
				this.template = null;
				this.subTypeLabel.textContent = '请选择子类';
				this.subTypeCard.hidden = false;
				this.setStage('select');
			},
		});
	}

	private openSubTypePicker(): void {
		if (!this.category) {
			this.status.textContent = '请先选择分类';
			return;
		}
		const templates = WriterAiArticleRegistry.getByCategory(this.category);
		if (!templates.length) {
			return;
		}
		const labels = templates.map((item) => item.subTypeLabel);
		let selectedIndex = 0;
		if (this.template) {
			const idx = templates.findIndex((t) => t.key === this.template?.key);
			if (idx >= 0) {
				selectedIndex = idx;
			}
		}
		MobileAiWaiDropdown.show({
			anchor: this.subTypeCard,
			labels,
			selectedIndex,
			onPick: (index) => {
				this.template = templates[index];
				this.subTypeLabel.textContent = this.template.subTypeLabel;
				this.renderForm(this.template);
			},
		});
	}

	private renderForm(template: WriterAiArticleTemplate): void {
		this.formContainer.replaceChildren();
		this.fieldInputs.length = 0;
		template.variables.forEach((variable, index) => {
			const fieldLabel = document.createElement('span');
			fieldLabel.className = 'mobile-ai-article-dialog__field-label';
			fieldLabel.textContent = variable.label;
			if (index > 0) {
				fieldLabel.classList.add('mobile-ai-article-dialog__field-label--spaced');
			}
			this.formContainer.appendChild(fieldLabel);
			const input = MobileAiTaskDialogLayout.fieldInput(
				variable.hint,
				variable.label,
			);
			this.formContainer.appendChild(input);
			this.fieldInputs.push(input);
		});
		this.setStage('form');
	}

	private startGeneration(): void {
		if (!this.template) {
			this.status.textContent = '请先选择文案类型';
			return;
		}
		const values = this.fieldInputs.map((input) => input.value.trim());
		this.controller.request(
			'article_generate',
			{
				template: this.template.key,
				variables: values,
			},
			'',
		);
		this.setStage('result');
	}

	private setStage(stage: 'select' | 'form' | 'result'): void {
		this.stage = stage;
		const select = stage === 'select';
		const form = stage === 'form';
		const result = stage === 'result';
		this.setupGroup.hidden = result;
		this.hintBar.hidden = !select;
		this.formStage.hidden = !form;
		this.subTypeCard.hidden = !this.category || result;
		if (!this.category) {
			this.subTypeCard.hidden = true;
		}
		this.resultGroup.hidden = !result;
		this.copyRow.hidden = !result;
	}

	private render(): void {
		const state = this.controller.getState();
		if (this.stage === 'result' || state.state === 'loading' || state.state === 'streaming') {
			MobileAiResultRenderer.renderInto(this.resultPreview, state.preview);
		}
		const active = state.state === 'loading' || state.state === 'streaming';
		const ready = state.state === 'ready' && !!state.preview;
		if (active && this.stage !== 'result') {
			this.setStage('result');
		}
		this.formGenerateBtn.disabled = active;
		this.copyRow.disabled = !ready;
		this.regenerateBtn.disabled = active;
		this.insertBtn.disabled = !ready;
		this.status.textContent =
			state.error ||
			(active
				? state.state === 'streaming'
					? 'AI 正在输出…'
					: 'AI 正在生成…'
				: ready && this.stage === 'result'
					? '生成完成'
					: '');
	}
}
