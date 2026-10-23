/*
 * Automatically accept the Core CSV import dialog only while iOS Calc is
 * loading a CSV document. All parsing and detection remain in LibreOffice.
 */
/* global globalThis */
(function (root) {
	'use strict';

	const AUTO_DETECTED_ENCODING = '32768'; // RTL_TEXTENCODING_USER_DETECTED (0x8000)
	const DIALOG_ID = 'TextImportCsvDialog';
	const TIMEOUT_MS = 5000;

	function controlsIn(node, result) {
		if (!node) return result;
		if (Array.isArray(node)) {
			node.forEach((child) => controlsIn(child, result));
			return result;
		}
		if (typeof node === 'object') {
			if (typeof node.id === 'string' && typeof node.type === 'string') result.push(node);
			if (node.control) controlsIn(node.control, result);
			if (node.children) controlsIn(node.children, result);
		}
		return result;
	}

	function csvPath(value) {
		if (typeof value !== 'string' || !value) return false;
		try {
			return /\.csv$/i.test(new URL(value, 'https://localhost/').pathname);
		} catch (_error) {
			return /\.csv(?:[?#].*)?$/i.test(value);
		}
	}

	class IOSCalcCsvImport {
		static _context() {
			const app = root.app;
			const map = app && app.map;
			const layer = map && map._docLayer;
			const urls = map && map.options
				? [map.options.doc, map.options.wopiSrc].filter((url) => typeof url === 'string' && url)
				: [];
			if (!map || !app.socket) return null;
			return { app, map, layer, socket: app.socket, urls };
		}

		static _isEligible(context) {
			return !!(root.ThisIsTheiOSApp && context &&
				(!context.layer || context.layer._docType === 'spreadsheet') &&
				context.map._docLoaded === false && context.urls.some(csvPath));
		}

		static _sameContext(left, right) {
			return !!(left && right && left.map === right.map &&
				left.socket === right.socket && left.urls.join('\0') === right.urls.join('\0'));
		}

		static _fallback(pending) {
			if (!pending || !pending.message || !pending.context ||
				!IOSCalcCsvImport._sameContext(pending.context, IOSCalcCsvImport._context())) return;
			const map = pending.context.map;
			const event = { data: pending.message, callback: pending.callback };
			if (root.mode && root.mode.isMobile && root.mode.isMobile()) map.fire('mobilewizard', event);
			else map.fire('jsdialog', event);
		}

		static _clear(pending) {
			if (pending && pending.timer) root.clearTimeout(pending.timer);
			if (IOSCalcCsvImport.pending === pending) IOSCalcCsvImport.pending = null;
		}

		static _rememberContext(context) {
			if (!IOSCalcCsvImport._sameContext(IOSCalcCsvImport.context, context)) {
				IOSCalcCsvImport._clear(IOSCalcCsvImport.pending);
				IOSCalcCsvImport.context = context;
				IOSCalcCsvImport.seenWindows = new Set();
			}
		}

		static _fail(pending) {
			if (pending && pending.stage === 'confirmed' && pending.context.map._docLoaded !== false) {
				IOSCalcCsvImport._clear(pending);
				return;
			}
			if (pending) IOSCalcCsvImport.seenWindows.delete(pending.windowId);
			IOSCalcCsvImport._clear(pending);
			if (pending && pending.intercepted) IOSCalcCsvImport._fallback(pending);
		}

		static _scheduleTimeout(pending) {
			pending.timer = root.setTimeout(() => IOSCalcCsvImport._fail(pending), TIMEOUT_MS);
		}

		static _send(pending, type, event, id, data) {
			try {
				if (typeof pending.callback === 'function' && pending.callback.length >= 4) {
					pending.callback(type, event, { id }, data, { windowId: pending.windowId });
				} else {
					pending.context.socket.sendMessage(
						'dialogevent ' + pending.windowId + ' ' + JSON.stringify({
							id,
							cmd: event,
							data: String(data),
							type,
						}),
					);
				}
				root._firstDialogHandled = true;
				return true;
			} catch (_error) {
				return false;
			}
		}

		static _responseId(message) {
			const responses = Array.isArray(message.responses) ? message.responses : [];
			const ok = responses.find((response) => Number(response.response) === 1);
			return ok && ok.id;
		}

		static _automaticEncodingSelection(charset) {
			if (!Array.isArray(charset.entries) || !charset.entries.length) return null;
			const entries = charset.entries;
			const idIndex = entries.findIndex((entry) => {
				if (!entry || typeof entry !== 'object') return false;
				const id = entry.id !== undefined ? entry.id : entry.value;
				return String(id !== undefined ? id : '') === AUTO_DETECTED_ENCODING;
			});
			const position = idIndex >= 0 ? idIndex : entries.length - 1;
			const entry = entries[position];
			const text = typeof entry === 'string' ? entry : entry &&
				(entry.text !== undefined ? entry.text : entry.label);
			return { position, text: typeof text === 'string' ? text : '' };
		}

		static _encodingSelected(control, position) {
			if (!control || !Array.isArray(control.selectedEntries) || control.selectedEntries.length < 1)
				return false;
			const selected = Number(control.selectedEntries[0]);
			if (selected === position) return true;
			const entry = Array.isArray(control.entries) ? control.entries[selected] : null;
			return !!(entry && typeof entry === 'object' &&
				String((entry.id !== undefined ? entry.id : entry.value) !== undefined ?
					(entry.id !== undefined ? entry.id : entry.value) : '') === AUTO_DETECTED_ENCODING);

		}

		static _confirm(pending, okId) {
			if (pending.stage === 'confirmed') return;
			pending.stage = 'confirmed';
			if (!okId || !IOSCalcCsvImport._send(pending, 'responsebutton', 'click', okId, 1)) {
				IOSCalcCsvImport._fail(pending);
				return;
			}
		}

		static _setCharset(pending, charset, okId) {
			if (IOSCalcCsvImport._encodingSelected(charset, pending.encodingSelection.position)) {
				IOSCalcCsvImport._confirm(pending, okId);
				return;
			}
			pending.stage = 'charset';
			const selection = pending.encodingSelection;
			if (!IOSCalcCsvImport._send(
				pending,
				'combobox',
				'selected',
				'charset',
				selection.position + ';' + selection.text,
			)) IOSCalcCsvImport._fail(pending);
		}

		static _advance(pending, message) {
			const incomingControls = controlsIn(message, []);
			const updatedIds = new Set(incomingControls.map((control) => control.id));
			if (!pending.controlsById) pending.controlsById = new Map();
			for (const control of incomingControls) pending.controlsById.set(control.id, control);
			const controls = Array.from(pending.controlsById.values());
			const separator = controls.find((control) => control.id === 'todetectseparator');
			const charset = controls.find((control) => control.id === 'charset');
			const okId = IOSCalcCsvImport._responseId(pending.message);
			if (pending.stage === 'confirmed') return;

			if (pending.stage === 'separator') {
				// Advance only after Core confirms that separator detection is active.
				if (!updatedIds.has('todetectseparator') || !separator || separator.checked !== true) return;
				if (!charset) return;
				IOSCalcCsvImport._setCharset(pending, charset, okId);
				return;
			}
			if (pending.stage === 'charset') {
				if (updatedIds.has('charset') && IOSCalcCsvImport._encodingSelected(charset, pending.encodingSelection.position))
					IOSCalcCsvImport._confirm(pending, okId);
				return;
			}
			// Read the controls on initial display. The separator radio usually
			// starts enabled; still send it when the core reports otherwise.
			if (!separator || !charset || !okId) {
				IOSCalcCsvImport._fail(pending);
				return;
			}
			pending.encodingSelection = IOSCalcCsvImport._automaticEncodingSelection(charset);
			if (!pending.encodingSelection) {
				IOSCalcCsvImport._fail(pending);
				return;
			}
			if (separator.checked !== true) {
				pending.stage = 'separator';
				if (!IOSCalcCsvImport._send(pending, 'radiobutton', 'change', separator.id, true)) {
					IOSCalcCsvImport._fail(pending);
				}
				return;
			}
			pending.stage = 'separator';
			IOSCalcCsvImport._setCharset(pending, charset, okId);
		}

		static handleMessage(message, callback) {
			const current = IOSCalcCsvImport._context();
			IOSCalcCsvImport._rememberContext(current);
			let pending = IOSCalcCsvImport.pending;
			if (pending && !IOSCalcCsvImport._sameContext(pending.context, current)) {
				if (pending.stage === 'confirmed') IOSCalcCsvImport._clear(pending);
				else IOSCalcCsvImport._fail(pending);
				pending = null;
			}
			if (!message) return false;

			const windowId = Number(message.id);
			if (pending && pending.windowId === windowId) {
				if (message.dialogid && message.dialogid !== DIALOG_ID) return false;
				if (message.action === 'close') {
					IOSCalcCsvImport._clear(pending);
					return true;
				}
				if (message.action === 'update') {
					IOSCalcCsvImport._advance(pending, message);
					return true;
				}
				// Once loading completes, the same window id can be reused by
				// Text to Columns. Keep consuming the import's trailing updates and
				// close above, but let a new full dialog payload reach the UI.
				if (!IOSCalcCsvImport._isEligible(current)) return false;
				return true; // duplicate full payload for the active window
			}
			if (message.dialogid !== DIALOG_ID) return false;

			// A loaded document may open this same dialog for Text to Columns.
			// The explicit load state and original URL are both required.
			if (IOSCalcCsvImport._isEligible(current) && IOSCalcCsvImport.seenWindows.has(windowId))
				return true;
			if (!IOSCalcCsvImport._isEligible(current) || !Number.isInteger(windowId) || windowId < 0 || message.action ||
				!IOSCalcCsvImport._responseId(message)) return false;

			pending = {
				context: current,
				windowId,
				message,
				callback,
				stage: 'initial',
				intercepted: false,
				timer: 0,
			};
			IOSCalcCsvImport.pending = pending;
			IOSCalcCsvImport._scheduleTimeout(pending);
			IOSCalcCsvImport._advance(pending, message);
			if (IOSCalcCsvImport.pending === pending) {
				pending.intercepted = true;
				IOSCalcCsvImport.seenWindows.add(windowId);
			}
			return IOSCalcCsvImport.pending === pending;
		}

		static resetForTests() {
			IOSCalcCsvImport._clear(IOSCalcCsvImport.pending);
			IOSCalcCsvImport.context = null;
			IOSCalcCsvImport.seenWindows = new Set();
		}
	}

	IOSCalcCsvImport.pending = null;
	IOSCalcCsvImport.context = null;
	IOSCalcCsvImport.seenWindows = new Set();

	root.IOSCalcCsvImport = IOSCalcCsvImport;
	if (typeof module !== 'undefined' && module.exports) module.exports = IOSCalcCsvImport;
})(typeof window !== 'undefined' ? window : globalThis);
