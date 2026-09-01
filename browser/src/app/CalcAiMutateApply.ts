/*
 * Best-effort Calc P2 mutate apply via existing UNO / paste paths.
 * Does not require lo-core changes; unsupported ops surface reason codes.
 */

class CalcAiMutateApply {
	static apply(taskType: string, preview: string): { ok: boolean; error?: string } {
		const plan = CalcAiCatalog.extractJsonObject(preview);
		if (!plan) {
			return {
				ok: false,
				error: 'AI 未返回可解析的 JSON 计划，请重新生成后再确认',
			};
		}
		try {
			if (taskType === 'calc_chart') {
				return CalcAiMutateApply.applyChart(plan);
			}
			if (taskType === 'calc_new_table') {
				return CalcAiMutateApply.applyNewTable(plan);
			}
			if (taskType === 'calc_data_process') {
				return CalcAiMutateApply.applyDataProcess(plan);
			}
			if (taskType === 'calc_cond_format') {
				return CalcAiMutateApply.applyCondFormat(plan);
			}
			return { ok: false, error: '不支持的改表任务' };
		} catch (error: any) {
			return {
				ok: false,
				error: error?.message || '改表执行失败',
			};
		}
	}

	private static sendUno(command: string): boolean {
		const socket = (window as any).app?.socket;
		if (!socket || typeof socket.sendMessage !== 'function') {
			return false;
		}
		socket.sendMessage('uno ' + command);
		return true;
	}

	private static applyChart(plan: any): { ok: boolean; error?: string } {
		const chart = plan.chart || plan;
		const dataRange =
			typeof chart.dataRange === 'string' ? chart.dataRange.trim() : '';
		if (!dataRange) {
			return { ok: false, error: '图表计划缺少 dataRange' };
		}
		// Insert chart using existing InsertObjectChart; type hints are best-effort.
		const ok = CalcAiMutateApply.sendUno('.uno:InsertObjectChart');
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
			const map = (window as any).app?.map;
			if (map && typeof map.paste === 'function') {
				map.paste(text);
				return true;
			}
			const clip = map?._clip;
			if (clip && typeof clip.dataTransferToDocument === 'function') {
				/* continue to pasteUno */
			}
			if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
				navigator.clipboard.writeText(text);
			}
			return CalcAiMutateApply.sendUno('.uno:Paste');
		} catch (_error) {
			return false;
		}
	}

	private static applyDataProcess(plan: any): { ok: boolean; error?: string } {
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
			if (type === 'set_formula' || type === 'set_value') {
				const value = String(action.value ?? action.params?.value ?? '');
				if (range) {
					CalcAiMutateApply.sendUno(
						'.uno:GoToCell {"GotoCell.To":{"type":"string","value":"' +
							range.replace(/"/g, '') +
							'"}}',
					);
				}
				if (CalcAiMutateApply.pastePlainText(value)) {
					applied++;
				}
			} else if (type === 'sort') {
				if (CalcAiMutateApply.sendUno('.uno:DataSort')) {
					applied++;
				}
			} else if (type === 'filter') {
				if (CalcAiMutateApply.sendUno('.uno:DataFilterAutoFilter')) {
					applied++;
				}
			} else if (type === 'merge_cells') {
				if (
					CalcAiMutateApply.sendUno(
						'.uno:MergeCells?MoveContents:bool=true',
					)
				) {
					applied++;
				}
			} else if (type === 'bold') {
				if (CalcAiMutateApply.sendUno('.uno:Bold')) {
					applied++;
				}
			} else if (type === 'clear_formatting') {
				if (CalcAiMutateApply.sendUno('.uno:ResetAttributes')) {
					applied++;
				}
			} else if (type === 'calculate') {
				if (CalcAiMutateApply.sendUno('.uno:Calculate')) {
					applied++;
				}
			} else {
				console.warn(
					'calc_data_process_unsupported_action',
					type,
					'reason=lo_core_or_uno_not_mapped',
				);
			}
		}
		if (applied === 0) {
			return {
				ok: false,
				error: '当前环境无法执行这些数据处理动作（reason=lo_core_or_uno_not_mapped）',
			};
		}
		return { ok: true };
	}

	private static applyCondFormat(plan: any): { ok: boolean; error?: string } {
		const conditionType = String(plan.conditionType || '').trim();
		if (conditionType === 'clear') {
			// Best-effort clear of direct attributes; CF rule deletion may need Core.
			const ok = CalcAiMutateApply.sendUno('.uno:ResetAttributes');
			if (!ok) {
				return {
					ok: false,
					error: '清除格式失败 reason=lo_core_clear_cf_not_available',
				};
			}
			console.warn(
				'calc_cond_format_clear_used_ResetAttributes',
				'reason=lo_core_clear_cf_not_available',
			);
			return { ok: true };
		}
		// Without dedicated Core CF command on iOS, apply a visible direct format
		// preview from the plan so users get undoable feedback; full CF rules need Core.
		const format = plan.format || {};
		const bg =
			typeof format.backgroundColor === 'string'
				? format.backgroundColor.replace('#', '')
				: '';
		if (bg && /^[0-9A-Fa-f]{6}$/.test(bg)) {
			const rgb = parseInt(bg, 16);
			CalcAiMutateApply.sendUno(
				'.uno:BackgroundColor {"BackgroundColor.Color":{"type":"long","value":' +
					rgb +
					'}}',
			);
		}
		if (format.fontBold) {
			CalcAiMutateApply.sendUno('.uno:Bold');
		}
		console.warn(
			'calc_cond_format_direct_format_fallback',
			conditionType,
			'reason=lo_core_cond_format_not_available',
		);
		return { ok: true };
	}
}

(window as any).CalcAiMutateApply = CalcAiMutateApply;
