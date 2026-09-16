/*
 * iOS AI image generation dialog.
 *
 * Image requests do not stream text: AIService answers with ai.done
 * carrying imageBase64 (b64 encoded PNG), so this dialog talks to the
 * bridge directly instead of the WriterAiController text state machine.
 */

class MobileAiImageDialog {
	private readonly bridge: MobileAiBridge;
	private readonly sheet: MobileAiSheet;
	private readonly layout: MobileAiTaskDialogControls;
	private readonly description: HTMLTextAreaElement;
	private readonly image: HTMLImageElement;
	private readonly unsubscribe: () => void;
	private requestId = '';
	private pendingImage = '';

	static buildPayload(description: string): { [key: string]: any } {
		return {
			taskType: 'image_generate',
			selection: description.trim(),
			context: {},
		};
	}

	constructor() {
		this.bridge = MobileAiBridge.getInstance();
		this.layout = MobileAiTaskDialogLayout.create({
			generateLabel: '生成图片',
		});
		this.layout.copyRow.hidden = true;
		this.layout.applyButton.textContent = '插入文档';
		this.layout.regenerateButton.textContent = '重新生成';
		this.layout.stopButton.textContent = '停止';
		this.sheet = new MobileAiSheet({
			title: 'AI 图片',
			presentation: 'writer',
			taskType: 'image_generate',
			onClose: () => {
				if (this.requestId) {
					this.bridge.cancel(this.requestId);
					this.requestId = '';
				}
				this.unsubscribe();
			},
		});

		this.description = MobileAiTaskDialogLayout.multilineInput(
			'描述你想生成的图片，例如：夕阳下的海面插画',
			'图片描述',
			3,
		);
		this.layout.inputSlot.appendChild(
			MobileAiTaskDialogLayout.cardField('', this.description),
		);

		this.image = document.createElement('img');
		this.image.className = 'mobile-ai-task-dialog__image-thumb';
		this.image.hidden = true;
		this.layout.resultScroll.appendChild(this.image);
		this.layout.resultScroll.hidden = false;
		this.layout.resultPreview.hidden = true;

		this.layout.generateButton.onclick = () => this.generate();
		this.layout.stopButton.onclick = () => this.stop();
		this.layout.regenerateButton.onclick = () => this.generate();
		this.layout.applyButton.onclick = () => this.insert();

		this.sheet.setBody(this.layout.root);
		this.unsubscribe = this.bridge.subscribe((message) =>
			this.handleMessage(message),
		);
	}

	open(): void {
		this.sheet.open();
		this.render();
	}

	close(): void {
		if (this.requestId) {
			this.bridge.cancel(this.requestId);
		}
		this.unsubscribe();
		this.sheet.close();
	}

	private generate(): void {
		const payload = MobileAiImageDialog.buildPayload(this.description.value);
		if (!payload.selection) {
			this.layout.status.textContent = '请输入图片描述';
			return;
		}
		if (this.requestId) {
			this.bridge.cancel(this.requestId);
		}
		this.pendingImage = '';
		this.image.hidden = true;
		this.requestId = this.bridge.request(payload);
		this.render();
	}

	private stop(): void {
		if (!this.requestId) {
			return;
		}
		if (this.bridge.cancel(this.requestId)) {
			this.requestId = '';
			this.render();
		}
	}

	private insert(): void {
		if (!this.pendingImage) {
			return;
		}
		WriterEditorController.getInstance().insertImage(
			'ai-image.png',
			this.pendingImage,
		);
		this.layout.status.textContent = '已插入文档';
	}

	private handleMessage(message: NativeBridgeEnvelope): void {
		if (!this.requestId || message.requestId !== this.requestId) {
			return;
		}
		const payload = message.payload || {};
		if (message.type === 'ai.done' && typeof payload.imageBase64 === 'string') {
			this.pendingImage = payload.imageBase64;
			this.requestId = '';
			this.image.src = 'data:image/png;base64,' + this.pendingImage;
			this.image.hidden = false;
			this.layout.status.textContent = '';
		} else if (message.type === 'ai.error') {
			this.requestId = '';
			this.layout.status.textContent =
				typeof payload.message === 'string' ? payload.message : '图片生成失败';
		} else if (message.type === 'ai.state' && payload.state === 'cancelled') {
			this.requestId = '';
		}
		this.render();
	}

	private render(): void {
		const generating = !!this.requestId;
		const ready = !!this.pendingImage;
		if (ready) {
			this.layout.setStage('result');
			this.layout.inputSlot.hidden = false;
		} else if (generating) {
			this.layout.setStage('generating');
		} else {
			this.layout.setStage('input');
		}
		this.layout.generateButton.disabled = generating;
		this.layout.stopButton.disabled = !generating;
		this.layout.regenerateButton.disabled = generating || !ready;
		this.layout.applyButton.disabled = generating || !ready;
		if (generating && !this.layout.status.textContent) {
			this.layout.status.textContent = 'AI 正在生成图片…';
		}
		if (
			!generating &&
			!ready &&
			this.layout.status.textContent === 'AI 正在生成图片…'
		) {
			this.layout.status.textContent = '';
		}
	}
}

if (typeof window !== 'undefined') {
	(window as any).MobileAiImageDialog = MobileAiImageDialog;
}
