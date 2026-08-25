import UIKit

/// 文件 tab — Figma 204:13803. 4 rows: 保存 / 另存为 / 导出为 / 打印.
/// Each row 686×128 (icon 64 + label 36px). Actions route through onAction.
@objc final class WriterFileTabView: UIView {
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
        stack.spacing = 0
        addSubview(stack)
        stack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor),
        ])
        for (idx, (title, key)) in [("保存", "save"), ("另存为", "saveAs"), ("导出为", "export"), ("打印", "print")].enumerated() {
            let row = row(title: title, key: key)
            stack.addArrangedSubview(row)
            if idx < 3 {
                let hairline = UIView()
                hairline.backgroundColor = WriterAITheme.Color.hairline
                hairline.heightAnchor.constraint(equalToConstant: 0.5).isActive = true
                stack.addArrangedSubview(hairline)
            }
        }
    }

    private func row(title: String, key: String) -> UIView {
        let label = UILabel()
        label.text = title
        label.font = WriterAITheme.Font.body()
        label.textColor = WriterAITheme.Color.textPrimary

        let row = UIStackView(arrangedSubviews: [label, UIView()])
        row.axis = .horizontal
        row.alignment = .center
        row.heightAnchor.constraint(equalToConstant: 128).isActive = true
        let container = UIView()
        row.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(row)
        NSLayoutConstraint.activate([
            row.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            row.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            row.centerYAnchor.constraint(equalTo: container.centerYAnchor),
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
