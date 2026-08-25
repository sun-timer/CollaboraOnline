import UIKit

/// 常用 tab 的字号+字体弹层（12 深化）。
/// 选项 = CO 全量：字号用中文号表 12 项（初号…小五，pt 固定映射）+ 字体运行时时实体列表。
/// 字体列表由宿主注入（`app.map.getToolbarCommandValues('.uno:CharFontName')` 动态拉取）。

/// 字号中文号表（Android FPC SIZE_OPTIONS/SIZE_VALUES 冻结）。
enum WriterFontSizeCatalog {
    static let options: [(label: String, pt: String)] = [
        ("初号", "42"), ("小初", "36"), ("一号", "26"), ("小一", "24"),
        ("二号", "22"), ("小二", "18"), ("三号", "16"), ("小三", "15"),
        ("四号", "14"), ("小四", "12"), ("五号", "10.5"), ("小五", "9"),
    ]
}

/// 字体选择器 — Figma 3082:60069：返回箭头 + 「字体」头 + 字体系列列表（CO 全量）。
@objc final class WriterFontPickerView: UIView {
    private let onSelect: (String) -> Void
    private let onBack: () -> Void
    private var list: [String] = []
    private let rowsStack = UIStackView()

    @objc init(fonts: [String], selected: String?, onSelect: @escaping (String) -> Void,
               onBack: @escaping () -> Void) {
        self.onSelect = onSelect
        self.onBack = onBack
        self.list = fonts
        super.init(frame: .zero)
        backgroundColor = .white
        build(fonts: fonts, selected: selected)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func build(fonts: [String], selected: String?) {
        let backButton = UIButton(type: .system)
        backButton.setImage(UIImage(systemName: "chevron.left"), for: .normal)
        backButton.tintColor = WriterAITheme.Color.textPrimary
        backButton.addTarget(self, action: #selector(backTapped), for: .touchUpInside)

        let title = UILabel()
        title.text = "字体"
        title.font = WriterAITheme.Font.section()
        title.textColor = WriterAITheme.Color.textPrimary

        let header = UIStackView(arrangedSubviews: [backButton, title, UIView()])
        header.axis = .horizontal
        header.spacing = 12
        header.alignment = .center
        header.heightAnchor.constraint(equalToConstant: 86).isActive = true

        let scroll = WriterAIScrollView()
        rowsStack.axis = .vertical
        rowsStack.spacing = 0
        scroll.addSubview(rowsStack)
        rowsStack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            rowsStack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            rowsStack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            rowsStack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            rowsStack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            rowsStack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),
        ])

        for font in fonts {
            let row = UIButton(type: .system)
            row.setTitle(font, for: .normal)
            row.titleLabel?.font = UIFont(name: font, size: 16) ?? WriterAITheme.Font.body()
            row.setTitleColor(WriterAITheme.Color.textPrimary, for: .normal)
            row.contentHorizontalAlignment = .leading
            row.setTitle(font == selected ? "✓" : "", for: .normal)
            row.setTitle(font, for: .normal)
            // checkmark on the trailing side.
            var config = UIButton.Configuration.plain()
            config.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 24, bottom: 12, trailing: 24)
            config.image = font == selected ? UIImage(systemName: "checkmark") : nil
            config.imagePlacement = .trailing
            config.imagePadding = 8
            config.baseForegroundColor = WriterAITheme.Color.textPrimary
            config.title = font
            config.attributedTitle = AttributedString(font)
            let attributed = AttributedString(font)
            config.attributedTitle = attributed
            row.configuration = config
            row.tag = fonts.firstIndex(of: font) ?? 0
            row.addTarget(self, action: #selector(fontTapped(_:)), for: .touchUpInside)
            rowsStack.addArrangedSubview(row)
            let hairline = UIView()
            hairline.backgroundColor = WriterAITheme.Color.hairline
            hairline.heightAnchor.constraint(equalToConstant: 0.5).isActive = true
            rowsStack.addArrangedSubview(hairline)
        }

        addSubview(header)
        addSubview(scroll)
        header.translatesAutoresizingMaskIntoConstraints = false
        scroll.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: topAnchor),
            header.leadingAnchor.constraint(equalTo: leadingAnchor),
            header.trailingAnchor.constraint(equalTo: trailingAnchor),
            scroll.topAnchor.constraint(equalTo: header.bottomAnchor),
            scroll.leadingAnchor.constraint(equalTo: leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: trailingAnchor),
            scroll.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    @objc private func backTapped() { onBack() }
    @objc private func fontTapped(_ sender: UIButton) {
        onSelect(list[sender.tag])
    }

}

/// 字号下拉 — 图 Figma 3082:59721：320×460 浮层，中文号 12 项 + 勾。
@objc final class WriterFontSizeSheet: UIView {
    private let onSelect: (String) -> Void  // pt
    private let labels: [(String, String)] = WriterFontSizeCatalog.options

    @objc init(selected: String?, onSelect: @escaping (String) -> Void) {
        self.onSelect = onSelect
        super.init(frame: .zero)
        build(selected: selected)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func build(selected: String?) {
        backgroundColor = .white
        layer.cornerRadius = WriterAITheme.Radius.panel
        writerAI_apply(WriterAITheme.Shadow.float)

        let scroll = WriterAIScrollView()
        let stack = UIStackView()
        stack.axis = .vertical
        scroll.addSubview(stack)
        stack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor),
            stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),
        ])
        for (index, entry) in labels.enumerated() {
            let row = UIButton(type: .system)
            var config = UIButton.Configuration.plain()
            config.contentInsets = NSDirectionalEdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 16)
            config.image = entry.0 == selected ? UIImage(systemName: "checkmark") : nil
            config.imagePlacement = .trailing
            config.imagePadding = 8
            config.baseForegroundColor = WriterAITheme.Color.textPrimary
            config.title = entry.0
            row.configuration = config
            row.tag = index
            row.addTarget(self, action: #selector(sizeTapped(_:)), for: .touchUpInside)
            stack.addArrangedSubview(row)
        }
        addSubview(scroll)
        scroll.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            scroll.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8),
            scroll.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            scroll.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
            widthAnchor.constraint(equalToConstant: 320),
            heightAnchor.constraint(equalToConstant: 460),
        ])
    }

    @objc private func sizeTapped(_ sender: UIButton) {
        onSelect(labels[sender.tag].1)
    }
}
