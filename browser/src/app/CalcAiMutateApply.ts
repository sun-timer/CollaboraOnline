/*
 * Best-effort Calc P2 mutate apply via existing UNO / paste paths.
 * Does not require lo-core changes; unsupported ops surface reason codes.
 */

class CalcAiMutateApply {
	private static scope = '';

	static async apply(taskType: string, preview: string, sourceRange?: string): Promise<{ ok: boolean; error?: string }> {
		CalcAiMutateApply.scope = typeof CalcAiContext !== 'undefined' ? CalcAiContext.scope() : '';
		const plan = CalcAiCatalog.extractJsonObject(preview);
		if (!plan) {
			return {
				ok: false,
				error: 'AI 未返回可解析的 JSON 计划，请重新生成后再确认',
			};
		}
		try {
			if (taskType === 'calc_chart') {
				if (!sourceRange) return { ok: false, error: '缺少原始选区，请重新生成' };
				return await CalcAiMutateApply.applyChart({ chart: { ...(plan.chart || plan), dataRange: typeof CalcAiContext !== 'undefined' && CalcAiContext.chartRange ? CalcAiContext.chartRange(sourceRange) : sourceRange } });
			}
			if (taskType === 'calc_new_table') {
				return CalcAiMutateApply.applyNewTable(plan);
			}
			if (taskType === 'calc_data_process') {
				return await CalcAiMutateApply.applyDataProcess(plan);
			}
			if (taskType === 'calc_cond_format') {
				return await CalcAiMutateApply.applyCondFormat(plan);
			}
			return { ok: false, error: '不支持的改表任务' };
		} catch (error: any) {
			return {
				ok: false,
				error: error?.message || '改表执行失败',
			};
		}
	}

	private static sendUno(command: string): Promise<boolean> {
		if (CalcAiMutateApply.scope && CalcAiMutateApply.scope !== CalcAiContext.scope()) return Promise.resolve(false);
		const map = (window as any).app?.map;
		const socket = (window as any).app?.socket;
		if (!socket || !map?.on) return Promise.resolve(false);
		const name = command.split(' ')[0].split('?')[0];
		return new Promise((resolve) => {
			const finish = (ok: boolean) => {
				window.clearTimeout(timer);
				map.off('commandresult', listener);
				resolve(ok);
			};
			const listener = (event: any) => {
				if (event.commandName?.split('?')[0] === name) finish(
					(name === '.uno:EnterString' && event.result?.type === 'void') ||
					(event.success === true && event.result?.value !== false && event.result?.value !== 'false'));
			};
			const timer = window.setTimeout(() => finish(false), 8000);
			map.on('commandresult', listener);
			try { socket.sendMessage('uno ' + command); } catch (_) { finish(false); }
		});
	}

	private static async applyChart(plan: any): Promise<{ ok: boolean; error?: string }> {
		const chart = plan.chart || plan;
		const dataRange =
			typeof chart.dataRange === 'string' ? chart.dataRange.trim() : '';
		if (!dataRange) {
			return { ok: false, error: '图表计划缺少 dataRange' };
		}
		const chartType = typeof chart.chartType === 'string'
			? chart.chartType.trim().toLowerCase() : '';
		const template = WriterEditorCatalog.chartTemplateService(chartType);
		if (!template) {
			return { ok: false, error: '图表类型缺失或不支持，请重新生成图表计划' };
		}
		// Use the same templates as manual chart insertion. Without typed args,
		// Core ignores the AI plan and inserts a default column chart.
		const args: { [key: string]: any } = {
			RangeList: { type: 'string', value: dataRange },
			InNewTable: { type: 'boolean', value: false },
		};
		if (WriterEditorCatalog.needsChartTemplate(chartType)) {
			args.ChartTemplate = { type: 'string', value: template };
			const curveStyle = WriterEditorCatalog.chartCurveStyle(chartType);
			if (curveStyle >= 0) {
				args.ChartCurveStyle = { type: 'int32', value: curveStyle };
			}
		}
		const ok = await CalcAiMutateApply.sendUno(
			'.uno:InsertObjectChart ' + JSON.stringify(args),
		);
		if (!ok) {
			return { ok: false, error: '无法发送插入图表命令' };
		}
		return { ok: true };
	}

	private static applyNewTable(plan: any): { ok: boolean; error?: string } {
		const columns = Array.isArray(plan.columns) ? plan.columns : [];
		const data = Array.isArray(plan.data) ? plan.data : [];
		if (columns.length === 0) {
			return { ok: false, error: '新建表格计划缺少 columns' };
		}
		const rows: string[] = [columns.map((c: any) => String(c ?? '')).join('\t')];
		for (let i = 0; i < data.length; i++) {
			const row = Array.isArray(data[i]) ? data[i] : [];
			rows.push(row.map((c: any) => String(c ?? '')).join('\t'));
		}
		const text = rows.join('\n');
		const okPaste = CalcAiMutateApply.pastePlainText(text);
		return okPaste
			? { ok: true }
			: { ok: false, error: '无法将表格写入文档' };
	}

