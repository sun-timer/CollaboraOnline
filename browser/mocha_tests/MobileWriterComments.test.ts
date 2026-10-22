/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

var assert = require('assert').strict;

describe('MobileWriterComments', function () {
	it('summarizeWriterComments keeps root unresolved comments', function () {
		const out = MobileWriterComments.summarizeWriterComments(
			[
				{ id: '1', author: 'A', text: 'Hello', parent: '0', resolved: 'false' },
				{ id: '3', author: 'LocalUser#12', text: 'Legacy author', parent: '0' },
				{ id: '2', author: 'B', text: 'Reply', parent: '1' },
				{ id: 'new', author: 'C', text: 'Draft', parent: '0' },
			],
			false,
		);
		assert.equal(out.length, 2);
		assert.equal(out[0].id, '1');
		assert.equal(out[0].author, 'A');
		assert.equal(out[0].kind, undefined, 'the existing comment-list protocol stays unchanged');
		assert.equal(out[1].author, 'LocalUser#12');
	});

	it('remaps only the exact current-view author to the Settings nickname', function () {
		const out = MobileWriterComments.summarizeWriterComments(
			[
				{ id: '1', author: 'LocalUser#12', text: 'Mine', parent: '0' },
				{ id: '2', author: 'LocalUser#13', text: 'Unknown legacy author', parent: '0' },
				{ id: '3', author: 'Alice', text: 'Other author', parent: '0' },
			],
			false,
			{ 'LocalUser#12': 'Settings Nickname' },
		);
		assert.deepEqual(out.map((comment: any) => comment.author), [
			'Settings Nickname',
			'LocalUser#13',
			'Alice',
		]);
	});

	it('requests the existing native comment list without inserting an annotation', function () {
		const originalIOS = (window as any).ThisIsTheiOSApp;
		(window as any).ThisIsTheiOSApp = true;
		const messages: string[] = [];
		try {
			assert.equal(
				MobileWriterComments.requestNativeCommentList((message) => messages.push(message)),
				true,
			);
		} finally {
			(window as any).ThisIsTheiOSApp = originalIOS;
		}
		assert.deepEqual(messages, ['SHOWCOMMENTLIST']);
	});

	it('summarizeWriterComments hides resolved unless requested', function () {
		const hidden = MobileWriterComments.summarizeWriterComments(
			[{ id: '9', author: 'X', text: 'Done', parent: '0', resolved: 'true' }],
			false,
		);
		assert.equal(hidden.length, 0);
		const shown = MobileWriterComments.summarizeWriterComments(
			[{ id: '9', author: 'X', text: 'Done', parent: '0', resolved: 'true' }],
			true,
		);
		assert.equal(shown.length, 1);
		assert.equal(shown[0].resolved, true);
	});

	it('tap lookup returns only anchored Writer comments and toggles their info card', function () {
		const previousApp = (window as any).app;
		const previousIOS = (window as any).ThisIsTheiOSApp;
		const visible = {
			sectionProperties: {
				data: {
					id: '17', author: 'Alice', text: 'A comment', dateTime: '2026-10-22',
					parent: '0', resolved: 'false', trackchange: false,
					rectangles: [{ containsPoint: (point: number[]) => point[0] >= 100 && point[0] <= 200 && point[1] === 300 }],
				},
			},
		};
		const revision = {
			sectionProperties: {
				data: {
					id: 'change-18', author: 'Bob', text: 'tracked edit', parent: '0',
					dateTime: '2026-10-20', description: 'Text insertion', type: 'Insert',
					trackchange: true, rectanglesOriginal: [[300, 300, 100, 8]],
				},
			},
		};
		(window as any).ThisIsTheiOSApp = true;
		(window as any).app = {
			map: { getDocType: () => 'text', isEditMode: () => true, getViewName: () => 'Alice' },
			isCommentEditingAllowed: () => true,
			CSections: { CommentList: { name: 'comment-list' } },
			sectionContainer: {
				getSectionWithName: () => ({ sectionProperties: { commentList: [revision, visible], showResolved: false } }),
			},
		};
		const messages: string[] = [];
		try {
			assert.equal(MobileWriterComments.findWriterCommentAtDocumentPoint({ x: 150, y: 300 })?.id, '17');
			assert.equal(MobileWriterComments.findWriterCommentAtDocumentPoint({ x: 150, y: 301 }), null);
			assert.equal(MobileWriterComments.handleWriterCommentTap({ x: 150, y: 300 }, (message) => messages.push(message)), 'shown');
			assert.deepEqual(JSON.parse(messages[0].slice('SHOWCOMMENTINFO '.length)), {
				id: '17', kind: 'comment', author: 'Alice', text: 'A comment', dateTime: '2026-10-22',
				resolved: false, isOwnAuthor: false, canEdit: true,
			});
			assert.equal(MobileWriterComments.handleWriterCommentTap({ x: 150, y: 300 }, (message) => messages.push(message)), 'dismissed');
			assert.equal(messages[1], 'HIDECOMMENTINFO');
		} finally {
			MobileWriterComments.clearActiveWriterCommentInfo();
			(window as any).app = previousApp;
			(window as any).ThisIsTheiOSApp = previousIOS;
		}
	});

	it('comment info edit/delete callbacks validate IDs and use existing section methods', function () {
		const previousApp = (window as any).app;
		const previousIOS = (window as any).ThisIsTheiOSApp;
		const comment = { sectionProperties: { data: { id: '21', text: 'Current text', trackchange: false } } };
		const revision = { sectionProperties: { data: { id: 'change-22', text: 'Tracked', trackchange: true } } };
		const modified: any[] = [];
		const removed: any[] = [];
		(window as any).ThisIsTheiOSApp = true;
		(window as any).app = {
			map: { getDocType: () => 'text', isEditMode: () => true },
			isCommentEditingAllowed: () => true,
			CSections: { CommentList: { name: 'comment-list' } },
			sectionContainer: {
				getSectionWithName: () => ({
					getComment: (id: string) => id === '21' ? comment : id === 'change-22' ? revision : null,
					modify: (value: any) => modified.push(value),
					remove: (id: string) => removed.push(id),
				}),
			},
		};
		try {
			assert.equal(MobileWriterComments.editWriterCommentInfo('21', 'stale text'), false);
			(window as any).app.isCommentEditingAllowed = () => false;
			assert.equal(MobileWriterComments.editWriterCommentInfo('21', 'Current text'), false);
			assert.deepEqual(modified, []);
			(window as any).app.isCommentEditingAllowed = () => true;
			assert.equal(MobileWriterComments.editWriterCommentInfo('21', 'Current text'), true);
			assert.deepEqual(modified, [comment]);
			assert.equal(MobileWriterComments.deleteWriterCommentInfo('change-22'), false);
			assert.equal(MobileWriterComments.deleteWriterCommentInfo('21'), true);
			assert.deepEqual(removed, ['21']);
		} finally {
			(window as any).app = previousApp;
			(window as any).ThisIsTheiOSApp = previousIOS;
		}
	});

	it('tracks real revision anchors separately from comments and toggles same-kind groups', function () {
		const previousApp = (window as any).app;
		const previousIOS = (window as any).ThisIsTheiOSApp;
		const makeComment = (id: string, trackchange: boolean, text: string) => ({
			sectionProperties: { data: trackchange ? {
				id: 'change-' + id, author: 'Revision Author', text, dateTime: '2026-10-20',
				trackchange: true, description: 'Inserted text', type: 'Insert',
				rectanglesOriginal: [[100, 300, 100, 12]],
			} : {
				id, author: 'Comment Author', text, dateTime: '2026-10-21', parent: '0',
				trackchange: false,
				rectangles: [new cool.SimpleRectangle(300, 300, 100, 12)],
			} },
		});
		const revision = makeComment('19', true, 'Inserted content');
		const commentA = makeComment('20', false, 'A comment');
		const commentB = makeComment('21', false, 'Another comment');
		(window as any).ThisIsTheiOSApp = true;
		(window as any).app = {
			map: { getDocType: () => 'text', isEditMode: () => false, getViewName: () => 'Me' },
			isCommentEditingAllowed: () => false,
			CSections: { CommentList: { name: 'comment-list' } },
			sectionContainer: {
				getSectionWithName: () => ({ sectionProperties: {
					commentList: [revision, commentA, commentB], showResolved: false,
				} }),
			},
		};
		const messages: string[] = [];
		try {
			const hits = MobileWriterComments.findWriterInfoAtDocumentPoint({ x: 150, y: 306 });
			assert.deepEqual(hits.map((item: any) => [item.kind, item.id]), [
				['revision', 'change-19'],
			]);
			assert.deepEqual(MobileWriterComments.findWriterInfoAtDocumentPoint({ x: 250, y: 306 }), []);
			assert.equal(MobileWriterComments.handleWriterCommentTap({ x: 150, y: 306 }, (m) => messages.push(m)), 'shown');
			const revisionPayload = JSON.parse(messages[0].slice('SHOWCOMMENTINFO '.length));
			assert.equal(revisionPayload.kind, 'revision');
			assert.equal(revisionPayload.id, 'change-19');
			assert.equal(revisionPayload.author, 'Revision Author');
			assert.equal(revisionPayload.text, 'Inserted content');
			assert.equal(revisionPayload.description, 'Inserted text');
			assert.equal(revisionPayload.revisionType, 'Insert');
			assert.equal(revisionPayload.dateTime, '2026-10-20');
			assert.equal(revisionPayload.comments, undefined);
			assert.equal(MobileWriterComments.handleWriterCommentTap({ x: 150, y: 306 }, (m) => messages.push(m)), 'dismissed');
			assert.equal(messages[1], 'HIDECOMMENTINFO');
			MobileWriterComments.clearActiveWriterCommentInfo();
			const commentHits = MobileWriterComments.findWriterInfoAtDocumentPoint({ x: 350, y: 306 });
			assert.deepEqual(commentHits.map((item: any) => [item.kind, item.id]), [
				['comment', '20'], ['comment', '21'],
			]);
			assert.equal(MobileWriterComments.handleWriterCommentTap({ x: 350, y: 306 }, (m) => messages.push(m)), 'shown');
			const commentPayload = JSON.parse(messages[2].slice('SHOWCOMMENTINFO '.length));
			assert.equal(commentPayload.kind, 'comment');
			assert.deepEqual(commentPayload.comments.map((item: any) => item.id), ['20', '21']);
			assert.equal(commentPayload.canEdit, false);
		} finally {
			MobileWriterComments.clearActiveWriterCommentInfo();
			(window as any).app = previousApp;
			(window as any).ThisIsTheiOSApp = previousIOS;
		}
	});

	it('closes the native info card when its item is removed or a document reloads', function () {
		const previousApp = (window as any).app;
		const previousIOS = (window as any).ThisIsTheiOSApp;
		const previousPost = (window as any).postMobileMessage;
		const comment = {
			sectionProperties: { data: {
				id: '31', author: 'Alice', text: 'Anchored comment', parent: '0',
				trackchange: false, rectangles: [new cool.SimpleRectangle(50, 70, 100, 10)],
			} },
		};
		const handlers: Record<string, Function> = {};
		const messages: string[] = [];
		const section = { sectionProperties: { commentList: [comment], showResolved: false } };
		const map: any = {
			getDocType: () => 'text', isEditMode: () => false,
			on: (name: string, callback: Function) => (handlers[name] = callback),
		};
		(window as any).ThisIsTheiOSApp = true;
		(window as any).postMobileMessage = (message: string) => messages.push(message);
		(window as any).app = {
			map, isCommentEditingAllowed: () => false,
			CSections: { CommentList: { name: 'comment-list' } },
			sectionContainer: { getSectionWithName: () => section },
		};
		try {
			MobileWriterComments.install();
			assert.equal(MobileWriterComments.handleWriterCommentTap({ x: 70, y: 75 }), 'shown');
			section.sectionProperties.commentList = [];
			handlers.deleteannotation();
			assert.equal(messages[1], 'HIDECOMMENTINFO');
			assert.equal(MobileWriterComments.isWriterCommentInfoVisible(), false);

			section.sectionProperties.commentList = [comment];
			assert.equal(MobileWriterComments.handleWriterCommentTap({ x: 70, y: 75 }), 'shown');
			handlers.docloaded();
			assert.equal(messages[3], 'HIDECOMMENTINFO');
			assert.equal(MobileWriterComments.isWriterCommentInfoVisible(), false);
		} finally {
			MobileWriterComments.clearActiveWriterCommentInfo();
			(window as any).app = previousApp;
			(window as any).ThisIsTheiOSApp = previousIOS;
			(window as any).postMobileMessage = previousPost;
		}
	});
});
