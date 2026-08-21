/*
 * iOS Browser AI entry point.
 *
 * The old implementation mounted a fixed card during page load. This facade
 * keeps the public WriterAiPanel name while exposing Android-style user
 * triggered Assistant and operation sheets.
 */

class WriterAiPanel {
	private readonly assistantPanel: MobileAiAssistantPanel;
	private readonly operationSheet: MobileAiOperationSheet;
	private activeDialog:
		| MobileAiOperationDialog
		| MobileAiTranslateDialog
		| MobileAiArticleDialog
		| MobileAiOutlineDialog
		| null = null;

	private constructor() {
		this.assistantPanel = new MobileAiAssistantPanel();
		this.operationSheet = new MobileAiOperationSheet((entry) => {
			this.openTask(entry.taskType);
		});
	}

	static mount(): WriterAiPanel | null {
		if (
			typeof window === 'undefined' ||
			!(window as any).ThisIsTheiOSApp ||
			typeof document === 'undefined' ||
			!document.body
		) {
			return null;
		}
		const existing = (window as any).__coolWriterAiPanel;
		if (existing instanceof WriterAiPanel) {
			return existing;
		}
		const panel = new WriterAiPanel();
		(window as any).__coolWriterAiPanel = panel;
		(window as any).WriterAiPanel = panel;
		return panel;
	}

	openAssistant(): void {
		this.assistantPanel.open();
	}

	openOperationSheet(): void {
		this.operationSheet.open();
	}

	openTask(taskType: string): void {
		const entry = MobileAiUiCatalog.getEntry(taskType);
		if (!entry || !entry.iosSupport) {
			this.showUnavailable(taskType);
			return;
		}
		if (this.activeDialog && typeof (this.activeDialog as any).close === 'function') {
			(this.activeDialog as any).close();
		}
		if (entry.dialog === 'translate') {
			this.activeDialog = new MobileAiTranslateDialog();
		} else {
			this.activeDialog = new MobileAiOperationDialog(taskType);
		}
		this.activeDialog.open();
	}

	dispose(): void {
		const controller = (window as any).__coolWriterAiController;
		if (controller instanceof WriterAiController) {
			controller.dispose();
		}
		const conversationController = (window as any).__coolMobileAiConversationController;
		if (conversationController instanceof MobileAiConversationController) {
			conversationController.dispose();
		}
		(window as any).__coolWriterAiPanel = undefined;
	}

	private showUnavailable(taskType: string): void {
		const entry = MobileAiUiCatalog.getEntry(taskType);
		const message = entry
			? `${entry.label}：iOS 尚未支持`
			: '此 AI 功能暂未支持';
		const status = document.createElement('div');
		status.textContent = message;
		status.setAttribute('role', 'status');
		status.style.cssText =
			'position:fixed;left:50%;bottom:24px;z-index:10001;transform:translateX(-50%);' +
			'padding:10px 14px;border-radius:8px;background:#303134;color:#fff;';
		document.body.appendChild(status);
		window.setTimeout(() => status.remove(), 2200);
	}
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	const mount = () => {
		if (WriterAiPanel.mount()) {
			return;
		}
		window.setTimeout(mount, 250);
	};
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', mount, { once: true });
	} else {
		mount();
	}
}
