import UIKit

/// AI result modal, Figma frames 1689:58674 (streaming) / 1689:57135 (ready).
/// Centered card: title bar with close, scrollable content with custom
/// indicator, bottom actions — Stop (streaming) or Retry + Insert (ready).
@objc final class WriterAAIResultModal: UIView {

    private let scrollView = WriterAIScrollView()
    private let textLabel = UILabel()
    private let actionsBar = UIStackView()
    private let stopButton = WriterAISecondaryButton(title: "停止生成")
    private let retryButton = WriterAISecondaryButton(title: "重新生成")
    private let insertButton = WriterAIPrimaryButton(title: "插入文档")
    private let copyButton = UIButton(type: .system)
    private let modalTitle: String
    private weak var scrimView: UIView?

    private let onClose: () -> Void
    private let onStop: () -> Void
    private let onRetry: () -> Void
    private let onInsert: () -> Void
    private let onCopy: () -> Void

    @objc init(title: String,
               onClose: @escaping () -> Void,
               onStop: @escaping () -> Void,
               onRetry: @escaping () -> Void,
               onInsert: @escaping () -> Void,
               onCopy: @escaping () -> Void) {
        self.modalTitle = title
        self.onClose = onClose
        self.onStop = onStop
        self.onRetry = onRetry
        self.onInsert = onInsert
        self.onCopy = onCopy
        super.init(frame: .zero)
        build()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    private func build() {
        backgroundColor = .white
        layer.cornerRadius = WriterAITheme.Radius.modal
        clipsToBounds = true

        let title = UILabel()
        title.text = modalTitle
        title.font = WriterAITheme.Font.title()
        title.textColor = WriterAITheme.Color.textPrimary
        title.numberOfLines = 1

        let close = WriterAICloseButton()
        close.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)

        textLabel.font = WriterAITheme.Font.body()
        textLabel.textColor = WriterAITheme.Color.textPrimary
        textLabel.numberOfLines = 0

        scrollView.addSubview(textLabel)
        textLabel.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            textLabel.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            textLabel.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            textLabel.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            textLabel.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            textLabel.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),
        ])

        copyButton.setTitle("复制", for: .normal)
        copyButton.setTitleColor(WriterAITheme.Color.textSecondary, for: .normal)
        copyButton.titleLabel?.font = WriterAITheme.Font.menu()
        copyButton.addTarget(self, action: #selector(copyTapped), for: .touchUpInside)
        copyButton.contentHorizontalAlignment = .trailing
        copyButton.isHidden = true

        let contentStack = UIStackView(arrangedSubviews: [scrollView, copyButton])
        contentStack.axis = .vertical
        contentStack.spacing = 8

        actionsBar.axis = .horizontal
        actionsBar.spacing = 16
        actionsBar.alignment = .center
        actionsBar.addArrangedSubview(stopButton)
        actionsBar.addArrangedSubview(retryButton)
        actionsBar.addArrangedSubview(insertButton)
        stopButton.addTarget(self, action: #selector(stopTapped), for: .touchUpInside)
        retryButton.addTarget(self, action: #selector(retryTapped), for: .touchUpInside)
        insertButton.addTarget(self, action: #selector(insertTapped), for: .touchUpInside)

        let titleBar = UIStackView(arrangedSubviews: [title, UIView(), close])
        titleBar.axis = .horizontal
        titleBar.alignment = .center

        addSubview(titleBar)
        addSubview(contentStack)
        addSubview(actionsBar)
        titleBar.translatesAutoresizingMaskIntoConstraints = false
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        actionsBar.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            titleBar.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            titleBar.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            titleBar.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            contentStack.topAnchor.constraint(equalTo: titleBar.bottomAnchor, constant: 8),
            contentStack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            actionsBar.topAnchor.constraint(equalTo: contentStack.bottomAnchor, constant: 8),
            actionsBar.centerXAnchor.constraint(equalTo: centerXAnchor),
            actionsBar.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -16),
        ])
    }

    // MARK: State

    @objc func setStreamingText(_ text: String) {
        textLabel.text = text
        scrollToBottomIfNeeded()
    }

    @objc func setReady(fullText: String) {
        textLabel.text = fullText
        textLabel.textColor = WriterAITheme.Color.textPrimary
        copyButton.isHidden = false
        actionsBar.removeArrangedSubview(stopButton)
        stopButton.removeFromSuperview()
        setNeedsLayout()
    }

    @objc func setError(message: String) {
        textLabel.text = message
        textLabel.textColor = WriterAITheme.Color.badgeRed
        actionsBar.removeArrangedSubview(stopButton)
        stopButton.removeFromSuperview()
        retryButton.setTitle("重试", for: .normal)
        setNeedsLayout()
    }

    private func scrollToBottomIfNeeded() {
        let contentHeight = scrollView.contentSize.height
        let visible = scrollView.bounds.height
        if contentHeight > visible {
            scrollView.contentOffset = CGPoint(x: 0, y: contentHeight - visible)
        }
    }

    // MARK: Actions

    @objc private func closeTapped() { onClose() }
    @objc private func stopTapped() { onStop() }
    @objc private func retryTapped() { onRetry() }
    @objc private func insertTapped() { onInsert() }
    @objc private func copyTapped() { onCopy() }

    // MARK: Presentation

    /// Centered modal with scrim; fade + slight scale-in (180ms), 60pt side
    /// margins, height capped by safe area minus 60pt top/bottom (§2.1).
    @objc func show(in parent: UIView) {
        let width = parent.bounds.width - 40
        let maxHeight = parent.bounds.height - parent.safeAreaInsets.top - parent.safeAreaInsets.bottom - 120
        let height = min(maxHeight, 396)

        let scrim = UIView()
        scrim.backgroundColor = WriterAITheme.Color.scrim
        scrim.translatesAutoresizingMaskIntoConstraints = false
        parent.addSubview(scrim)
        NSLayoutConstraint.activate([
            scrim.topAnchor.constraint(equalTo: parent.topAnchor),
            scrim.bottomAnchor.constraint(equalTo: parent.bottomAnchor),
            scrim.leadingAnchor.constraint(equalTo: parent.leadingAnchor),
            scrim.trailingAnchor.constraint(equalTo: parent.trailingAnchor),
        ])
        scrimView = scrim

        translatesAutoresizingMaskIntoConstraints = false
        parent.addSubview(self)
        NSLayoutConstraint.activate([
            centerXAnchor.constraint(equalTo: parent.centerXAnchor),
            centerYAnchor.constraint(equalTo: parent.centerYAnchor),
            widthAnchor.constraint(equalToConstant: width),
            heightAnchor.constraint(equalToConstant: height),
        ])

        scrim.alpha = 0
        alpha = 0
        transform = CGAffineTransform(scaleX: 0.96, y: 0.96)
        UIView.animate(withDuration: WriterAITheme.Animation.modalIn, delay: 0,
                       options: .curveEaseOut) {
            scrim.alpha = 1
            self.alpha = 1
            self.transform = .identity
        }
    }

    @objc func dismiss() {
        UIView.animate(withDuration: WriterAITheme.Animation.modalOut, delay: 0,
                       options: .curveEaseIn) {
            self.scrimView?.alpha = 0
            self.alpha = 0
            self.transform = CGAffineTransform(scaleX: 0.97, y: 0.97)
        } completion: { _ in
            self.scrimView?.removeFromSuperview()
            self.removeFromSuperview()
        }
    }
}