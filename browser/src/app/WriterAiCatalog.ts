/*
 * Shared Writer AI task and option catalog.
 *
 * This file is the Browser-side public contract. Native implementations must
 * keep credentials and provider configuration outside of this catalog.
 */

interface WriterAiTaskDefinition {
	taskType: string;
	promptId: string;
	androidTaskType: string;
	requiredInput: 'selection' | 'document' | 'prompt';
	resultMode: 'replaceSelection' | 'appendAfterSelection' | 'insertAtEnd';
	allowedContextFields: string[];
}

interface WriterAiArticleTemplate {
	key: string;
	category: string;
	variables: string[];
}

interface WriterAiValidationResult {
	valid: boolean;
	errorCode?: string;
}

class WriterAiCatalog {
	static readonly P0_TASK_TYPES = [
		'polish',
		'translate',
		'expand',
		'condense',
		'rewrite',
		'continue',
		'summarize',
	];

	static readonly DEFAULT_POLISH_STYLE = 'quick';
	static readonly DEFAULT_SOURCE_LANGUAGE = 'auto';
	static readonly DEFAULT_TARGET_LANGUAGE = 'zh';

	static readonly POLISH_STYLES = [
		'quick',
		'formal',
		'lively',
		'party_govt',
		'colloquial',
		'academic',
		'internet',
	];

	static readonly TRANSLATE_LANGUAGES = [
		'auto',
		'zh',
		'en',
		'ja',
		'ko',
		'fr',
		'de',
		'es',
		'ru',
	];

	static readonly OUTLINE_TYPES = [
		{ key: 'paper', label: '论文' },
		{ key: 'report', label: '工作报告' },
		{ key: 'speech', label: '演讲稿' },
		{ key: 'event', label: '活动策划' },
		{ key: 'general', label: '通用文档' },
	];

	static readonly TASKS: { [taskType: string]: WriterAiTaskDefinition } = {
		polish: {
			taskType: 'polish',
			promptId: 'writer.polish',
			androidTaskType: 'polish',
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			allowedContextFields: ['polishStyle'],
		},
		translate: {
			taskType: 'translate',
			promptId: 'writer.translate',
			androidTaskType: 'translate',
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			allowedContextFields: ['sourceLang', 'targetLang'],
		},
		expand: {
			taskType: 'expand',
			promptId: 'writer.expand',
			androidTaskType: 'expand',
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			allowedContextFields: ['requirement'],
		},
		condense: {
			taskType: 'condense',
			promptId: 'writer.condense',
			androidTaskType: 'condense',
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			allowedContextFields: ['requirement'],
		},
		rewrite: {
			taskType: 'rewrite',
			promptId: 'writer.rewrite',
			androidTaskType: 'rewrite',
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			allowedContextFields: ['requirement'],
		},
		continue: {
			taskType: 'continue',
			promptId: 'writer.continue',
			androidTaskType: 'continue_write',
			requiredInput: 'selection',
			resultMode: 'appendAfterSelection',
			allowedContextFields: [],
		},
		summarize: {
			taskType: 'summarize',
			promptId: 'writer.summarize',
			androidTaskType: 'summarize',
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			allowedContextFields: [],
		},
		outline: {
			taskType: 'outline',
			promptId: 'writer.outline',
			androidTaskType: 'outline',
			requiredInput: 'document',
			resultMode: 'insertAtEnd',
			allowedContextFields: ['outlineType', 'requirement'],
		},
		article_generate: {
			taskType: 'article_generate',
			promptId: 'writer.article_generate',
			androidTaskType: 'article_generate',
			requiredInput: 'prompt',
			resultMode: 'insertAtEnd',
			allowedContextFields: ['template', 'variables'],
		},
	};

