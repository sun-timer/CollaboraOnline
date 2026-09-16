/*
 * Android lolib_ic_ai_* vector paths, converted for the iOS operation sheet.
 */

const AI_BLACK = '#101010';
const AI_BLUE = '#1278d9';
const AI_ORANGE = '#ec5d1f';

function aiIcon(inner: string): string {
	return (
		'<svg viewBox="0 0 48 48" width="36" height="36" fill="none" ' +
		'xmlns="http://www.w3.org/2000/svg" aria-hidden="true">' +
		inner +
		'</svg>'
	);
}

function stroke(
	d: string,
	color: string,
	width = 2,
	join = 'round',
	cap = 'round',
): string {
	return (
		'<path d="' +
		d +
		'" fill="none" stroke="' +
		color +
		'" stroke-width="' +
		width +
		'" stroke-linecap="' +
		cap +
		'" stroke-linejoin="' +
		join +
		'"/>'
	);
}

class MobileAiOperationIcons {
	static svgFor(taskType: string): string {
		switch (taskType) {
			case 'impress_generate':
			case 'impress_outline':
				return MobileAiOperationIcons.generatePpt();
			case 'continue':
				return MobileAiOperationIcons.continueWrite();
			case 'outline':
				return MobileAiOperationIcons.outline();
			case 'article_generate':
				return MobileAiOperationIcons.articleGenerate();
			case 'expand':
				return MobileAiOperationIcons.expand();
			case 'polish':
				return MobileAiOperationIcons.polish();
			case 'condense':
				return MobileAiOperationIcons.condense();
			case 'rewrite':
				return MobileAiOperationIcons.rewrite();
			case 'translate':
				return MobileAiOperationIcons.translate();
			case 'text_extract':
				return MobileAiOperationIcons.textExtract();
			case 'typeset':
				return MobileAiOperationIcons.typeset();
			case 'image_generate':
				return MobileAiOperationIcons.imageGenerate();
			case 'format_batch':
				return MobileAiOperationIcons.formatBatch();
			case 'calc_formula':
				return MobileAiOperationIcons.articleGenerate();
			case 'calc_cond_format':
				return MobileAiOperationIcons.polish();
			case 'calc_data_analysis':
				return MobileAiOperationIcons.outline();
			case 'calc_data_process':
				return MobileAiOperationIcons.formatBatch();
			case 'calc_chart':
				return MobileAiOperationIcons.imageGenerate();
			case 'calc_new_table':
				return MobileAiOperationIcons.typeset();
			default:
				return MobileAiOperationIcons.outline();
		}
	}

	private static generatePpt(): string {
		return aiIcon(
			stroke('M12 6H30L38 14V42H12V6Z', AI_BLACK) +
				stroke('M30 6V14H38', AI_BLACK) +
				stroke('M20 18V32M20 18H27.5C29.433 18 31 19.567 31 21.5C31 23.433 29.433 25 27.5 25H20', AI_BLACK) +
				stroke(
					'M39.11 8.44L34.11 12.89L28.54 9.62L31.33 15.67L26.33 20.11L33 19.56L35.5 25.11L36.89 19L43.55 18.44L37.72 14.92L39.11 8.44Z',
					AI_ORANGE,
				) +
				stroke('M45.22 27.34L36.89 19', AI_ORANGE),
		);
	}

	private static continueWrite(): string {
		return aiIcon(
			stroke('M24 24V19L39 4L44 9L29 24H24Z', AI_BLACK) +
				stroke(
					'M15.998 24H8.99805C6.23663 24 3.99805 26.2386 3.99805 29C3.99805 31.7614 6.23663 34 8.99805 34H38.998C41.7594 34 43.998 36.2386 43.998 39C43.998 41.7614 41.7594 44 38.998 44H17.998',
					AI_BLUE,
				),
		);
	}

	private static outline(): string {
		return aiIcon(
			stroke('M3.99902 24H21.999', AI_BLACK) +
				stroke('M3.99902 37.9995H29.999', AI_BLACK) +
				stroke('M3.99902 10.0005H29.999', AI_BLACK) +
				stroke(
					'M40.1105 20.4443L35.1105 24.8888L29.537 21.6164L32.3327 27.6666L27.3327 32.111L33.9994 31.5554L36.4994 37.111L37.8882 30.9999L44.5547 30.4443L38.717 26.9166L40.1105 20.4443Z',
					AI_BLUE,
				) +
				stroke('M46.2217 39.3452L37.8883 31.0005', AI_BLUE),
		);
	}

	private static articleGenerate(): string {
		return aiIcon(
			stroke('M24 9H42', AI_BLACK) +
				stroke('M24 19H42', AI_BLACK) +
				stroke('M6 29H42', AI_BLACK, 2, 'miter', 'round') +
				stroke('M6 39H42', AI_BLACK, 2, 'miter', 'round') +
				stroke('M6 19L7 17M7 17L11 9L15 17M7 17H15M16 19L15 17', AI_BLUE),
		);
	}

	private static expand(): string {
		return aiIcon(
			stroke(
				'M39 6H9C7.34315 6 6 7.34315 6 9V39C6 40.6569 7.34315 42 9 42H39C40.6569 42 42 40.6569 42 39V9C42 7.34315 40.6569 6 39 6Z',
				AI_BLACK,
				2,
				'round',
				'butt',
			) +
				stroke('M34 24H14', AI_BLACK) +
				stroke('M34 15H14', AI_BLACK) +
				stroke('M34 33H14', AI_BLUE),
		);
	}

