#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'

root = File.expand_path('..', __dir__)
directory = File.expand_path(ENV.fetch('ODAI_PHOTO_SANDBOX_DEVICE_TEST_DIR', 'artifacts/verification/p4-picker-sandbox-device'), root)
abort('Refusing to replace existing sandbox device output') if File.exist?(directory) || File.symlink?(directory)
fixture_dir = File.expand_path(ENV.fetch('ODAI_PHOTO_PICKER_FIXTURE_DIR', 'artifacts/verification/p4-picker-sandbox-device/fixtures'), root)
framework = File.join(root, 'demo/shared/build/bin/iosArm64/debugFramework/ODAIKitDemo.framework')
sources = %w[DemoController IOSFaceBridge CameraSource CameraLifecycle PhotoSubmission].map { |name| "demo/iosApp/#{name}.swift" }
sources << 'probes/ios-photo-picker/PhotoSandboxDeviceHost.swift'
test_source = 'probes/ios-photo-picker/PhotoSandboxDeviceUITests.swift'
inputs = [framework, File.join(root, 'apple'), fixture_dir] + (sources + [test_source]).map { |path| File.join(root, path) }
inputs.each { |path| abort("Missing input: #{path}") unless File.exist?(path) }
fixtures = Dir[File.join(fixture_dir, 'exif-*.jpg')].sort
abort('Expected exif-2.jpg, exif-6.jpg and exif-7.jpg') unless fixtures.map { |path| File.basename(path) } == %w[exif-2.jpg exif-6.jpg exif-7.jpg]

FileUtils.mkdir_p(File.dirname(directory))
Dir.mkdir(directory)
project = Xcodeproj::Project.new(File.join(directory, 'PhotoSandboxDeviceTests.xcodeproj'))
host = project.new_target(:application, 'PhotoSandboxDeviceHost', :ios, '15.0')
tests = project.new_target(:ui_test_bundle, 'PhotoSandboxDeviceTests', :ios, '15.0')
tests.add_dependency(host)
sources.each { |source| host.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, source))) }
tests.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, test_source)))
fixtures.each { |fixture| host.resources_build_phase.add_file_reference(project.main_group.new_file(fixture)) }

package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
package.relative_path = File.join(root, 'apple')
project.root_object.package_references << package
%w[ODAIKitCore ODAIKitFace ODAIKitAppleVision].each do |name|
  dependency = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  dependency.product_name = name
  dependency.package = package
  host.package_product_dependencies << dependency
  build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  build_file.product_ref = dependency
  host.frameworks_build_phase.files << build_file
end

framework_ref = project.main_group.new_file(framework)
host.frameworks_build_phase.add_file_reference(framework_ref)
embed = host.new_copy_files_build_phase('Embed Frameworks')
embed.dst_subfolder_spec = '10'
embed_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
embed_file.file_ref = framework_ref
embed_file.settings = { 'ATTRIBUTES' => ['CodeSignOnCopy', 'RemoveHeadersOnCopy'] }
embed.files << embed_file

[host, tests].each do |target|
  target.build_configurations.each do |config|
    config.build_settings.merge!(
      'SWIFT_VERSION' => '5.0', 'GENERATE_INFOPLIST_FILE' => 'YES', 'ARCHS' => 'arm64',
      'SUPPORTED_PLATFORMS' => 'iphoneos', 'SDKROOT' => 'iphoneos', 'TARGETED_DEVICE_FAMILY' => '1,2',
      'CODE_SIGN_STYLE' => 'Automatic', 'CODE_SIGN_IDENTITY' => 'Apple Development',
      'DEVELOPMENT_TEAM' => ENV.fetch('ODAI_DEVELOPMENT_TEAM', '4X996UV8LC'),
      'PRODUCT_BUNDLE_IDENTIFIER' => "io.ondeviceai.validation.#{target.name.downcase}"
    )
    if target == host
      config.build_settings.merge!(
        'FRAMEWORK_SEARCH_PATHS' => ['$(inherited)', File.dirname(framework)],
        'LD_RUNPATH_SEARCH_PATHS' => ['$(inherited)', '@executable_path/Frameworks'],
        'OTHER_LDFLAGS' => ['$(inherited)', '-ObjC', '-lsqlite3'],
        'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES',
        'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES'
      )
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
scheme.save_as(project.path, 'PhotoSandboxDeviceTests', true)
puts project.path
