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
      LiveActivityLockScreenView(state: context.state)
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
          if let progress = context.state.progress {
            Text("\(Int((progress * 100).rounded()))%")
              .font(.caption).bold()
              .monospacedDigit()
              .padding(.trailing, 4)
          }
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
          if let progress = context.state.progress {
            ProgressView(value: progress)
              .tint(.white)
          }
        }
      } compactLeading: {
        Image(systemName: "bell.badge.fill")
          .foregroundStyle(.tint)
      } compactTrailing: {
        if let progress = context.state.progress {
          Text("\(Int((progress * 100).rounded()))%")
            .font(.caption2)
            .monospacedDigit()
        }
      } minimal: {
        Image(systemName: "bell.fill")
          .foregroundStyle(.tint)
      }
    }
  }
}

@available(iOS 16.1, *)
private struct LiveActivityLockScreenView: View {
  let state: LiveActivityAttributes.ContentState

  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      HStack {
        Image(systemName: "bell.badge.fill")
          .foregroundStyle(.tint)
        Text(state.title)
          .font(.headline)
          .lineLimit(1)
        Spacer()
        if let progress = state.progress {
          Text("\(Int((progress * 100).rounded()))%")
            .font(.subheadline).bold()
            .monospacedDigit()
        }
      }

      if let subtitle = state.subtitle {
        Text(subtitle)
          .font(.subheadline)
          .foregroundStyle(.secondary)
          .lineLimit(2)
      }

      if let progress = state.progress {
        ProgressView(value: progress)
          .tint(.white)
      }
    }
  }
}
