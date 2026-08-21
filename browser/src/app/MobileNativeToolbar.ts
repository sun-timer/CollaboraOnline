/*
 * Shared platform predicate for native mobile document toolbars.
 *
 * Android and iOS own the document shell outside the Browser WebView.
 * Desktop and ordinary mobile browsers keep the Browser toolbar path.
 */

interface NativeMobileToolbarPlatform {
	android: boolean;
	ios: boolean;
	mobile: boolean;
}

function shouldUseNativeMobileToolbar(platform: NativeMobileToolbarPlatform): boolean {
	return (platform.android || platform.ios) && platform.mobile;
}
