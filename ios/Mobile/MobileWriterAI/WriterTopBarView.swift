import UIKit

/// 编辑态顶栏 — Figma 145:3222 标题栏2: 完成(蓝胶囊) / 撤回 / 重做 / 批注(红角标) / 关闭.
/// undo/redo state driven by `setUndoRedo(enabled:redo:)` from the undo.redo v1 message.
@objc final class WriterTopBarView: UIView {
    private let onAction: (String) -> Void
    private let undoButton = UIButton(type: .system)
    private let redoButton = UIButton(type: .system)
    private let closeButton = WriterAICloseButton()
    private let commentBadge = UILabel()

    @objc init(onAction: @escaping (String) -> Void) {
        self.onAction = onAction
        super.init(frame: .zero)
        build()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func build() {
        backgroundColor = WriterAITheme.Color.surfaceCard

        let done = UIButton(type: .system)
        done.setTitle("完成", for: .normal)
        done.setTitleColor(.white, for: .normal)
        done.titleLabel?.font = WriterAITheme.Font.button()
        done.backgroundColor = WriterAITheme.Color.primaryBlue
        done.layer.cornerRadius = 31
        done.widthAnchor.constraint(equalToConstant: 144).isActive = true
        done.heightAnchor.constraint(equalToConstant: 62).isActive = true
        done.addTarget(self, action: #selector(act(_:)), for: .touchUpInside)
        done.accessibilityHint = "done"

        undoButton.setImage(UIImage(systemName: "arrow.uturn.backward"), for: .normal)
        redoButton.setImage(UIImage(systemName: "arrow.uturn.forward"), for: .normal)
        for b in [undoButton, redoButton] {
            b.tintColor = WriterAITheme.Color.textPrimary
            b.widthAnchor.constraint(equalToConstant: 48).isActive = true
            b.heightAnchor.constraint(equalToConstant: 48).isActive = true
        }
        undoButton.addTarget(self, action: #selector(act(_:)), for: .touchUpInside)
        undoButton.accessibilityHint = "undo"
        redoButton.addTarget(self, action: #selector(act(_:)), for: .touchUpInside)
        redoButton.accessibilityHint = "redo"

        let comment = UIButton(type: .system)
        comment.setImage(UIImage(systemName: "bubble.right"), for: .normal)
        comment.tintColor = WriterAITheme.Color.textPrimary
        comment.widthAnchor.constraint(equalToConstant: 48).isActive = true
        comment.heightAnchor.constraint(equalToConstant: 48).isActive = true
        comment.addTarget(self, action: #selector(act(_:)), for: .touchUpInside)
        comment.accessibilityHint = "comment"
        commentBadge.text = "1"
        commentBadge.font = UIFont.systemFont(ofSize: 10, weight: .medium)
        commentBadge.textColor = .white
        commentBadge.backgroundColor = WriterAITheme.Color.badgeRed
        commentBadge.layer.cornerRadius = 10
        commentBadge.clipsToBounds = true
        commentBadge.translatesAutoresizingMaskIntoConstraints = false
        comment.addSubview(commentBadge)
        NSLayoutConstraint.activate([
            commentBadge.topAnchor.constraint(equalTo: comment.topAnchor, constant: 2),
            commentBadge.trailingAnchor.constraint(equalTo: comment.trailingAnchor, constant: 0),
            commentBadge.widthAnchor.constraint(equalToConstant: 20),
            commentBadge.heightAnchor.constraint(equalToConstant: 20),
        ])

        closeButton.addTarget(self, action: #selector(act(_:)), for: .touchUpInside)
        closeButton.accessibilityHint = "close"

        let row = UIStackView(arrangedSubviews: [done, UIView(), undoButton, redoButton, comment, closeButton])
        row.axis = .horizontal
        row.spacing = 8
        row.alignment = .center
        addSubview(row)
        row.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            row.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            row.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            row.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -12),
        ])
    }

    @objc private func act(_ sender: UIControl) {
        if let hint = sender.accessibilityHint {
            onAction(hint)
        }
    }

    /// Consume the `undo.redo` v1 message: enable/disable the two buttons.
    @objc func setUndoRedoEnabled:(BOOL)canUndo redo:(BOOL)canRedo {
        undoButton.isEnabled = canUndo
        undoButton.alpha = canUndo ? 1 : 0.4
        redoButton.isEnabled = canRedo
        redoButton.alpha = canRedo ? 1 : 0.4
    }
}
