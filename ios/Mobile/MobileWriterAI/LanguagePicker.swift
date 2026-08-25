import UIKit

/// Translation language picker — Figma 1689:58231: floating popover with the
/// frozen 9-language registry (catalog §4.1), checkmark on the selected row.
@objc final class WriterALanguagePicker: UIView {
    private var rows: [WriterAILanguageRow] = []
    private let languages: [(key: String, label: String)]
    private let onSelect: (String) -> Void

    @objc init(onSelect: @escaping (String) -> Void) {
        self.onSelect = onSelect
        self.languages = WriterALanguagePicker.registry
        super.init(frame: .zero)
        build()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func build() {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 0
        stack.alignment = .fill
        for (key, label) in languages {
            let row = WriterAILanguageRow(languageName: label)
            row.tag = rows.count
            row.addTarget(self, action: #selector(rowTapped(_:)), for: .touchUpInside)
            rows.append(row)
            stack.addArrangedSubview(row)
            if key != languages.last?.key {
                let hairline = UIView()
                hairline.backgroundColor = WriterAITheme.Color.hairline
                hairline.heightAnchor.constraint(equalToConstant: 0.5).isActive = true
                stack.addArrangedSubview(hairline)
            }
        }
        addSubview(stack)
        stack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 8),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -8),
        ])
        // Mark the first row selected initially (auto → 中文 default flow).
        rows.first?.isSelected = true
    }

    @objc private func rowTapped(_ sender: WriterAILanguageRow) {
        for (index, row) in rows.enumerated() {
            row.isSelected = (row === sender)
            if row === sender {
                onSelect(languages[index].key)
            }
        }
    }

    /// Frozen language registry (catalog §4.1, order preserved).
    private static let registry: [(key: String, label: String)] = [
        ("auto", "自动检测"),
        ("zh", "中文"),
        ("en", "English"),
        ("ja", "日本語"),
        ("ko", "한국어"),
        ("fr", "Français"),
        ("de", "Deutsch"),
        ("es", "Español"),
        ("ru", "Русский"),
    ]

    // MARK: Presentation

    /// Popover anchored below the triggering control; slides up 8pt with
    /// 160ms ease-out fade-in (§3.10).
    /// Anchor-free placement (menu collapsed): top-right under the safe area.
    @objc func showIn(_ parent: UIView) {
        parent.addSubview(self)
        translatesAutoresizingMaskIntoConstraints = false
        widthAnchor.constraint(equalToConstant: 180).isActive = true
        topAnchor.constraint(equalTo: parent.safeAreaLayoutGuide.topAnchor, constant: 60).isActive = true
        trailingAnchor.constraint(equalTo: parent.trailingAnchor, constant: -16).isActive = true
        alpha = 0
        transform = CGAffineTransform(translationX: 0, y: 8)
        UIView.animate(withDuration: WriterAITheme.Animation.floatIn, delay: 0,
                       options: .curveEaseOut) {
            self.alpha = 1
            self.transform = .identity
        }
    }

    @objc func dismiss() {
        UIView.animate(withDuration: WriterAITheme.Animation.modalOut, delay: 0,
                       options: .curveEaseIn) {
            self.alpha = 0
            self.transform = CGAffineTransform(translationX: 0, y: 8)
        } completion: { _ in
            self.removeFromSuperview()
        }
    }
}