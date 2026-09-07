/*
 * iOS Writer comment list bridge: export CO comments to native shell and
 * navigate/focus on selection.
 */

interface WriterCommentSummary {
	id: string;
	author: string;
	text: string;
	dateTime: string;
	resolved: boolean;
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
			author: item.author || '',
			text: item.text || '',
			dateTime: item.dateTime || '',
			resolved,
		});
	}
	return out;
}

class MobileWriterComments {
	static summarizeWriterComments = summarizeWriterComments;

	static isIOs(): boolean {
		return typeof window !== 'undefined' && !!(window as any).ThisIsTheiOSApp;
	}

	static collectWriterComments(): WriterCommentSummary[] {
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
		return summarizeWriterComments(raw, showResolved);
	}

	static countVisibleComments(): number {
		return MobileWriterComments.collectWriterComments().length;
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
		const sync = () => MobileWriterComments.syncCommentCountToNative();
		map.on('docloaded', sync);
		map.on('insertannotation', sync);
		map.on('deleteannotation', sync);
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
