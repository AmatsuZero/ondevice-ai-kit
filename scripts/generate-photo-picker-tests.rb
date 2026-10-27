#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'

root = File.expand_path('..', __dir__)
directory = File.expand_path(ENV.fetch('ODAI_PHOTO_PICKER_TEST_DIR', 'artifacts/verification/ios-photo-picker'), root)
abort('Refusing to replace existing photo picker output') if File.exist?(directory) || File.symlink?(directory)
framework = File.join(root, 'demo/shared/build/bin/iosSimulatorArm64/debugFramework/ODAIKitDemo.framework')
sources = %w[DemoController IOSFaceBridge CameraSource CameraLifecycle PhotoSubmission].map { |name| "demo/iosApp/#{name}.swift" }
sources << 'probes/ios-photo-picker/PhotoPickerHost.swift'
test_source = 'probes/ios-photo-picker/PhotoPickerOrientationUITests.swift'
([framework, File.join(root, 'apple')] + (sources + [test_source]).map { |path| File.join(root, path) }).each do |path|
  abort("Missing input: #{path}") unless File.exist?(path)
end
FileUtils.mkdir_p(File.dirname(directory))
Dir.mkdir(directory)
project = Xcodeproj::Project.new(File.join(directory, 'PhotoPickerTests.xcodeproj'))
host = project.new_target(:application, 'PhotoPickerHost', :ios, '15.0')
tests = project.new_target(:ui_test_bundle, 'PhotoPickerTests', :ios, '15.0')
tests.add_dependency(host)
sources.each { |source| host.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, source))) }
tests.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, test_source)))
package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
package.relative_path = File.join(root, 'apple')
project.root_object.package_references << package
%w[ODAIKitCore ODAIKitFace ODAIKitAppleVision].each do |name|
  dependency = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  dependency.product_name = name
  dependency.package = package
  host.package_product_dependencies << dependency
  file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  file.product_ref = dependency
  host.frameworks_build_phase.files << file
end
host.frameworks_build_phase.add_file_reference(project.main_group.new_file(framework))
[host, tests].each do |target|
  target.build_configurations.each do |config|
    config.build_settings.merge!({
      'SWIFT_VERSION' => '5.0', 'GENERATE_INFOPLIST_FILE' => 'YES',
      'ARCHS' => 'arm64', 'SUPPORTED_PLATFORMS' => 'iphonesimulator',
      'CODE_SIGNING_ALLOWED' => 'NO', 'TARGETED_DEVICE_FAMILY' => '1,2',
      'PRODUCT_BUNDLE_IDENTIFIER' => "io.ondeviceai.validation.#{target.name.downcase}"
    })
    if target == host
      config.build_settings.merge!({
        'FRAMEWORK_SEARCH_PATHS' => ['$(inherited)', File.dirname(framework)],
        'OTHER_LDFLAGS' => ['$(inherited)', '-ObjC', '-lsqlite3'],
        'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES',
        'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES'
      })
    else
      config.build_settings['TEST_TARGET_NAME'] = host.name
    end
  end
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(host)
scheme.set_launch_target(host)
scheme.add_build_target(tests, false)
scheme.add_test_target(tests)
scheme.save_as(project.path, 'PhotoPickerTests', true)
puts project.path
