#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'

root = File.expand_path('..', __dir__)
directory = File.expand_path(ENV.fetch('ODAI_CAMERA_LIFECYCLE_TEST_DIR', 'artifacts/verification/ios-camera-lifecycle'), root)
abort('Refusing to replace existing camera lifecycle test output') if File.exist?(directory) || File.symlink?(directory)
device = ENV['ODAI_CAMERA_LIFECYCLE_DEVICE'] == '1'
sustained = ENV['ODAI_CAMERA_LIFECYCLE_SUSTAINED'] == '1'
abort('Sustained camera validation requires physical-device mode') if sustained && !device
platform = device ? 'iosArm64' : 'iosSimulatorArm64'
framework = File.join(root, "demo/shared/build/bin/#{platform}/debugFramework/ODAIKitDemo.framework")
sources = %w[DemoController IOSFaceBridge CameraSource CameraLifecycle PhotoSubmission].map { |name| "demo/iosApp/#{name}.swift" }
sources += %w[CameraOwnerReleaseTests CameraSourceCloseTests CameraHardwareReleaseTests].map { |name| "probes/ios-camera-lifecycle/#{name}.swift" }
sources << 'probes/ios-camera-lifecycle/CameraControllerSustainedTests.swift' if sustained
([framework, File.join(root, 'apple')] + sources.map { |source| File.expand_path(source, root) }).each do |path|
  abort("Missing input: #{path}") unless File.exist?(path)
end
FileUtils.mkdir_p(File.dirname(directory))
Dir.mkdir(directory)
project = Xcodeproj::Project.new(File.join(directory, 'CameraLifecycleTests.xcodeproj'))
host = nil
if device
  host = project.new_target(:application, 'CameraLifecycleHost', :ios, '15.0')
  host_source = File.join(directory, 'CameraLifecycleHost.swift')
  File.write(host_source, <<~SWIFT)
    import SwiftUI
    @main struct CameraLifecycleHost: App {
      var body: some Scene { WindowGroup { Text("Camera lifecycle verification") } }
    }
  SWIFT
  host.source_build_phase.add_file_reference(project.main_group.new_file(host_source))
  host.build_configurations.each do |config|
    config.build_settings.merge!({
      'SWIFT_VERSION' => '5.0', 'PRODUCT_BUNDLE_IDENTIFIER' => 'io.ondeviceai.ondeviceaidemo',
      'GENERATE_INFOPLIST_FILE' => 'YES', 'ARCHS' => 'arm64', 'SUPPORTED_PLATFORMS' => 'iphoneos',
      'INFOPLIST_KEY_NSCameraUsageDescription' => '在设备本地验证相机关闭，不保存或上传画面。',
      'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES',
      'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES', 'CODE_SIGN_STYLE' => 'Automatic'
    })
  end
end
target = project.new_target(:unit_test_bundle, 'CameraLifecycleTests', :ios, '15.0')
target.add_dependency(host) if host
package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
package.relative_path = File.join(root, 'apple')
project.root_object.package_references << package
%w[ODAIKitCore ODAIKitFace ODAIKitAppleVision].each do |name|
  dependency = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  dependency.product_name = name
  dependency.package = package
  target.package_product_dependencies << dependency
  file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  file.product_ref = dependency
  target.frameworks_build_phase.files << file
end
sources.each { |source| target.source_build_phase.add_file_reference(project.main_group.new_file(File.expand_path(source, root))) }
target.frameworks_build_phase.add_file_reference(project.main_group.new_file(framework))
target.build_configurations.each do |config|
  config.build_settings.merge!({
    'SWIFT_VERSION' => '5.0',
    'PRODUCT_BUNDLE_IDENTIFIER' => device ? 'io.ondeviceai.ondeviceaidemo.uitests' : 'io.ondeviceai.camera.lifecycle.tests',
    'GENERATE_INFOPLIST_FILE' => 'YES', 'ARCHS' => 'arm64', 'SUPPORTED_PLATFORMS' => device ? 'iphoneos' : 'iphonesimulator',
    'FRAMEWORK_SEARCH_PATHS' => ['$(inherited)', File.dirname(framework)],
    'OTHER_LDFLAGS' => ['$(inherited)', '-ObjC', '-lsqlite3'],
    'ENABLE_TESTABILITY' => 'YES', 'LD_RUNPATH_SEARCH_PATHS' => ['$(inherited)', '@loader_path/Frameworks']
  })
  if device
    config.build_settings.merge!({
      'CODE_SIGN_STYLE' => 'Automatic',
      'TEST_HOST' => '$(BUILT_PRODUCTS_DIR)/CameraLifecycleHost.app/CameraLifecycleHost',
      'BUNDLE_LOADER' => '$(TEST_HOST)'
    })
  else
    config.build_settings['CODE_SIGNING_ALLOWED'] = 'NO'
  end
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(host) if host
scheme.add_build_target(target)
scheme.add_test_target(target)
scheme.save_as(project.path, target.name, true)
puts project.path
