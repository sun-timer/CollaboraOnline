/*
 * iOS document comment list bridge: export CO comments to native shell and
 * navigate/focus on selection.
 */

interface WriterCommentSummary {
	id: string;
	kind?: 'comment' | 'revision';
	author: string;
	text: string;
	dateTime: string;
	resolved: boolean;
	canEdit?: boolean;
	isOwnAuthor?: boolean;
	description?: string;
	revisionType?: string;
}

/** Pure helper — unit-tested without DOM/CO. */
function summarizeWriterComments(
	items: Array<{
		id?: string | number;
		author?: string;
		text?: string;
		dateTime?: string;
		parent?: string | number;
		resolved?: string | boolean;
	}>,
	showResolved: boolean,
	authorAliases: Record<string, string> = {},
): WriterCommentSummary[] {
	const out: WriterCommentSummary[] = [];
	for (const item of items) {
		if (!item || item.id === undefined || item.id === 'new') {
			continue;
		}
		const parent = item.parent !== undefined ? String(item.parent) : '0';
		if (parent !== '0') {
			continue;
		}
		const resolved =
			item.resolved === true ||
			item.resolved === 'true';
		if (!showResolved && resolved) {
			continue;
		}
		out.push({
			id: String(item.id),
			author:
				item.author && typeof authorAliases[item.author] === 'string'
					? authorAliases[item.author]
					: item.author || '',
			text: item.text || '',
			dateTime: item.dateTime || '',
			resolved,
		});
	}
	return out;
}

class MobileWriterComments {
	static summarizeWriterComments = summarizeWriterComments;
	private static readonly ownAuthorAliases: Record<string, string> = Object.create(null);
	private static activeInfoCommentId = '';
	private static activeInfoKind: 'comment' | 'revision' | '' = '';

	/** Map an exact current-view author name to the updated Settings nickname. */
	static registerOwnAuthorAlias(
		author: string,
		displayName: string,
		aliases: Record<string, string> = MobileWriterComments.ownAuthorAliases,
	): boolean {
		const source = (author || '').trim();
		const target = (displayName || '').trim();
		if (!source || !target || source === target) {
			return false;
		}
		aliases[source] = target;
		return true;
	}

	static isIOs(): boolean {
		return typeof window !== 'undefined' && !!(window as any).ThisIsTheiOSApp;
	}

	static collectWriterComments(): WriterCommentSummary[] {
		// First document open fires showannotations from WriterTileLayer.beforeAdd,
		// before CanvasTileLayer.onAdd creates the section container. Reading it
		// then throws and aborts addLayer, so the canvas stays hidden.
		if (!app.sectionContainer) {
			return [];
		}
		const sec = app.sectionContainer.getSectionWithName(
			app.CSections.CommentList.name,
		) as any;
		if (!sec || !sec.sectionProperties) {
			return [];
		}
		const showResolved = !!sec.sectionProperties.showResolved;
		const raw = (sec.sectionProperties.commentList || []).map((comment: any) => {
			const data = comment.sectionProperties?.data || {};
			return {
				id: data.id,
				author: data.author,
				text: data.text,
				dateTime: data.dateTime,
				parent: data.parent,
				resolved: data.resolved,
			};
		});
		return summarizeWriterComments(
			raw,
			showResolved,
			MobileWriterComments.ownAuthorAliases,
		);
	}

	static countVisibleComments(): number {
		return MobileWriterComments.collectWriterComments().length;
	}

	/** Find a real Writer comment whose text anchor contains a document point. */
	static findWriterCommentAtDocumentPoint(
		point: { x: number; y: number },
	): WriterCommentSummary | null {
		return MobileWriterComments.findWriterInfoAtDocumentPoint(point)[0] || null;
	}

	/** Find all real Writer comments whose text anchors contain a document point. */
	static findWriterCommentsAtDocumentPoint(
		point: { x: number; y: number },
	): WriterCommentSummary[] {
		return MobileWriterComments.findWriterInfoAtDocumentPoint(point).filter(
			(item) => item.kind === 'comment',
		);
	}

