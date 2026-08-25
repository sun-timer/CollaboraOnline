import UIKit

// MARK: - Shared behavior

extension UIView {
    /// Apply a contract shadow (§1.3).
    func writerAI_apply(_ shadow: WriterAIShadow) {
        layer.shadowColor = shadow.color.cgColor
        layer.shadowRadius = shadow.radius
        layer.shadowOffset = shadow.offset
        layer.shadowOpacity = 1
    }

    /// Press animation: scale down in 80ms easeOut, spring back in 120ms (§4).
    func writerAI_press(scale: CGFloat) {
        UIView.animate(withDuration: WriterAITheme.Animation.press, delay: 0,
                       options: .curveEaseOut) {
            self.transform = CGAffineTransform(scaleX: scale, y: scale)
        }
    }

    func writerAI_release() {
        UIView.animate(withDuration: WriterAITheme.Animation.springBack, delay: 0,
                       usingSpringWithDamping: 0.6, initialSpringVelocity: 0.8,
                       options: .curveEaseOut) {
            self.transform = .identity
        }
    }

    /// Fade the view out and back in — used for disabled-state feedback.
    func writerAI_blink() {
        let faded = alpha
        alpha = faded * 0.4
        UIView.animate(withDuration: 0.15) {
            self.alpha = faded
        }
    }
}

/// Small transient toast used for disabled (non-P0) tile taps.
final class WriterAIToast: UILabel {
    static func show(_ text: String, in window: UIWindow?) {
        guard let window else { return }
        let toast = WriterAIToast()
        toast.text = text
        toast.textColor = .white
        toast.font = WriterAITheme.Font.body()
        toast.backgroundColor = UIColor.black.withAlphaComponent(0.75)
        toast.layer.cornerRadius = 8
        toast.clipsToBounds = true
        toast.textAlignment = .center
        toast.sizeToFit()
        toast.bounds.size.width += 24
        toast.bounds.size.height += 12
        toast.center = CGPoint(x: window.bounds.midX, y: window.bounds.height - 160)
        toast.alpha = 0
        window.addSubview(toast)
        UIView.animate(withDuration: 0.2) { toast.alpha = 1 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            UIView.animate(withDuration: 0.3, animations: { toast.alpha = 0 }) { _ in
                toast.removeFromSuperview()
            }
        }
    }
}

// MARK: - §3.1 Close button

/// Visual 40×40pt × button (80×80px ÷2) inside a 44×44pt touch target (§2.1, C5).
final class WriterAICloseButton: UIButton {
    init() {
        super.init(frame: .zero)
        let config = UIButton.Configuration.plain()
        let symbol = UIImage(systemName: "xmark",
                             withConfiguration: UIImage.SymbolConfiguration(pointSize: 12, weight: .medium))
        config.image = symbol
        config.baseForegroundColor = WriterAITheme.Color.textPrimary
        configuration = config
        accessibilityLabel = NSLocalizedString("关闭", comment: "close button")
        addTarget(self, action: #selector(pressed), for: .touchDown)
        addTarget(self, action: #selector(released), for: [.touchUpInside, .touchUpOutside, .touchCancel])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    @objc private func pressed() { alpha = 0.5 }
    @objc private func released() { alpha = 1 }
}

// MARK: - §3.2 Title bar

/// 60pt bar: left-aligned title + right close button. Used by Modal and Sheet.
final class WriterAITitleBar: UIView {
    let titleLabel = UILabel()
    let closeButton = WriterAICloseButton()

    init(title: String, horizontalPadding: CGFloat = 16) {
        super.init(frame: .zero)
        titleLabel.text = title
        titleLabel.font = WriterAITheme.Font.title()
        titleLabel.textColor = WriterAITheme.Color.textPrimary
        titleLabel.numberOfLines = 1
        titleLabel.lineBreakMode = .byTruncatingTail

        addSubview(titleLabel)
        addSubview(closeButton)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            titleLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: horizontalPadding),
            titleLabel.centerYAnchor.constraint(equalTo: centerYAnchor),
            titleLabel.trailingAnchor.constraint(lessThanOrEqualTo: closeButton.leadingAnchor, constant: -8),
            closeButton.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -(horizontalPadding - 16)),
            closeButton.centerYAnchor.constraint(equalTo: centerYAnchor),
            closeButton.widthAnchor.constraint(equalToConstant: 44),
            closeButton.heightAnchor.constraint(equalToConstant: 44),
            heightAnchor.constraint(equalToConstant: 60),
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }
}

// MARK: - §3.3 / §3.4 Buttons

/// Gradient pill button 120×35pt: the only primary action ("插入文档").
final class WriterAIPrimaryButton: UIButton {
    private let gradientLayer = CAGradientLayer()

