/*
 * Shared Calc AI task catalog for Phase 5 (P1 read/insert + P2 mutate-confirm).
 *
 * Native code owns credentials and networking. This catalog only freezes
 * taskType, promptId, context fields and validation rules.
 */

interface CalcAiTaskDefinition {
	taskType: string;
	promptId: string;
	androidTaskType: string;
	requiredInput: 'prompt';
	resultMode: 'insertFormula' | 'conversation' | 'mutateConfirm';
	allowedContextFields: string[];
	requiresRange: boolean;
}

interface CalcAiValidationResult {
	valid: boolean;
	errorCode?: string;
}

class CalcAiCatalog {
	static readonly P1_TASK_TYPES = ['calc_formula', 'calc_data_analysis'];
	static readonly P2_TASK_TYPES = [
		'calc_cond_format',
		'calc_data_process',
		'calc_chart',
		'calc_new_table',
	];

	static readonly TASKS: { [taskType: string]: CalcAiTaskDefinition } = {
		calc_formula: {
			taskType: 'calc_formula',
			promptId: 'calc.formula',
			androidTaskType: 'calc_formula',
			requiredInput: 'prompt',
			resultMode: 'insertFormula',
			allowedContextFields: ['cellAddress'],
			requiresRange: false,
		},
		calc_data_analysis: {
			taskType: 'calc_data_analysis',
			promptId: 'calc.data_analysis',
			androidTaskType: 'calc_data_analysis',
			requiredInput: 'prompt',
			resultMode: 'conversation',
			allowedContextFields: ['cellRange', 'cellData'],
			requiresRange: true,
		},
		calc_cond_format: {
			taskType: 'calc_cond_format',
			promptId: 'calc.cond_format',
			androidTaskType: 'calc_cond_format',
			requiredInput: 'prompt',
			resultMode: 'mutateConfirm',
			allowedContextFields: ['cellRange', 'cellData'],
			requiresRange: true,
		},
		calc_data_process: {
			taskType: 'calc_data_process',
			promptId: 'calc.data_process',
			androidTaskType: 'calc_data_process',
			requiredInput: 'prompt',
			resultMode: 'mutateConfirm',
			allowedContextFields: ['cellRange', 'cellData'],
			requiresRange: true,
		},
		calc_chart: {
			taskType: 'calc_chart',
			promptId: 'calc.chart',
			androidTaskType: 'calc_chart',
			requiredInput: 'prompt',
			resultMode: 'mutateConfirm',
			allowedContextFields: ['cellRange', 'cellData'],
			requiresRange: true,
		},
		calc_new_table: {
			taskType: 'calc_new_table',
			promptId: 'calc.new_table',
			androidTaskType: 'calc_new_table',
			requiredInput: 'prompt',
			resultMode: 'mutateConfirm',
			allowedContextFields: ['cellAddress'],
			requiresRange: false,
		},
	};

	static getTask(taskType: string): CalcAiTaskDefinition | null {
		if (!taskType || !CalcAiCatalog.TASKS[taskType]) {
			return null;
		}
		return CalcAiCatalog.TASKS[taskType];
	}

	static validateRequest(payload: any): CalcAiValidationResult {
		if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
			return { valid: false, errorCode: 'invalid_payload' };
		}
		if (CalcAiCatalog.containsSensitiveField(payload)) {
			return { valid: false, errorCode: 'sensitive_field' };
		}

		const task = CalcAiCatalog.getTask(payload.taskType);
		if (!task) {
			return { valid: false, errorCode: 'unsupported_task_type' };
		}
		if (
			typeof payload.selection !== 'string' ||
			payload.selection.trim().length === 0
		) {
			return { valid: false, errorCode: 'empty_prompt' };
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

		if (task.requiresRange) {
			const cellRange =
				typeof context.cellRange === 'string' ? context.cellRange.trim() : '';
			if (!cellRange) {
				return { valid: false, errorCode: 'empty_range' };
			}
		}
		return { valid: true };
	}

	static normalizeFormula(text: string): string {
		if (typeof text !== 'string') {
			return '';
		}
		let cleaned = text.trim();
		cleaned = cleaned.replace(/^```[a-zA-Z]*\s*/m, '').replace(/\s*```$/m, '');
		cleaned = cleaned.trim();
		const lines = cleaned
			.split(/\r?\n/)
			.map((line) => line.trim())
			.filter((line) => line.length > 0);
		let formula =
			lines.find((line) => line.indexOf('=') >= 0) || lines[0] || '';
		formula = formula.replace(/^公式\s*[:：]\s*/, '');
		const equalsIndex = formula.indexOf('=');
		if (equalsIndex > 0) {
			formula = formula.slice(equalsIndex);
		}
		formula = formula.trim();
		if (formula.length > 0 && formula.charAt(0) !== '=') {
			formula = '=' + formula;
		}
		return formula;
	}

	static extractJsonObject(text: string): any | null {
		if (typeof text !== 'string' || text.trim().length === 0) {
			return null;
		}
		let cleaned = text.trim();
		cleaned = cleaned.replace(/^```(?:json)?\s*/i, '').replace(/\s*```$/m, '');
		const start = cleaned.indexOf('{');
		const end = cleaned.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return null;
		}
		try {
			return JSON.parse(cleaned.slice(start, end + 1));
		} catch (_error) {
			return null;
		}
	}

	private static containsSensitiveField(value: any): boolean {
		if (!value || typeof value !== 'object') {
			return false;
		}
		const blocked = ['apiKey', 'Authorization', 'accessToken', 'password'];
		const keys = Object.keys(value);
		for (let i = 0; i < keys.length; i++) {
			if (blocked.indexOf(keys[i]) >= 0) {
				return true;
			}
			if (CalcAiCatalog.containsSensitiveField(value[keys[i]])) {
				return true;
			}
		}
		return false;
	}
}

if (typeof window !== 'undefined') {
	(window as any).CalcAiCatalog = CalcAiCatalog;
}
