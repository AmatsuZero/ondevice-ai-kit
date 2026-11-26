#!/usr/bin/env ruby
require 'xcodeproj'
require_relative 'configure-openvoice-demo'
require 'fileutils'
require 'json'
require 'digest'
require 'find'

def notice_snapshot(directory)
  abort('Notice input must be a directory, not a symlink') unless File.directory?(directory) && !File.symlink?(directory)
  files = {}
  Find.find(directory) do |path|
    stat = File.lstat(path)
    abort("Unsupported notice input: #{path}") unless stat.directory? || stat.file?
    files[path.delete_prefix(directory + File::SEPARATOR)] = File.binread(path) if stat.file?
  end
  begin
    index = JSON.parse(files.fetch('NOTICE-INDEX.json'))
  rescue KeyError, JSON::ParserError => error
    abort("Invalid notice index: #{error.message}")
  end
  unless index.is_a?(Hash) && index['schemaVersion'] == 1 && index['files'].is_a?(Array) && !index['files'].empty?
    abort('Notice index requires schemaVersion 1 and nonempty files')
  end
  expected = ['NOTICE-INDEX.json']
  index['files'].each do |entry|
    abort('Invalid notice entry') unless entry.is_a?(Hash)
    path, hash = entry.values_at('path', 'sha256')
    unless path.is_a?(String) && !path.empty? && !path.include?("\0") && !path.include?('\\') &&
           path.split('/', -1).none? { |part| ['', '.', '..'].include?(part) } &&
           hash.is_a?(String) && hash.match?(/\A[0-9a-f]{64}\z/)
      abort('Unsafe notice path or invalid SHA256')
    end
    abort("Duplicate notice path: #{path}") if expected.include?(path)
    abort("Missing or changed notice: #{path}") unless files.key?(path) && Digest::SHA256.hexdigest(files[path]) == hash
    expected << path
  end
  abort('Notice file set differs from index') unless files.keys.sort == expected.sort
  files
end

root = File.expand_path('..', __dir__)
directory = File.expand_path(ENV.fetch('ODAI_SPEECH_DEMO_DIR', 'artifacts/verification/speech-demo'), root)
abort('Refusing to replace existing speech Demo output') if File.exist?(directory) || File.symlink?(directory)
sources = %w[OnDeviceAIDemoApp DemoController IOSFaceBridge CameraSource CameraLifecycle PhotoSubmission
             SpeechHost SpeechFilePicker SpeechInputStore SpeechAudioSession].map { |name| "demo/iosApp/#{name}.swift" }
sources += ['kmp-binding/apple/IOSSpeechBridge.swift', 'kmp-binding/apple/IOSSpeechAudioInput.swift']
video_demo = ENV['ODAI_VIDEO_DEMO'] == '1'
voice_effects_demo = ENV['ODAI_VOICE_EFFECTS_DEMO'] == '1'
if voice_effects_demo
  sources += ['demo/iosApp/VoiceEffectsHost.swift', 'demo/iosApp/VoiceEffectsInputStore.swift',
              'demo/iosApp/VoiceEffectsWave.swift', 'kmp-binding/apple/IOSVoiceEffectsBridge.swift']
end
multimodal_demo = ENV['ODAI_MULTIMODAL_DEMO'] == '1'
mlx_demo = ENV['ODAI_MULTIMODAL_MLX_DEMO'] == '1'
wemm_video_demo = ENV['ODAI_WEMM_VIDEO_DEMO'] == '1'
wemm_ui_fixtures = ENV['ODAI_WEMM_UI_FIXTURES'] == '1'
abort('ODAI_WEMM_UI_FIXTURES requires ODAI_WEMM_VIDEO_DEMO=1') if wemm_ui_fixtures && !wemm_video_demo
identity_demo = ENV['ODAI_OPENCV_IDENTITY_DEMO'] == '1'
validation = ENV['ODAI_MULTIMODAL_DEMO_VALIDATION'] == '1'
validation_commit = ENV.fetch('ODAI_VALIDATION_SOURCE_COMMIT', '')
abort('Demo validation requires ODAI_MULTIMODAL_MLX_DEMO=1') if validation && !mlx_demo
abort('Demo validation requires full source commit') if validation && !validation_commit.match?(/\A[0-9a-f]{40}\z/)
abort('MLX Demo requires ODAI_MULTIMODAL_DEMO=1') if mlx_demo && !multimodal_demo
abort('W6 WeMM video Demo requires ODAI_MULTIMODAL_DEMO=1') if wemm_video_demo && !multimodal_demo
abort('Multimodal Demo requires ODAI_VIDEO_DEMO=1') if multimodal_demo && !video_demo
if video_demo
  sources += ['demo/iosApp/VideoHost.swift', 'demo/iosApp/VideoInputStore.swift', 'demo/iosApp/VideoFilePicker.swift',
              'kmp-binding/apple/IOSVideoBridge.swift', 'kmp-binding/apple/IOSVideoInput.swift']
