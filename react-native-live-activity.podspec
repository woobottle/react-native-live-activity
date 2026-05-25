require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-live-activity"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = "https://github.com/woobottle/react-native-live-activity"
  s.license      = { :type => "MIT" }
  s.authors      = { "woobottle" => "dkrnfls@gmail.com" }

  # Module loads on iOS 15.1+; ActivityKit features are gated to iOS 16.1+
  # at runtime so that consuming apps can still target lower iOS versions.
  s.platforms    = { :ios => "15.1" }
  s.source       = { :git => "https://github.com/woobottle/react-native-live-activity.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift}"
  s.swift_version = "5.0"

  s.dependency "React-Core"
end
