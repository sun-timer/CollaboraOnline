/* iOS Calc formatting helpers. Keep document state separate from picker defaults. */
interface CalcNumberFormatState {
	thousands: boolean;
	negativeRed: boolean;
	precision: number;
	leadingZeroes: number;
	natNum12: number;
}

class CalcEditorFormat {
	static isActive(): boolean {
		return !!(window as any).ThisIsTheiOSApp &&
			(window as any).app?.map?.getDocType?.() === 'spreadsheet';
	}

	static state(command: string): any {
		if (!CalcEditorFormat.isActive()) return undefined;
		const bridge = (window as any).CalcFormatStateBridge;
		if (bridge?.handles?.(command)) return bridge.state(command);
		const state = (window as any).app.map.stateChangeHandler?.getItemValue(command);
		return state !== undefined ? state : bridge?.state(command);
	}

	static color(command: string): number | null {
		const state = CalcEditorFormat.state(command);
		if (typeof state !== 'number' &&
			(typeof state !== 'string' || !/^-?\d+$/.test(state.trim()))) return null;
		const value = Number(state);
		return Number.isInteger(value) && value >= -1 && value <= 0xffffff
			? value : null;
	}

	static checked(command: string): boolean | undefined {
		const state = CalcEditorFormat.state(command);
		if (state === true || state === 'true' || state === 1 || state === '1') return true;
		if (state === false || state === 'false' || state === 0 || state === '0') return false;
		return undefined;
	}

	static highlightColor(): number | null {
		// The cell-format shell has no CharBackColor state. An old edit-shell
		// callback must not be presented as the current cell selection's state.
		return (window as any).app?.map?._docLayer?.insertMode === true
			? CalcEditorFormat.color('.uno:CharBackColor') : null;
	}

	static number(command: string): number | null {
		const state = CalcEditorFormat.state(command);
		if (typeof state !== 'number' &&
			(typeof state !== 'string' || !/^-?\d+$/.test(state.trim()))) return null;
		return Number.isInteger(Number(state)) ? Number(state) : null;
	}

	static numberFormat(): CalcNumberFormatState | null {
		const state = CalcEditorFormat.state('.uno:NumberFormat');
		if (typeof state !== 'string') return null;
		const fields = state.split(',');
		if (fields.length < 4 || fields.slice(0, 4).some((s) => !/^\d+$/.test(s))) return null;
		const values = fields.slice(0, 4).map(Number);
		if (values[0] > 1 || values[1] > 1 || values.some((n) => n > 65535)) return null;
		return {
			thousands: values[0] === 1, negativeRed: values[1] === 1,
			precision: values[2], leadingZeroes: values[3],
			natNum12: fields[4] === '1' ? 1 : 0,
		};
	}

	static setNumberFormat(state: CalcNumberFormatState): boolean {
		return CalcEditorFormat.send('.uno:NumberFormat', {
			NumberFormat: { type: 'string', value: [
				Number(state.thousands), Number(state.negativeRed), state.precision,
				state.leadingZeroes, state.natNum12, '',
			].join(',') },
		});
	}

	static mergeNumberFormat(
		state: CalcNumberFormatState, changes: Partial<CalcNumberFormatState>,
	): CalcNumberFormatState {
		return { ...state, ...changes };
	}

	static send(command: string, args?: { [key: string]: any }): boolean {
		if (!CalcEditorFormat.isActive()) return false;
		const map = (window as any).app.map;
		if (typeof map.isEditMode === 'function' && !map.isEditMode()) return false;
		if (typeof map.sendUnoCommand !== 'function') return false;
		map.sendUnoCommand(command, args);
		return true;
	}

	static colorCss(rgb: number | null): string {
		return rgb === null || rgb === -1 ? 'transparent'
			: '#' + ('000000' + rgb.toString(16)).slice(-6);
	}

	static fontColorCss(rgb: number | null): string {
		return rgb === -1 ? '#101010' : CalcEditorFormat.colorCss(rgb);
	}

	static notify(message: string): void {
		const map = (window as any).app?.map;
		if (map?.uiManager?.showSnackbar) map.uiManager.showSnackbar(message);
		else if (map?.fire) map.fire('error', { msg: message });
	}
}