	private static pastePlainText(text: string): boolean {
		try {
			const clip = (window as any).app?.map?._clip;
			if (clip && typeof clip.pastePlainText === 'function') {
				return !!clip.pastePlainText(text);
			}
		} catch (_error) {
			return false;
		}
		return false;
	}

	/** `Sheet1.$C$14` or `C14:C16` → `C14`. */
	private static cellRef(raw: string): string {
		let text = String(raw || '').trim().replace(/\$/g, '');
		const dot = text.lastIndexOf('.');
		if (dot >= 0) {
			text = text.slice(dot + 1);
		}
		const match = /^([A-Za-z]+)([1-9][0-9]*)/.exec(text);
		return match ? match[1].toUpperCase() + match[2] : '';
	}

	private static goToCell(cell: string): Promise<boolean> {
		return CalcAiMutateApply.sendUno('.uno:GoToCell ' + JSON.stringify({
			ToPoint: { type: 'string', value: cell },
		}));
	}

	private static async writeRange(range: string, value: string): Promise<boolean> {
		const match = /^\$?([A-Z]+)\$?([1-9]\d*)(?::\$?([A-Z]+)\$?([1-9]\d*))?$/i.exec(range);
		if (!match) throw new Error('写入范围无效：' + range);
		const col = (s: string) => s.toUpperCase().split('').reduce((n, c) => n * 26 + c.charCodeAt(0) - 64, 0) - 1;
		const c1 = col(match[1]), c2 = col(match[3] || match[1]);
		const r1 = Number(match[2]), r2 = Number(match[4] || match[2]);
		if (c2 < c1 || r2 < r1) throw new Error('写入范围无效：' + range);
		if ((c2-c1+1)*(r2-r1+1) > 1000) throw new Error('生成范围 ' + range + ' 超过 1000 个单元格，请选择实际数据行后重新生成；本动作未写入');
		for (let row = r1; row <= r2; row++) {
			for (let column = c1; column <= c2; column++) {
				const cell = CalcAiContext.columnToLetters(column) + row;
				const expected = value.replace(/\{row\}/g, String(row));
				if (!await CalcAiMutateApply.goToCell(cell)) return false;
				if (!await CalcAiMutateApply.sendUno('.uno:EnterString ' + JSON.stringify({
					StringName: { type: 'string', value: expected },
				}))) return false;
				// EnterString returns a void/false dispatch result even on success.
				// Re-read the cell through the core before advancing to the next one.
				if (!await CalcAiMutateApply.goToCell(cell)) return false;
				const actual = String((window as any).app?.map?._docLayer?._lastFormula ?? '');
				const number = (text: string) => Number(text.replace(/,/g, '').replace(/%$/, '')) / (text.endsWith('%') ? 100 : 1);
				if (actual !== expected && !(expected.trim() && actual.trim() && !expected.startsWith('=') && Number.isFinite(number(expected)) && number(expected) === number(actual)))
					throw new Error('单元格 ' + cell + ' 写入后核对失败，已停止后续写入，请检查已写入的单元格');
			}
		}
		return true;
	}

