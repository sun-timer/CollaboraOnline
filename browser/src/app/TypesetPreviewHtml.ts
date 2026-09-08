/*
 * Approximate HTML preview for typeset V2 section maps (mirrors Android buildPreviewHtml).
 */

class TypesetPreviewHtml {
	static build(typesetType: string, sections: { [key: string]: string }): string {
		const rendered: { [key: string]: boolean } = {};
		const parts: string[] = [];
		parts.push(
			'<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8">',
			'<meta name="viewport" content="width=device-width,initial-scale=1.0">',
			'<style>',
			"body{font-family:'PingFang SC','Microsoft YaHei',sans-serif;padding:0 16px;color:#333;line-height:1.8;font-size:15px;word-break:break-word;}",
			'h2{text-align:center;font-size:20px;margin:16px 0;}',
			'h3{font-size:17px;margin:12px 0;border-bottom:1px solid #eee;padding-bottom:6px;}',
			'h4{font-size:15px;margin:8px 0;}',
			'p{margin:6px 0;}',
			'.abstract{background:#f5f5f5;padding:12px;border-radius:8px;margin:8px 0;font-size:14px;}',
			'.signature{text-align:right;margin-top:24px;}',
			'</style></head><body>',
		);

		const title = sections.title || '';
		if (title) {
			parts.push('<h2>', TypesetPreviewHtml.escape(title), '</h2>');
			rendered.title = true;
		}

		switch (typesetType) {
			case 'paper':
				TypesetPreviewHtml.appendPaper(parts, sections, rendered);
				break;
			case 'gov':
				TypesetPreviewHtml.appendGov(parts, sections, rendered);
				break;
			case 'contract':
				TypesetPreviewHtml.appendContract(parts, sections, rendered);
				break;
			default:
				TypesetPreviewHtml.appendGeneral(parts, sections, rendered);
				break;
		}

		Object.keys(sections).forEach((key) => {
			if (!rendered[key]) {
				parts.push('<p>', TypesetPreviewHtml.escape(sections[key]), '</p>');
			}
		});
		parts.push('</body></html>');
		return parts.join('');
	}

	private static appendPaper(
		parts: string[],
		sections: { [key: string]: string },
		rendered: { [key: string]: boolean },
	): void {
		if (sections.abstract) {
			parts.push(
				'<div class="abstract"><strong>摘要：</strong>',
				TypesetPreviewHtml.escape(sections.abstract),
				'</div>',
			);
			rendered.abstract = true;
		}
		if (sections.keywords) {
			parts.push(
				'<p><strong>关键词：</strong>',
				TypesetPreviewHtml.escape(sections.keywords),
				'</p>',
			);
			rendered.keywords = true;
		}
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'introduction', '引言');
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'heading1', null, 'h3');
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'heading2', null, 'h4');
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'heading3', null, 'h4');
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'body', null);
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'conclusion_body', '结语');
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'ack_body', '致谢');
	}

	private static appendGov(
		parts: string[],
		sections: { [key: string]: string },
		rendered: { [key: string]: boolean },
	): void {
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'recipient', null);
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'body', null);
		if (sections.signature_org) {
			parts.push(
				'<div class="signature"><p>',
				TypesetPreviewHtml.escape(sections.signature_org),
				'</p></div>',
			);
			rendered.signature_org = true;
		}
		if (sections.signature_date) {
			parts.push(
				'<div class="signature"><p>',
				TypesetPreviewHtml.escape(sections.signature_date),
				'</p></div>',
			);
			rendered.signature_date = true;
		}
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'notes', null);
	}

	private static appendContract(
		parts: string[],
		sections: { [key: string]: string },
		rendered: { [key: string]: boolean },
	): void {
		[
			'contract_number',
			'party_a',
			'party_a_id',
			'party_b',
			'party_b_id',
			'preamble',
			'clause_title',
			'clause_subtitle',
			'clause_body',
		].forEach((key) => {
			TypesetPreviewHtml.appendSection(parts, sections, rendered, key, null);
		});
	}

	private static appendGeneral(
		parts: string[],
		sections: { [key: string]: string },
		rendered: { [key: string]: boolean },
	): void {
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'heading1', null, 'h3');
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'heading2', null, 'h4');
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'heading3', null, 'h4');
		TypesetPreviewHtml.appendSection(parts, sections, rendered, 'body', null);
	}

	private static appendSection(
		parts: string[],
		sections: { [key: string]: string },
		rendered: { [key: string]: boolean },
		key: string,
		label: string | null,
		tag: string = 'p',
	): void {
		const value = sections[key];
		if (!value) {
			return;
		}
		if (label) {
			parts.push('<', tag, '><strong>', TypesetPreviewHtml.escape(label), '：</strong>');
			parts.push(TypesetPreviewHtml.escape(value), '</', tag, '>');
		} else if (tag === 'h3' || tag === 'h4') {
			parts.push('<', tag, '>', TypesetPreviewHtml.escape(value), '</', tag, '>');
		} else {
			parts.push('<p>', TypesetPreviewHtml.escape(value), '</p>');
		}
		rendered[key] = true;
	}

	private static escape(value: string): string {
		return value
			.replace(/&/g, '&amp;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;')
			.replace(/"/g, '&quot;');
	}
}