end
if multimodal_demo
  sources += ['demo/iosApp/MultimodalHost.swift', 'demo/iosApp/MultimodalInputStore.swift',
              'demo/iosApp/MultimodalFilePicker.swift', 'kmp-binding/apple/IOSMultimodalBridge.swift',
              'kmp-binding/apple/IOSMultimodalInput.swift', 'demo/iosApp/WeMMVideoPreparation.swift']
end
sources += ['demo/iosApp/MultimodalModelHostFactory.swift', 'demo/iosApp/MultimodalHostPreparation.swift'] if mlx_demo || wemm_video_demo
if mlx_demo
  sources += ['demo/iosApp/MultimodalMLXHostFactory.swift']
end
if wemm_video_demo
  sources += ['demo/iosApp/WeMMVideoModel.swift', 'demo/iosApp/WeMMVideoBackend.swift',
              'demo/iosApp/WeMMVideoDemoHost.swift',
              'demo/iosApp/PlaybackEnhancementHost.swift', 'demo/iosApp/PlaybackEnhancementMetalRenderer.swift',
              'demo/iosApp/PlaybackEnhancementSR.swift', 'demo/iosApp/PlaybackEnhancementSystemSR.swift',
              'demo/iosApp/PlaybackFrameChoice.swift', 'demo/iosApp/PlaybackEnhancementShaders.metal',
              'demo/iosApp/PlaybackEnhancementCuNNy.metal', 'demo/iosApp/PlaybackEnhancementSpatial.metal']
end
if identity_demo
  sources += ['demo/iosApp/IdentityOpenCvHost.swift', 'demo/iosApp/IdentityOpenCvSession.swift',
              'kmp-binding/apple/IOSIdentityBridge.swift']
end
sources += ['probes/ios-multimodal-demo/AppAccessibility.swift', 'probes/ios-multimodal-demo/AppBackgroundObservation.swift', 'probes/ios-multimodal-demo/MultimodalDemoValidation.swift', 'probes/ios-multimodal-demo/MultimodalDemoActiveValidation.swift', 'probes/ios-multimodal-demo/MultimodalDemoBackgroundValidation.swift'] if validation
frameworks = %w[iosSimulatorArm64 iosArm64].map do |platform|
  File.join(root, "demo/shared/build/bin/#{platform}/debugFramework/ODAIKitDemo.framework")
