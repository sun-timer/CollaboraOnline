import UIKit

/// AI function panel — bottom sheet, Figma frame 250:12885.
/// Gradient header + title bar + scrollable tile grid in three sections
/// (文案生成 / 文案处理 / 其他). Non-P0 tiles are greyed with a toast (D4).
/// Tiles tap through `onTile` with the taskType.
@objc final class WriterAIPanelView: UIView {
    private let scrollView = WriterAIScrollView()
    private let content = UIStackView()
    private let onTile: (String) -> Void
    private let onClose: () -> Void

    @objc init(width: CGFloat, onTile: @escaping (String) -> Void, onClose: @escaping () -> Void) {
        // Bottom sheet spans the parent width; `width` kept for API parity.
        _ = width
        self.onTile = onTile
        self.onClose = onClose
        super.init(frame: .zero)

        backgroundColor = .white

        let header = WriterAIPanelGradientHeader()
        let titleBar = WriterAITitleBar(title: "AI功能", horizontalPadding: 16)
        titleBar.closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)

        addSubview(header)
        addSubview(titleBar)
        addSubview(scrollView)
        header.translatesAutoresizingMaskIntoConstraints = false
        titleBar.translatesAutoresizingMaskIntoConstraints = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: topAnchor),
            header.leadingAnchor.constraint(equalTo: leadingAnchor),
            header.trailingAnchor.constraint(equalTo: trailingAnchor),
            header.heightAnchor.constraint(equalToConstant: 74),
            titleBar.topAnchor.constraint(equalTo: header.bottomAnchor),
            titleBar.leadingAnchor.constraint(equalTo: leadingAnchor),
            titleBar.trailingAnchor.constraint(equalTo: trailingAnchor),
            scrollView.topAnchor.constraint(equalTo: titleBar.bottomAnchor),
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        content.axis = .vertical
        content.spacing = WriterAITheme.Spacing.sections
        content.alignment = .fill
        content.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(content)
        NSLayoutConstraint.activate([
            content.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 12),
            content.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -12),
            content.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 12),
            content.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -12),
            content.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -24),
        ])

        content.addArrangedSubview(section("文案生成", tiles: [
            tile("sparkles", "AI续写", "continue_write", enabled: true),
            tile("list.bullet", "生成大纲", "outline", enabled: false),
            tile("doc.badge.plus", "文案生成", "article_generate", enabled: false),
        ]))
        content.addArrangedSubview(section("文案处理", tiles: [
            tile("wand.and.stars", "文案润色", "polish", enabled: true),
            tile("plus.rectangle", "文案扩写", "expand", enabled: false),
            tile("minus.rectangle", "文案缩写", "condense", enabled: false),
            tile("arrow.triangle.2.circlepath", "文案重写", "rewrite", enabled: false),
            tile("globe", "文案翻译", "translate", enabled: true),
        ]))
        content.addArrangedSubview(section("其他", tiles: [
            tile("doc.text", "文字提取", "text_extract", enabled: false),
            tile("textformat", "AI排版", "typeset", enabled: false),
            tile("photo", "AI图片", "image_generate", enabled: false),
            tile("wand.and.rays", "格式批量处理", "format_batch", enabled: false),
        ]))
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    @objc private func closeTapped() { onClose() }

    // MARK: Sections

    private func section(_ title: String, tiles: [WriterAITileCard]) -> UIStackView {
        let label = UILabel()
        label.text = title
        label.font = WriterAITheme.Font.section()
        label.textColor = WriterAITheme.Color.textPrimary

        let grid = UIStackView(arrangedSubviews: tiles)
        grid.spacing = WriterAITheme.Spacing.cardGap
        grid.alignment = .top

        let section = UIStackView(arrangedSubviews: [label, grid])
        section.axis = .vertical
        section.spacing = 8
        return section
    }
    private func tile(_ iconName: String, _ title: String, _ taskType: String, enabled: Bool) -> WriterAITileCard {
        let icon = UIImage(systemName: iconName,
                           withConfiguration: UIImage.SymbolConfiguration(pointSize: 24, weight: .regular))
        let card = WriterAITileCard(icon: icon, title: title, enabled: enabled)
        // Frozen §3.7 tile is 100×80pt; pin the width so stack .fill cannot
        // stretch rows into inconsistent tile sizes.
        card.widthAnchor.constraint(equalToConstant: 100).isActive = true
        card.addTarget(self, action: #selector(tileTapped(_:)), for: .touchUpInside)
        card.accessibilityIdentifier = taskType
        return card
    }

    @objc private func tileTapped(_ sender: WriterAITileCard) {
        guard sender.tileEnabled, let taskType = sender.accessibilityIdentifier else { return }
        onTile(taskType)
    }

    // MARK: Presentation

    /// Bottom sheet: slide up 280ms, rounded top corners only.
    @objc func show(in parent: UIView, aboveBottomInset inset: CGFloat) {
        parent.addSubview(self)
        translatesAutoresizingMaskIntoConstraints = false
        let height = max(320, parent.bounds.height * 0.85 - inset)
        NSLayoutConstraint.activate([
            leadingAnchor.constraint(equalTo: parent.leadingAnchor),
            trailingAnchor.constraint(equalTo: parent.trailingAnchor),
            bottomAnchor.constraint(equalTo: parent.bottomAnchor),
            heightAnchor.constraint(equalToConstant: height),
        ])
        layer.cornerRadius = WriterAITheme.Radius.panel
        layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        writerAI_apply(WriterAITheme.Shadow.panel)

        let offset = height
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