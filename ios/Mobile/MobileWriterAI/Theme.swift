import UIKit

/// Writer AI design tokens — frozen contract v1.0 (see `.scratch/ios-writer-ai/ios-ui-contract.md`).
/// All dimensions in pt. Convert Figma px with `Layout.pt(fromPx:canvasWidth:)`.
enum WriterAITheme {

    // MARK: - §1.1 Color tokens (Figma PDS values)

    enum Color {
        static let primaryBlue = UIColor(rgbaHex: 0x1278D9FF)          // 主按钮、图标描边、选中态
        static let selectionFill = UIColor(rgbaHex: 0x0081EA45)        // 选区填充（27% 蓝）
        static let selectionStroke = UIColor(rgbaHex: 0x0078D7FF)      // 选区描边 1.5pt
        static let gradientPrimaryStart = UIColor(rgbaHex: 0x1DA8FFFF) // 「插入文档」主按钮
        static let gradientPrimaryEnd = UIColor(rgbaHex: 0xDD00CEFF)
        static let gradientPanelTopStart = UIColor(rgbaHex: 0xC45DFFFF) // AI 面板顶部装饰（上）
        static let gradientPanelTopEnd = UIColor(rgbaHex: 0x4369FF00)
        static let gradientPanelBaseStart = UIColor(rgbaHex: 0xF7E6FFFF) // AI 面板顶部装饰（底）
        static let gradientPanelBaseEnd = UIColor(rgbaHex: 0xDFF2FFFF)
        static let textPrimary = UIColor(rgbaHex: 0x101010FF)          // 标题/正文
        static let textSecondary = UIColor(rgbaHex: 0x6A6A6AFF)        // 次操作（复制/重新生成）
        static let textTertiary = UIColor(rgbaHex: 0x999999FF)         // 页签剩余页
        static let surfaceCard = UIColor(rgbaHex: 0xF2F3F5FF)          // 卡片/选中项
        static let surfaceButtonGrey = UIColor(rgbaHex: 0x0000000F)    // 次按钮（6% 黑）
        static let buttonTextDark = UIColor(rgbaHex: 0x000000E5)       // 次按钮文字（90% 黑）
        static let hairline = UIColor(rgbaHex: 0xD8D8D8FF)             // 行分隔线
        static let scrollbar = UIColor(rgbaHex: 0xCBD1D7FF)            // 滚动条
        static let scrim = UIColor(rgbaHex: 0x0000004D)                // 弹窗遮罩（30% 黑）
        static let badgeRed = UIColor(rgbaHex: 0xFE3A3AFF)             // 角标
    }

    // MARK: - §1.2 Font tokens (PingFang SC via system font)

    enum Font {
        static func title() -> UIFont { UIFont.systemFont(ofSize: 20, weight: .medium) }    // 弹窗标题
        static func section() -> UIFont { UIFont.systemFont(ofSize: 16, weight: .regular) } // 面板分区标题
        static func body() -> UIFont { UIFont.systemFont(ofSize: 16, weight: .regular) }    // 弹窗正文
        static func button() -> UIFont { UIFont.systemFont(ofSize: 16, weight: .regular) }  // 按钮文字
        static func tile() -> UIFont { UIFont.systemFont(ofSize: 12, weight: .regular) }    // 面板 tile 标签
        static func menu() -> UIFont { UIFont.systemFont(ofSize: 10, weight: .regular) }    // 选区菜单项
    }

    // MARK: - §1.3 Radius / Shadow / Spacing tokens

    enum Radius {
        static let modal: CGFloat = 12   // 弹窗、语言浮层容器
        static let panel: CGFloat = 24   // AI 面板（仅顶角）
        static let menu: CGFloat = 8     // 选区菜单
        static let card: CGFloat = 12    // tile 卡
        static let small: CGFloat = 4    // 选中项、快捷栏项
        static let floating: CGFloat = 8.9 // 悬浮快捷栏
    }

    enum Shadow {
        static let menu = WriterAIShadow(color: UIColor(rgbaHex: 0x0000004D), radius: 20, offset: CGSize(width: 0, height: 0))
        static let panel = WriterAIShadow(color: UIColor(rgbaHex: 0x00000047), radius: 52.7, offset: CGSize(width: 0, height: -1))
        static let float = WriterAIShadow(color: UIColor(rgbaHex: 0x0000004D), radius: 22.35, offset: CGSize(width: 0, height: 0))
    }

    enum Spacing {
        static let sections: CGFloat = 12   // 面板分区间距（24px）
        static let cardGap: CGFloat = 5     // tile 卡间距（10px）
        static let paddingCard: CGFloat = 12 // 卡片内边距（24px）
        static let paddingModal: CGFloat = 12 // 弹窗内容内边距（24px）
    }

    // MARK: - §4 Animation tokens

    enum Animation {
        static let sheetIn: TimeInterval = 0.28   // easeOut
        static let sheetOut: TimeInterval = 0.22  // easeIn
        static let modalIn: TimeInterval = 0.18   // easeOut
        static let modalOut: TimeInterval = 0.15  // easeIn
        static let floatIn: TimeInterval = 0.16   // easeOut 上移 8pt 淡入
        static let press: TimeInterval = 0.08     // 按压 scale
        static let springBack: TimeInterval = 0.12 // 松开回弹 spring
    }

    // MARK: - §6 Adaptive scaling

    enum Layout {
        /// Figma px → pt. iPhone baseline (375pt canvas) is ÷2;
        /// iPad/wide uses width/750 proportional scaling (§6).
        static func pt(fromPx px: CGFloat, canvasWidth: CGFloat) -> CGFloat {
            px * canvasWidth / 750
        }

        /// HIG minimum touch target (§5 check).
        static let touchTargetMin: CGFloat = 44
    }
}

/// One shadow spec from the contract.
struct WriterAIShadow {
    let color: UIColor
    let radius: CGFloat
    let offset: CGSize
}

/// `0xRRGGBBAA` — 8-digit RGBA hex as exported by the Figma PDS.
extension UIColor {
    convenience init(rgbaHex: UInt32) {
        let r = CGFloat((rgbaHex >> 24) & 0xFF) / 255
        let g = CGFloat((rgbaHex >> 16) & 0xFF) / 255
        let b = CGFloat((rgbaHex >> 8) & 0xFF) / 255
        let a = CGFloat(rgbaHex & 0xFF) / 255
        self.init(red: r, green: g, blue: b, alpha: a)
    }
}
