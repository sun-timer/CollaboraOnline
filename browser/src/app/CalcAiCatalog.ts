/*
 * Shared Calc AI task catalog for Phase 5 P1.
 *
 * Native code owns credentials and networking. This catalog only freezes
 * taskType, promptId, context fields and validation rules.
 */

interface CalcAiTaskDefinition {
	taskType: string;
	promptId: string;
	androidTaskType: string;
	requiredInput: 'prompt';
	resultMode: 'insertFormula' | 'conversation';
	allowedContextFields: string[];
	requiresRange: boolean;
}

interface CalcAiValidationResult {
	valid: boolean;
	errorCode?: string;
}

class CalcAiCatalog {
	static readonly P1_TASK_TYPES = ['calc_formula', 'calc_data_analysis'];

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

	private static containsSensitiveField(value: any): boolean {
		if (!value || typeof value !== 'object') {
			return false;
		}
		if (Array.isArray(value)) {
			for (let i = 0; i < value.length; i++) {
				if (CalcAiCatalog.containsSensitiveField(value[i])) {
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
			if (CalcAiCatalog.containsSensitiveField(value[keys[i]])) {
				return true;
			}
		}
		return false;
	}
}
