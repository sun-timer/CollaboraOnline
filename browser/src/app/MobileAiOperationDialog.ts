/*
 * Android-style Writer text operation dialog.
 */

class MobileAiOperationDialog {
	private readonly taskType: string;
	private readonly controller: WriterAiController;
	private readonly sheet: MobileAiSheet;
	private readonly layout: MobileAiTaskDialogControls;
	private readonly requirement?: HTMLTextAreaElement;
	private readonly styleSelect?: HTMLSelectElement;
	private polishStyleValue?: HTMLSpanElement;
	private readonly outlineTypeSelect?: HTMLSelectElement;
	private outlineTypeValue?: HTMLSpanElement;
	private readonly outlineDescription?: HTMLTextAreaElement;
	private readonly imageThumb?: HTMLImageElement;
	private pendingImage = '';
	private lastGeneratedImage = '';
	private readonly unsubscribe: () => void;

	constructor(taskType: string) {
		this.taskType = taskType;
		this.controller = WriterAiController.getInstance();
		const entry = MobileAiUiCatalog.getEntry(taskType);
		const generateLabel =
			taskType === 'text_extract'
				? '提取文字'
				: taskType === 'continue'
					? '开始生成'
					: '生成';
		this.layout = MobileAiTaskDialogLayout.create({ generateLabel });
		this.sheet = new MobileAiSheet({
			title: entry?.label || 'AI 文案处理',
			presentation: 'writer',
			taskType,
		});

		const input = this.layout.inputSlot;

		if (taskType === 'polish') {
			this.styleSelect = document.createElement('select');
			this.styleSelect.setAttribute('aria-label', '润色风格');
			this.styleSelect.className = 'mobile-ai-task-dialog__control';
			const labels: { [key: string]: string } = {
				quick: '快速润色',
				formal: '更正式',
				lively: '更活泼',
				party_govt: '党政风',
				colloquial: '口语化',
				academic: '更学术',
				internet: '网络话术',
			};
			WriterAiCatalog.POLISH_STYLES.forEach((style) => {
				const option = document.createElement('option');
				option.value = style;
				option.textContent = labels[style] || style;
				this.styleSelect?.appendChild(option);
			});
			this.styleSelect.value = WriterAiCatalog.DEFAULT_POLISH_STYLE;
			const initialLabel =
				labels[this.styleSelect.value] || this.styleSelect.value;
			const card = MobileAiTaskDialogLayout.pickerCard(
				'润色风格',
				initialLabel,
				() => this.openStyleSelect(),
			);
			this.polishStyleValue = card.querySelector(
				'.mobile-ai-task-dialog__picker-card-value',
			) as HTMLSpanElement;
			this.styleSelect.onchange = () => {
				if (this.polishStyleValue && this.styleSelect) {
					this.polishStyleValue.textContent =
						labels[this.styleSelect.value] || this.styleSelect.value;
				}
			};
			input.appendChild(card);
			input.appendChild(this.styleSelect);
			this.styleSelect.hidden = true;
		}

		if (
			taskType === 'expand' ||
			taskType === 'condense' ||
			taskType === 'rewrite'
		) {
			const hint =
				taskType === 'condense'
					? '请输入文案缩写要求'
					: taskType === 'rewrite'
						? '请输入文案重写要求'
						: '请输入文案扩写要求';
			this.requirement = MobileAiTaskDialogLayout.multilineInput(
				hint,
				'文案要求',
				5,
			);
			input.appendChild(
				MobileAiTaskDialogLayout.cardField('', this.requirement),
			);
		}

		if (taskType === 'outline') {
			this.outlineTypeSelect = document.createElement('select');
			this.outlineTypeSelect.setAttribute('aria-label', '大纲类型');
			WriterAiCatalog.OUTLINE_TYPES.forEach((item) => {
				const option = document.createElement('option');
				option.value = item.key;
				option.textContent = item.label;
				this.outlineTypeSelect?.appendChild(option);
			});
			this.outlineTypeSelect.value = 'general';
			const typeCard = MobileAiTaskDialogLayout.pickerCard(
				'大纲类型',
				WriterAiCatalog.OUTLINE_TYPES.find((t) => t.key === 'general')
					?.label || '通用文档',
				() => this.openOutlineTypeSelect(),
			);
			this.outlineTypeValue = typeCard.querySelector(
				'.mobile-ai-task-dialog__picker-card-value',
			) as HTMLSpanElement;
			this.outlineTypeSelect.onchange = () => {
				const item = WriterAiCatalog.OUTLINE_TYPES.find(
					(t) => t.key === this.outlineTypeSelect?.value,
				);
				if (this.outlineTypeValue && item) {
					this.outlineTypeValue.textContent = item.label;
				}
				this.syncOutlineDescHint();
			};
			input.appendChild(typeCard);
			input.appendChild(this.outlineTypeSelect);
			this.outlineTypeSelect.hidden = true;

			this.outlineDescription = MobileAiTaskDialogLayout.multilineInput(
				'请输入通用文档要求',
				'大纲补充说明',
				5,
			);
			this.syncOutlineDescHint();
			input.appendChild(
				MobileAiTaskDialogLayout.cardField('', this.outlineDescription),
			);
		}

		if (taskType === 'text_extract') {
			const pick = document.createElement('button');
			pick.type = 'button';
			pick.className =
				'mobile-ai-task-dialog__btn mobile-ai-task-dialog__btn--secondary';
			pick.textContent = '选择图片';
			pick.onclick = () => this.pickImage();
			input.appendChild(pick);
			const thumb = document.createElement('img');
			thumb.className = 'mobile-ai-task-dialog__image-thumb';
			thumb.hidden = true;
			input.appendChild(thumb);
			this.imageThumb = thumb;
		}

		this.layout.generateButton.onclick = () => {
			void this.request();
		};
		this.layout.stopButton.onclick = () => this.controller.cancel();
		this.layout.copyRow.onclick = () => this.controller.copy();
		this.layout.regenerateButton.onclick = () => this.regenerate();
		this.layout.applyButton.onclick = () =>
			this.controller.accept(
				MobileAiResultRenderer.toHtml(this.controller.getState().preview),
			);

		this.sheet.setBody(this.layout.root);
		this.unsubscribe = this.controller.subscribe(() => this.render());
	}

