/* Impress master slide solid palette — transcribed from Android ImpressSolidColorCatalog.java */

interface ImpressSolidColorEntry {
	index: number;
	rgb: number;
}

class ImpressSolidColorCatalog {
	static readonly BLOCKS: ImpressSolidColorEntry[][] = [
		[
			{ index: 1, rgb: 10883091 },
			{ index: 2, rgb: 13963540 },
			{ index: 3, rgb: 16432384 },
			{ index: 4, rgb: 15924000 },
			{ index: 5, rgb: 9488707 },
			{ index: 6, rgb: 1287518 },
			{ index: 7, rgb: 43761 },
			{ index: 8, rgb: 944294 },
			{ index: 9, rgb: 74334 },
			{ index: 10, rgb: 7680405 },
			{ index: 11, rgb: 10855845 },
			{ index: 12, rgb: 8289918 },
		],
		[
			{ index: 13, rgb: 16711422 },
			{ index: 14, rgb: 15921906 },
			{ index: 15, rgb: 14474460 },
			{ index: 16, rgb: 12566463 },
			{ index: 17, rgb: 2500134 },
			{ index: 18, rgb: 789516 },
			{ index: 19, rgb: 0 },
			{ index: 20, rgb: 8355711 },
			{ index: 21, rgb: 6118749 },
			{ index: 22, rgb: 4144959 },
			{ index: 23, rgb: 3684408 },
			{ index: 24, rgb: 1447446 },
			{ index: 25, rgb: 15132389 },
			{ index: 26, rgb: 13619151 },
			{ index: 27, rgb: 11316396 },
			{ index: 28, rgb: 7500402 },
			{ index: 29, rgb: 3358539 },
			{ index: 30, rgb: 2304564 },
			{ index: 31, rgb: 4609386 },
			{ index: 32, rgb: 14277857 },
			{ index: 33, rgb: 11319241 },
			{ index: 34, rgb: 8820907 },
			{ index: 35, rgb: 3175352 },
			{ index: 36, rgb: 2182262 },
			{ index: 37, rgb: 6201317 },
			{ index: 38, rgb: 14937079 },
			{ index: 39, rgb: 12572139 },
			{ index: 40, rgb: 10339306 },
			{ index: 47, rgb: 12737044 },
			{ index: 48, rgb: 8731660 },
			{ index: 49, rgb: 14840620 },
			{ index: 50, rgb: 16179927 },
			{ index: 51, rgb: 16305829 },
			{ index: 52, rgb: 16037000 },
			{ index: 53, rgb: 8026240 },
			{ index: 54, rgb: 5001811 },
			{ index: 55, rgb: 10725539 },
			{ index: 56, rgb: 15396073 },
			{ index: 57, rgb: 14671841 },
			{ index: 58, rgb: 13290696 },
			{ index: 59, rgb: 12684807 },
			{ index: 60, rgb: 7889664 },
			{ index: 61, rgb: 15450665 },
			{ index: 62, rgb: 16773324 },
			{ index: 63, rgb: 16179649 },
			{ index: 64, rgb: 16177512 },
			{ index: 65, rgb: 3167641 },
			{ index: 66, rgb: 2177117 },
			{ index: 67, rgb: 4878782 },
			{ index: 68, rgb: 14345203 },
			{ index: 69, rgb: 11716575 },
			{ index: 70, rgb: 9416147 },
			{ index: 71, rgb: 6125394 },
			{ index: 72, rgb: 3756589 },
			{ index: 73, rgb: 7187275 },
			{ index: 74, rgb: 15068380 },
			{ index: 75, rgb: 12902834 },
			{ index: 76, rgb: 11193997 },
		],
	];

	static flat(): ImpressSolidColorEntry[] {
		const out: ImpressSolidColorEntry[] = [];
		ImpressSolidColorCatalog.BLOCKS.forEach((block) => block.forEach((e) => out.push(e)));
		return out;
	}

	static indexForRgb(rgb: number): number | null {
		for (const block of ImpressSolidColorCatalog.BLOCKS) {
			for (const e of block) {
				if (e.rgb === rgb) return e.index;
			}
		}
		return null;
	}
}
