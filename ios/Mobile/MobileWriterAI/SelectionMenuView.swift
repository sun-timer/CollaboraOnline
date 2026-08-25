import UIKit

/// Selection menu action (ticket 05). AI actions hand off to the AI panel in
@objc enum WriterAISelectionAction: Int {
    case copy, cut, paste, selectAll
    case translate
    case summarize, continueWrite, polish
    case disabled
}

/// Selection menu — edit mode, Figma frame 145:3602.
/// 3 rows: clipboard+translate / AI page 1 / AI page 2 (greyed non-P0 items),
/// white `radiusMenu` card with `shadowMenu`, hairline separators around the
/// AI rows. Width 602px ÷2 = 301pt on iPhone; scale with `Layout.pt` on iPad.
final class WriterASelectionMenuView: UIView {
    private let onAction: (WriterAISelectionAction) -> Void
    private let verticalStack = UIStackView()
    private var bottomConstraint: NSLayoutConstraint?

    @objc init(width: CGFloat, onAction: @escaping (WriterAISelectionAction) -> Void) {
        self.onAction = onAction
        super.init(frame: .zero)

        backgroundColor = .white
        layer.cornerRadius = WriterAITheme.Radius.menu
        writerAI_apply(WriterAITheme.Shadow.menu)

        verticalStack.axis = .vertical
        verticalStack.spacing = 2
        verticalStack.alignment = .center
        addSubview(verticalStack)
        verticalStack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            widthAnchor.constraint(equalToConstant: width),
            verticalStack.topAnchor.constraint(equalTo: topAnchor, constant: 4),
            verticalStack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -4),
            verticalStack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 4),
            verticalStack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -4),
        ])

        verticalStack.addArrangedSubview(row1())
        verticalStack.addArrangedSubview(divider())
        verticalStack.addArrangedSubview(row2())
        verticalStack.addArrangedSubview(divider())
        verticalStack.addArrangedSubview(row3())
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    // MARK: Rows

    private func item(_ iconName: String, _ title: String, enabled: Bool,
                      action: @escaping () -> WriterAISelectionAction) -> WriterAIMenuItemView {
        let icon = UIImage(systemName: iconName,
                           withConfiguration: UIImage.SymbolConfiguration(pointSize: 20, weight: .regular))
        let view = WriterAIMenuItemView(icon: icon, title: title)
        view.itemEnabled = enabled
        view.onTap = { [weak self] tappedEnabled in
            if !tappedEnabled {
                self?.onAction(.disabled)
            } else {
                self?.onAction(action())
            }
        }
        return view
    }

    private func row(_ items: [WriterAIMenuItemView]) -> UIStackView {
        let stack = UIStackView(arrangedSubviews: items)
        stack.axis = .horizontal
        stack.spacing = 0
        stack.alignment = .center
        return stack
    }

    private func divider() -> UIView {
        let line = UIView()
        line.backgroundColor = WriterAITheme.Color.hairline
        line.translatesAutoresizingMaskIntoConstraints = false
        line.heightAnchor.constraint(equalToConstant: 0.5).isActive = true
        line.widthAnchor.constraint(equalTo: verticalStack.widthAnchor).isActive = true
        return line
    }

    private func row1() -> UIStackView {
        row([
            item("doc.on.doc", "复制", enabled: true) { .copy },
            item("scissors", "剪切", enabled: true) { .cut },
            item("doc.on.clipboard", "粘贴", enabled: true) { .paste },
            item("checkmark.rectangle", "全选", enabled: true) { .selectAll },
            item("globe", "翻译", enabled: true) { .translate },
        ])
    }

    private func row2() -> UIStackView {
        row([
            item("list.bullet", "总结大纲", enabled: true) { .summarize },
            item("pencil", "文案续写", enabled: true) { .continueWrite },
            item("sparkles", "文案生成", enabled: false) { .disabled },
            item("plus.rectangle", "文案扩写", enabled: false) { .disabled },
            item("wand.and.stars", "文案润色", enabled: true) { .polish },
        ])
    }

    private func row3() -> UIStackView {
        row([
            item("minus.rectangle", "文案缩写", enabled: false) { .disabled },
            item("arrow.triangle.2.circlepath", "文案重写", enabled: false) { .disabled },
        ])
    }

    // MARK: Presentation

    /// Attach pinned above the bottom safe area, centered horizontally.
    @objc func show(in parent: UIView, aboveBottomInset inset: CGFloat) {
        parent.addSubview(self)
        translatesAutoresizingMaskIntoConstraints = false
        bottomConstraint = bottomAnchor.constraint(
            equalTo: parent.safeAreaLayoutGuide.bottomAnchor, constant: -inset)
        NSLayoutConstraint.activate([
            centerXAnchor.constraint(equalTo: parent.centerXAnchor),
            bottomConstraint!,
        ])
        alpha = 0
        transform = CGAffineTransform(translationX: 0, y: 8)
        UIView.animate(withDuration: WriterAITheme.Animation.floatIn, delay: 0,
                       options: .curveEaseOut) {
            self.alpha = 1
            self.transform = .identity
        }
    }

    /// Re-anchor when the keyboard frame changes (selection menus must stay
    /// above the keyboard, contract §5).
    @objc func updateBottomInset(_ inset: CGFloat) {
        bottomConstraint?.constant = -inset
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
