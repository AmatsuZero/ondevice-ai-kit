#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'

root = File.expand_path('..', __dir__)
directory = File.expand_path(ENV.fetch('ODAI_MULTIMODAL_BRIDGE_TEST_DIR', 'artifacts/verification/ios-multimodal-bridge'), root)
abort('Refusing to replace existing multimodal bridge test output') if File.exist?(directory) || File.symlink?(directory)
binding_module = ENV.fetch('ODAI_MULTIMODAL_BRIDGE_KMP_MODULE', 'ODAIKitBinding')
framework_root = { 'ODAIKitBinding' => 'kmp-binding', 'ODAIKitDemo' => 'demo/shared' }[binding_module]
abort('Unsupported multimodal bridge KMP module') unless framework_root
framework = File.join(root, framework_root, "build/bin/iosSimulatorArm64/debugFramework/#{binding_module}.framework")
sources = ['kmp-binding/apple/IOSMultimodalBridge.swift', 'kmp-binding/apple/IOSMultimodalInput.swift',
           'probes/ios-multimodal-bridge/IOSMultimodalBridgeTests.swift',
           'probes/ios-multimodal-bridge/IOSMultimodalVideoBridgeTests.swift']
if binding_module == 'ODAIKitDemo'
  sources += ['demo/iosApp/MultimodalInputStore.swift', 'demo/iosApp/SpeechInputStore.swift',
              'kmp-binding/apple/IOSSpeechAudioInput.swift', 'probes/ios-multimodal-bridge/MultimodalInputStoreTests.swift',
              'demo/iosApp/MultimodalHost.swift', 'probes/ios-multimodal-bridge/MultimodalHostTests.swift',
              'demo/iosApp/MultimodalFilePicker.swift', 'probes/ios-multimodal-bridge/MultimodalFilePickerTests.swift',
              'demo/iosApp/MultimodalModelHostFactory.swift', 'probes/ios-multimodal-bridge/MultimodalModelHostFactoryTests.swift',
              'demo/iosApp/MultimodalHostPreparation.swift', 'probes/ios-multimodal-bridge/MultimodalHostPreparationTests.swift',
              'probes/ios-multimodal-demo/AppAccessibility.swift', 'probes/ios-multimodal-bridge/AppAccessibilityTests.swift',
              'probes/ios-multimodal-demo/AppBackgroundObservation.swift', 'probes/ios-multimodal-bridge/AppBackgroundObservationTests.swift']
end
([framework, File.join(root, 'apple')] + sources.map { |s| File.join(root, s) }).each do |path|
  abort("Missing input: #{path}") unless File.exist?(path)
end
FileUtils.mkdir_p(File.dirname(directory))
Dir.mkdir(directory)
project = Xcodeproj::Project.new(File.join(directory, 'MultimodalBridgeTests.xcodeproj'))
target = project.new_target(:unit_test_bundle, 'MultimodalBridgeTests', :ios, '15.0')
package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
package.relative_path = File.join(root, 'apple')
project.root_object.package_references << package
products = ['ODAIKitMultimodal', 'ODAIKitCore']
products += ['ODAIKitModelStore'] if binding_module == 'ODAIKitDemo'
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
    'SWIFT_VERSION' => '5.0', 'PRODUCT_BUNDLE_IDENTIFIER' => 'io.ondeviceai.multimodal.bridge.tests',
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
