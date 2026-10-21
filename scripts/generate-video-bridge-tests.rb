#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'

root = File.expand_path('..', __dir__)
directory = File.expand_path(ENV.fetch('ODAI_VIDEO_BRIDGE_TEST_DIR', 'artifacts/verification/ios-video-bridge'), root)
abort('Refusing to replace existing video bridge test output') if File.exist?(directory) || File.symlink?(directory)
binding_module = ENV.fetch('ODAI_VIDEO_BRIDGE_KMP_MODULE', 'ODAIKitBinding')
framework_root = { 'ODAIKitBinding' => 'kmp-binding', 'ODAIKitDemo' => 'demo/shared' }[binding_module]
abort('Unsupported video bridge KMP module') unless framework_root
framework = File.join(root, framework_root, "build/bin/iosSimulatorArm64/debugFramework/#{binding_module}.framework")
sources = ['kmp-binding/apple/IOSVideoBridge.swift', 'kmp-binding/apple/IOSVideoInput.swift',
           'probes/ios-video-bridge/IOSVideoBridgeTests.swift']
if binding_module == 'ODAIKitDemo'
  sources += ['demo/iosApp/VideoHost.swift', 'demo/iosApp/VideoInputStore.swift', 'demo/iosApp/VideoFilePicker.swift',
              'demo/iosApp/SpeechInputStore.swift', 'kmp-binding/apple/IOSSpeechAudioInput.swift',
              'probes/ios-video-bridge/VideoHostTests.swift']
end
([framework, File.join(root, 'apple')] + sources.map { |s| File.join(root, s) }).each do |path|
  abort("Missing input: #{path}") unless File.exist?(path)
end
FileUtils.mkdir_p(File.dirname(directory))
Dir.mkdir(directory)
project = Xcodeproj::Project.new(File.join(directory, 'VideoBridgeTests.xcodeproj'))
target = project.new_target(:unit_test_bundle, 'VideoBridgeTests', :ios, '15.0')
package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
package.relative_path = File.join(root, 'apple')
project.root_object.package_references << package
products = ['ODAIKitVideo', 'ODAIKitCore']
products << 'ODAIKitAppleVideo' if binding_module == 'ODAIKitDemo'
products.each do |name|
  dependency = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  dependency.product_name = name
  dependency.package = package
  target.package_product_dependencies << dependency
  file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  file.product_ref = dependency
  target.frameworks_build_phase.files << file
end
sources.each { |path| target.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, path))) }
target.frameworks_build_phase.add_file_reference(project.main_group.new_file(framework))
target.build_configurations.each do |config|
  config.build_settings.merge!({
    'SWIFT_ACTIVE_COMPILATION_CONDITIONS' => binding_module == 'ODAIKitDemo' ? '$(inherited) ODAI_DEMO_BINDING' : '$(inherited)',
    'SWIFT_VERSION' => '5.0', 'PRODUCT_BUNDLE_IDENTIFIER' => 'io.ondeviceai.video.bridge.tests',
    'GENERATE_INFOPLIST_FILE' => 'YES', 'ARCHS' => 'arm64', 'SUPPORTED_PLATFORMS' => 'iphonesimulator',
    'FRAMEWORK_SEARCH_PATHS' => ['$(inherited)', File.dirname(framework)],
    'OTHER_LDFLAGS' => ['$(inherited)', '-ObjC', '-lsqlite3'], 'CODE_SIGNING_ALLOWED' => 'NO',
    'ENABLE_TESTABILITY' => 'YES', 'LD_RUNPATH_SEARCH_PATHS' => ['$(inherited)', '@loader_path/Frameworks']
  })
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(target)
scheme.add_test_target(target)
scheme.save_as(project.path, target.name, true)
puts project.path
