/*
 * Calc data validation options (aligned with Android CalcValidationCatalog / LO validate.cxx).
 */

interface CalcValidationOption {
	id: string;
	label: string;
	index: number;
}

class CalcEditorValidationCatalog {
	static readonly ALLOW_OPTIONS: CalcValidationOption[] = [
		{ id: 'any', label: '所有值', index: 0 },
		{ id: 'whole', label: '整数', index: 1 },
		{ id: 'decimal', label: '小数', index: 2 },
		{ id: 'date', label: '日期', index: 3 },
		{ id: 'time', label: '时间', index: 4 },
		{ id: 'range', label: '单元格区域', index: 5 },
		{ id: 'list', label: '列表', index: 6 },
		{ id: 'textlen', label: '文本长度', index: 7 },
		{ id: 'custom', label: '自定义', index: 8 },
	];

	static readonly DATA_OPTIONS: CalcValidationOption[] = [
		{ id: 'equal', label: '等于', index: 0 },
		{ id: 'less', label: '小于', index: 1 },
		{ id: 'greater', label: '大于', index: 2 },
		{ id: 'eqless', label: '小于或等于', index: 3 },
		{ id: 'eqgreater', label: '大于或等于', index: 4 },
		{ id: 'notequal', label: '不等于', index: 5 },
		{ id: 'validrange', label: '有效的区域', index: 6 },
		{ id: 'invalidrange', label: '无效的区域', index: 7 },
	];

	static readonly ERROR_ACTION_OPTIONS: CalcValidationOption[] = [
		{ id: 'stop', label: '停止', index: 0 },
		{ id: 'warning', label: '警告', index: 1 },
		{ id: 'info', label: '信息', index: 2 },
		{ id: 'macro', label: '宏', index: 3 },
		{ id: 'silent', label: '默默拒绝', index: 4 },
	];

	static findAllowByIndex(index: number): CalcValidationOption {
		return (
			CalcEditorValidationCatalog.ALLOW_OPTIONS.find((o) => o.index === index) ||
			CalcEditorValidationCatalog.ALLOW_OPTIONS[0]
		);
	}

	static findDataByIndex(index: number): CalcValidationOption {
		return (
			CalcEditorValidationCatalog.DATA_OPTIONS.find((o) => o.index === index) ||
			CalcEditorValidationCatalog.DATA_OPTIONS[0]
		);
	}

	static findErrorActionByIndex(index: number): CalcValidationOption {
		return (
			CalcEditorValidationCatalog.ERROR_ACTION_OPTIONS.find(
				(o) => o.index === index,
			) || CalcEditorValidationCatalog.ERROR_ACTION_OPTIONS[0]
		);
	}

	static needsDataOperator(allowIndex: number): boolean {
		return allowIndex !== 0;
	}

	static isListAllow(allowIndex: number): boolean {
		return allowIndex === 6;
	}

	static isRangeAllow(allowIndex: number): boolean {
		return allowIndex === 5;
	}

	static isCustomAllow(allowIndex: number): boolean {
		return allowIndex === 8;
	}

	static needsBetweenValues(dataIndex: number): boolean {
		return dataIndex === 6 || dataIndex === 7;
	}

	static comboTextFor(controlId: string, index: number): string {
		if (controlId === 'allow') {
			return CalcEditorValidationCatalog.findAllowByIndex(index).label;
		}
		if (controlId === 'data') {
			return CalcEditorValidationCatalog.findDataByIndex(index).label;
		}
		if (controlId === 'actionCB') {
			return CalcEditorValidationCatalog.findErrorActionByIndex(index).label;
		}
		return '';
	}
}

interface CalcEditorValidationState {
	allowIndex: number;
	dataIndex: number;
	errorActionIndex: number;
	minValue: string;
	maxValue: string;
	listEntries: string;
	inputHelpTitle: string;
	inputHelpText: string;
	errorTitle: string;
	errorMessage: string;
	macroUrl: string;
	allowEmpty: boolean;
	showDropdownList: boolean;
	sortAscending: boolean;
	caseSensitive: boolean;
	showInputHelp: boolean;
	showErrorAlert: boolean;
}

function calcEditorValidationDefaultState(): CalcEditorValidationState {
	return {
		allowIndex: 0,
		dataIndex: 0,
		errorActionIndex: 0,
		minValue: '',
		maxValue: '',
		listEntries: '',
		inputHelpTitle: '',
		inputHelpText: '',
		errorTitle: '',
		errorMessage: '',
		macroUrl: '',
		allowEmpty: true,
		showDropdownList: true,
		sortAscending: true,
		caseSensitive: false,
		showInputHelp: false,
		showErrorAlert: true,
	};
}