	open(): void {
		this.sheet.open();
		const state = this.controller.getState();
		if (this.taskType === 'continue' && state.state !== 'ready') {
			void this.request();
		}
		this.render();
	}

	private syncOutlineDescHint(): void {
		if (!this.outlineDescription || !this.outlineTypeSelect) {
			return;
		}
		const item = WriterAiCatalog.OUTLINE_TYPES.find(
			(t) => t.key === this.outlineTypeSelect?.value,
		);
		const label = item?.label || '通用文档';
		this.outlineDescription.placeholder = '请输入' + label + '要求';
	}

	private openStyleSelect(): void {
		if (!this.styleSelect) {
			return;
		}
		this.styleSelect.focus();
		const picker = (this.styleSelect as HTMLSelectElement & {
			showPicker?: () => void;
		}).showPicker;
		if (typeof picker === 'function') {
			picker.call(this.styleSelect);
		} else {
			this.styleSelect.click();
		}
	}

	private openOutlineTypeSelect(): void {
		if (!this.outlineTypeSelect) {
			return;
		}
		this.outlineTypeSelect.focus();
		const picker = (this.outlineTypeSelect as HTMLSelectElement & {
			showPicker?: () => void;
		}).showPicker;
		if (typeof picker === 'function') {
			picker.call(this.outlineTypeSelect);
		} else {
			this.outlineTypeSelect.click();
		}
	}

	private pickImage(): void {
		const input = document.createElement('input');
		input.type = 'file';
		input.accept = 'image/*';
		input.onchange = () => {
			const file = input.files && input.files[0];
			if (!file) {
				return;
			}
			const reader = new FileReader();
			reader.onload = () => {
				const bytes = new Uint8Array(reader.result as ArrayBuffer);
				let binary = '';
				for (let i = 0; i < bytes.length; i++) {
					binary += String.fromCharCode(bytes[i]);
				}
				this.pendingImage = window.btoa(binary);
				if (this.imageThumb) {
					this.imageThumb.src =
						'data:image/png;base64,' + this.pendingImage;
					this.imageThumb.hidden = false;
				}
				this.render();
			};
			reader.readAsArrayBuffer(file);
		};
		input.click();
	}

	close(): void {
		this.controller.cancel();
		this.unsubscribe();
		this.sheet.close();
	}

	private async request(): Promise<void> {
		const context: { [key: string]: any } = {};
		if (this.taskType === 'polish') {
			context.polishStyle =
				this.styleSelect?.value || WriterAiCatalog.DEFAULT_POLISH_STYLE;
		} else if (
			this.taskType === 'expand' ||
			this.taskType === 'condense' ||
			this.taskType === 'rewrite'
		) {
			context.requirement = this.requirement?.value.trim() || '';
		} else if (this.taskType === 'outline') {
			context.outlineType = this.outlineTypeSelect?.value || 'general';
			context.requirement = this.outlineDescription?.value.trim() || '';
		} else if (this.taskType === 'text_extract') {
			this.lastGeneratedImage = this.pendingImage;
			this.controller.request(
				'text_extract',
				{},
				undefined,
				this.pendingImage ? [this.pendingImage] : undefined,
			);
			return;
		}
		this.controller.requestWithCurrentSelection(this.taskType, context);
	}

	private regenerate(): void {
		if (
			this.taskType === 'text_extract' &&
			this.pendingImage !== this.lastGeneratedImage
		) {
			this.request();
			return;
		}
		this.controller.regenerate();
	}

	private render(): void {
		const state = this.controller.getState();
		MobileAiResultRenderer.renderInto(
			this.layout.resultPreview,
			state.preview,
		);
		const active = state.state === 'loading' || state.state === 'streaming';
		const ready = state.state === 'ready' && !!state.preview;
		if (ready) {
			this.layout.setStage('result');
		} else if (active) {
			this.layout.setStage('generating');
		} else {
			this.layout.setStage('input');
		}
		this.layout.generateButton.disabled =
			active ||
			(this.taskType === 'text_extract' && !this.pendingImage);
		this.layout.stopButton.disabled = !active;
		this.layout.copyRow.disabled = !ready;
		this.layout.regenerateButton.disabled = !ready;
		this.layout.applyButton.disabled = !ready;
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
