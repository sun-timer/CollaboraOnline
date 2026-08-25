import UIKit

/// 常用 tab content — Figma 192:5683. Four blocks: 样式 / 字体 / 字号 / 段落.
/// Cards show current values; commands and popovers route through callbacks
/// (the owner VC dispatches UNO and presents font/size popovers).
@objc final class WriterCommonTabView: UIView {
    private let onCommand: (String) -> Void
    private let onValueTap: (String) -> Void   // "style" / "font" / "size"
    private let fontValueLabel = UILabel()
    private let sizeValueLabel = UILabel()

    @objc init(onCommand: @escaping (String) -> Void, onValueTap: @escaping (String) -> Void) {
        self.onCommand = onCommand
        self.onValueTap = onValueTap
        super.init(frame: .zero)
        build()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func build() {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = WriterAITheme.Spacing.sections
        addSubview(stack)
        stack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
        ])

        stack.addArrangedSubview(section("样式", card: valueCard(label: "正文", iconName: "textformat", tapKey: "style")))
        stack.addArrangedSubview(section("字体", card: valueCard(label: "宋体", iconName: "character.textbox", tapKey: "font", valueLabel: fontValueLabel)))
        stack.addArrangedSubview(section("字号", card: valueCard(label: "4号", iconName: "textformat.size", tapKey: "size", valueLabel: sizeValueLabel)))
        stack.addArrangedSubview(section("段落", card: alignmentRow()))
    }

    // MARK: Sections

    private func section(_ title: String, card: UIView) -> UIStackView {
        let label = UILabel()
        label.text = title
        label.font = WriterAITheme.Font.section()
        label.textColor = WriterAITheme.Color.textPrimary
        let section = UIStackView(arrangedSubviews: [label, card])
        section.axis = .vertical
        section.spacing = 8
        return section
    }

    /// 686×112 radius 24 #f2f3f5 value card: leading icon + label + trailing chevron.
    private func valueCard(label: String, iconName: String, tapKey: String,
                           valueLabel: UILabel? = nil) -> UIView {
        let icon = UIImage(systemName: iconName)
        let iconView = UIImageView(image: icon)
        iconView.tintColor = WriterAITheme.Color.textPrimary
        iconView.contentMode = .scaleAspectFit
        iconView.widthAnchor.constraint(equalToConstant: 24).isActive = true
        iconView.heightAnchor.constraint(equalToConstant: 24).isActive = true

        let textLabel = UILabel()
        textLabel.text = label
        textLabel.font = WriterAITheme.Font.body()
        textLabel.textColor = WriterAITheme.Color.textPrimary

        let chevron = UIImageView(image: UIImage(systemName: "chevron.down"))
        chevron.tintColor = WriterAITheme.Color.textPrimary

        let row = UIStackView(arrangedSubviews: [iconView, textLabel, UIView(), chevron])
        row.axis = .horizontal
        row.spacing = 12

        let card = UIView()
        card.backgroundColor = WriterAITheme.Color.surfaceCard
        card.layer.cornerRadius = WriterAITheme.Radius.card
        row.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(row)
        NSLayoutConstraint.activate([
            row.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 20),
            row.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -20),
            row.centerYAnchor.constraint(equalTo: card.centerYAnchor),
        ])
        card.heightAnchor.constraint(equalToConstant: 112).isActive = true
        let tap = UITapGestureRecognizer(target: self, action: #selector(valueTapped(_:)))
        tap.accessibilityHint = tapKey
        card.addGestureRecognizer(tap)
        return card
    }

    @objc private func valueTapped(_ gesture: UITapGestureRecognizer) {
        if let key = gesture.accessibilityHint {
            onValueTap(key)
        }
    }

    /// 3 alignment cards 202×160 (left/center/right).
    private func alignmentRow() -> UIView {
        let row = UIStackView()
        row.axis = .horizontal
        row.spacing = WriterAITheme.Spacing.cardGap
        row.distribution = .fillEqually
        for (title, cmd, icon) in [
            ("左对齐", ".uno:LeftPara", "text.alignleft"),
            ("居中", ".uno:CenterPara", "text.aligncenter"),
            ("右对齐", ".uno:RightPara", "text.alignright"),
        ] {
            let card = alignmentCard(title: title, cmd: cmd, icon: icon)
            row.addArrangedSubview(card)
        }
        return row
    }

    private func alignmentCard(title: String, cmd: String, icon: String) -> UIView {
        let icon = UIImageView(image: UIImage(systemName: icon))
        icon.tintColor = WriterAITheme.Color.textPrimary
        icon.contentMode = .scaleAspectFit
        let label = UILabel()
        label.text = title
        label.font = WriterAITheme.Font.tile()
        label.textColor = WriterAITheme.Color.textPrimary
        label.textAlignment = .center

        let card = UIView()
        card.backgroundColor = WriterAITheme.Color.surfaceCard
        card.layer.cornerRadius = WriterAITheme.Radius.card
        let column = UIStackView(arrangedSubviews: [icon, label])
        column.axis = .vertical
        column.spacing = WriterAITheme.Spacing.cardGap
        column.alignment = .center
        column.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(column)
        NSLayoutConstraint.activate([
            column.centerXAnchor.constraint(equalTo: card.centerXAnchor),
            column.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            column.leadingAnchor.constraint(greaterThanOrEqualTo: card.leadingAnchor, constant: 12),
            column.trailingAnchor.constraint(lessThanOrEqualTo: card.trailingAnchor, constant: -12),
            card.heightAnchor.constraint(equalToConstant: 160),
        ])
        let tap = UITapGestureRecognizer(target: self, action: #selector(alignmentTapped(_:)))
        tap.accessibilityHint = cmd
        card.addGestureRecognizer(tap)
        return card
    }

    @objc private func alignmentTapped(_ gesture: UITapGestureRecognizer) {
        if let cmd = gesture.accessibilityHint {
            onCommand(cmd)
        }
    }

    /// Update current font/size text (owned by the popover picker).
    @objc func setFontValue(_ font: String) { fontValueLabel.text = font }
    @objc func setSizeValue(_ size: String) { sizeValueLabel.text = size }
}
