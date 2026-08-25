import UIKit

/// 审阅 tab — Android 5 项（FPC:2092-2103），通用行布局（无独立 Figma 帧）。
/// 拼写检查 / 追踪修订(开关) / 显示修订(开关) / 接受修订 / 拒绝修订.
@objc final class WriterReviewTabView: UIView {
    private let onCommand: (String, Bool) -> Void
    private let trackSwitch = UISwitch()
    private let showSwitch = UISwitch()

    @objc init(onCommand: @escaping (String, Bool) -> Void) {
        self.onCommand = onCommand
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
        stack.addArrangedSubview(row(title: "拼写检查", key: "spell", toggle: nil))
        trackSwitch.onTintColor = WriterAITheme.Color.primaryBlue
        trackSwitch.addTarget(self, action: #selector(trackChanged), for: .valueChanged)
        stack.addArrangedSubview(row(title: "追踪修订", key: "track", toggle: trackSwitch))
        showSwitch.onTintColor = WriterAITheme.Color.primaryBlue
        showSwitch.isOn = true
        showSwitch.addTarget(self, action: #selector(showChanged), for: .valueChanged)
        stack.addArrangedSubview(row(title: "显示修订", key: "show", toggle: showSwitch))
        stack.addArrangedSubview(row(title: "接受修订", key: "accept", toggle: nil))
        stack.addArrangedSubview(row(title: "拒绝修订", key: "reject", toggle: nil))
    }

    private func row(title: String, key: String, toggle: UISwitch?) -> UIView {
        let label = UILabel()
        label.text = title
        label.font = WriterAITheme.Font.body()
        label.textColor = WriterAITheme.Color.textPrimary

        let leading = toggle ?? {
            let b = UIButton(type: .system)
            b.setTitle("", for: .normal)
            return b
        }()
        let row = UIStackView(arrangedSubviews: [label, UIView(), leading])
        row.axis = .horizontal
        row.alignment = .center
        let container = UIView()
        container.backgroundColor = WriterAITheme.Color.surfaceCard
        container.layer.cornerRadius = WriterAITheme.Radius.card
        row.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(row)
        NSLayoutConstraint.activate([
            row.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 20),
            row.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -20),
            row.centerYAnchor.constraint(equalTo: container.centerYAnchor),
            container.heightAnchor.constraint(equalToConstant: 112),
        ])
        if toggle == nil {
            let tap = UITapGestureRecognizer(target: self, action: #selector(rowTapped(_:)))
            tap.accessibilityHint = key
            container.addGestureRecognizer(tap)
        }
        return container
    }

    @objc private func rowTapped(_ gesture: UITapGestureRecognizer) {
        if let key = gesture.accessibilityHint {
            onCommand(key, false)
        }
    }

    @objc private func trackChanged() { onCommand("track", trackSwitch.isOn) }
    @objc private func showChanged() { onCommand("show", showSwitch.isOn) }
}
