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
	private readonly description: HTMLTextAreaElement;
	private readonly generateButton: HTMLButtonElement;
	private readonly stopButton: HTMLButtonElement;
	private readonly insertButton: HTMLButtonElement;
	private readonly regenerateButton: HTMLButtonElement;
	private readonly image: HTMLImageElement;
	private readonly status: HTMLDivElement;
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
		this.description = document.createElement('textarea');
		const content = document.createElement('div');
		this.sheet = new MobileAiSheet({
			title: 'AI 图片',
			onClose: () => {
				if (this.requestId) {
					this.bridge.cancel(this.requestId);
					this.requestId = '';
				}
				this.unsubscribe();
			},
		});
		this.description.rows = 3;
		this.description.placeholder = '描述你想生成的图片，例如：夕阳下的海面插画';
		this.description.setAttribute('aria-label', '图片描述');
		this.description.style.cssText =
			'width:100%;box-sizing:border-box;resize:vertical;';
		content.appendChild(this.description);

		const actions = document.createElement('div');
		actions.style.cssText = 'display:flex;gap:8px;';
		this.generateButton = this.createButton('生成图片');
		this.generateButton.onclick = () => this.generate();
		actions.appendChild(this.generateButton);
		this.stopButton = this.createButton('停止');
		this.stopButton.onclick = () => this.stop();
		actions.appendChild(this.stopButton);
		content.appendChild(actions);

		this.image = document.createElement('img');
		this.image.style.cssText =
			'max-width:100%;max-height:240px;object-fit:contain;' +
			'display:none;border-radius:8px;';
		content.appendChild(this.image);

		const resultActions = document.createElement('div');
		resultActions.style.cssText = 'display:flex;gap:8px;';
		this.regenerateButton = this.createButton('重新生成');
		this.regenerateButton.onclick = () => this.generate();
		resultActions.appendChild(this.regenerateButton);
		this.insertButton = this.createButton('插入文档');
		this.insertButton.onclick = () => this.insert();
		resultActions.appendChild(this.insertButton);
		content.appendChild(resultActions);

		this.status = document.createElement('div');
		this.status.setAttribute('role', 'status');
		content.appendChild(this.status);

		this.sheet.setBody(content);
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
			this.status.textContent = '请输入图片描述';
			return;
		}
		if (this.requestId) {
			this.bridge.cancel(this.requestId);
		}
		this.pendingImage = '';
		this.requestId = this.bridge.request(payload);
		this.render();
	}

	private stop(): void {
		if (!this.requestId) {
			return;
		}
		// cancel() 的 ack 可能永不返回(请求已终态/消息失败),
		// ack 时同步复位,避免停在 generating 态。
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
		this.status.textContent = '已插入文档';
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
			this.image.style.display = 'block';
			this.status.textContent = '';
		} else if (message.type === 'ai.error') {
			this.requestId = '';
			this.status.textContent =
				typeof payload.message === 'string' ? payload.message : '图片生成失败';
		} else if (message.type === 'ai.state' && payload.state === 'cancelled') {
			this.requestId = '';
		}
		this.render();
	}

	private render(): void {
		const generating = !!this.requestId;
		const ready = !!this.pendingImage;
		this.generateButton.disabled = generating;
		this.stopButton.disabled = !generating;
		this.regenerateButton.disabled = generating || !ready;
		this.insertButton.disabled = generating || !ready;
		if (generating && !this.status.textContent) {
			this.status.textContent = 'AI 正在生成图片...';
		}
		if (!generating && !ready && this.status.textContent === 'AI 正在生成图片...') {
			this.status.textContent = '';
		}
	}

	private createButton(label: string): HTMLButtonElement {
		const button = document.createElement('button');
		button.type = 'button';
		button.textContent = label;
		return button;
	}
}

if (typeof window !== 'undefined') {
	(window as any).MobileAiImageDialog = MobileAiImageDialog;
}