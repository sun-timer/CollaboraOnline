/*
 * Android-style outline generation form.
 */

class MobileAiOutlineDialog {
	private readonly sheet: MobileAiSheet;

	constructor() {
		this.sheet = new MobileAiSheet({ title: '生成大纲' });
		const content = document.createElement('div');
		content.style.cssText = 'display:flex;flex-direction:column;gap:12px;';
		const type = document.createElement('select');
		type.setAttribute('aria-label', '大纲类型');
		[
			['general', '通用文档'],
			['paper', '学术论文'],
			['report', '工作报告'],
			['speech', '演讲稿'],
			['event', '活动策划'],
		].forEach(([value, label]) => {
			const option = document.createElement('option');
			option.value = value;
			option.textContent = label;
			type.appendChild(option);
		});
		content.appendChild(type);
		const description = document.createElement('textarea');
		description.rows = 8;
		description.placeholder = '请输入补充说明';
		description.setAttribute('aria-label', '大纲补充说明');
		description.style.cssText = 'width:100%;box-sizing:border-box;resize:vertical;';
		content.appendChild(description);
		const generate = document.createElement('button');
		generate.type = 'button';
		generate.textContent = '生成';
		generate.disabled = true;
		generate.title = 'iOS 尚未支持生成大纲请求';
		content.appendChild(generate);
		const status = document.createElement('div');
		status.textContent = 'iOS 尚未支持生成大纲请求';
		status.setAttribute('role', 'status');
		content.appendChild(status);
		this.sheet.setBody(content);
	}

	open(): void {
		this.sheet.open();
	}
}
