/*
 * Copyright the Collabora Online contributors.
 * SPDX-License-Identifier: MPL-2.0
 *
 * Run with: node --test mocha_tests/MobileCommentTap.node.test.js
 * Exercise real comment touch handlers and the existing comment wizard data
 * route without starting a device or constructing the entire document editor.
 */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const ts = require('typescript');
const { JSDOM } = require('jsdom');

function createHarness({ docType = 'spreadsheet', platform = 'ios', tablet = false,
		desktop = false, dragging = false, editable = true } = {}) {
	const dom = new JSDOM('<!doctype html><body></body>');
	const calls = { selections: [], events: [], core: [], focus: 0, clicks: [], hover: 0, writer: 0 };
	class Point {
		constructor(x, y) { this.pX = x; this.pY = y; }
		get x() { return this.pX; }
		get y() { return this.pY; }
		clone() { return new Point(this.pX, this.pY); }
	}
	const map = {
		getDocType: () => docType,
		isEditMode: () => editable,
		focus() { calls.focus++; },
		fire(name, payload) { calls.events.push({ name, payload }); },
		sendUnoCommand(command, args) { calls.core.push({ command, args }); },
	};
	const layer = map._docLayer = {
		_docType: docType, _selectedPart: 0, _map: map,
		_postMouseEvent(...args) { calls.core.push(args); },
	};
	const app = {
		map, definitions: {}, CSections: {}, pixelsToTwips: 15,
		file: { fileBasedView: false },
		isCommentEditingAllowed: () => editable,
		activeDocument: { mouseControl: { onClick() { calls.writer++; } } },
	};
	const window = dom.window;
	window.ThisIsTheiOSApp = platform === 'ios';
	window.ThisIsTheAndroidApp = platform === 'android';
	window.mode = { isMobile: () => !tablet && !desktop,
		isTablet: () => tablet, isDesktop: () => desktop };
	window.L = { Map: { include() {} }, DomUtil: {
		create(tag, className = '', parent) {
			const node = window.document.createElement(tag);
			node.className = className;
			if (parent) parent.appendChild(node);
			return node;
		},
		createWithId(tag, id, parent) {
			const node = window.document.createElement(tag);
			node.id = id;
			if (parent) parent.appendChild(node);
			return node;
		},
		addClass(node, name) { node.classList.add(name); },
	} };
	const context = vm.createContext({ window, document: window.document,
		DOMRect: window.DOMRect, app, cool: { SimplePoint: Point },
		DOMPurify: { addHook() {} }, _: text => text,
		$: selector => ({ click() { calls.clicks.push(selector); } }), console });
	const load = file => {
		const source = fs.readFileSync(path.resolve(__dirname, '../src', file), 'utf8');
		vm.runInContext(ts.transpileModule(source, {
			compilerOptions: { target: ts.ScriptTarget.ES2016, module: ts.ModuleKind.None },
		}).outputText, context, { filename: file });
	};
	load('canvas/CanvasSectionObject.ts');
	load('canvas/sections/HTMLObjectSection.ts');
	load('canvas/sections/CommentSection.ts');
	load('canvas/sections/CommentListSection.ts');
	load('canvas/sections/CommentMarkerSubSection.ts');

	// Load these exact functions from the real tile layer, without bootstrapping
	// all tile rendering dependencies. Do not duplicate the wizard implementation.
	const tileSource = fs.readFileSync(path.resolve(__dirname, '../src/layer/tile/CanvasTileLayer.js'), 'utf8');
	const tree = ts.createSourceFile('CanvasTileLayer.js', tileSource, ts.ScriptTarget.Latest, true);
	for (const name of ['getCommentWizardStructure', '_openCommentWizard']) {
		let found;
		const visit = node => {
			if (ts.isPropertyAssignment(node) && node.name.getText(tree) === name) found = node.initializer;
			ts.forEachChild(node, visit);
		};
		visit(tree);
		assert.ok(found, name);
		layer[name] = vm.runInContext('(' + found.getText(tree) + ')', context);
	}
	const list = Object.create(context.cool.CommentSection.prototype);
	list.sectionProperties = { commentList: [] };
	list.selectById = id => calls.selections.push(id);
	app.sectionContainer = { getSectionWithName: () => list };
	app.CSections.CommentList = { name: 'comments' };
	map.insertComment = () => { throw new Error('Viewing a comment must not insert one'); };
	const comment = Object.create(context.cool.Comment.prototype);
	comment.sectionProperties = { data: { id: '7', text: 'Existing comment', author: 'Reviewer', tab: '0' },
		partIndex: 0, commentListSection: list };
	comment.containerObject = { isDraggingSomething: () => dragging };
	comment.name = 'comment 7';
	comment.position = [10, 20];
	comment.myTopLeft = [10, 20];
	comment.onMouseEnter = () => { calls.hover++; };
	list.sectionProperties.commentList = [comment, { sectionProperties: {
		data: { id: '8', text: 'Other sheet or slide', tab: '1' }, partIndex: 1,
	} }];
	const marker = Object.create(vm.runInContext('CommentMarkerSubSection.prototype', context));
	marker.name = 'marker 7';
	marker.sectionProperties = { parentSection: comment, data: comment.sectionProperties.data, dragStartPosition: [1, 2] };
	marker.containerObject = { isDraggingSomething: () => dragging };
	marker.position = [30, 40];
	const point = new Point(1, 2);
	const touch = () => new window.Event('touchend', { cancelable: true });
	const click = () => new window.MouseEvent('click');
	return { calls, layer, comment, marker, point, touch, click, close: () => window.close() };
}