	/** Find actual comments and tracked revisions whose rendered anchors contain a point. */
	static findWriterInfoAtDocumentPoint(
		point: { x: number; y: number },
	): WriterCommentSummary[] {
		if (
			!MobileWriterComments.isIOs() ||
			!app.map ||
			app.map.getDocType() !== 'text' ||
			!app.sectionContainer ||
			!point
		) {
			return [];
		}
		const section = app.sectionContainer.getSectionWithName(
			app.CSections.CommentList.name,
		) as any;
		if (!section?.sectionProperties) {
			return [];
		}
		const { commentList = [], showResolved = false } = section.sectionProperties;
		const matches: any[] = [];
		for (const comment of commentList) {
			const data = comment?.sectionProperties?.data;
			if (
				!data ||
				data.id === undefined ||
				data.id === 'new' ||
				(data.parent !== undefined && String(data.parent) !== '0') ||
				!data.trackchange && (data.resolved === true || data.resolved === 'true') && !showResolved
			) {
				continue;
			}
			const isRevision = !!data.trackchange;
			const containsPoint = isRevision
				? (data.rectanglesOriginal || []).some((rect: any) => {
					// adjustRedLine stores textRange rectangles as [x, y, width, height].
					if (!Array.isArray(rect) || rect.length < 4) {
						return false;
					}
					return new cool.SimpleRectangle(rect[0], rect[1], rect[2], rect[3])
						.containsPoint([point.x, point.y]);
				})
				: (data.rectangles || []).some((rect: any) =>
					typeof rect?.containsPoint === 'function' &&
					rect.containsPoint([point.x, point.y]),
				);
			if (!containsPoint) {
				continue;
			}
			const summary = summarizeWriterComments(
				[{
					id: data.id,
					author: data.author,
					text: data.text,
					dateTime: data.dateTime,
					parent: data.parent,
					resolved: data.resolved,
				}],
				true,
				isRevision ? {} : MobileWriterComments.ownAuthorAliases,
			)[0];
			if (summary) {
				summary.kind = isRevision ? 'revision' : 'comment';
				if (isRevision) {
					summary.description = typeof data.description === 'string' ? data.description : '';
					summary.revisionType = typeof data.type === 'string' ? data.type : '';
				} else {
					const currentAuthor = app.map.getViewName?.(app.map._docLayer?._viewId);
					summary.isOwnAuthor = !!currentAuthor && data.author === currentAuthor;
					summary.canEdit = !!app.map.isEditMode() && !!app.isCommentEditingAllowed();
				}
				matches.push(summary);
			}
		}
		return matches;
	}

	/** Show the tapped comment or toggle its info card off on a repeated tap. */
	static handleWriterCommentTap(
		point: { x: number; y: number },
		postMessage?: (message: string) => void,
	): 'shown' | 'dismissed' | 'none' {
		const matches = MobileWriterComments.findWriterInfoAtDocumentPoint(point);
		const active = matches.find((item) =>
			item.id === MobileWriterComments.activeInfoCommentId &&
			item.kind === MobileWriterComments.activeInfoKind,
		);
		const comment = active || matches[0];
		if (!comment) {
			return 'none';
		}
		const send = postMessage || (window as any).postMobileMessage;
		if (typeof send !== 'function') {
			return 'none';
		}
		if (
			MobileWriterComments.activeInfoCommentId === comment.id &&
			MobileWriterComments.activeInfoKind === comment.kind
		) {
			send('HIDECOMMENTINFO');
			MobileWriterComments.activeInfoCommentId = '';
			MobileWriterComments.activeInfoKind = '';
			return 'dismissed';
		}
		const payload: any = { ...comment };
		const group = matches.filter((item) => item.kind === comment.kind);
		if (group.length > 1) {
			payload.comments = group;
		}
		MobileWriterComments.activeInfoCommentId = comment.id;
		MobileWriterComments.activeInfoKind = comment.kind || 'comment';
		send('SHOWCOMMENTINFO ' + JSON.stringify(payload));
		return 'shown';
	}

