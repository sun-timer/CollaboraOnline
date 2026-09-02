/*
 * Browser UI catalog for the Android mobile AI information architecture.
 *
 * WriterAiCatalog remains the request contract for supported Writer tasks.
 * This catalog describes what the UI can show and whether iOS can execute it.
 */

type MobileAiDocumentType = 'text' | 'spreadsheet' | 'presentation';
type MobileAiUiGroup =
	| 'assistant'
	| 'writerGeneration'
	| 'writerProcessing'
	| 'other'
	| 'calc'
	| 'impress';
type MobileAiUiResultMode =
	| 'replaceSelection'
	| 'appendAfterSelection'
	| 'insertAtEnd'
	| 'conversation';
type MobileAiUiDialog =
	| 'assistant'
	| 'operation'
	| 'translate'
	| 'continue'
	| 'outline'
	| 'article'
	| 'calc'
	| 'unavailable'
	| 'formatBatch';

interface MobileAiUiEntry {
	taskType: string;
	androidTaskType: string;
	label: string;
	group: MobileAiUiGroup;
	documentTypes: MobileAiDocumentType[];
	requiredInput: 'selection' | 'document' | 'none' | 'prompt';
	resultMode: MobileAiUiResultMode;
	dialog: MobileAiUiDialog;
	iosSupport: boolean;
	selectionRequired: boolean;
	includeInOperationSheet: boolean;
}

