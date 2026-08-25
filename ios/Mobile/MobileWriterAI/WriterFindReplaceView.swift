import UIKit

/// 查找/替换弹层 — Figma 127:6209 (查找) / 1689:55743 (替换).
/// Bottom sheet: title bar (设置/标题/关闭), segmented 查找|替换, inputs,
/// action buttons. Commands go through onCommand (host dispatches ExecuteSearch).
@objc final class WriterFindReplaceView: UIView {
    @objc enum Mode: Int {
        case find = 0
        case replace = 1
    }

    private let onCommand: (String, String, Bool) -> Void   // (action, search, replace)
    private let onClose: () -> Void
    private let searchField = UITextField()
    private let replaceField = UITextField()
    private let segment = UISegmentedControl(items: ["查找", "替换"])
    private var mode: Mode = .find

    @objc init(onCommand: @escaping (String, String, Bool) -> Void, onClose: @escaping () -> Void) {
        self.onCommand = onCommand
        self.onClose = onClose
        super.init(frame: .zero)
        build()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func build() {
        backgroundColor = .white
        layer.cornerRadius = WriterAITheme.Radius.panel
        layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        writerAI_apply(WriterAITheme.Shadow.panel)

        // Title bar: settings icon + 查找/替换 title + close.
        let title = UILabel()
        title.text = "查找"
        title.font = WriterAITheme.Font.title()
        title.textColor = WriterAITheme.Color.textPrimary
        let close = WriterAICloseButton()
        close.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        let titleBar = UIStackView(arrangedSubviews: [title, UIView(), close])
        titleBar.axis = .horizontal
        titleBar.alignment = .center

        // Segmented control 查找|替换.
        segment.selectedSegmentIndex = 0
        segment.addTarget(self, action: #selector(segmentChanged), for: .valueChanged)
        segment.heightAnchor.constraint(equalToConstant: 76).isActive = true

        // Find input.
        searchField.placeholder = "请输入查找内容"
        searchField.font = WriterAITheme.Font.body()
        searchField.backgroundColor = WriterAITheme.Color.surfaceCard
        searchField.layer.cornerRadius = WriterAITheme.Radius.floating
        searchField.leftViewMode = .always
        searchField.leftView = UIImageView(image: UIImage(systemName: "magnifyingglass"))
        searchField.leftView?.tintColor = WriterAITheme.Color.textTertiary
        searchField.heightAnchor.constraint(equalToConstant: 104).isActive = true

        // Replace input (hidden until replace mode).
        replaceField.placeholder = "请输入替换内容"
        replaceField.font = WriterAITheme.Font.body()
        replaceField.backgroundColor = WriterAITheme.Color.surfaceCard
        replaceField.layer.cornerRadius = WriterAITheme.Radius.floating
        replaceField.leftViewMode = .always
        replaceField.leftView = UIImageView(image: UIImage(systemName: "pencil"))
        replaceField.leftView?.tintColor = WriterAITheme.Color.textTertiary
        replaceField.heightAnchor.constraint(equalToConstant: 104).isActive = true
        replaceField.isHidden = true

        // Action buttons.
        let prev = WriterAISecondaryButton(title: "上一处")
        let next = WriterAISecondaryButton(title: "下一处")
        let replaceAll = WriterAISecondaryButton(title: "全部替换")
        let replaceOne = WriterAIPrimaryButton(title: "替换")
        replaceAll.isHidden = true
        replaceOne.isHidden = true
        prev.addTarget(self, action: #selector(prevTapped), for: .touchUpInside)
        next.addTarget(self, action: #selector(nextTapped), for: .touchUpInside)
        replaceAll.addTarget(self, action: #selector(replaceAllTapped), for: .touchUpInside)
        replaceOne.addTarget(self, action: #selector(replaceOneTapped), for: .touchUpInside)

        let actionsRow = UIStackView(arrangedSubviews: [prev, next, replaceAll, replaceOne])
        actionsRow.axis = .horizontal
        actionsRow.spacing = 16
        actionsRow.distribution = .fillEqually
        actionsRow.heightAnchor.constraint(equalToConstant: 70).isActive = true

        let stack = UIStackView(arrangedSubviews: [titleBar, segment, searchField, replaceField, actionsRow])
        stack.axis = .vertical
        stack.spacing = 20
        addSubview(stack)
        stack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -40),
        ])
    }

    // MARK: Mode switching

    @objc private func segmentChanged() {
        mode = segment.selectedSegmentIndex == 0 ? .find : .replace
        replaceField.isHidden = mode == .find
        replaceAll.isHidden = mode == .find
        replaceOne.isHidden = mode == .find
    }

    // MARK: Actions

    @objc private func closeTapped() { onClose() }
    @objc private func prevTapped() { onCommand("prev", searchField.text ?? "", mode == .replace) }
    @objc private func nextTapped() { onCommand("next", searchField.text ?? "", mode == .replace) }
    @objc private func replaceAllTapped() { onCommand("all", searchField.text ?? "", replaceField.text ?? "") }
    @objc private func replaceOneTapped() { onCommand("one", searchField.text ?? "", replaceField.text ?? "") }

    // MARK: Presentation

    @objc func show(in parent: UIView, aboveBottomInset inset: CGFloat) {
        parent.addSubview(self)
        translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            leadingAnchor.constraint(equalTo: parent.leadingAnchor),
            trailingAnchor.constraint(equalTo: parent.trailingAnchor),
            bottomAnchor.constraint(equalTo: parent.bottomAnchor),
            heightAnchor.constraint(equalToConstant: 420),
        ])
        let offset = bounds.height
        transform = CGAffineTransform(translationX: 0, y: offset)
        UIView.animate(withDuration: WriterAITheme.Animation.sheetIn, delay: 0,
                       options: .curveEaseOut) {
            self.transform = .identity
        }
    }

    @objc func dismiss() {
        UIView.animate(withDuration: WriterAITheme.Animation.sheetOut, delay: 0,
                       options: .curveEaseIn) {
            self.transform = CGAffineTransform(translationX: 0, y: self.bounds.height)
        } completion: { _ in
            self.removeFromSuperview()
        }
    }
}