function assertOpenedComment(h) {
	const events = h.calls.events.filter(event => event.name === 'mobilewizard');
	assert.equal(events.length, 1);
	const children = events[0].payload.data.children;
	assert.equal(children.length, 1, 'only comments on the current sheet or slide');
	assert.equal(children[0].data.id, '7');
	assert.equal(children[0].text, 'Existing comment');
	assert.equal(h.calls.clicks[0], '#comment7', 'the tapped comment is expanded');
	assert.deepEqual(h.calls.selections, ['7']);
	assert.equal(h.calls.focus, 0, 'reading a comment must not focus the document keyboard');
	assert.equal(h.calls.core.length, 0, 'reading must not send cell clicks or mutations');
}

for (const platform of ['ios', 'android']) {
	for (const editable of [true, false]) {
		test(platform + ' Calc comment tap opens existing content in ' + (editable ? 'edit' : 'read-only') + ' mode', () => {
			const h = createHarness({ platform, editable });
			try {
				const event = h.touch();
				h.comment.onMouseUp(h.point, event);
				assertOpenedComment(h);
				assert.equal(event.defaultPrevented, true);
			} finally { h.close(); }
		});
		test(platform + ' PPT marker tap opens existing content in ' + (editable ? 'edit' : 'read-only') + ' mode', () => {
			const h = createHarness({ platform, editable, docType: 'presentation' });
			try {
				h.marker.onMouseUp(h.point, h.touch());
				assertOpenedComment(h);
				h.marker.onClick(h.point, h.click());
				assert.equal(h.calls.events.length, 1, 'compatibility click must not reopen the wizard');
			} finally { h.close(); }
		});
	}
}

for (const docType of ['spreadsheet', 'presentation']) {
	test('tablet ' + docType + ' scrolling does not open a comment', () => {
		const h = createHarness({ docType, tablet: true, dragging: true });
		try {
			h.comment.onMouseUp(h.point, h.touch());
			assert.equal(h.calls.events.length, 0);
			assert.equal(h.calls.core.length, 0);
		} finally { h.close(); }
	});
}

test('dragging a PPT marker changes its position without opening the comment', () => {
	const h = createHarness({ docType: 'presentation', dragging: true });
	try {
		h.marker.onMouseUp(h.point, h.touch());
		assert.equal(h.calls.events.length, 0);
		assert.equal(h.calls.core.length, 1);
		assert.equal(h.calls.core[0].command, '.uno:EditAnnotation');
		assert.equal(h.calls.core[0].args.Id.value, '7');
		assert.equal(h.calls.core[0].args.PositionX.value, 450);
		assert.equal(h.calls.core[0].args.PositionY.value, 600);
	} finally { h.close(); }
});

test('desktop PPT marker click keeps the existing selection behavior', () => {
	const h = createHarness({ docType: 'presentation', desktop: true });
	try {
		h.marker.onMouseUp(h.point, h.click());
		h.marker.onClick(h.point, h.click());
		assert.deepEqual(h.calls.selections, ['7']);
		assert.equal(h.calls.events.length, 0);
	} finally { h.close(); }
});

test('Writer touch keeps its original document comment route', () => {
	const h = createHarness({ docType: 'text' });
	try {
		h.comment.onMouseUp(h.point, h.touch());
		assert.equal(h.calls.writer, 1);
		assert.equal(h.calls.events.length, 0);
	} finally { h.close(); }
});

test('a removed PPT marker parent does not open stale content', () => {
	const h = createHarness({ docType: 'presentation' });
	try {
		h.marker.sectionProperties.parentSection = null;
		h.marker.onMouseUp(h.point, h.touch());
		assert.equal(h.calls.events.length, 0);
	} finally { h.close(); }
});
