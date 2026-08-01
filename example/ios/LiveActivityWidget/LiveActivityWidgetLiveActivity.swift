import ActivityKit
import WidgetKit
import SwiftUI

// Renders the library's `LiveActivityAttributes` on the Lock Screen and in the
// Dynamic Island. The `LiveActivityAttributes` type is shared with the library
// via target membership on `ios/LiveActivityAttributes.swift` — do not redefine
// a divergent copy here or ActivityKit will fail to match the running activity.
@available(iOS 16.1, *)
struct LiveActivityWidgetLiveActivity: Widget {
  var body: some WidgetConfiguration {
    ActivityConfiguration(for: LiveActivityAttributes.self) { context in
      // Lock Screen / banner presentation.
      LiveActivityLockScreenView(context: context)
        .padding(16)
        .activityBackgroundTint(Color.black.opacity(0.35))
        .activitySystemActionForegroundColor(Color.white)
    } dynamicIsland: { context in
      DynamicIsland {
        DynamicIslandExpandedRegion(.leading) {
          Image(systemName: "bell.badge.fill")
            .foregroundStyle(.tint)
            .padding(.leading, 4)
        }
        DynamicIslandExpandedRegion(.trailing) {
          LiveActivityStatusView(context: context, compact: true)
            .padding(.trailing, 4)
        }
        DynamicIslandExpandedRegion(.center) {
          VStack(alignment: .leading, spacing: 2) {
            Text(context.state.title)
              .font(.headline)
              .lineLimit(1)
            if let subtitle = context.state.subtitle {
              Text(subtitle)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            }
          }
        }
        DynamicIslandExpandedRegion(.bottom) {
          LiveActivityStatusView(context: context)
        }
      } compactLeading: {
        Image(systemName: "bell.badge.fill")
          .foregroundStyle(.tint)
      } compactTrailing: {
        LiveActivityStatusView(context: context, compact: true)
      } minimal: {
        Image(systemName: "bell.fill")
          .foregroundStyle(.tint)
      }
    }
  }
}

@available(iOS 16.1, *)
private struct LiveActivityLockScreenView: View {
  let context: ActivityViewContext<LiveActivityAttributes>

  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      HStack {
        Image(systemName: "bell.badge.fill")
          .foregroundStyle(.tint)
        Text(context.state.title)
          .font(.headline)
          .lineLimit(1)
        Spacer()
        LiveActivityStatusView(context: context, compact: true)
      }

      if let subtitle = context.state.subtitle {
        Text(subtitle)
          .font(.subheadline)
          .foregroundStyle(.secondary)
          .lineLimit(2)
      }

      if let progress = context.state.progress,
         context.state.timerStartAt == nil {
        ProgressView(value: progress)
          .tint(.white)
      }

      if context.state.timerStartAt != nil {
        LiveActivityStatusView(context: context)
          .font(.title2.bold())
      }
    }
  }
}

@available(iOS 16.1, *)
private struct LiveActivityStatusView: View {
  let context: ActivityViewContext<LiveActivityAttributes>
  var compact = false

  private var isCompleted: Bool {
    if #available(iOS 16.2, *) {
      return context.isStale || context.state.timerState == "completed"
    }
    return context.state.timerState == "completed"
  }

  var body: some View {
    Group {
      if isCompleted {
        Text(compact ? "달성!" : "10분 달성!")
      } else if
        let startAt = context.state.timerStartAt,
        let endAt = context.state.timerEndAt
      {
        Text(
          timerInterval: startAt...endAt,
          pauseTime: context.state.timerPauseAt,
          countsDown: true,
          showsHours: false
        )
        .monospacedDigit()
        .accessibilityLabel("미션 남은 시간")
      } else if let progress = context.state.progress {
        Text("\(Int((progress * 100).rounded()))%")
          .monospacedDigit()
      }
    }
    .font(compact ? .caption.bold() : .body.bold())
  }
}