	private static async applyDataProcess(plan: any): Promise<{ ok: boolean; error?: string }> {
		const actions = Array.isArray(plan.actions) ? plan.actions : [];
		if (actions.length === 0) {
			return {
				ok: false,
				error: plan.description || '无可执行的数据处理动作',
			};
		}
		let applied = 0;
		for (let i = 0; i < actions.length; i++) {
			const action = actions[i] || {};
			const type = String(action.type || '');
			const range = String(action.range || '').trim();
			if (type !== 'calculate' && type !== 'clear_contents' && (!range || !await CalcAiMutateApply.goToCell(range))) {
				return { ok: false, error: `已完成 ${applied}/${actions.length} 项，目标范围定位失败` };
			}
			if (type === 'clear_contents' || (type === 'set_value' && (action.value ?? action.params?.value) === '')) {
				// The native range command clears contents in one undoable operation,
				// including whole columns; it preserves formatting and sheet structure.
				if (range && await CalcAiMutateApply.sendUno('.uno:ClearContentsInRange ' + JSON.stringify({
					Range: { type: 'string', value: range },
				}))) applied++;
			} else if (type === 'set_formula' || type === 'set_value') {
				let value = String(action.value ?? action.params?.value ?? '');
				if (type === 'set_formula' && value && value.charAt(0) !== '=') {
					value = '=' + value;
				}
				if (range && value && await CalcAiMutateApply.writeRange(range, value)) {
					applied++;
				}
			} else if (type === 'sort') {
				const ascending = action.ascending !== false &&
					action.params?.ascending !== false;
				if (await CalcAiMutateApply.sendUno(
					ascending ? '.uno:SortAscending' : '.uno:SortDescending',
				)) {
					applied++;
				}
			} else if (type === 'filter') {
				if (await CalcAiMutateApply.sendUno('.uno:DataFilterAutoFilter')) {
					applied++;
				}
			} else if (type === 'merge_cells') {
				if (
					await CalcAiMutateApply.sendUno(
						'.uno:MergeCells?MoveContents:bool=true',
					)
				) {
					applied++;
				}
			} else if (type === 'bold') {
				if (await CalcAiMutateApply.sendUno('.uno:Bold')) {
					applied++;
				}
			} else if (type === 'clear_formatting') {
				if (await CalcAiMutateApply.sendUno('.uno:ResetAttributes')) {
					applied++;
				}
			} else if (type === 'calculate') {
				if (await CalcAiMutateApply.sendUno('.uno:Calculate')) {
					applied++;
				}
			} else {
				console.warn(
					'calc_data_process_unsupported_action',
					type,
					'reason=lo_core_or_uno_not_mapped',
				);
			}
			if (applied !== i + 1) return { ok: false, error: `已完成 ${applied}/${actions.length} 项，第 ${i+1} 项失败或无法确认，请检查表格` };
		}
		if (applied === 0) {
			return {
				ok: false,
				error: '当前环境无法执行这些数据处理动作（reason=lo_core_or_uno_not_mapped）',
			};
		}
		return { ok: true };
	}

	private static conditionRule(conditionType: string): number {
		const rules: { [key: string]: number } = {
			equal: 0,
			less: 1,
			greater: 2,
			between: 6,
			duplicate: 8,
			unique: 9,
			formula: 10,
			top_n: 11,
			bottom_n: 12,
			above_average: 15,
			below_average: 16,
			contains_text: 23,
		};
		return Object.prototype.hasOwnProperty.call(rules, conditionType)
			? rules[conditionType]
			: -1;
	}

	private static async applyCondFormat(plan: any): Promise<{ ok: boolean; error?: string }> {
		const conditionType = String(plan.conditionType || '').trim();
		let range = String(plan.range || '').trim();
		if (!range && typeof CalcAiContext !== 'undefined') {
			range = CalcAiContext.getSelectedRange();
		}
		if (!range) {
			return { ok: false, error: '条件格式缺少应用范围' };
		}
		if (conditionType === 'clear') {
			const ok = await CalcAiMutateApply.sendUno(
				'.uno:ClearConditionalFormat ' + JSON.stringify({
					Range: { type: 'string', value: range },
				}),
			);
			return ok
				? { ok: true }
				: { ok: false, error: '无法发送清除条件格式命令' };
		}
		const formatRule = CalcAiMutateApply.conditionRule(conditionType);
		if (formatRule < 0) {
			return { ok: false, error: '不支持的条件类型: ' + conditionType };
		}
		const needsValue = conditionType !== 'above_average' &&
			conditionType !== 'below_average' &&
			conditionType !== 'duplicate' &&
			conditionType !== 'unique';
		let value = String(plan.value ?? '').trim();
		const value2 = String(plan.value2 ?? '').trim();
		if (needsValue && !value) {
			return { ok: false, error: '条件值不能为空' };
		}
		if (conditionType === 'between' && !value2) {
			return { ok: false, error: '介于条件需要两个值' };
		}
		if (conditionType === 'formula' && value.charAt(0) !== '=') {
			value = '=' + value;
		}
		const format = plan.format && typeof plan.format === 'object' ? plan.format : null;
		const formatSpec = JSON.stringify(format && Object.keys(format).length ? format : { backgroundColor: '#FFEB9C' });
		const args: { [key: string]: { type: string; value: string | number } } = {
			FormatRule: { type: 'short', value: formatRule },
			Expression1: { type: 'string', value },
			Expression2: { type: 'string', value: value2 },
			Range: { type: 'string', value: range },
			Style: { type: 'string', value: '' },
		};
		if (formatSpec) {
			args.FormatSpec = { type: 'string', value: formatSpec };
		}
		const ok = await CalcAiMutateApply.sendUno(
			'.uno:ApplyConditionalFormat ' + JSON.stringify(args),
		);
		return ok
			? { ok: true }
			: { ok: false, error: '无法发送条件格式命令' };
	}
}

if (typeof window !== 'undefined') {
	(window as any).CalcAiMutateApply = CalcAiMutateApply;
}
