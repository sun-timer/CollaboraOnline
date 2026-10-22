/*
 * Apply Impress transitions only after Core has populated the sidebar icon view.
 * SlideTransitionPane fills its presets from a delayed initialization callback;
 * dispatching activate immediately after opening the deck can dereference an
 * empty selection in Core. Listen to its real JSON rather than guessing a delay.
 */
class ImpressTransitionBridge {
	private static cancelPending: (() => void) | null = null;
	private static pendingReceiver: ((event: any) => void) | null = null;
	private static readonly EVENTS = [
		'sidebar',
		'mobilewizard',
		'jsdialogupdate',
	];

	static apply(iconViewIndex: number, applyToAll: boolean): void {
		ImpressTransitionBridge.cancelPending?.();
		const map = (window as any).app?.map;
		const socket = (window as any).app?.socket;
		if (
			!map?.on ||
			!map?.off ||
			!socket?.sendMessage ||
			!Number.isInteger(iconViewIndex) ||
			iconViewIndex < 0
		)
			return;

		let finished = false;
		let timeout = 0;
		const cleanup = () => {
			finished = true;
			window.clearTimeout(timeout);
			ImpressTransitionBridge.EVENTS.forEach((name) => map.off(name, onData));
			ImpressTransitionBridge.cancelPending = null;
			ImpressTransitionBridge.pendingReceiver = null;
		};
		const onData = (event: any) => {
			const data = event?.data;
			const icons = ImpressTransitionBridge.findIcons(data);
			if (finished || !icons || !Array.isArray(icons.entries)) return;
			// Use the actual row reported by Core. An unavailable preset must
			// never be passed to item_activated (which expects a valid entry).
			const entry = icons.entries.find(
				(item: any) => item.row === iconViewIndex,
			);
			if (!entry || icons.enabled === false) return;
			// iOS always targets window -1; data.id belongs to the document
			// window and must not be echoed back as the dialog target.
			const windowId = -1;
			cleanup();
			window.setTimeout(() => {
				// Select the row first so the iconview shows the choice, then
				// activate it to apply the transition.
				ImpressTransitionBridge.sendDialogEvent(
					windowId,
					'transitions_icons',
					'select',
					String(entry.row),
					'iconview',
				);
				ImpressTransitionBridge.sendDialogEvent(
					windowId,
					'transitions_icons',
					'activate',
					String(entry.row),
					'iconview',
				);
				if (applyToAll)
					ImpressTransitionBridge.sendDialogEvent(
						windowId,
						'apply_to_all',
						'click',
						'0',
						'pushbutton',
					);
			}, 0);
		};
		ImpressTransitionBridge.EVENTS.forEach((name) => map.on(name, onData));
		ImpressTransitionBridge.cancelPending = cleanup;
		ImpressTransitionBridge.pendingReceiver = onData;
		timeout = window.setTimeout(() => {
			cleanup();
			console.warn(
				'ImpressTransitionBridge: Core transition presets not ready',
			);
			map.uiManager?.showSnackbar?.('切换效果尚未加载，请重试');
		}, 6000);
		map.sidebar?.setupTargetDeck?.('.uno:SlideChangeWindow');
		socket.sendMessage('uno .uno:SidebarShow');
		socket.sendMessage('uno .uno:SlideChangeWindow');
	}

	/** Consume only matching transition messages while a selection is pending. */
	static handlePayload(data: any): boolean {
		const receiver = ImpressTransitionBridge.pendingReceiver;
		if (!receiver || !ImpressTransitionBridge.findIcons(data)) return false;
		receiver({ data });
		return true;
	}

	private static findIcons(node: any): any {
		if (!node || typeof node !== 'object') return null;
		if (node.id === 'transitions_icons' && node.type === 'iconview')
			return node;
		if (node.control) {
			const found = ImpressTransitionBridge.findIcons(node.control);
			if (found) return found;
		}
		for (const child of node.children || []) {
			const found = ImpressTransitionBridge.findIcons(child);
			if (found) return found;
		}
		return null;
	}

	private static sendDialogEvent(
		windowId: number,
		id: string,
		cmd: string,
		data: string,
		type: string,
	): void {
		(window as any).app?.socket?.sendMessage(
			'dialogevent ' + windowId + ' ' + JSON.stringify({ id, cmd, data, type }),
		);
	}
}
