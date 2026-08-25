import UIKit

/// 插入 tab — Figma 204:14537. 2 rows × 3 cards: 图片/表格/形状/批注/页码/分页符.
/// Cards 202×160 radius 24 gap 40. Actions route through onAction.
@objc final class WriterInsertTabView: UIView {
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
        let row1 = hRow([("图片", "image"), ("表格", "table"), ("形状", "shape")])
        let row2 = hRow([("批注", "comment"), ("页码", "pageNumber"), ("分页符", "pageBreak")])
        stack.addArrangedSubview(row1)
        stack.addArrangedSubview(row2)
    }

    private func hRow(_ items: [(String, String)]) -> UIStackView {
        let row = UIStackView()
        row.axis = .horizontal
        row.spacing = WriterAITheme.Spacing.cardGap
        row.distribution = .fillEqually
        for (title, key) in items {
            row.addArrangedSubview(card(title: title, key: key))
        }
        return row
    }

    private func card(title: String, key: String) -> UIView {
        let icon = UIImageView(image: UIImage(systemName: "plus"))
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
            card.heightAnchor.constraint(equalToConstant: 160),
        ])
        let tap = UITapGestureRecognizer(target: self, action: #selector(cardTapped(_:)))
        tap.accessibilityHint = key
        card.addGestureRecognizer(tap)
        return card
    }

    @objc private func cardTapped(_ gesture: UITapGestureRecognizer) {
        if let key = gesture.accessibilityHint {
            onAction(key)
        }
    }
}
