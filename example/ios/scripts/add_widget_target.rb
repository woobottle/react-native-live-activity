#!/usr/bin/env ruby
# Adds the LiveActivityWidget app-extension target to the example Xcode project.
# Idempotent: if the target already exists it is removed and recreated so the
# script can be re-run safely.
require 'xcodeproj'

PROJECT   = 'LiveActivityExample.xcodeproj'
APP_NAME  = 'LiveActivityExample'
WIDGET    = 'LiveActivityWidget'
APP_BUNDLE_ID = 'org.reactjs.native.example.LiveActivityExample'
WIDGET_BUNDLE_ID = "#{APP_BUNDLE_ID}.#{WIDGET}"
DEPLOY_TARGET = '16.1'

proj = Xcodeproj::Project.open(PROJECT)
app  = proj.native_targets.find { |t| t.name == APP_NAME }
raise "app target #{APP_NAME} not found" unless app

# --- clean up any previous run --------------------------------------------
existing = proj.native_targets.find { |t| t.name == WIDGET }
if existing
  # drop embed build files referencing the old product
  app.build_phases.grep(Xcodeproj::Project::Object::PBXCopyFilesBuildPhase).each do |ph|
    ph.files.dup.each do |bf|
      ph.remove_build_file(bf) if bf.display_name.to_s.include?(WIDGET)
    end
  end
  app.dependencies.dup.each do |dep|
    dep.remove_from_project if dep.target == existing
  end
  existing.remove_from_project
end
grp = proj.main_group[WIDGET]
grp.remove_from_project if grp

# --- create the widget extension target -----------------------------------
widget = proj.new_target(:app_extension, WIDGET, :ios, DEPLOY_TARGET)

# Build settings for both configs
widget.build_configurations.each do |cfg|
  s = cfg.build_settings
  s['PRODUCT_BUNDLE_IDENTIFIER']   = WIDGET_BUNDLE_ID
  s['PRODUCT_NAME']                = '$(TARGET_NAME)'
  s['INFOPLIST_FILE']              = "#{WIDGET}/Info.plist"
  s['GENERATE_INFOPLIST_FILE']     = 'NO'
  s['IPHONEOS_DEPLOYMENT_TARGET']  = DEPLOY_TARGET
  s['SWIFT_VERSION']               = '5.0'
  s['TARGETED_DEVICE_FAMILY']      = '1,2'
  s['CODE_SIGN_STYLE']             = 'Automatic'
  s['CURRENT_PROJECT_VERSION']     = '1'
  s['MARKETING_VERSION']           = '1.0'
  s['SKIP_INSTALL']                = 'YES'
  s['SWIFT_EMIT_LOC_STRINGS']      = 'YES'
  s['LD_RUNPATH_SEARCH_PATHS']     = ['$(inherited)', '@executable_path/Frameworks', '@executable_path/../../Frameworks']
  s['INFOPLIST_KEY_CFBundleDisplayName'] = WIDGET
end

# --- source files ----------------------------------------------------------
group = proj.main_group.new_group(WIDGET, WIDGET)
widget_sources = [
  "#{WIDGET}/#{WIDGET}Bundle.swift",
  "#{WIDGET}/#{WIDGET}LiveActivity.swift",
]
widget_sources.each do |path|
  ref = group.new_reference(File.basename(path))
  ref.path = path            # keep path relative to project root
  widget.add_file_references([ref])
end

# Shared ActivityAttributes from the library (compiled into the widget too).
shared = group.new_reference('../../ios/LiveActivityAttributes.swift')
shared.name = 'LiveActivityAttributes.swift'
widget.add_file_references([shared])

# Info.plist reference (not compiled, just for navigation)
group.new_reference("#{WIDGET}/Info.plist")

# --- embed extension into the app -----------------------------------------
app.add_dependency(widget)

embed = app.build_phases.grep(Xcodeproj::Project::Object::PBXCopyFilesBuildPhase)
           .find { |p| p.name == 'Embed Foundation Extensions' }
unless embed
  embed = proj.new(Xcodeproj::Project::Object::PBXCopyFilesBuildPhase)
  embed.name = 'Embed Foundation Extensions'
  embed.symbol_dst_subfolder_spec = :plug_ins
  app.build_phases << embed
end
bf = embed.add_file_reference(widget.product_reference)
bf.settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }

proj.save
puts "OK: added #{WIDGET} target"
puts "  bundle id: #{WIDGET_BUNDLE_ID}"
puts "  targets now: #{proj.native_targets.map(&:name).join(', ')}"
puts "  widget sources: #{widget.source_build_phase.files.map { |f| f.display_name }.join(', ')}"
puts "  app embeds: #{embed.files.map { |f| f.display_name }.join(', ')}"