	private static polish(): string {
		return aiIcon(
			stroke(
				'M20.1005 8.1005L24.3431 12.3431M30 4V10M39.8995 8.1005L35.6569 12.3431M44 18H38M39.8995 27.8995L35.6569 23.6569M30 32V26M16 18H22',
				AI_BLUE,
			) +
				stroke(
					'M20.3896 32.3171L7.81887 43.3165C7.81887 43.3165 6.64037 44.4951 5.46187 43.3165C4.28337 42.138 5.46187 40.9595 5.46187 40.9595L16.4613 28.3888L20.3896 32.3171Z',
					AI_BLACK,
					2,
					'round',
					'butt',
				) +
				stroke(
					'M26.2833 26.6667L20.389 32.3174L16.4606 28.389L21.8283 22C21.8283 22 21.9603 22.1036 24.3174 24.4606C26.6744 26.8177 26.2833 26.6667 26.2833 26.6667Z',
					AI_BLACK,
					2,
					'round',
					'butt',
				) +
				stroke('M18.8174 33.692L17.246 35.0669L15.6747 36.4419', AI_BLACK, 2, 'round', 'butt') +
				stroke('M12.3351 33.103L13.7101 31.5317L15.085 29.9604', AI_BLACK, 2, 'round', 'butt'),
		);
	}

	private static condense(): string {
		return aiIcon(
			stroke(
				'M39 6H9C7.34315 6 6 7.34315 6 9V39C6 40.6569 7.34315 42 9 42H39C40.6569 42 42 40.6569 42 39V9C42 7.34315 40.6569 6 39 6Z',
				AI_BLACK,
				2,
				'round',
				'butt',
			) +
				stroke('M30 24H18', AI_BLUE) +
				stroke('M34 15H14', AI_BLACK),
		);
	}

	private static rewrite(): string {
		return aiIcon(
			stroke('M30.999 8.99902L38.999 16.999', AI_BLUE) +
				stroke('M8.99902 31.999L15.999 38.999', AI_BLUE) +
				stroke('M7.99904 31.999L35.9989 4L43.999 11.999L15.999 39.999L5.99902 41.999L7.99904 31.999Z', AI_BLACK),
		);
	}

	private static translate(): string {
		return aiIcon(
			stroke(
				'M28.2877 37H39.7163M28.2877 37L26.002 42M28.2877 37L34.002 24L39.7163 37M39.7163 37L42.002 42',
				AI_BLUE,
			) +
				stroke('M15.998 6L16.998 9', AI_BLACK, 2, 'miter', 'round') +
				stroke('M6 10.9995H28', AI_BLACK, 2, 'miter', 'round') +
				stroke(
					'M9.99805 16.0005C9.99805 16.0005 11.7875 22.2614 16.2612 25.7396C20.7348 29.2179 27.998 32.0005 27.998 32.0005',
					AI_BLACK,
					2,
					'miter',
					'round',
				) +
				stroke(
					'M24 10.9995C24 10.9995 22.2105 19.2169 17.7368 23.7821C13.2632 28.3473 6 31.9995 6 31.9995',
					AI_BLACK,
					2,
					'miter',
					'round',
				),
		);
	}

	private static textExtract(): string {
		return aiIcon(
			stroke('M24 44C35.0457 44 44 35.0457 44 24C44 12.9543 35.0457 4 24 4C12.9543 4 4 12.9543 4 24C4 35.0457 12.9543 44 24 44Z', AI_BLACK, 2, 'miter', 'butt') +
				stroke('M32 16H16', AI_BLUE) +
				stroke('M24 34V16', AI_BLUE),
		);
	}

	private static typeset(): string {
		return aiIcon(
			stroke('M8 6H40V42H8V6Z', AI_BLACK, 2, 'round', 'butt') +
				stroke('M14 16H34', AI_BLUE, 2.5, 'miter', 'round') +
				stroke('M14 22H30', AI_BLACK, 2, 'miter', 'round') +
				stroke('M14 28H34', AI_BLACK, 2, 'miter', 'round') +
				stroke('M14 32H34', AI_BLACK, 2, 'miter', 'round') +
				stroke('M14 36H26', AI_BLACK, 2, 'miter', 'round'),
		);
	}

	private static imageGenerate(): string {
		return aiIcon(
			stroke(
				'M29.4998 4L33.9998 8L39.0159 5.0549L36.4998 10.5L40.9998 14.5L34.9998 14L32.7498 19L31.4998 13.5L25.5 13L30.7539 9.825L29.4998 4Z',
				AI_BLUE,
			) +
				stroke('M24 21.0103L31.5 13.5', AI_BLUE, 2, 'miter', 'round') +
				stroke(
					'M42.6663 32.4288V42.1333C42.6663 43.1643 41.6217 44 40.333 44H7.66634C6.37768 44 5.33301 43.1643 5.33301 42.1333V8.53333C5.33301 7.50237 6.37768 6.66666 7.66634 6.66666H19.333',
					AI_BLACK,
				) +
				stroke(
					'M6 35.0001L16.6931 25.1981C17.4389 24.5144 18.5779 24.4954 19.3461 25.1539L32 36.0001',
					AI_BLACK,
				) +
				stroke(
					'M28 30.9999L32.7735 26.2264C33.4772 25.5227 34.5914 25.4435 35.3877 26.0407L42 30.9999',
					AI_BLACK,
				),
		);
	}

	private static formatBatch(): string {
		return aiIcon(
			stroke('M20 24H44', AI_BLACK) +
				stroke('M20 38H44', AI_BLACK, 2, 'miter', 'round') +
				stroke('M20 10H44', AI_BLACK, 2, 'miter', 'round') +
				stroke('M12 34H4V42H12V34Z', AI_BLUE, 2, 'round', 'butt') +
				stroke('M12 20H4V28H12V20Z', AI_BLUE, 2, 'round', 'butt') +
				stroke('M12 6H4V14H12V6Z', AI_BLUE, 2, 'round', 'butt'),
		);
	}
}