end
wemm_xcframework = nil
wemm_notice_path = nil
wemm_required_files = []
if wemm_video_demo
  xcframework_path = ENV['ODAI_WEMM_NATIVE_XCFRAMEWORK']
  abort('ODAI_WEMM_VIDEO_DEMO requires ODAI_WEMM_NATIVE_XCFRAMEWORK') if xcframework_path.nil? || xcframework_path.empty?
  wemm_xcframework = File.expand_path(xcframework_path, root)
  abort('Invalid WeMM native XCFramework directory') unless File.directory?(wemm_xcframework) && !File.symlink?(wemm_xcframework)
  wemm_required_files = [File.join(wemm_xcframework, 'Info.plist')]
  %w[ios-arm64 ios-arm64-simulator].each do |slice|
    slice_directory = File.join(wemm_xcframework, slice)
    wemm_required_files += [File.join(slice_directory, 'libODAIWeMMNative.a'),
                            File.join(slice_directory, 'Headers/module.modulemap'),
                            File.join(slice_directory, 'Headers/WeMMNative.h')]
  end
  wemm_required_files.each do |path|
    abort("Missing WeMM native XCFramework input: #{path}") unless File.file?(path)
  end
  notice_path = ENV['ODAI_WEMM_NATIVE_NOTICE']
  abort('ODAI_WEMM_VIDEO_DEMO requires ODAI_WEMM_NATIVE_NOTICE') if notice_path.nil? || notice_path.empty?
  wemm_notice_path = File.expand_path(notice_path, root)
  abort('Missing or invalid WeMM native NOTICE file') unless File.file?(wemm_notice_path) && !File.symlink?(wemm_notice_path) && File.size?(wemm_notice_path)
  realtime_notices = File.join(root, 'demo/iosApp/RealtimeSRNotices')
  abort('Missing or invalid RealtimeSRNotices directory') unless File.directory?(realtime_notices)
end
inputs = sources.map { |path| File.join(root, path) } + frameworks +
  [File.join(root, 'apple'), File.join(root, 'demo/iosApp/Info.plist'),
   File.join(root, 'demo/iosApp/UITests/SpeechDemoUITests.swift')]
inputs += wemm_required_files + [wemm_notice_path, File.join(root, 'demo/iosApp/RealtimeSRNotices')] if wemm_video_demo
inputs += [File.join(root, 'apple-mlx'), File.join(root, 'apple-mlx/Package.resolved')] if mlx_demo
inputs += [File.join(root, 'demo/iosApp/UITests/VoiceEffectsDemoUITests.swift')] if voice_effects_demo
inputs += [File.join(root, 'demo/iosApp/UITests/W6UnifiedVideoUITests.swift'),
           File.join(root, 'demo/iosApp/UITests/W6Fixtures-Info.plist')] if wemm_ui_fixtures
inputs += [File.join(root, 'apple-opencv/Package.swift'), File.join(root, 'apple-opencv/Vendor/opencv2.xcframework')] if identity_demo
inputs.each { |path| abort("Missing input: #{path}") unless File.exist?(path) }
identity_notices = {}
if identity_demo
  notice_directory = File.join(root, 'apple-opencv/Notices')
  abort('Invalid OpenCV notice directory') unless File.directory?(notice_directory) && !File.symlink?(notice_directory)
  %w[OpenCV-LICENSE.txt YuNet-LICENSE.txt SFace-LICENSE.txt].each do |name|
    path = File.join(notice_directory, name)
    abort("Missing or invalid OpenCV notice: #{name}") unless File.file?(path) && !File.symlink?(path) && File.size?(path)
    identity_notices[name] = File.binread(path)
  end
end
notices_directory = ENV['ODAI_THIRD_PARTY_NOTICES_DIR']
notices = notice_snapshot(File.expand_path(notices_directory, root)) unless notices_directory.nil? || notices_directory.empty?
FileUtils.mkdir_p(File.dirname(directory))
Dir.mkdir(directory)
project = Xcodeproj::Project.new(File.join(directory, 'SpeechDemo.xcodeproj'))
target = project.new_target(:application, 'SpeechDemo', :ios, mlx_demo ? '18.0' : (wemm_video_demo ? '16.4' : '15.0'))
package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
package.relative_path = File.join(root, 'apple')
project.root_object.package_references << package
products = %w[ODAIKitCore ODAIKitFace ODAIKitAppleVision ODAIKitSpeech ODAIKitAppleSpeech]
products += %w[ODAIKitVideo ODAIKitAppleVideo] if video_demo
products += %w[ODAIKitVoiceEffects ODAIKitAppleVoiceEffects] if voice_effects_demo
products += ['ODAIKitMultimodal'] if multimodal_demo
products += ['ODAIKitModelStore'] if wemm_video_demo
mlx_package = nil
if mlx_demo
  mlx_package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
  mlx_package.relative_path = File.join(root, 'apple-mlx')
  project.root_object.package_references << mlx_package
  products += ['ODAIKitModelStore', 'ODAIKitAppleMLXVLM']
