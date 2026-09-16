/*
 * Article generate templates (mirrors Android ArticleTemplateRegistry).
 */

interface WriterAiArticleVariable {
	label: string;
	hint: string;
}

interface WriterAiArticleTemplate {
	key: string;
	category: string;
	subTypeLabel: string;
	variables: WriterAiArticleVariable[];
}

class WriterAiArticleRegistry {
	static readonly CATEGORIES: string[] = [
		'通知类',
		'申请类',
		'证明类',
		'营销类',
	];

	private static readonly ALL: WriterAiArticleTemplate[] = [
		WriterAiArticleRegistry.t(
			'general_notice',
			'通知类',
			'通用通知',
			WriterAiArticleRegistry.v('通知主要内容', '公司今晚聚餐'),
			WriterAiArticleRegistry.v('通知时间', '2025年01月01日'),
		),
		WriterAiArticleRegistry.t(
			'meeting_notice',
			'通知类',
			'会议通知',
			WriterAiArticleRegistry.v('会议主要内容', '2025年研发计划'),
			WriterAiArticleRegistry.v('会议时间', '2025年01月01日 17:00'),
			WriterAiArticleRegistry.v('参会人员', '软件研发人员'),
		),
		WriterAiArticleRegistry.t(
			'holiday_notice',
			'通知类',
			'放假通知',
			WriterAiArticleRegistry.v('假期名称', '元旦节'),
			WriterAiArticleRegistry.v('接收方', '全体员工'),
			WriterAiArticleRegistry.v('发送方', '橙子云计算（深圳）有限公司'),
			WriterAiArticleRegistry.v('放假时间', '2025年01月01日'),
		),
		WriterAiArticleRegistry.t(
			'interview_notice',
			'通知类',
			'面试通知',
			WriterAiArticleRegistry.v('面试人员', '小王'),
			WriterAiArticleRegistry.v('面试时间', '2025年01月01日 17:00'),
			WriterAiArticleRegistry.v('面试地点', '名优大厦A座1区101'),
			WriterAiArticleRegistry.v('面试单位', '橙子云计算'),
		),
		WriterAiArticleRegistry.t(
			'activity_notice',
			'通知类',
			'活动通知',
			WriterAiArticleRegistry.v('活动主题', '员工羽毛球大赛'),
			WriterAiArticleRegistry.v('活动时间', '2025年01月01日 17:00'),
			WriterAiArticleRegistry.v('活动地点', '羽毛球馆'),
		),
		WriterAiArticleRegistry.t(
			'training_notice',
			'通知类',
			'培训通知',
			WriterAiArticleRegistry.v('培训主要内容', '如何使用AI Office提效'),
			WriterAiArticleRegistry.v('培训人员', '全体员工'),
			WriterAiArticleRegistry.v('培训日期', '2025年01月01日 17:00'),
		),
		WriterAiArticleRegistry.t(
			'general_apply',
			'申请类',
			'通用申请',
			WriterAiArticleRegistry.v('申请人', '小王'),
			WriterAiArticleRegistry.v('申请事项', '外出参加会议'),
			WriterAiArticleRegistry.v('申请时间', '2025年01月01日'),
		),
		WriterAiArticleRegistry.t(
			'leave_apply',
			'申请类',
			'请假申请',
			WriterAiArticleRegistry.v('请假人', '小王'),
			WriterAiArticleRegistry.v('请假原因', '身体不适'),
			WriterAiArticleRegistry.v('请假天数', '3天'),
			WriterAiArticleRegistry.v('请假起始日期', '2025年01月01日'),
		),
		WriterAiArticleRegistry.t(
			'resign_apply',
			'申请类',
			'离职申请',
			WriterAiArticleRegistry.v('申请人', '小王'),
			WriterAiArticleRegistry.v('离职原因', '身体长期不适'),
			WriterAiArticleRegistry.v('离职时间', '2025年01月01日'),
		),
		WriterAiArticleRegistry.t(
			'general_cert',
			'证明类',
			'通用证明',
			WriterAiArticleRegistry.v('被证明人', '小王'),
			WriterAiArticleRegistry.v('证明主要内容', '小王是公司的员工'),
			WriterAiArticleRegistry.v('证明单位', '橙子云计算（深圳）有限公司'),
			WriterAiArticleRegistry.v('证明时间', '2025年01月01日'),
		),
		WriterAiArticleRegistry.t(
			'work_cert',
			'证明类',
			'工作证明',
			WriterAiArticleRegistry.v('被证明人', '小王'),
			WriterAiArticleRegistry.v('工作时间', '2020年01月01日至2025年01月01日'),
			WriterAiArticleRegistry.v('工作单位', '橙子云计算（深圳）有限公司'),
			WriterAiArticleRegistry.v('工作岗位', '软件研发工程师'),
		),
		WriterAiArticleRegistry.t(
			'income_cert',
			'证明类',
			'收入证明',
			WriterAiArticleRegistry.v('被证明人', '小王'),
			WriterAiArticleRegistry.v('收入', '年收入10万元'),
			WriterAiArticleRegistry.v('工作单位', '橙子云计算（深圳）有限公司'),
			WriterAiArticleRegistry.v('工作岗位', '软件研发工程师'),
		),
		WriterAiArticleRegistry.t(
			'resign_cert',
			'证明类',
			'离职证明',
			WriterAiArticleRegistry.v('被证明人', '小王'),
			WriterAiArticleRegistry.v('离职原因', '员工个人原因'),
			WriterAiArticleRegistry.v('离职时间', '2025年01月01日'),
			WriterAiArticleRegistry.v('证明单位', '橙子云计算（深圳）有限公司'),
			WriterAiArticleRegistry.v('证明时间', '2025年01月01日'),
		),
		WriterAiArticleRegistry.t(
			'xiaohongshu',
			'营销类',
			'小红书种草文',
			WriterAiArticleRegistry.v('种草对象', '最新复古游戏掌机'),
			WriterAiArticleRegistry.v('目标受众', '喜欢游戏机的年轻人'),
			WriterAiArticleRegistry.v('核心卖点', '畅玩复古游戏'),
			WriterAiArticleRegistry.v('文章长度', '500字左右'),
			WriterAiArticleRegistry.v('文案风格', '幽默风趣'),
		),
		WriterAiArticleRegistry.t(
			'ad_soft',
			'营销类',
			'产品广告软文',
			WriterAiArticleRegistry.v('产品名称', '最新复古游戏掌机'),
			WriterAiArticleRegistry.v('品牌', '香橙派'),
			WriterAiArticleRegistry.v('核心卖点', '畅玩复古游戏'),
			WriterAiArticleRegistry.v('目标受众', '爱玩游戏的年轻人'),
			WriterAiArticleRegistry.v('投放平台', '微博'),
			WriterAiArticleRegistry.v('营销节点', '情人节'),
			WriterAiArticleRegistry.v('文案风格', '幽默风趣'),
		),
		WriterAiArticleRegistry.t(
			'douyin_script',
			'营销类',
			'抖音视频脚本',
			WriterAiArticleRegistry.v('主题内容', '旅游攻略'),
			WriterAiArticleRegistry.v('目标受众', '旅游爱好者'),
			WriterAiArticleRegistry.v('视频风格', '搞笑幽默'),
			WriterAiArticleRegistry.v('视频时长', '三分钟左右'),
		),
	];

	private static v(label: string, hint: string): WriterAiArticleVariable {
		return { label, hint };
	}

	private static t(
		key: string,
		category: string,
		subTypeLabel: string,
		...variables: WriterAiArticleVariable[]
	): WriterAiArticleTemplate {
		return { key, category, subTypeLabel, variables };
	}

	static getCategories(): string[] {
		return WriterAiArticleRegistry.CATEGORIES.slice();
	}

	static findByKey(key: string): WriterAiArticleTemplate | null {
		const found = WriterAiArticleRegistry.ALL.find((item) => item.key === key);
		return found || null;
	}

	static getByCategory(category: string): WriterAiArticleTemplate[] {
		return WriterAiArticleRegistry.ALL.filter(
			(item) => item.category === category,
		);
	}
}
