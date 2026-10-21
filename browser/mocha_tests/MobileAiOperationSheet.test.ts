/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('Mobile AI operation sheet document routing', function () {
	it('uses the native iOS document type before the WebView map is ready', function () {
		assert.equal(
			MobileAiOperationSheet.resolveDocumentType('spreadsheet', undefined),
			'spreadsheet',
		);
		assert.equal(
			MobileAiOperationSheet.resolveDocumentType('presentation', 'text'),
			'presentation',
		);
	});

	it('falls back to the map type and then Writer only when native type is absent', function () {
		assert.equal(
			MobileAiOperationSheet.resolveDocumentType(undefined, 'spreadsheet'),
			'spreadsheet',
		);
		assert.equal(
			MobileAiOperationSheet.resolveDocumentType(undefined, undefined),
			'text',
		);
	});

	it('disables selection cards without a selection and blocks oversized Word selections', function () {
		const previousApp = (window as any).app;
		const previousBridge = MobileAiBridge.getInstance;
		const previousNativeType = (window as any).MobileNativeDocumentType;
		let selectedText = '';
		let openedTask = '';
		(window as any).MobileNativeDocumentType = 'text';
		(window as any).app = { map: { getDocType: () => 'text' } };
		MobileAiBridge.getInstance = function () {
			return { getSelectedText: () => selectedText } as any;
		};
		const sheet = new MobileAiOperationSheet((entry) => {
			openedTask = entry.taskType;
		});
		sheet.open();
		const polish = document.querySelector(
			'.mobile-ai-operation-card[aria-label="文案润色"]',
		) as HTMLButtonElement;
		assert.ok(polish);
		assert.equal(polish.disabled, true);

		selectedText = '😀'.repeat(5001);
		sheet.open();
		const oversizedExpand = document.querySelector(
			'.mobile-ai-operation-card[aria-label="文案扩写"]',
		) as HTMLButtonElement;
		assert.equal(oversizedExpand.disabled, false);
		oversizedExpand.click();
		assert.equal(openedTask, '');
		assert.equal(
			document.querySelector('.mobile-ai-op-hint')?.textContent,
			'选中内容不可超过5000字符',
		);

		sheet.close();
		MobileAiBridge.getInstance = previousBridge;
		(window as any).app = previousApp;
		if (previousNativeType === undefined) {
			delete (window as any).MobileNativeDocumentType;
		} else {
			(window as any).MobileNativeDocumentType = previousNativeType;
		}
	});
});