class MobileAiUiCatalog {
	static readonly ENTRIES: MobileAiUiEntry[] = [
		{
			taskType: 'doc_qa',
			androidTaskType: 'doc_qa',
			label: '文档 Q&A',
			group: 'assistant',
			documentTypes: ['text', 'spreadsheet', 'presentation'],
			requiredInput: 'document',
			resultMode: 'conversation',
			dialog: 'assistant',
			iosSupport: true,
			selectionRequired: false,
			includeInOperationSheet: false,
		},
		{
			taskType: 'chat',
			androidTaskType: 'chat',
			label: '聊天',
			group: 'assistant',
			documentTypes: ['text', 'spreadsheet', 'presentation'],
			requiredInput: 'prompt',
			resultMode: 'conversation',
			dialog: 'assistant',
			iosSupport: true,
			selectionRequired: false,
			includeInOperationSheet: false,
		},
		{
			taskType: 'continue',
			androidTaskType: 'continue_write',
			label: 'AI 续写',
			group: 'writerGeneration',
			documentTypes: ['text'],
			requiredInput: 'selection',
			resultMode: 'appendAfterSelection',
			dialog: 'continue',
			iosSupport: true,
			selectionRequired: true,
			includeInOperationSheet: true,
		},
		{
			taskType: 'outline',
			androidTaskType: 'outline',
			label: '生成大纲',
			group: 'writerGeneration',
			documentTypes: ['text'],
			requiredInput: 'document',
			resultMode: 'insertAtEnd',
			dialog: 'outline',
			iosSupport: true,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'article_generate',
			androidTaskType: 'article_generate',
			label: '文案生成',
			group: 'writerGeneration',
			documentTypes: ['text'],
			requiredInput: 'prompt',
			resultMode: 'insertAtEnd',
			dialog: 'article',
			iosSupport: true,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'polish',
			androidTaskType: 'polish',
			label: '文案润色',
			group: 'writerProcessing',
			documentTypes: ['text'],
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			dialog: 'operation',
			iosSupport: true,
			selectionRequired: true,
			includeInOperationSheet: true,
		},
		{
			taskType: 'expand',
			androidTaskType: 'expand',
			label: '文案扩写',
			group: 'writerProcessing',
			documentTypes: ['text'],
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			dialog: 'operation',
			iosSupport: true,
			selectionRequired: true,
			includeInOperationSheet: true,
		},
		{
			taskType: 'condense',
			androidTaskType: 'condense',
			label: '文案缩写',
			group: 'writerProcessing',
			documentTypes: ['text'],
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			dialog: 'operation',
			iosSupport: true,
			selectionRequired: true,
			includeInOperationSheet: true,
		},
		{
			taskType: 'rewrite',
			androidTaskType: 'rewrite',
			label: '文案重写',
			group: 'writerProcessing',
			documentTypes: ['text'],
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			dialog: 'operation',
			iosSupport: true,
			selectionRequired: true,
			includeInOperationSheet: true,
		},
		{
			taskType: 'translate',
			androidTaskType: 'translate',
			label: '文案翻译',
			group: 'writerProcessing',
			documentTypes: ['text'],
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			dialog: 'translate',
			iosSupport: true,
			selectionRequired: true,
			includeInOperationSheet: true,
		},
		{
			taskType: 'summarize',
			androidTaskType: 'summarize',
			label: '总结',
			group: 'writerProcessing',
			documentTypes: ['text'],
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			dialog: 'operation',
			iosSupport: true,
			selectionRequired: true,
			includeInOperationSheet: false,
		},
		{
			taskType: 'text_extract',
			androidTaskType: 'text_extract',
			label: '文字提取',
			group: 'other',
			documentTypes: ['text', 'presentation'],
			requiredInput: 'prompt',
			resultMode: 'insertAtEnd',
			dialog: 'unavailable',
			iosSupport: false,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'typeset',
			androidTaskType: 'typeset',
			label: 'AI 排版',
			group: 'other',
			documentTypes: ['text'],
			requiredInput: 'document',
			resultMode: 'insertAtEnd',
			dialog: 'unavailable',
			iosSupport: false,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'image_generate',
			androidTaskType: 'image_generate',
			label: 'AI 图片',
			group: 'other',
			documentTypes: ['text', 'presentation'],
			requiredInput: 'prompt',
			resultMode: 'insertAtEnd',
			dialog: 'unavailable',
			iosSupport: false,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'format_batch',
			androidTaskType: 'format_batch',
			label: '格式批量处理',
			group: 'other',
			documentTypes: ['text', 'presentation'],
			requiredInput: 'selection',
			resultMode: 'replaceSelection',
			dialog: 'formatBatch',
			iosSupport: true,
			selectionRequired: true,
			includeInOperationSheet: true,
		},
		{
			taskType: 'calc_formula',
			androidTaskType: 'calc_formula',
			label: 'AI 公式',
			group: 'calc',
			documentTypes: ['spreadsheet'],
			requiredInput: 'prompt',
			resultMode: 'replaceSelection',
			dialog: 'calc',
			iosSupport: true,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'calc_cond_format',
			androidTaskType: 'calc_cond_format',
			label: 'AI 条件格式',
			group: 'calc',
			documentTypes: ['spreadsheet'],
			requiredInput: 'prompt',
			resultMode: 'replaceSelection',
			dialog: 'calc',
			iosSupport: true,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'calc_new_table',
			androidTaskType: 'calc_new_table',
			label: 'AI 新建表格',
			group: 'calc',
			documentTypes: ['spreadsheet'],
			requiredInput: 'prompt',
			resultMode: 'replaceSelection',
			dialog: 'calc',
			iosSupport: true,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'calc_data_process',
			androidTaskType: 'calc_data_process',
			label: 'AI 数据处理',
			group: 'calc',
			documentTypes: ['spreadsheet'],
			requiredInput: 'prompt',
			resultMode: 'replaceSelection',
			dialog: 'calc',
			iosSupport: true,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'calc_data_analysis',
			androidTaskType: 'calc_data_analysis',
			label: 'AI 数据分析',
			group: 'calc',
			documentTypes: ['spreadsheet'],
			requiredInput: 'prompt',
			resultMode: 'conversation',
			dialog: 'calc',
			iosSupport: true,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'calc_chart',
			androidTaskType: 'calc_chart',
			label: 'AI 图表生成',
			group: 'calc',
			documentTypes: ['spreadsheet'],
			requiredInput: 'prompt',
			resultMode: 'insertAtEnd',
			dialog: 'calc',
			iosSupport: true,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'impress_outline',
			androidTaskType: 'impress_outline',
			label: 'PPT 大纲',
			group: 'impress',
			documentTypes: ['presentation'],
			requiredInput: 'prompt',
			resultMode: 'insertAtEnd',
			dialog: 'unavailable',
			iosSupport: false,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
		{
			taskType: 'impress_generate',
			androidTaskType: 'impress_generate',
			label: '生成 PPT',
			group: 'impress',
			documentTypes: ['presentation'],
			requiredInput: 'prompt',
			resultMode: 'insertAtEnd',
			dialog: 'unavailable',
			iosSupport: false,
			selectionRequired: false,
			includeInOperationSheet: true,
		},
	];

	static getEntry(taskType: string): MobileAiUiEntry | null {
		return (
			MobileAiUiCatalog.ENTRIES.find((entry) => entry.taskType === taskType) ||
			null
		);
	}

	static getEntries(
		documentType: MobileAiDocumentType,
		includeUnavailable = true,
	): MobileAiUiEntry[] {
		return MobileAiUiCatalog.ENTRIES.filter(
			(entry) =>
				entry.documentTypes.indexOf(documentType) >= 0 &&
				entry.includeInOperationSheet &&
				(includeUnavailable || entry.iosSupport),
		);
	}

	static canRun(taskType: string, documentType: MobileAiDocumentType): boolean {
		const entry = MobileAiUiCatalog.getEntry(taskType);
		return !!entry && entry.iosSupport && entry.documentTypes.indexOf(documentType) >= 0;
	}
}
