require 'pathname'

# Both the checked-in app and generated voice-capable demo use the same native engine.
def configure_openvoice_demo(project, target, root)
  relative = Pathname.new(File.join(root, 'demo/openvoice')).relative_path_from(project.path.dirname).to_s
  ios_relative = Pathname.new(File.join(root, 'demo/iosApp')).relative_path_from(project.path.dirname).to_s
  file = project.main_group.new_file(File.join(relative, 'OpenVoice.cpp'))
  target.source_build_phase.add_file_reference(file)
  prepare = target.new_shell_script_build_phase('Prepare OpenVoice')
  target.build_phases.move(prepare, 0)
  prepare.shell_script = 'set -eu
/usr/bin/python3 "$OPENVOICE_DIR/prepare.py" ios "$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/OpenVoice"'
  prepare.input_paths = %w[prepare.py assets/original.wav assets/voiceA.f32 assets/voiceB.f32 assets/voiceC.f32 assets/CREDITS.txt].map { |name| "$(OPENVOICE_DIR)/#{name}" }
  prepare.output_paths = %w[original.wav converter.onnx ref_encoder.onnx voiceA.f32 voiceB.f32 voiceC.f32 CREDITS.txt onnxruntime-LICENSE onnxruntime-ThirdPartyNotices.txt].map do |name|
    "$(TARGET_BUILD_DIR)/$(UNLOCALIZED_RESOURCES_FOLDER_PATH)/OpenVoice/#{name}"
  end
  prepare.output_paths << '$(OPENVOICE_FRAMEWORK)/onnxruntime'
  target.build_configurations.each do |config|
    settings = config.build_settings
    settings['IPHONEOS_DEPLOYMENT_TARGET'] = [Gem::Version.new(settings.fetch('IPHONEOS_DEPLOYMENT_TARGET', '15.0')), Gem::Version.new('15.1')].max.to_s
    settings['OPENVOICE_DIR'] = "$(SRCROOT)/#{relative}"
    settings['OPENVOICE_SLICE[sdk=iphoneos*]'] = 'ios-arm64'
    settings['OPENVOICE_SLICE[sdk=iphonesimulator*]'] = 'ios-arm64_x86_64-simulator'
    settings['OPENVOICE_FRAMEWORK'] = '$(OPENVOICE_DIR)/build/ios/onnxruntime.xcframework/$(OPENVOICE_SLICE)/onnxruntime.framework'
    settings['SWIFT_OBJC_BRIDGING_HEADER'] = "$(SRCROOT)/#{ios_relative}/RealtimeVoiceBridge.h"
    settings['SWIFT_ACTIVE_COMPILATION_CONDITIONS'] += ' ODAI_OPENVOICE_DEMO'
    settings['HEADER_SEARCH_PATHS'] = ['$(inherited)', '$(OPENVOICE_FRAMEWORK)/Headers', '$(OPENVOICE_DIR)', "$(SRCROOT)/#{ios_relative}"]
    settings['CLANG_CXX_LANGUAGE_STANDARD'] = 'c++17'
    settings['OTHER_LDFLAGS'] += ['"$(OPENVOICE_FRAMEWORK)/onnxruntime"', '-framework', 'Accelerate', '-framework', 'CoreML', '-framework', 'Metal', '-framework', 'CoreVideo', '-framework', 'AudioToolbox', '-framework', 'AVFoundation', '-lz']
  end
end