end
opencv_package = nil
if identity_demo
  opencv_package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
  opencv_package.relative_path = File.join(root, 'apple-opencv')
  project.root_object.package_references << opencv_package
  products += ['ODAIKitIdentity', 'ODAIKitModelStore', 'ODAIKitOpenCVIdentity']
end
products.uniq.each do |name|
  dependency = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  dependency.product_name = name
  dependency.package = case name
                       when 'ODAIKitAppleMLXVLM' then mlx_package
                       when 'ODAIKitOpenCVIdentity' then opencv_package
                       else package
                       end
  target.package_product_dependencies << dependency
  file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  file.product_ref = dependency
  target.frameworks_build_phase.files << file
end
sources.each { |source| target.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, source))) }
unless identity_notices.empty?
  destination = File.join(directory, 'OpenCVIdentityNotices')
  Dir.mkdir(destination)
  identity_notices.each { |name, bytes| File.binwrite(File.join(destination, name), bytes) }
  resource = project.main_group.new_file('OpenCVIdentityNotices')
  resource.last_known_file_type = 'folder'
  target.resources_build_phase.add_file_reference(resource)
end
if notices
  notices.each do |path, bytes|
    destination = File.join(directory, 'ThirdPartyNotices', path)
    FileUtils.mkdir_p(File.dirname(destination))
    File.binwrite(destination, bytes)
  end
  resource = project.main_group.new_file('ThirdPartyNotices')
  resource.last_known_file_type = 'folder'
  target.resources_build_phase.add_file_reference(resource)
end
if wemm_video_demo
  File.binwrite(File.join(directory, 'WeMMNative-NOTICE.txt'), File.binread(wemm_notice_path))
  target.resources_build_phase.add_file_reference(project.main_group.new_file(File.join(directory, 'WeMMNative-NOTICE.txt')))
  realtime_notices = project.main_group.new_file(File.join(root, 'demo/iosApp/RealtimeSRNotices'))
  realtime_notices.last_known_file_type = 'folder'
  target.resources_build_phase.add_file_reference(realtime_notices)
end
framework_parent = File.join(root, 'demo/shared/build/bin/$(ODAI_KONAN_TARGET)/debugFramework')
target.frameworks_build_phase.add_file_reference(project.main_group.new_file(File.join(framework_parent, 'ODAIKitDemo.framework')))
if wemm_video_demo
  native_framework = project.main_group.new_file(wemm_xcframework)
  native_framework.last_known_file_type = 'wrapper.xcframework'
  target.frameworks_build_phase.add_file_reference(native_framework)