    init(title: String) {
        super.init(frame: .zero)
        setTitle(title, for: .normal)
        setTitleColor(.white, for: .normal)
        titleLabel?.font = WriterAITheme.Font.button()
        gradientLayer.colors = [
            WriterAITheme.Color.gradientPrimaryStart.cgColor,
            WriterAITheme.Color.gradientPrimaryEnd.cgColor,
        ]
        // Figma angle 136.7° ≈ 135° (start bottom-left → top-right).
        gradientLayer.startPoint = CGPoint(x: 0.02, y: 0.98)
        gradientLayer.endPoint = CGPoint(x: 0.98, y: 0.02)
        layer.insertSublayer(gradientLayer, at: 0)
        layer.cornerRadius = 17.5
        layer.masksToBounds = true
        addTarget(self, action: #selector(pressed), for: .touchDown)
        addTarget(self, action: #selector(released), for: [.touchUpInside, .touchUpOutside, .touchCancel])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func layoutSubviews() {
        super.layoutSubviews()
        gradientLayer.frame = bounds
    }

    override var isEnabled: Bool {
        didSet { alpha = isEnabled ? 1 : 0.4 }
    }

    @objc private func pressed() { writerAI_press(scale: 0.85) }
    @objc private func released() { writerAI_release() }
}

/// Grey pill button 120×35pt: "重新生成" / "停止生成".
final class WriterAISecondaryButton: UIButton {
    init(title: String) {
        super.init(frame: .zero)
        setTitle(title, for: .normal)
        setTitleColor(WriterAITheme.Color.buttonTextDark, for: .normal)
        titleLabel?.font = WriterAITheme.Font.button()
        backgroundColor = WriterAITheme.Color.surfaceButtonGrey
        layer.cornerRadius = 17.5
        layer.masksToBounds = true
        addTarget(self, action: #selector(pressed), for: .touchDown)
        addTarget(self, action: #selector(released), for: [.touchUpInside, .touchUpOutside, .touchCancel])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    @objc private func pressed() { writerAI_press(scale: 0.85) }
    @objc private func released() { writerAI_release() }
}

// MARK: - §3.5 Panel gradient header

/// 74pt decorative header for the AI Sheet: base gradient full width,
/// top gradient fading out above it (mask per contract).
final class WriterAIPanelGradientHeader: UIView {
    private let baseLayer = CAGradientLayer()
    private let topLayer = CAGradientLayer()

    init() {
        super.init(frame: .zero)
        baseLayer.colors = [
            WriterAITheme.Color.gradientPanelBaseStart.cgColor,
            WriterAITheme.Color.gradientPanelBaseEnd.cgColor,
        ]
        baseLayer.startPoint = CGPoint(x: 0, y: 0)
        baseLayer.endPoint = CGPoint(x: 1, y: 1)
        topLayer.colors = [
            WriterAITheme.Color.gradientPanelTopStart.cgColor,
            WriterAITheme.Color.gradientPanelTopEnd.cgColor,
        ]
        topLayer.startPoint = CGPoint(x: 0, y: 0)
        topLayer.endPoint = CGPoint(x: 1, y: 1)
        layer.addSublayer(baseLayer)
        layer.addSublayer(topLayer)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func layoutSubviews() {
        super.layoutSubviews()
        baseLayer.frame = bounds
        // Top gradient occupies the upper half, fading to transparent.
        topLayer.frame = CGRect(x: 0, y: 0, width: bounds.width, height: bounds.height / 2)
    }
}

// MARK: - §3.6 Scroll indicator

/// UIScrollView with a custom 4pt-wide `#CBD1D7` indicator (8px) instead of
/// the system one. System indicator stays hidden.
final class WriterAIScrollView: UIScrollView {
    private let thumb = UIView()

    init() {
        super.init(frame: .zero)
        showsVerticalScrollIndicator = false
        thumb.backgroundColor = WriterAITheme.Color.scrollbar
        thumb.layer.cornerRadius = 1.5
        thumb.isHidden = true
        addSubview(thumb)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override func layoutSubviews() {
        super.layoutSubviews()
        updateThumb()
    }

    override var contentOffset: CGPoint {
        didSet { updateThumb() }
    }

    override var contentSize: CGSize {
        didSet { updateThumb() }
    }

    override var bounds: CGRect {
        didSet { updateThumb() }
    }

    private func updateThumb() {
        bringSubviewToFront(thumb)
        let visible = bounds.height
        guard visible > 0, contentSize.height > visible else {
            thumb.isHidden = true
            return
        }
        let ratio = visible / contentSize.height
        let thumbHeight = max(visible * ratio, 24)
        let travel = visible - thumbHeight
        let maxOffset = max(contentSize.height - visible, 1)
        let y = (contentOffset.y / maxOffset) * travel
        thumb.frame = CGRect(x: bounds.width - 6, y: y, width: 4, height: thumbHeight)
        thumb.isHidden = false
    }


}

// MARK: - §3.7 Tile card

/// 100×80pt grid tile: icon 24pt + label 12pt, gap 6pt (§1.3 card gap).
final class WriterAITileCard: UIButton {
    private let iconView = UIImageView()
    private let labelView = UILabel()

    init(icon: UIImage?, title: String, enabled: Bool = true) {
        super.init(frame: .zero)
        iconView.image = icon
        iconView.contentMode = .scaleAspectFit
        iconView.tintColor = WriterAITheme.Color.textPrimary
        labelView.text = title
        labelView.font = WriterAITheme.Font.tile()
        labelView.textColor = WriterAITheme.Color.textPrimary
        labelView.textAlignment = .center
        labelView.numberOfLines = 1

        backgroundColor = WriterAITheme.Color.surfaceCard
        layer.cornerRadius = WriterAITheme.Radius.card
        layer.masksToBounds = true

        addSubview(iconView)
        addSubview(labelView)
        iconView.translatesAutoresizingMaskIntoConstraints = false
        labelView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            iconView.centerXAnchor.constraint(equalTo: centerXAnchor),
            iconView.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            iconView.widthAnchor.constraint(equalToConstant: 24),
            iconView.heightAnchor.constraint(equalToConstant: 24),
            labelView.topAnchor.constraint(equalTo: iconView.bottomAnchor, constant: 6),
            labelView.centerXAnchor.constraint(equalTo: centerXAnchor),
            labelView.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor, constant: 4),
            labelView.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -4),
        ])

        self.tileEnabled = enabled
        addTarget(self, action: #selector(pressed), for: .touchDown)
        addTarget(self, action: #selector(released), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        addTarget(self, action: #selector(tapped), for: .touchUpInside)
    }

    override var intrinsicContentSize: CGSize { CGSize(width: 100, height: 80) }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    /// Tile availability flag; dims content but keeps the control touchable
    /// so disabled taps can surface the "暂未开放" toast (§3.7).
    var tileEnabled: Bool = true {
        didSet {
            let dim: CGFloat = tileEnabled ? 1 : 0.45
            iconView.alpha = dim
            labelView.alpha = dim
        }
    }

    @objc private func pressed() { if tileEnabled { writerAI_press(scale: 0.97) } }
    @objc private func released() { writerAI_release() }

    @objc private func tapped() {
        if !tileEnabled {
            WriterAIToast.show(NSLocalizedString("暂未开放", comment: "non-P0 tile toast"),
                               in: window)
        }
    }
}

// MARK: - §3.8 Selection menu item

/// 57×55.5pt menu item: icon 20pt + label 10pt, gap 4pt.
/// Selected state = `surfaceCard` + `radiusSmall`.
final class WriterAIMenuItemView: UIButton {
    private let iconView = UIImageView()
    private let labelView = UILabel()

    /// Availability flag: dims content but keeps the control touchable so
    /// disabled taps can surface the "暂未开放" toast (D4, same as TileCard).
    var itemEnabled: Bool = true {
        didSet {
            let dim: CGFloat = itemEnabled ? 1 : 0.45
            iconView.alpha = dim
            labelView.alpha = dim
        }
    }

    /// Tap callback; `enabled` reports the tap landed on an enabled item.
    var onTap: ((Bool) -> Void)?

    init(icon: UIImage?, title: String) {
        iconView.image = icon
        addTarget(self, action: #selector(tapped), for: .touchUpInside)
        iconView.contentMode = .scaleAspectFit
        iconView.tintColor = WriterAITheme.Color.textPrimary
        labelView.text = title
        labelView.font = WriterAITheme.Font.menu()
        labelView.textColor = WriterAITheme.Color.textPrimary
        labelView.textAlignment = .center
        labelView.numberOfLines = 1

        layer.cornerRadius = WriterAITheme.Radius.small

        addSubview(iconView)
        addSubview(labelView)
        iconView.translatesAutoresizingMaskIntoConstraints = false
        labelView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            iconView.centerXAnchor.constraint(equalTo: centerXAnchor),
            iconView.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            iconView.widthAnchor.constraint(equalToConstant: 20),
            iconView.heightAnchor.constraint(equalToConstant: 20),
            labelView.topAnchor.constraint(equalTo: iconView.bottomAnchor, constant: 4),
            labelView.centerXAnchor.constraint(equalTo: centerXAnchor),
            labelView.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor, constant: 2),
            labelView.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -2),
        ])

    }
    override var intrinsicContentSize: CGSize { CGSize(width: 57, height: 55.5) }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override var isSelected: Bool {
        didSet {
            backgroundColor = isSelected ? WriterAITheme.Color.surfaceCard : .clear
        }
    }

    @objc private func tapped() {
        onTap?(itemEnabled)
    }
}

// MARK: - §3.9 Language row

/// 48.5pt row: language name (16pt) + trailing 24pt checkmark (`primaryBlue` when selected).
final class WriterAILanguageRow: UIButton {
    private let checkView = UIImageView()

    init(languageName: String) {
        super.init(frame: .zero)
        var config = UIButton.Configuration.plain()
        config.contentHorizontalAlignment = .leading
        var title = AttributedString(languageName)
        title.font = WriterAITheme.Font.button()
        title.foregroundColor = WriterAITheme.Color.textPrimary
        config.attributedTitle = title
        configuration = config

        let symbol = UIImage(systemName: "checkmark",
                             withConfiguration: UIImage.SymbolConfiguration(pointSize: 24, weight: .medium))
        checkView.image = symbol
        checkView.tintColor = WriterAITheme.Color.primaryBlue
        checkView.isHidden = true
        checkView.contentMode = .scaleAspectFit
        addSubview(checkView)
        checkView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            checkView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            checkView.centerYAnchor.constraint(equalTo: centerYAnchor),
            checkView.widthAnchor.constraint(equalToConstant: 24),
            checkView.heightAnchor.constraint(equalToConstant: 24),
            heightAnchor.constraint(equalToConstant: 48.5),
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    override var isSelected: Bool {
        didSet { checkView.isHidden = !isSelected }
    }
}

// MARK: - §3.10 Popover container

/// 180×113pt floating container: white, 1pt `#CBD1D7` border, `radiusModal`,
/// `shadowFloat`. Slides up 8pt with 160ms ease-out fade-in.
final class WriterAIPopoverContainer: UIView {
    init() {
        super.init(frame: .zero)
        backgroundColor = .white
        layer.borderColor = WriterAITheme.Color.scrollbar.cgColor
        layer.borderWidth = 1
        layer.cornerRadius = WriterAITheme.Radius.modal
        writerAI_apply(WriterAITheme.Shadow.float)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    func show(in parent: UIView, animated: Bool = true) {
        alpha = 0
        transform = CGAffineTransform(translationX: 0, y: 8)
        parent.addSubview(self)
        guard animated else {
            alpha = 1
            transform = .identity
            return
        }
        UIView.animate(withDuration: WriterAITheme.Animation.floatIn, delay: 0,
                       options: .curveEaseOut) {
            self.alpha = 1
            self.transform = .identity
        }
    }
}

// MARK: - §3.11 Floating AI shortcut bar

/// 343×52pt floating bar pinned above the bottom safe area, fixed while
/// the document scrolls. Icon 32pt inside a 44pt touch target (C5).
final class WriterAIFloatingAIButton: UIButton {
    @objc init(icon: UIImage?, title: String? = nil) {
        super.init(frame: .zero)
        var config = UIButton.Configuration.filled()
        config.baseBackgroundColor = .white
        config.baseForegroundColor = WriterAITheme.Color.textPrimary
        config.image = icon?.withRenderingMode(.alwaysTemplate)
        config.imagePadding = 6
        if let title {
            var attributed = AttributedString(title)
            attributed.font = WriterAITheme.Font.body()
            attributed.foregroundColor = WriterAITheme.Color.textPrimary
            config.attributedTitle = attributed
        }
        configuration = config
        layer.cornerRadius = WriterAITheme.Radius.floating
        writerAI_apply(WriterAITheme.Shadow.float)
        addTarget(self, action: #selector(pressed), for: .touchDown)
        addTarget(self, action: #selector(released), for: [.touchUpInside, .touchUpOutside, .touchCancel])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    @objc private func pressed() { writerAI_press(scale: 0.97) }
    @objc private func released() { writerAI_release() }
}