	static clearActiveWriterCommentInfo(notifyNative = false): void {
		const wasVisible = MobileWriterComments.isWriterCommentInfoVisible();
		MobileWriterComments.activeInfoCommentId = '';
		MobileWriterComments.activeInfoKind = '';
		if (wasVisible && notifyNative && typeof (window as any).postMobileMessage === 'function') {
			(window as any).postMobileMessage('HIDECOMMENTINFO');
		}
	}

	private static closeInfoIfActiveItemWasRemoved(): void {
		if (!MobileWriterComments.isWriterCommentInfoVisible() || !app.sectionContainer) {
			return;
		}
		const section = app.sectionContainer.getSectionWithName(
			app.CSections.CommentList.name,
		) as any;
		const list = section?.sectionProperties?.commentList || [];
		const active = list.some((item: any) => {
			const data = item?.sectionProperties?.data;
			return data && String(data.id) === MobileWriterComments.activeInfoCommentId &&
				(data.trackchange ? 'revision' : 'comment') === MobileWriterComments.activeInfoKind;
		});
		if (!active) {
			MobileWriterComments.clearActiveWriterCommentInfo(true);
		}
	}

	static isWriterCommentInfoActive(id: string, kind?: 'comment' | 'revision'): boolean {
		return MobileWriterComments.activeInfoCommentId === String(id || '') &&
			(!kind || MobileWriterComments.activeInfoKind === kind);
	}

	static isWriterCommentInfoVisible(): boolean {
		return !!MobileWriterComments.activeInfoCommentId;
	}

	static shouldSuppressDocumentKeyboard(): boolean {
		return MobileWriterComments.isWriterCommentInfoVisible() ||
			!!(window as any).AndroidSelectionMenu?.shouldSuppressDocumentKeyboard?.();
	}

	static editWriterCommentInfo(id: string, expectedText?: string): boolean {
		const comment = MobileWriterComments.getRealWriterComment(id);
		if (
			!comment ||
			!app.map?.isEditMode() ||
			!app.isCommentEditingAllowed() ||
			(expectedText !== undefined && comment.sectionProperties.data.text !== expectedText)
		) {
			return false;
		}
		const section = app.sectionContainer.getSectionWithName(
			app.CSections.CommentList.name,
		) as any;
		if (typeof section.modify !== 'function') {
			return false;
		}
		try {
			section.modify(comment);
			MobileWriterComments.clearActiveWriterCommentInfo();
			return true;
		} catch (error) {
			return false;
		}
	}

	static deleteWriterCommentInfo(id: string): boolean {
		const comment = MobileWriterComments.getRealWriterComment(id);
		if (!comment || !app.map?.isEditMode() || !app.isCommentEditingAllowed()) {
			return false;
		}
		const section = app.sectionContainer.getSectionWithName(
			app.CSections.CommentList.name,
		) as any;
		if (typeof section.remove !== 'function') {
			return false;
		}
		try {
			section.remove(comment.sectionProperties.data.id);
			MobileWriterComments.clearActiveWriterCommentInfo();
			return true;
		} catch (error) {
			return false;
		}
	}

	private static getRealWriterComment(id: string): any {
		if (
			!MobileWriterComments.isIOs() ||
			!app.map ||
			app.map.getDocType() !== 'text' ||
			!app.sectionContainer ||
			!id
		) {
			return null;
		}
		const section = app.sectionContainer.getSectionWithName(
			app.CSections.CommentList.name,
		) as any;
		const comment = section?.getComment?.(id);
		const data = comment?.sectionProperties?.data;
		return data && String(data.id) === String(id) && !data.trackchange && id !== 'new'
			? comment
			: null;
	}

	static requestNativeCommentList(postMessage?: (message: string) => void): boolean {
		const send = postMessage || (window as any).postMobileMessage;
		if (!MobileWriterComments.isIOs() || typeof send !== 'function') {
			return false;
		}
		send('SHOWCOMMENTLIST');
		return true;
	}

