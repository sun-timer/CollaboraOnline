/* -*- js-indent-level: 8 -*- */
/*
 * AndroidNativeDialogRouter - intercept core JSDialog on Android and delegate to native UI.
 */

interface NativeDialogControl {
	id: string;
	type: string;
	text?: string;
	checked?: boolean;
	enabled?: boolean;
	visible?: boolean;
	hidden?: boolean;
	entries?: any;
	selectedEntries?: any;
	image?: string;
}

interface NativeDialogPayload {
	action: 'show' | 'update' | 'dismiss';
	windowId: number;
	dialogId?: string;
	title?: string;
	text?: string;
	dialogType?: string;
	controls?: NativeDialogControl[];
	responses?: Array<{ id: string; response: number }>;
}

/** Dialog types handled by a dedicated native layout on Android. */
const NATIVE_SPECIFIC_DIALOG_IDS = new Set<string>(['DeleteContentsDialog', 'SpellingDialog', 'ValidationDialog', 'MacroSelectorDialog']);

/** Dialogs that receive jsdialog update messages on the native side. */
const NATIVE_UPDATE_DIALOG_IDS = new Set<string>(['SpellingDialog', 'ValidationDialog', 'MacroSelectorDialog']);

class AndroidNativeDialogRouter {
	private activeWindowIds = new Set<number>();
	private activeDialogIds = new Map<number, string>();

	private extractControl(child: any): NativeDialogControl | null {
		if (!child || !child.id) {
			return null;
		}
		const type = child.type;
		const control: NativeDialogControl = {
			id: child.id,
			type: type,
		};
		if (child.text !== undefined) {
			control.text = child.text;
		} else if (child.label !== undefined) {
			control.text = child.label;
		}
		if (child.checked !== undefined) {
			control.checked = !!child.checked;
		}
		if (child.enabled !== undefined) {
			control.enabled = child.enabled;
		}
		if (child.visible !== undefined) {
			control.visible = child.visible;
		}
		if (child.hidden !== undefined) {
			control.hidden = child.hidden;
		}
		if (child.entries !== undefined) {
			control.entries = child.entries;
		}
		if (child.selectedEntries !== undefined) {
			control.selectedEntries = child.selectedEntries;
		}
		if (child.image !== undefined) {
			control.image = child.image;
		}
		return control;
	}

	private flattenControls(children: any[] | undefined, out: NativeDialogControl[]): void {
		if (!children) {
			return;
		}
		for (const child of children) {
			if (!child) {
				continue;
			}
			const extracted = this.extractControl(child);
			if (
				extracted &&
				(extracted.type === 'checkbox' ||
					extracted.type === 'radiobutton' ||
					extracted.type === 'fixedtext' ||
					extracted.type === 'label' ||
					extracted.type === 'listbox' ||
					extracted.type === 'treelistbox' ||
					extracted.type === 'edit' ||
					extracted.type === 'multilineedit' ||
					extracted.type === 'drawingarea' ||
					extracted.type === 'pushbutton' ||
					extracted.type === 'okbutton' ||
					extracted.type === 'cancelbutton' ||
					extracted.type === 'helpbutton')
			) {
				out.push(extracted);
			}
			if (child.children) {
				this.flattenControls(child.children, out);
			}
		}
	}

	private buildPayload(msgData: any, action: 'show' | 'update' | 'dismiss'): NativeDialogPayload {
		const controls: NativeDialogControl[] = [];
		if (action === 'update' && msgData.control) {
			const extracted = this.extractControl(msgData.control);
			if (extracted) {
				controls.push(extracted);
			}
		} else {
			this.flattenControls(msgData.children, controls);
		}
		const dialogId =
			msgData.dialogid || this.activeDialogIds.get(msgData.id) || '';
		return {
			action: action,
			windowId: msgData.id,
			dialogId: dialogId,
			title: msgData.title || msgData.text || '',
			text: msgData.text || '',
			dialogType: msgData.type,
			controls: controls,
			responses: msgData.responses,
		};
	}

	private postPayload(payload: NativeDialogPayload): void {
		if (!window.ThisIsTheAndroidApp || typeof window.postMobileMessage !== 'function') {
			return;
		}
		window.postMobileMessage('JSDIALOG ' + JSON.stringify(payload));
	}

	private forwardsUpdates(dialogId: string): boolean {
		return NATIVE_UPDATE_DIALOG_IDS.has(dialogId);
	}

	public shouldIntercept(msgData: any): boolean {
		if (!window.ThisIsTheAndroidApp || !msgData || msgData.id === undefined) {
			return false;
		}
		if (msgData.action === 'close') {
			return this.activeWindowIds.has(msgData.id);
		}
		if (msgData.action === 'update') {
			const dialogId = this.activeDialogIds.get(msgData.id) || '';
			return this.activeWindowIds.has(msgData.id) && this.forwardsUpdates(dialogId);
		}
		if (msgData.action) {
			return this.activeWindowIds.has(msgData.id);
		}
		if (msgData.type === 'messagebox') {
			return true;
		}
		if (msgData.dialogid && NATIVE_SPECIFIC_DIALOG_IDS.has(msgData.dialogid)) {
			return true;
		}
		return false;
	}

	public tryIntercept(msgData: any): boolean {
		if (!this.shouldIntercept(msgData)) {
			return false;
		}

		if (msgData.action === 'close') {
			this.activeWindowIds.delete(msgData.id);
			this.activeDialogIds.delete(msgData.id);
			this.postPayload(this.buildPayload(msgData, 'dismiss'));
			return true;
		}

		if (msgData.action === 'update') {
			this.postPayload(this.buildPayload(msgData, 'update'));
			return true;
		}

		if (msgData.action) {
			// other actions for an already-native dialog: swallow
			return this.activeWindowIds.has(msgData.id);
		}

		this.activeWindowIds.add(msgData.id);
		if (msgData.dialogid) {
			this.activeDialogIds.set(msgData.id, msgData.dialogid);
		}
		this.postPayload(this.buildPayload(msgData, 'show'));
		window.app.console.log(
			'AndroidNativeDialogRouter: intercept dialogId=' +
				msgData.dialogid +
				' windowId=' +
				msgData.id +
				' type=' +
				msgData.type,
		);
		return true;
	}

	public unregister(windowId: number): void {
		this.activeWindowIds.delete(windowId);
		this.activeDialogIds.delete(windowId);
	}
}

const androidNativeDialogRouter = new AndroidNativeDialogRouter();
window.AndroidNativeDialogRouter = androidNativeDialogRouter;
