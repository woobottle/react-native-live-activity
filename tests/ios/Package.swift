// swift-tools-version:5.9
import PackageDescription

// 라이브러리의 순수 파싱 로직만 컴파일하는 테스트 전용 패키지.
// `ios/LiveActivityContentParser.swift`는 React/ActivityKit을 import 하지 않으므로
// Xcode 프로젝트 없이 macOS에서 `swift test`로 바로 돌릴 수 있다.
// SwiftPM은 패키지 루트 밖의 소스를 직접 참조하지 못해 심볼릭 링크로 끌어온다.
let package = Package(
  name: "LiveActivityCore",
  platforms: [.macOS(.v12)],
  targets: [
    .target(name: "LiveActivityCore", path: "Sources/LiveActivityCore"),
    .testTarget(
      name: "LiveActivityCoreTests",
      dependencies: ["LiveActivityCore"],
      path: "Tests/LiveActivityCoreTests"
    ),
  ]
)