end
target.build_configurations.each do |config|
  config.build_settings.merge!({
    'PRODUCT_BUNDLE_IDENTIFIER' => wemm_video_demo ? 'io.ondeviceai.w6.unifiedvideo' : (identity_demo ? 'io.ondeviceai.combined.demo' : (mlx_demo ? 'io.ondeviceai.multimodalmlxdemo' : 'io.ondeviceai.ondeviceaidemo')),
    'SWIFT_VERSION' => '5.0', 'SWIFT_ACTIVE_COMPILATION_CONDITIONS' => '$(inherited) ODAI_DEMO_BINDING ODAI_SPEECH_DEMO' + (video_demo ? ' ODAI_VIDEO_DEMO' : '') + (multimodal_demo ? ' ODAI_MULTIMODAL_DEMO' : '') + (mlx_demo ? ' ODAI_MULTIMODAL_MLX_DEMO' : '') + (wemm_video_demo ? ' ODAI_WEMM_VIDEO_DEMO' : '') + (identity_demo ? ' ODAI_OPENCV_IDENTITY_DEMO' : '') + (validation ? ' ODAI_MULTIMODAL_DEMO_VALIDATION' : ''),
    'GENERATE_INFOPLIST_FILE' => 'YES', 'INFOPLIST_FILE' => File.join(root, 'demo/iosApp/Info.plist'),
    'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES', 'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES',
    'INFOPLIST_KEY_NSCameraUsageDescription' => '在设备本地检测人脸，不上传图像。',
    'INFOPLIST_KEY_NSSpeechRecognitionUsageDescription' => '在设备本地识别选择的音频文件或主动开始的麦克风语音，不上传音频。',
    'INFOPLIST_KEY_NSMicrophoneUsageDescription' => voice_effects_demo ? '仅在你点击录音或语音识别时采集声音，在设备本地处理；变声录音可由你导出。' : '仅在你主动开始语音识别时采集声音，在本机处理，不保存录音。',
    'TARGETED_DEVICE_FAMILY' => '1,2', 'CODE_SIGN_STYLE' => 'Automatic', 'ARCHS' => 'arm64',
    'ODAI_KONAN_TARGET[sdk=iphonesimulator*]' => 'iosSimulatorArm64',
    'ODAI_KONAN_TARGET[sdk=iphoneos*]' => 'iosArm64',
    'FRAMEWORK_SEARCH_PATHS' => ['$(inherited)', framework_parent],
    'OTHER_LDFLAGS' => ['$(inherited)', '-ObjC', '-lsqlite3'],
    'ODAI_SDK_COMMIT' => 'working-tree'
  })
  if wemm_ui_fixtures
    config.build_settings['PRODUCT_BUNDLE_IDENTIFIER'] = 'io.ondeviceai.w6.unifiedvideo.fixtures'
    config.build_settings['INFOPLIST_FILE'] = File.join(root, 'demo/iosApp/UITests/W6Fixtures-Info.plist')
  end
  config.build_settings['SWIFT_ACTIVE_COMPILATION_CONDITIONS'] += ' ODAI_VOICE_EFFECTS_DEMO' if voice_effects_demo
  if wemm_video_demo
    config.build_settings['OTHER_LDFLAGS'] += ['-lc++', '-framework', 'Accelerate', '-framework', 'Metal', '-framework', 'MetalKit']
  end
  # The existing OpenCV binary has no arm64 simulator slice.
  config.build_settings['SUPPORTED_PLATFORMS'] = 'iphoneos' if identity_demo
  team = ENV['ODAI_DEVELOPMENT_TEAM']
  config.build_settings['DEVELOPMENT_TEAM'] = team unless team.nil? || team.empty?
end
ui = project.new_target(:ui_test_bundle, 'SpeechDemoUITests', :ios, '17.0')
ui.add_dependency(target)
ui.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, 'demo/iosApp/UITests/SpeechDemoUITests.swift')))
ui.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, 'demo/iosApp/UITests/VoiceEffectsDemoUITests.swift'))) if voice_effects_demo
ui.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, 'demo/iosApp/UITests/W6UnifiedVideoUITests.swift'))) if wemm_ui_fixtures
ui_bundle_identifier = 'io.ondeviceai.ondeviceaidemo.uitests'
ui_bundle_identifier = 'io.ondeviceai.w6.unifiedvideo.uitests' if wemm_video_demo
ui_bundle_identifier = 'io.ondeviceai.w6.unifiedvideo.fixtures.uitests' if wemm_ui_fixtures
ui.build_configurations.each do |config|
  config.build_settings.merge!({
    'PRODUCT_BUNDLE_IDENTIFIER' => ui_bundle_identifier,
    'SWIFT_VERSION' => '5.0', 'GENERATE_INFOPLIST_FILE' => 'YES', 'ARCHS' => 'arm64',
    'TEST_TARGET_NAME' => 'SpeechDemo', 'CODE_SIGN_STYLE' => 'Automatic',
    'TARGETED_DEVICE_FAMILY' => '1,2'
  })
  team = ENV['ODAI_DEVELOPMENT_TEAM']
  config.build_settings['DEVELOPMENT_TEAM'] = team unless team.nil? || team.empty?
end
if validation
  metadata = File.join(directory, 'validation-metadata.json')
  File.write(metadata, JSON.generate({ sourceCommit: validation_commit }))
  target.resources_build_phase.add_file_reference(project.main_group.new_file(metadata))
end
configure_openvoice_demo(project, target, root) if voice_effects_demo
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
