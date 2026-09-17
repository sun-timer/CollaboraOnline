/*
 * Apply Impress slide transitions via Core Sidebar (SlideTransitionPane).
 * Mirrors Android ImpressTransitionApplier: wait for pane init, then
 * iconview select + activate on windowId -1 (mobile has no sidebar DOM).
 */

class ImpressTransitionBridge {
	/** Core Definitions.WindowId.Sidebar */
	private static readonly SIDEBAR_WINDOW_ID = -1;
	private static readonly MAX_ATTEMPTS = 8;
	private static readonly INITIAL_DELAY_MS = 600;
	private static readonly RETRY_DELAY_MS = 400;

	static apply(iconViewIndex: number, applyToAll: boolean): void {
		ImpressTransitionBridge.scheduleAttempt(iconViewIndex, applyToAll, 0);
	}

	private static scheduleAttempt(
		iconViewIndex: number,
		applyToAll: boolean,
		attempt: number,
	): void {
		if (attempt >= ImpressTransitionBridge.MAX_ATTEMPTS) {
			console.warn(
				'ImpressTransitionBridge: timeout attempts=' +
					ImpressTransitionBridge.MAX_ATTEMPTS,
			);
			return;
		}
		const delay =
			ImpressTransitionBridge.INITIAL_DELAY_MS +
			attempt * ImpressTransitionBridge.RETRY_DELAY_MS;
		window.setTimeout(() => {
			const phase = ImpressTransitionBridge.tryApply(
				iconViewIndex,
				applyToAll,
			);
			if (
				phase === 'activated' ||
				phase === 'apply_all' ||
				phase === 'done'
			) {
				return;
			}
			if (attempt + 1 < ImpressTransitionBridge.MAX_ATTEMPTS) {
				ImpressTransitionBridge.scheduleAttempt(
					iconViewIndex,
					applyToAll,
					attempt + 1,
				);
			}
		}, delay);
	}

	private static tryApply(
		iconViewIndex: number,
		applyToAll: boolean,
	): string {
		const socket = (window as any).app?.socket;
		if (!socket || typeof socket.sendMessage !== 'function') {
			return 'waiting';
		}
		try {
			socket.sendMessage('uno .uno:SidebarShow');
			socket.sendMessage('uno .uno:SlideChangeWindow');
		} catch (_e) {
			return 'waiting';
		}
		const map = (window as any).app?.map;
		if (map?.sidebar && typeof map.sidebar.setupTargetDeck === 'function') {
			try {
				map.sidebar.setupTargetDeck('.uno:SlideChangeWindow');
			} catch (_err) {
				// Deck may be unavailable until Core finishes building the pane.
			}
		}

		const windowId = ImpressTransitionBridge.sidebarDialogWindowId();
		ImpressTransitionBridge.sendDialogEvent(
			windowId,
			'transitions_icons',
			'select',
			String(iconViewIndex),
			'iconview',
		);
		ImpressTransitionBridge.sendDialogEvent(
			windowId,
			'transitions_icons',
			'activate',
			String(iconViewIndex),
			'iconview',
		);
		if (applyToAll) {
			ImpressTransitionBridge.sendDialogEvent(
				windowId,
				'apply_to_all',
				'click',
				'0',
				'pushbutton',
			);
			return 'apply_all';
		}
		return 'activated';
	}

	/** iOS function panel: always target Core sidebar (-1), not stale jsdialog ids. */
	private static sidebarDialogWindowId(): number {
		if ((window as any).ThisIsTheiOSApp) {
			return ImpressTransitionBridge.SIDEBAR_WINDOW_ID;
		}
		const sid = (window as any).sidebarId;
		if (sid !== undefined && sid !== null) {
			return sid;
		}
		return ImpressTransitionBridge.SIDEBAR_WINDOW_ID;
	}

	private static sendDialogEvent(
		windowId: number,
		id: string,
		cmd: string,
		data: string,
		type: string,
	): void {
		const socket = (window as any).app?.socket;
		if (!socket || typeof socket.sendMessage !== 'function') {
			return;
		}
		socket.sendMessage(
			'dialogevent ' +
				windowId +
				' {"id":"' +
				id +
				'", "cmd": "' +
				cmd +
				'", "data": "' +
				data +
				'", "type": "' +
				type +
				'"}',
		);
	}
}
