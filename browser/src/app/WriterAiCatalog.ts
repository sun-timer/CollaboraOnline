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

	static readonly TYPESET_TYPES = [
		{ key: 'paper', label: '论文' },
		{ key: 'gov', label: '党政公文' },
		{ key: 'contract', label: '合同协议' },
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
		text_extract: {
			taskType: 'text_extract',
			promptId: 'writer.text_extract',
			androidTaskType: 'text_extract',
			requiredInput: 'document',
			resultMode: 'insertAtEnd',
			allowedContextFields: [],
		},
		article_generate: {
			taskType: 'article_generate',
			promptId: 'writer.article_generate',
			androidTaskType: 'article_generate',
			requiredInput: 'prompt',
			resultMode: 'insertAtEnd',
			allowedContextFields: ['template', 'variables'],
		},
		typeset: {
			taskType: 'typeset',
			promptId: 'writer.typeset',
			androidTaskType: 'typeset',
			requiredInput: 'document',
			resultMode: 'insertAtEnd',
			allowedContextFields: ['typesetType', 'typesetVersion', 'paragraphMode'],
		},
	};

	static getTask(taskType: string): WriterAiTaskDefinition | null {
		if (!taskType || !WriterAiCatalog.TASKS[taskType]) {
			return null;
		}
		return WriterAiCatalog.TASKS[taskType];
	}

	static getArticleTemplate(key: string): WriterAiArticleTemplate | null {
		return WriterAiArticleRegistry.findByKey(key);
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
		if (taskType === 'typeset') {
			if (
				context.typesetType !== undefined &&
				!WriterAiCatalog.TYPESET_TYPES.some(
					(item) => item.key === context.typesetType,
				)
			) {
				return { valid: false, errorCode: 'invalid_typeset_type' };
			}
			if (
				typeof payload.selection !== 'string' ||
				payload.selection.trim().length === 0
			) {
				return { valid: false, errorCode: 'empty_document' };
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
