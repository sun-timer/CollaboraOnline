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
	private polishStyleCard?: HTMLElement;
	private disposed = false;
	private readonly outlineTypeSelect?: HTMLSelectElement;
	private outlineTypeValue?: HTMLSpanElement;
	private readonly outlineDescription?: HTMLTextAreaElement;
	private readonly imageThumb?: HTMLImageElement;
	private pendingImage = '';
	private lastGeneratedImage = '';
	private readonly unsubscribe: () => void;
	private readonly iosWord: boolean;
	private selectionSnapshot = '';
	private selectionSnapshotReady = false;
	private requestPending = false;
	private requestSequence = 0;
	private localError = '';

	constructor(taskType: string) {
		this.taskType = taskType;
		this.controller = WriterAiController.getInstance();
		this.iosWord = MobileAiOperationDialog.isIOSWordDocument();
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
			onClose: () => this.dispose(),
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
			card.setAttribute('aria-expanded', 'false');
			this.polishStyleCard = card;
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

		if (taskType === 'outline') {
			this.outlineTypeSelect = document.createElement('select');
			this.outlineTypeSelect.setAttribute('aria-label', '大纲类型');
			const outlineTypes = this.iosWord
				? WriterAiCatalog.IOS_WORD_OUTLINE_TYPES
				: WriterAiCatalog.OUTLINE_TYPES;
			outlineTypes.forEach((item) => {
				const option = document.createElement('option');
				option.value = item.key;
				option.textContent = item.label;
				this.outlineTypeSelect?.appendChild(option);
			});
			this.outlineTypeSelect.value = 'general';
			const typeCard = MobileAiTaskDialogLayout.pickerCard(
				'大纲类型',
				outlineTypes.find((t) => t.key === 'general')
					?.label || '通用文档',
				() => this.openOutlineTypeSelect(),
			);
			typeCard.setAttribute('aria-expanded', 'false');
			this.outlineTypeValue = typeCard.querySelector(
				'.mobile-ai-task-dialog__picker-card-value',
			) as HTMLSpanElement;
			this.outlineTypeSelect.onchange = () => {
				const item = outlineTypes.find(
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
		this.layout.stopButton.onclick = () => this.stopRequest();
		this.layout.copyRow.onclick = async () => {
			const copied = await this.controller.copy();
			if (!this.disposed && copied && this.iosWord && this.taskType === 'polish') {
				this.close();
			}
		};
		this.layout.regenerateButton.onclick = () => this.regenerate();
		this.layout.applyButton.onclick = async () => {
			const html = MobileAiResultRenderer.toHtml(this.controller.getState().preview);
			if ((window as any).ThisIsTheiOSApp) {
				const accepted = await this.controller.acceptAsync(html);
				if (!this.disposed && accepted) this.close();
			} else {
				this.controller.accept(html);
			}
		};

		this.sheet.setBody(this.layout.root);
		this.unsubscribe = this.controller.subscribe(() => this.render());
	}

	open(): void {
		// Clears any loading or preview left by a previously opened task.
		this.controller.resetPreview();
		const selectionPromise = this.iosWord && this.isSelectionTask()
			? MobileAiBridge.getInstance().getSelectedTextAsync()
			: null;
		this.sheet.open();
		const state = this.controller.getState();
		if (selectionPromise) {
			this.selectionSnapshotReady = false;
			this.localError = '';
			void selectionPromise
				.then((selection) => {
					if (this.disposed) return;
					this.selectionSnapshot = selection || '';
					this.selectionSnapshotReady = true;
					if (!this.selectionSnapshot.trim()) {
						this.localError = '请先在文档中选择文本';
					} else if (
						this.isLimitedSelectionTask() &&
						WriterAiCatalog.countUnicodeCodePoints(this.selectionSnapshot) > 5000
					) {
						this.localError = '选中内容不可超过5000字符';
					} else if (this.isAutoStartSelectionTask()) {
						void this.request();
					}
					this.render();
				})
				.catch(() => {
					if (this.disposed) return;
					this.selectionSnapshotReady = true;
					this.localError = '请先在文档中选择文本';
					this.render();
				});
		} else if (this.taskType === 'continue' && state.state !== 'ready') {
			void this.request();
		}
		this.render();
	}

	private syncOutlineDescHint(): void {
		if (!this.outlineDescription || !this.outlineTypeSelect) {
			return;
		}
		const outlineTypes = this.iosWord
			? WriterAiCatalog.IOS_WORD_OUTLINE_TYPES
			: WriterAiCatalog.OUTLINE_TYPES;
		const item = outlineTypes.find(
			(t) => t.key === this.outlineTypeSelect?.value,
		);
		const label = item?.label || '通用文档';
		this.outlineDescription.placeholder = '请输入' + label + '要求';
	}

	private openStyleSelect(): void {
		if (!this.styleSelect || !this.polishStyleCard) {
			return;
		}
		// A hidden HTML select cannot present its native picker in WKWebView.
		// Use the same anchored menu as Android, while keeping the select as
		// the task's value model for request validation.
		const options = Array.from(this.styleSelect.options);
		const expanded = MobileAiWaiDropdown.toggle({
			anchor: this.polishStyleCard,
			labels: options.map((option) => option.textContent || option.value),
			selectedIndex: this.styleSelect.selectedIndex,
				onDismiss: () => this.setPickerExpanded(this.polishStyleCard, false),
			onPick: (index, label) => {
				this.setPickerExpanded(this.polishStyleCard, false);
				if (this.styleSelect) {
					this.styleSelect.value = options[index].value;
				}
				if (this.polishStyleValue) {
					this.polishStyleValue.textContent = label;
				}
			},
		});
		this.setPickerExpanded(this.polishStyleCard, expanded);
	}

	private openOutlineTypeSelect(): void {
		if (!this.outlineTypeSelect || !this.outlineTypeValue) {
			return;
		}
		const card = this.outlineTypeValue.closest(
			'.mobile-ai-task-dialog__picker-card',
		) as HTMLElement | null;
		if (!card) return;
		const options = Array.from(this.outlineTypeSelect.options);
		const expanded = MobileAiWaiDropdown.toggle({
			anchor: card,
			labels: options.map((option) => option.textContent || option.value),
			selectedIndex: this.outlineTypeSelect.selectedIndex,
			onDismiss: () => this.setPickerExpanded(card, false),
			onPick: (index, label) => {
				this.outlineTypeSelect!.value = options[index].value;
				this.outlineTypeValue!.textContent = label;
				this.setPickerExpanded(card, false);
				this.syncOutlineDescHint();
			},
		});
		this.setPickerExpanded(card, expanded);
	}

	private setPickerExpanded(card: HTMLElement | undefined | null, expanded: boolean): void {
		if (!card) return;
		card.setAttribute('aria-expanded', String(expanded));
		const chevron = card.querySelector(
			'.mobile-ai-task-dialog__picker-card-chevron',
		) as HTMLElement | null;
		if (chevron) chevron.textContent = expanded ? '▴' : '▾';
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
		this.sheet.close();
	}

	private dispose(): void {
		if (this.disposed) {
			return;
		}
		this.disposed = true;
		this.requestSequence++;
		this.requestPending = false;
		const state = this.controller.getState().state;
		if (state === 'loading' || state === 'streaming') {
			this.controller.cancel();
		}
		this.unsubscribe();
		MobileAiWaiDropdown.dismiss();
	}

	private isPresentation(): boolean {
		return (window as any).app?.map?.getDocType?.() === 'presentation';
	}

	private static isIOSWordDocument(): boolean {
		if (!(window as any).ThisIsTheiOSApp) return false;
		const nativeType = (window as any).MobileNativeDocumentType;
		const mapType = (window as any).app?.map?.getDocType?.();
		return (nativeType || mapType || 'text') === 'text';
	}

	private isSelectionTask(): boolean {
		return ['polish', 'expand', 'condense', 'rewrite', 'continue', 'summarize'].indexOf(
			this.taskType,
		) >= 0;
	}

	private isLimitedSelectionTask(): boolean {
		return ['expand', 'condense', 'rewrite'].indexOf(this.taskType) >= 0;
	}

	private isAutoStartSelectionTask(): boolean {
		return this.isLimitedSelectionTask() || this.taskType === 'continue';
	}

	private async request(): Promise<void> {
		if (this.disposed || this.requestPending) return;
		this.localError = '';
		const context: { [key: string]: any } = {};
		if (this.taskType === 'polish') {
			context.polishStyle =
				this.styleSelect?.value || WriterAiCatalog.DEFAULT_POLISH_STYLE;
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
		if (this.taskType === 'outline' && this.iosWord) {
			const requestSequence = ++this.requestSequence;
			this.requestPending = true;
			this.render();
			try {
				const fullText = await MobileAiDocumentExtractor.extractFullText({
					allowEmpty: true,
				});
				if (this.disposed || requestSequence !== this.requestSequence) return;
				this.controller.request(this.taskType, context, fullText);
			} catch (error) {
				if (this.disposed || requestSequence !== this.requestSequence) return;
				this.localError = error instanceof Error ? error.message : '文档全文提取失败';
			} finally {
				if (requestSequence === this.requestSequence) {
					this.requestPending = false;
					if (!this.disposed) this.render();
				}
			}
			return;
		}
		if (this.iosWord && this.isSelectionTask()) {
			if (!this.selectionSnapshotReady) return;
			if (!this.selectionSnapshot.trim()) {
				this.localError = '请先在文档中选择文本';
				this.render();
				return;
			}
			if (
				this.isLimitedSelectionTask() &&
				WriterAiCatalog.countUnicodeCodePoints(this.selectionSnapshot) > 5000
			) {
				this.localError = '选中内容不可超过5000字符';
				this.render();
				return;
			}
			this.controller.request(this.taskType, context, this.selectionSnapshot);
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

	private stopRequest(): void {
		if (this.requestPending) {
			this.requestSequence++;
			this.requestPending = false;
			this.localError = '已停止读取文档';
			this.render();
			return;
		}
		this.controller.cancel();
	}

	private render(): void {
		const state = this.controller.getState();
		MobileAiResultRenderer.renderInto(
			this.layout.resultPreview,
			state.preview,
		);
		const active = state.state === 'loading' || state.state === 'streaming' || this.requestPending;
		const ready = state.state === 'ready' && !!state.preview;
		if (ready || state.state === 'inserting') {
			this.layout.setStage('result');
		} else if (active) {
			this.layout.setStage('generating');
		} else {
			this.layout.setStage('input');
		}
		this.layout.generateButton.disabled =
			active || this.requestPending ||
			(this.iosWord && this.isSelectionTask() && !this.selectionSnapshotReady) ||
			(this.taskType === 'text_extract' && !this.pendingImage);
		this.layout.stopButton.disabled = !active;
		this.layout.copyRow.disabled = !ready;
		this.layout.regenerateButton.disabled = !ready;
		this.layout.applyButton.disabled = !ready;
		this.layout.status.textContent =
			this.localError || state.error || state.warning ||
			(state.state === 'inserting' ? '正在插入文档…' : this.requestPending
				? '正在读取文档…'
				: active
				? state.state === 'streaming'
					? 'AI 正在输出…'
					: 'AI 正在生成…'
				: ready
					? '生成完成'
					: '');
	}
}