	static syncCommentCountToNative(): void {
		if (!MobileWriterComments.isIOs() || typeof (window as any).postMobileMessage !== 'function') {
			return;
		}
		const count = MobileWriterComments.countVisibleComments();
		(window as any).postMobileMessage('COMMENTCOUNT n=' + count);
	}

	static fetchCommentsJson(): string {
		return JSON.stringify({
			comments: MobileWriterComments.collectWriterComments(),
		});
	}

	static navigateToComment(id: string): boolean {
		if (!app.sectionContainer || !id) {
			return false;
		}
		const sec = app.sectionContainer.getSectionWithName(
			app.CSections.CommentList.name,
		) as any;
		if (!sec || !id) {
			return false;
		}
		const list = sec.sectionProperties?.commentList || [];
		for (let i = 0; i < list.length; i++) {
			const comment = list[i];
			const data = comment.sectionProperties?.data;
			if (data && String(data.id) === String(id)) {
				if (typeof app.map.showComments === 'function') {
					app.map.showComments(true);
				}
				sec.navigateAndFocusComment(comment);
				return true;
			}
		}
		return false;
	}

	static install(): void {
		if (!MobileWriterComments.isIOs() || !app.map) {
			return;
		}
		const map = app.map as any;
		if (map.__mobileWriterCommentsInstalled) {
			return;
		}
		map.__mobileWriterCommentsInstalled = true;
		const sync = () => {
			try {
				MobileWriterComments.syncCommentCountToNative();
			} catch (error) {
				window.app.console.error('MobileWriterComments sync failed', error);
			}
		};
		map.on('docloaded', () => {
			MobileWriterComments.clearActiveWriterCommentInfo(true);
			sync();
		});
		map.on('insertannotation', () => {
			MobileWriterComments.closeInfoIfActiveItemWasRemoved();
			sync();
		});
		map.on('deleteannotation', () => {
			MobileWriterComments.closeInfoIfActiveItemWasRemoved();
			sync();
		});
		map.on('commandstatechanged', (event: any) => {
			if (
				event &&
				(event.commandName === 'showannotations' ||
					event.commandName === 'ShowResolvedAnnotations')
			) {
				sync();
			}
		});
		window.setTimeout(sync, 300);
	}
}

if (typeof window !== 'undefined') {
	(window as any).MobileWriterComments = MobileWriterComments;
	(window as any).__coolFetchWriterComments = () => MobileWriterComments.fetchCommentsJson();
	(window as any).__coolNavigateWriterComment = (id: string) =>
		MobileWriterComments.navigateToComment(id);
	(window as any).__coolOpenCommentList = () =>
		MobileWriterComments.requestNativeCommentList();
	(window as any).__coolClearWriterCommentInfo = () =>
		MobileWriterComments.clearActiveWriterCommentInfo();
	(window as any).__coolIsWriterCommentInfoVisible = () =>
		MobileWriterComments.isWriterCommentInfoVisible();
	(window as any).__coolShouldSuppressDocumentKeyboard = () =>
		MobileWriterComments.shouldSuppressDocumentKeyboard();
	(window as any).__coolEditWriterCommentInfo = (id: string, expectedText?: string) =>
		MobileWriterComments.editWriterCommentInfo(id, expectedText);
	(window as any).__coolDeleteWriterCommentInfo = (id: string) =>
		MobileWriterComments.deleteWriterCommentInfo(id);
	(window as any).__coolRegisterOwnCommentAuthorAlias = (displayName: string) => {
		const map = (window as any).app?.map;
		const viewId = map?._docLayer?._viewId;
		if (!map || !Number.isInteger(viewId) || typeof map.getViewName !== 'function') {
			return false;
		}
		return MobileWriterComments.registerOwnAuthorAlias(
			map.getViewName(viewId),
			displayName,
		);
	};
}

if (typeof window !== 'undefined' && (window as any).ThisIsTheiOSApp) {
	const installWhenReady = () => {
		if (app.map) {
			MobileWriterComments.install();
			return;
		}
		window.setTimeout(installWhenReady, 250);
	};
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', installWhenReady, { once: true });
	} else {
		installWhenReady();
	}
}
