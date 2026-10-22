#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'

root = File.expand_path('..', __dir__)
directory = File.expand_path(ENV.fetch('ODAI_SPEECH_DEMO_DIR', 'artifacts/verification/speech-demo'), root)
abort('Refusing to replace existing speech Demo output') if File.exist?(directory) || File.symlink?(directory)
sources = %w[OnDeviceAIDemoApp DemoController IOSFaceBridge CameraSource CameraLifecycle PhotoSubmission
             SpeechHost SpeechFilePicker SpeechInputStore SpeechAudioSession].map { |name| "demo/iosApp/#{name}.swift" }
sources += ['kmp-binding/apple/IOSSpeechBridge.swift', 'kmp-binding/apple/IOSSpeechAudioInput.swift']
video_demo = ENV['ODAI_VIDEO_DEMO'] == '1'
multimodal_demo = ENV['ODAI_MULTIMODAL_DEMO'] == '1'
mlx_demo = ENV['ODAI_MULTIMODAL_MLX_DEMO'] == '1'
abort('MLX Demo requires ODAI_MULTIMODAL_DEMO=1') if mlx_demo && !multimodal_demo
abort('Multimodal Demo requires ODAI_VIDEO_DEMO=1') if multimodal_demo && !video_demo
if video_demo
  sources += ['demo/iosApp/VideoHost.swift', 'demo/iosApp/VideoInputStore.swift', 'demo/iosApp/VideoFilePicker.swift',
              'kmp-binding/apple/IOSVideoBridge.swift', 'kmp-binding/apple/IOSVideoInput.swift']
end
if multimodal_demo
  sources += ['demo/iosApp/MultimodalHost.swift', 'demo/iosApp/MultimodalInputStore.swift',
              'demo/iosApp/MultimodalFilePicker.swift', 'kmp-binding/apple/IOSMultimodalBridge.swift',
              'kmp-binding/apple/IOSMultimodalInput.swift']
end
if mlx_demo
  sources += ['demo/iosApp/MultimodalModelHostFactory.swift', 'demo/iosApp/MultimodalHostPreparation.swift',
              'demo/iosApp/MultimodalMLXHostFactory.swift']
end
frameworks = %w[iosSimulatorArm64 iosArm64].map do |platform|
  File.join(root, "demo/shared/build/bin/#{platform}/debugFramework/ODAIKitDemo.framework")
end
inputs = sources.map { |path| File.join(root, path) } + frameworks +
  [File.join(root, 'apple'), File.join(root, 'demo/iosApp/Info.plist'),
   File.join(root, 'demo/iosApp/UITests/SpeechDemoUITests.swift')]
inputs += [File.join(root, 'apple-mlx'), File.join(root, 'apple-mlx/Package.resolved')] if mlx_demo
inputs.each { |path| abort("Missing input: #{path}") unless File.exist?(path) }
FileUtils.mkdir_p(File.dirname(directory))
Dir.mkdir(directory)
project = Xcodeproj::Project.new(File.join(directory, 'SpeechDemo.xcodeproj'))
target = project.new_target(:application, 'SpeechDemo', :ios, mlx_demo ? '18.0' : '15.0')
package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
package.relative_path = File.join(root, 'apple')
project.root_object.package_references << package
products = %w[ODAIKitCore ODAIKitFace ODAIKitAppleVision ODAIKitSpeech ODAIKitAppleSpeech]
products += %w[ODAIKitVideo ODAIKitAppleVideo] if video_demo
products += ['ODAIKitMultimodal'] if multimodal_demo
mlx_package = nil
if mlx_demo
  mlx_package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
  mlx_package.relative_path = File.join(root, 'apple-mlx')
  project.root_object.package_references << mlx_package
  products += ['ODAIKitModelStore', 'ODAIKitAppleMLXVLM']
end
products.each do |name|
  dependency = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  dependency.product_name = name
  dependency.package = name == 'ODAIKitAppleMLXVLM' ? mlx_package : package
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
    'PRODUCT_BUNDLE_IDENTIFIER' => mlx_demo ? 'io.ondeviceai.multimodalmlxdemo' : 'io.ondeviceai.ondeviceaidemo',
    'SWIFT_VERSION' => '5.0', 'SWIFT_ACTIVE_COMPILATION_CONDITIONS' => '$(inherited) ODAI_DEMO_BINDING ODAI_SPEECH_DEMO' + (video_demo ? ' ODAI_VIDEO_DEMO' : '') + (multimodal_demo ? ' ODAI_MULTIMODAL_DEMO' : '') + (mlx_demo ? ' ODAI_MULTIMODAL_MLX_DEMO' : ''),
    'GENERATE_INFOPLIST_FILE' => 'YES', 'INFOPLIST_FILE' => File.join(root, 'demo/iosApp/Info.plist'),
    'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES', 'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES',
    'INFOPLIST_KEY_NSCameraUsageDescription' => '在设备本地检测人脸，不上传图像。',
    'INFOPLIST_KEY_NSSpeechRecognitionUsageDescription' => '在设备本地识别选择的音频文件或主动开始的麦克风语音，不上传音频。',
    'INFOPLIST_KEY_NSMicrophoneUsageDescription' => '仅在你主动开始语音识别时采集声音，在本机处理，不保存录音。',
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
ui = project.new_target(:ui_test_bundle, 'SpeechDemoUITests', :ios, '17.0')
ui.add_dependency(target)
ui.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, 'demo/iosApp/UITests/SpeechDemoUITests.swift')))
ui.build_configurations.each do |config|
  config.build_settings.merge!({
    'PRODUCT_BUNDLE_IDENTIFIER' => 'io.ondeviceai.ondeviceaidemo.uitests',
    'SWIFT_VERSION' => '5.0', 'GENERATE_INFOPLIST_FILE' => 'YES', 'ARCHS' => 'arm64',
    'TEST_TARGET_NAME' => 'SpeechDemo', 'CODE_SIGN_STYLE' => 'Automatic',
    'TARGETED_DEVICE_FAMILY' => '1,2'
  })
  team = ENV['ODAI_DEVELOPMENT_TEAM']
  config.build_settings['DEVELOPMENT_TEAM'] = team unless team.nil? || team.empty?
end
project.save
if mlx_demo
  pins = File.join(project.path, 'project.xcworkspace/xcshareddata/swiftpm')
  FileUtils.mkdir_p(pins)
  FileUtils.cp(File.join(root, 'apple-mlx/Package.resolved'), File.join(pins, 'Package.resolved'))
end
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(target)
scheme.set_launch_target(target)
scheme.add_build_target(ui)
scheme.add_test_target(ui)
scheme.save_as(project.path, target.name, true)
puts project.path
