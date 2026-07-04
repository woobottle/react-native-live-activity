import Foundation

#if canImport(ActivityKit)
import ActivityKit

/// Shared ActivityAttributes for the library's Live Activity.
///
/// This type is intentionally free of any React / bridging dependency so that it
/// can be compiled into BOTH the app target (via the pod) and the app's Widget
/// Extension target (which must render the Live Activity UI). ActivityKit matches
/// a running Activity to its Widget presentation by this attributes type and the
/// shape of `ContentState`, so the definition must stay identical on both sides.
///
/// Consuming apps that build their own Widget Extension should add THIS file to
/// their widget target (or mirror it exactly) rather than redefining a divergent
/// copy. See the README "iOS Widget Extension setup" section.
@available(iOS 16.1, *)
public struct LiveActivityAttributes: ActivityAttributes {
  public struct ContentState: Codable, Hashable {
    public var title: String
    public var subtitle: String?
    public var progress: Double?

    public init(title: String, subtitle: String?, progress: Double?) {
      self.title = title
      self.subtitle = subtitle
      self.progress = progress
    }
  }

  public init() {}
}
#endif
