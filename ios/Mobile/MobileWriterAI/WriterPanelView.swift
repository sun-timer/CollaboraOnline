import UIKit

/// Document function panel — bottom sheet, Figma frames 192:5683 / 204:* / 214:*.
/// 5 tabs (常用/文件/插入/布局/审阅) + 3 shortcut icon buttons on the tab bar.
/// Content per tab is supplied by the tab content providers (tickets 12-17).
@objc final class WriterPanelView: UIView {
    @objc enum Tab: Int {
        case common = 0
        case file = 1
        case insert = 2
        case layout = 3
        case review = 4
    }

    private let scrollView = WriterAIScrollView()
    private let contentHost = UIView()
    private let tabBar = UIStackView()
    private var tabButtons: [UIButton] = []
    private let onClose: () -> Void

    /// Builds the content view for a tab. Owner injects per-tab views.
    @objc var contentProvider: ((WriterPanelView.Tab) -> UIView?)?

    @objc init(onClose: @escaping () -> Void) {
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

        // Tab bar: 5 text tabs + vertical separator + 3 icon buttons.
        tabBar.axis = .horizontal
        tabBar.alignment = .fill
        let names = ["常用", "文件", "插入", "布局", "审阅"]
        for (index, name) in names.enumerated() {
            let button = UIButton(type: .system)
            button.setTitle(name, for: .normal)
            button.titleLabel?.font = WriterAITheme.Font.section()
            button.setTitleColor(WriterAITheme.Color.textTertiary, for: .normal)
            button.tag = index
            button.addTarget(self, action: #selector(tabTapped(_:)), for: .touchUpInside)
            tabBar.addArrangedSubview(button)
            tabButtons.append(button)
        }
        // "常用" default selected.
        selectTab(.common)

        let separator = UIView()
        separator.backgroundColor = WriterAITheme.Color.hairline

        let shortcutStack = UIStackView()
        shortcutStack.axis = .horizontal
        shortcutStack.spacing = 0
        for (iconName, _) in [("line.3.horizontal.decrease", "filter"), ("text.alignleft", "text"), ("chevron.down", "more")] {
            let b = UIButton(type: .system)
            b.setImage(UIImage(systemName: iconName), for: .normal)
            b.tintColor = WriterAITheme.Color.textPrimary
            b.widthAnchor.constraint(equalToConstant: 72).isActive = true
            b.heightAnchor.constraint(equalToConstant: 88).isActive = true
            shortcutStack.addArrangedSubview(b)
        }

        let tabBarRow = UIStackView(arrangedSubviews: [tabBar, separator, shortcutStack])
        tabBarRow.axis = .horizontal
        tabBarRow.spacing = 0
        tabBarRow.alignment = .center

        let topStrip = UIView()
        topStrip.backgroundColor = WriterAITheme.Color.hairline
        topStrip.heightAnchor.constraint(equalToConstant: 0.5).isActive = true

        addSubview(tabBarRow)
        addSubview(topStrip)
        addSubview(scrollView)
        tabBar.translatesAutoresizingMaskIntoConstraints = false
        tabBarRow.translatesAutoresizingMaskIntoConstraints = false
        topStrip.translatesAutoresizingMaskIntoConstraints = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            tabBarRow.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            tabBarRow.leadingAnchor.constraint(equalTo: leadingAnchor),
            tabBarRow.trailingAnchor.constraint(equalTo: trailingAnchor),
            tabBarRow.heightAnchor.constraint(equalToConstant: 88),
            topStrip.topAnchor.constraint(equalTo: tabBarRow.bottomAnchor),
            topStrip.leadingAnchor.constraint(equalTo: leadingAnchor),
            topStrip.trailingAnchor.constraint(equalTo: trailingAnchor),
            scrollView.topAnchor.constraint(equalTo: topStrip.bottomAnchor),
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])

        scrollView.addSubview(contentHost)
        contentHost.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            contentHost.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 12),
            contentHost.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -12),
            contentHost.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor, constant: 16),
            contentHost.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor, constant: -16),
            contentHost.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor, constant: -32),
        ])
    }

    // MARK: Tab selection

    @objc private func tabTapped(_ sender: UIButton) {
        selectTab(Tab(rawValue: sender.tag) ?? .common)
    }

    private func selectTab(_ tab: Tab) {
        for (index, button) in tabButtons.enumerated() {
            let selected = index == tab.rawValue
            button.setTitleColor(selected ? WriterAITheme.Color.primaryBlue : WriterAITheme.Color.textTertiary, for: .normal)
            button.titleLabel?.font = selected ? WriterAITheme.Font.section() : WriterAITheme.Font.section()
        }
        contentHost.subviews.forEach { $0.removeFromSuperview() }
        if let view = contentProvider?(tab) {
            contentHost.addSubview(view)
            view.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                view.topAnchor.constraint(equalTo: contentHost.topAnchor),
                view.bottomAnchor.constraint(equalTo: contentHost.bottomAnchor),
                view.leadingAnchor.constraint(equalTo: contentHost.leadingAnchor),
                view.trailingAnchor.constraint(equalTo: contentHost.trailingAnchor),
            ])
        }
    }

    // MARK: Close

    @objc private func closeTapped() { onClose() }

    // MARK: Presentation (same bottom-sheet shell as AIPanelView)

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
