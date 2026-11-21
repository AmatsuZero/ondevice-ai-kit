#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'

# Isolated local Files source. Assets are transferred separately; never embed a portrait.
directory = File.expand_path(ENV.fetch('ODAI_FILES_SOURCE_DIR'))
abort('Refusing to replace existing source app output') if File.exist?(directory)
FileUtils.mkdir_p(directory)
File.write(File.join(directory, 'FilesSource.swift'), <<~SWIFT)
  import SwiftUI
  @main struct IdentityFilesSource: App {
      var body: some Scene {
          WindowGroup {
              Text("Local Files validation source. Approved inputs are transferred separately.")
                  .padding()
          }
      }
  }
SWIFT
Xcodeproj::Plist.write_to_path({
  'CFBundleExecutable' => '$(EXECUTABLE_NAME)',
  'CFBundleIdentifier' => '$(PRODUCT_BUNDLE_IDENTIFIER)',
  'CFBundleName' => 'IdentityFilesSource', 'CFBundleDisplayName' => 'IdentityInput',
  'CFBundlePackageType' => 'APPL', 'CFBundleVersion' => '1', 'CFBundleShortVersionString' => '1.0',
  'LSRequiresIPhoneOS' => true, 'UILaunchScreen' => {},
  'UIFileSharingEnabled' => true, 'LSSupportsOpeningDocumentsInPlace' => true,
  'UIApplicationSceneManifest' => { 'UIApplicationSupportsMultipleScenes' => false }
}, File.join(directory, 'Info.plist'))
project = Xcodeproj::Project.new(File.join(directory, 'IdentityFilesSource.xcodeproj'))
target = project.new_target(:application, 'IdentityFilesSource', :ios, '15.0')
target.source_build_phase.add_file_reference(project.main_group.new_file(File.join(directory, 'FilesSource.swift')))
target.build_configurations.each do |config|
  config.build_settings.merge!({
    'SWIFT_VERSION' => '5.0', 'PRODUCT_BUNDLE_IDENTIFIER' => 'io.ondeviceai.fixture.identityfiles',
    'INFOPLIST_FILE' => File.join(directory, 'Info.plist'), 'GENERATE_INFOPLIST_FILE' => 'NO',
    'TARGETED_DEVICE_FAMILY' => '1,2', 'SUPPORTED_PLATFORMS' => 'iphoneos',
    'CODE_SIGN_STYLE' => 'Automatic', 'ENABLE_USER_SCRIPT_SANDBOXING' => 'YES'
  })
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(target); scheme.set_launch_target(target)
scheme.save_as(project.path, target.name, true)
puts project.path
