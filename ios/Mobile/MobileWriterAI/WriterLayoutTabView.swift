import UIKit

/// 布局 tab — Figma 214:15736. Rows: 水印 / 页边距 / 纸张大小 / 纸张方向.
/// Each row 686×112 radius 24 (icon 48 + label + value/chevron). Actions to onAction.
@objc final class WriterLayoutTabView: UIView {
    private let onAction: (String) -> Void

    @objc init(onAction: @escaping (String) -> Void) {
        self.onAction = onAction
        super.init(frame: .zero)
        build()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func build() {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = WriterAITheme.Spacing.cardGap
        addSubview(stack)
        stack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
        ])
        for (title, value, key) in [
            ("水印", "", "watermark"),
            ("页边距", "默认", "margins"),
            ("纸张大小", "A4", "paperSize"),
            ("纸张方向", "纵向", "orientation"),
        ] {
            stack.addArrangedSubview(row(title: title, value: value, key: key))
        }
    }

    private func row(title: String, value: String, key: String) -> UIView {
        let label = UILabel()
        label.text = title
        label.font = WriterAITheme.Font.body()
        label.textColor = WriterAITheme.Color.textPrimary

        let valueLabel = UILabel()
        valueLabel.text = value
        valueLabel.font = WriterAITheme.Font.body()
        valueLabel.textColor = WriterAITheme.Color.textTertiary

        let chevron = UIImageView(image: UIImage(systemName: "chevron.down"))
        chevron.tintColor = WriterAITheme.Color.textPrimary

        let row = UIStackView(arrangedSubviews: [label, UIView(), valueLabel, chevron])
        row.axis = .horizontal
        row.spacing = 12
        row.alignment = .center

        let container = UIView()
        container.backgroundColor = WriterAITheme.Color.surfaceCard
        container.layer.cornerRadius = WriterAITheme.Radius.card
        row.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(row)
        NSLayoutConstraint.activate([
            row.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 20),
            row.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -20),
            row.centerYAnchor.constraint(equalTo: container.centerYAnchor),
            container.heightAnchor.constraint(equalToConstant: 112),
        ])
        let tap = UITapGestureRecognizer(target: self, action: #selector(rowTapped(_:)))
        tap.accessibilityHint = key
        container.addGestureRecognizer(tap)
        return container
    }

    @objc private func rowTapped(_ gesture: UITapGestureRecognizer) {
        if let key = gesture.accessibilityHint {
            onAction(key)
        }
    }
}