	static readonly ARTICLE_TEMPLATES: {
		[key: string]: WriterAiArticleTemplate;
	} = {
		general_notice: {
			key: 'general_notice',
			category: '通知类',
			variables: ['通知主要内容', '通知时间'],
		},
		meeting_notice: {
			key: 'meeting_notice',
			category: '通知类',
			variables: ['会议主要内容', '会议时间', '参会人员'],
		},
		holiday_notice: {
			key: 'holiday_notice',
			category: '通知类',
			variables: ['假期名称', '接收方', '发送方', '放假时间'],
		},
		interview_notice: {
			key: 'interview_notice',
			category: '通知类',
			variables: ['面试人员', '面试时间', '面试地点', '面试单位'],
		},
		activity_notice: {
			key: 'activity_notice',
			category: '通知类',
			variables: ['活动主题', '活动时间', '活动地点'],
		},
		training_notice: {
			key: 'training_notice',
			category: '通知类',
			variables: ['培训主要内容', '培训人员', '培训日期'],
		},
		general_apply: {
			key: 'general_apply',
			category: '申请类',
			variables: ['申请人', '申请事项', '申请时间'],
		},
		leave_apply: {
			key: 'leave_apply',
			category: '申请类',
			variables: ['请假人', '请假原因', '请假天数', '请假起始日期'],
		},
		resign_apply: {
			key: 'resign_apply',
			category: '申请类',
			variables: ['申请人', '离职原因', '离职时间'],
		},
		general_cert: {
			key: 'general_cert',
			category: '证明类',
			variables: ['被证明人', '证明主要内容', '证明单位', '证明时间'],
		},
		work_cert: {
			key: 'work_cert',
			category: '证明类',
			variables: ['被证明人', '工作时间', '工作单位', '工作岗位'],
		},
		income_cert: {
			key: 'income_cert',
			category: '证明类',
			variables: ['被证明人', '收入', '工作单位', '工作岗位'],
		},
		resign_cert: {
			key: 'resign_cert',
			category: '证明类',
			variables: ['被证明人', '离职原因', '离职时间', '证明单位', '证明时间'],
		},
		xiaohongshu: {
			key: 'xiaohongshu',
			category: '营销类',
			variables: ['种草对象', '目标受众', '核心卖点', '文章长度', '文案风格'],
		},
		ad_soft: {
			key: 'ad_soft',
			category: '营销类',
			variables: [
				'产品名称',
				'品牌',
				'核心卖点',
				'目标受众',
				'投放平台',
				'营销节点',
				'文案风格',
			],
		},
		douyin_script: {
			key: 'douyin_script',
			category: '营销类',
			variables: ['主题内容', '目标受众', '视频风格', '视频时长'],
		},
	};

	static getTask(taskType: string): WriterAiTaskDefinition | null {
		if (!taskType || !WriterAiCatalog.TASKS[taskType]) {
			return null;
		}
		return WriterAiCatalog.TASKS[taskType];
	}

	static getArticleTemplate(key: string): WriterAiArticleTemplate | null {
		if (!key || !WriterAiCatalog.ARTICLE_TEMPLATES[key]) {
			return null;
		}
		return WriterAiCatalog.ARTICLE_TEMPLATES[key];
	}

	static validateRequest(payload: any): WriterAiValidationResult {
		if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
			return { valid: false, errorCode: 'invalid_payload' };
		}
		if (WriterAiCatalog.containsSensitiveField(payload)) {
			return { valid: false, errorCode: 'sensitive_field' };
		}

		const taskType = payload.taskType;
		const task = WriterAiCatalog.getTask(taskType);
		if (!task) {
			return { valid: false, errorCode: 'unsupported_task_type' };
		}
		if (
			task.requiredInput === 'selection' &&
			(typeof payload.selection !== 'string' ||
				payload.selection.trim().length === 0)
		) {
			return { valid: false, errorCode: 'empty_selection' };
		}

		const context = payload.context === undefined ? {} : payload.context;
		if (!context || typeof context !== 'object' || Array.isArray(context)) {
			return { valid: false, errorCode: 'invalid_context' };
		}
		const contextKeys = Object.keys(context);
		for (let i = 0; i < contextKeys.length; i++) {
			if (task.allowedContextFields.indexOf(contextKeys[i]) < 0) {
				return { valid: false, errorCode: 'invalid_context_field' };
			}
		}

		if (
			taskType === 'polish' &&
			context.polishStyle !== undefined &&
			WriterAiCatalog.POLISH_STYLES.indexOf(context.polishStyle) < 0
		) {
			return { valid: false, errorCode: 'invalid_polish_style' };
		}
		if (taskType === 'translate') {
			if (
				context.sourceLang !== undefined &&
				WriterAiCatalog.TRANSLATE_LANGUAGES.indexOf(context.sourceLang) < 0
			) {
				return { valid: false, errorCode: 'invalid_source_language' };
			}
			if (
				context.targetLang !== undefined &&
				WriterAiCatalog.TRANSLATE_LANGUAGES.indexOf(context.targetLang) < 0
			) {
				return { valid: false, errorCode: 'invalid_target_language' };
			}
		}
		return { valid: true };
	}

	private static containsSensitiveField(value: any): boolean {
		if (!value || typeof value !== 'object') {
			return false;
		}
		if (Array.isArray(value)) {
			for (let i = 0; i < value.length; i++) {
				if (WriterAiCatalog.containsSensitiveField(value[i])) {
					return true;
				}
			}
			return false;
		}
		const keys = Object.keys(value);
		for (let i = 0; i < keys.length; i++) {
			const lowerKey = keys[i].toLowerCase();
			if (
				lowerKey.indexOf('apikey') >= 0 ||
				lowerKey.indexOf('authorization') >= 0 ||
				lowerKey.indexOf('accesstoken') >= 0
			) {
				return true;
			}
			if (WriterAiCatalog.containsSensitiveField(value[keys[i]])) {
				return true;
			}
		}
		return false;
	}
}
