/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('TextInput mobile IME handling', function () {
	it('keeps iOS composition selection intact and preserves text diffs', function () {
		const fs = require('fs');
		const vm = require('vm');
		const originalWindowL = (window as any).L;
		const originalL = originalWindowL || L;
		(window as any).L = originalL;
		const originalMobileMode = (window as any).mode;
		const originalIOS = (window as any).ThisIsTheiOSApp;
		const originalAndroid = (window as any).ThisIsTheAndroidApp;
		const originalApp = (global as any).app;
		const originalTextInput = originalL.TextInput;
		const sent: string[] = [];
		let cursorMoves = 0;
		let selectionValid = true;
		let content = 'g';
		const removals: number[] = [];
		const TextInput = (window.L.TextInput || (() => {
			window.L = Object.assign({}, originalL, {
				Layer: { extend: (properties: any) => properties },
				Browser: Object.assign({}, originalL.Browser, { gecko: false }),
			});
			vm.runInThisContext(fs.readFileSync('src/layer/marker/TextInput.js', 'utf8'));
			return window.L.TextInput;
		})()) as any;

		try {
			(window as any).mode = { isMobile: () => true };
			(window as any).ThisIsTheiOSApp = true;
			(window as any).ThisIsTheAndroidApp = false;
			(global as any).app = { idleHandler: { notifyActive: () => {} } };
			const input: any = {
				_map: {
					uiManager: { isUIBlocked: () => false },
					_debug: { logKeyboardEvents: false },
					getDocType: () => 'text',
					mention: { handleMentionInput: () => {} },
				},
				_isComposing: false,
				hasAccessibilitySupport: () => false,
				_ignoreInputCount: 0,
				_deleteHint: '',
				_newlineHint: false,
				_ignoreNextBackspace: false,
				_lastContent: [],
				_isSelectionValid: () => selectionValid,
				_isInitialContent: () => false,
				_isCursorAtBeginning: () => false,
				_hasPreSpace: () => true,
				_hasPostSpace: () => true,
				getValue: () => content,
				getValueAsCodePoints: (value: string) => Array.from(value).map((char) => char.codePointAt(0)),
				codePointsToString: (text: number[]) => String.fromCodePoint(...text),
				_setCursorPosition: () => {
					cursorMoves++;
					selectionValid = false;
				},
				_emptyArea: function () {
					this._lastContent = [];
					content = '';
				},
				_removeTextContent: (before: number) => removals.push(before),
				_flushAndroidRemoveText: () => {},
				_sendNewText: (_event: any, _content: number[], text: number[]) => sent.push(String.fromCodePoint(...text)),
				_finishFormulabarEditing: () => {},
				_hasFormulaBarFocus: () => false,
				_statusLog: () => {},
			};
			input._onInput = (event: any) => TextInput._onInput.call(input, event);
			const event = { inputType: 'insertCompositionText', data: 'g' };

			// iOS must not get the generic mobile caret repair even for ordinary
			// first-character input.
			TextInput._onInput.call(input, { inputType: 'insertText', data: 'g' });
			assert.equal(cursorMoves, 0);
			sent.length = 0;
			input._lastContent = [];

			TextInput._onCompositionStart.call(input, event);
			TextInput._onCompositionUpdate.call(input, event);
			assert.deepEqual(sent, ['g'], 'incremental preedit text is forwarded');
			assert.equal(cursorMoves, 0, 'iOS composing selection is left intact');

			// A later beforeinput is invalidated if caret repair rewrites WebKit's
			// marked range; the old code then emptied _lastContent and resent commit.
			TextInput._onBeforeInput.call(input, event);
			content = 'g';
			selectionValid = true;

			// WebKit delivers the same value again at commit. The maintained content
			// diff must deduplicate that event while still accepting a second real g.
			TextInput._onCompositionEnd.call(input, { data: 'g' });
			assert.deepEqual(sent, ['g'], 'the duplicate commit is removed by content diffing');
			assert.equal(cursorMoves, 0);

			content = 'guo';
			TextInput._onCompositionUpdate.call(input, { inputType: 'insertCompositionText', data: 'guo' });
			assert.deepEqual(sent, ['g', 'uo'], 'composition replacement sends only the changed suffix');
			content = '国';
			TextInput._onCompositionUpdate.call(input, { inputType: 'insertCompositionText', data: '国' });
			assert.equal(removals[0], 3, 'replacing pinyin removes the previous composing string');
			assert.deepEqual(sent, ['g', 'uo', '国']);
			TextInput._onCompositionEnd.call(input, { data: '国' });
			assert.deepEqual(sent, ['g', 'uo', '国']);

			content = '';
			TextInput._onInput.call(input, { inputType: 'deleteContentBackward', data: null });
			assert.deepEqual(sent, ['g', 'uo', '国'], 'deleting preedit sends removal without reinserting text');
			content = 'g';
			TextInput._onInput.call(input, { inputType: 'insertText', data: 'g' });
			content = 'gg';
			TextInput._onInput.call(input, { inputType: 'insertText', data: 'g' });
			assert.deepEqual(sent.slice(-2), ['g', 'g'], 'two legitimate Latin g inputs are preserved');
			content = '🙂';
			TextInput._onInput.call(input, { inputType: 'insertText', data: '🙂' });
			assert.equal(sent[sent.length - 1], '🙂', 'a supplementary Unicode character remains intact');

			// Preserve the existing Android workaround, including while composing.
			(window as any).ThisIsTheiOSApp = false;
			(window as any).ThisIsTheAndroidApp = true;
			input._isComposing = true;
			input._lastContent = [];
			content = 'g';
			TextInput._onInput.call(input, event);
			assert.equal(cursorMoves, 1, 'Android mobile caret repair behavior is unchanged');
		} finally {
			(window as any).mode = originalMobileMode;
			(window as any).ThisIsTheiOSApp = originalIOS;
			(window as any).ThisIsTheAndroidApp = originalAndroid;
			(global as any).app = originalApp;
			if (originalTextInput === undefined) {
				delete window.L.TextInput;
			}
			if (originalWindowL === undefined) {
				delete (window as any).L;
			} else {
				(window as any).L = originalWindowL;
			}
		}
	});

	it('draws an iOS composition underline aligned to the caret handle', function () {
		const fs = require('fs');
		const vm = require('vm');
		const originalWindowL = (window as any).L;
		const baseL = originalWindowL || L;
		const originalIOS = (window as any).ThisIsTheiOSApp;
		const handler = document.createElement('div');
		const textArea = document.createElement('div');
		textArea.textContent = 'shhs';

		(window as any).L = Object.assign({}, baseL, {
			Layer: { extend: (properties: any) => properties },
		});

		try {
			vm.runInThisContext(fs.readFileSync('src/layer/marker/TextInput.js', 'utf8'));
			const TextInput = (window as any).L.TextInput as any;

			(window as any).ThisIsTheiOSApp = true;
			handler.className = 'cursor-handler';
			handler.style.left = '120px';
			handler.style.top = '80px';
			document.body.appendChild(handler);

			const input: any = { _isComposing: true, _textArea: textArea };
			TextInput._updateCompositionUnderline.call(input);

			const underline = document.getElementById('ios-composition-underline') as any;
			assert.ok(underline, 'underline element is created');
			assert.equal(underline.parentNode, handler.parentNode, 'underline shares the cursor handler offset parent');
			assert.equal(underline.style.display, '', 'underline is shown while composing');

			const width = parseFloat(underline.style.width);
			assert.ok(width > 8, 'underline width follows the composing string, got ' + width);
			assert.ok(Math.abs(parseFloat(underline.style.left) + width - 120) <= 1, 'right edge sits on the caret X');
			assert.ok(Math.abs(parseFloat(underline.style.top) + 2 - 80) <= 1, 'bottom edge sits on the caret Y');

			TextInput._hideCompositionUnderline.call(input);
			assert.equal(underline.style.display, 'none', 'underline is hidden when composition ends');
		} finally {
			(window as any).ThisIsTheiOSApp = originalIOS;
			if (originalWindowL === undefined) {
				delete (window as any).L;
			} else {
				(window as any).L = originalWindowL;
			}
			handler.remove();
			const underline = document.getElementById('ios-composition-underline');
			if (underline)
				underline.remove();
		}
	});
});
