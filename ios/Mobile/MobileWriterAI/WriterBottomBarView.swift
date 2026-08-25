import UIKit

/// Bottom function bar — Word edit mode, Figma 145:3222 / 1714:30692.
/// 5 items (适配手机/功能/AI助手/AI功能/呼出键盘), each 108×108 (icon 48 + label 24px).
@objc final class WriterBottomBarView: UIView {
    private let onItem: (String) -> Void

    @objc init(onItem: @escaping (String) -> Void) {
        self.onItem = onItem
        super.init(frame: .zero)
        build()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func build() {
        backgroundColor = .white
        let topStrip = UIView()
        topStrip.backgroundColor = WriterAITheme.Color.hairline
        topStrip.heightAnchor.constraint(equalToConstant: 0.5).isActive = true
        addSubview(topStrip)

        let row = UIStackView()
        row.axis = .horizontal
        row.distribution = .fillEqually
        row.alignment = .center
        for (title, icon, key) in [
            ("适配手机", "ipad.and.iphone", "preview"),
            ("功能", "square.grid.2x2", "function"),
            ("AI助手", "sparkles", "assistant"),
            ("AI功能", "line.3.horizontal.decrease", "ai"),
            ("呼出键盘", "keyboard", "keyboard"),
        ] {
            row.addArrangedSubview(item(title: title, icon: icon, key: key))
        }
        addSubview(row)
        topStrip.translatesAutoresizingMaskIntoConstraints = false
        row.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            topStrip.topAnchor.constraint(equalTo: topAnchor),
            topStrip.leadingAnchor.constraint(equalTo: leadingAnchor),
            topStrip.trailingAnchor.constraint(equalTo: trailingAnchor),
            row.topAnchor.constraint(equalTo: topStrip.bottomAnchor),
            row.leadingAnchor.constraint(equalTo: leadingAnchor),
            row.trailingAnchor.constraint(equalTo: trailingAnchor),
            row.bottomAnchor.constraint(equalTo: bottomAnchor),
            heightAnchor.constraint(equalToConstant: 148),
        ])
    }

    private func item(title: String, icon: String, key: String) -> UIView {
        let button = UIButton(type: .system)
        button.setImage(UIImage(systemName: icon), for: .normal)
        button.setTitle(title, for: .normal)
        button.tintColor = WriterAITheme.Color.textPrimary
        button.setTitleColor(WriterAITheme.Color.textPrimary, for: .normal)
        button.titleLabel?.font = WriterAITheme.Font.menu()
        button.titleLabel?.textAlignment = .center
        button.sizeThatFits(CGSize(width: 108, height: 108))
        var config = UIButton.Configuration.plain()
        config.image = UIImage(systemName: icon)
        config.title = title
        config.imagePlacement = .top
        config.imagePadding = 6
        config.baseForegroundColor = WriterAITheme.Color.textPrimary
        config.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { input in
            var out = input
            out.font = WriterAITheme.Font.menu()
            return out
        }
        button.configuration = config
        let tap = UITapGestureRecognizer(target: self, action: #selector(itemTapped(_:)))
        tap.accessibilityHint = key
        button.addGestureRecognizer(tap)
        button.widthAnchor.constraint(equalToConstant: 108).isActive = true
        button.heightAnchor.constraint(equalToConstant: 108).isActive = true
        return button
    }

    @objc private func itemTapped(_ gesture: UITapGestureRecognizer) {
        if let key = gesture.accessibilityHint {
            onItem(key)
        }
    }
}
