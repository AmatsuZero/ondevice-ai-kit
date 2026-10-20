#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'

root = File.expand_path('..', __dir__)
directory = File.expand_path(ENV.fetch('ODAI_SPEECH_DEMO_DIR', 'artifacts/verification/speech-demo'), root)
abort('Refusing to replace existing speech Demo output') if File.exist?(directory) || File.symlink?(directory)
sources = %w[OnDeviceAIDemoApp DemoController IOSFaceBridge CameraSource CameraLifecycle PhotoSubmission
             SpeechHost SpeechFilePicker SpeechInputStore].map { |name| "demo/iosApp/#{name}.swift" }
sources += ['kmp-binding/apple/IOSSpeechBridge.swift', 'kmp-binding/apple/IOSSpeechAudioInput.swift']
frameworks = %w[iosSimulatorArm64 iosArm64].map do |platform|
  File.join(root, "demo/shared/build/bin/#{platform}/debugFramework/ODAIKitDemo.framework")
end
inputs = sources.map { |path| File.join(root, path) } + frameworks +
  [File.join(root, 'apple'), File.join(root, 'demo/iosApp/Info.plist')]
inputs.each { |path| abort("Missing input: #{path}") unless File.exist?(path) }
FileUtils.mkdir_p(File.dirname(directory))
Dir.mkdir(directory)
project = Xcodeproj::Project.new(File.join(directory, 'SpeechDemo.xcodeproj'))
target = project.new_target(:application, 'SpeechDemo', :ios, '15.0')
package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
package.relative_path = File.join(root, 'apple')
project.root_object.package_references << package
%w[ODAIKitCore ODAIKitFace ODAIKitAppleVision ODAIKitSpeech ODAIKitAppleSpeech].each do |name|
  dependency = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  dependency.product_name = name
  dependency.package = package
  target.package_product_dependencies << dependency
  file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  file.product_ref = dependency
  target.frameworks_build_phase.files << file
end
sources.each { |source| target.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, source))) }
framework_parent = File.join(root, 'demo/shared/build/bin/$(ODAI_KONAN_TARGET)/debugFramework')
target.frameworks_build_phase.add_file_reference(project.main_group.new_file(File.join(framework_parent, 'ODAIKitDemo.framework')))
target.build_configurations.each do |config|
  config.build_settings.merge!({
    'PRODUCT_BUNDLE_IDENTIFIER' => 'io.ondeviceai.ondeviceaidemo',
    'SWIFT_VERSION' => '5.0', 'SWIFT_ACTIVE_COMPILATION_CONDITIONS' => '$(inherited) ODAI_DEMO_BINDING ODAI_SPEECH_DEMO',
    'GENERATE_INFOPLIST_FILE' => 'YES', 'INFOPLIST_FILE' => File.join(root, 'demo/iosApp/Info.plist'),
    'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES', 'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES',
    'INFOPLIST_KEY_NSCameraUsageDescription' => '在设备本地检测人脸，不上传图像。',
    'INFOPLIST_KEY_NSSpeechRecognitionUsageDescription' => '在设备本地识别主动选择的音频文件，不上传音频。',
    'TARGETED_DEVICE_FAMILY' => '1,2', 'CODE_SIGN_STYLE' => 'Automatic', 'ARCHS' => 'arm64',
    'ODAI_KONAN_TARGET[sdk=iphonesimulator*]' => 'iosSimulatorArm64',
    'ODAI_KONAN_TARGET[sdk=iphoneos*]' => 'iosArm64',
    'FRAMEWORK_SEARCH_PATHS' => ['$(inherited)', framework_parent],
    'OTHER_LDFLAGS' => ['$(inherited)', '-ObjC', '-lsqlite3'],
    'ODAI_SDK_COMMIT' => 'working-tree'
  })
  team = ENV['ODAI_DEVELOPMENT_TEAM']
  config.build_settings['DEVELOPMENT_TEAM'] = team unless team.nil? || team.empty?
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(target)
scheme.set_launch_target(target)
scheme.save_as(project.path, target.name, true)
puts project.path
