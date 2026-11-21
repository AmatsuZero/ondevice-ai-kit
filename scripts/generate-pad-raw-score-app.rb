#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'

# Independent research consumer. Does not change production packages or Demo.
root = File.expand_path('..', __dir__)
directory = File.expand_path(ENV.fetch('ODAI_PAD_APP_DIR'), root)
abort('Refusing to replace existing PAD app output') if File.exist?(directory)
native = File.join(root, 'apple-opencv/Vendor/opencv2.xcframework/ios-arm64')
abort('Prepare the approved OpenCV static XCFramework first') unless File.file?(File.join(native, 'libopencv2.a'))
FileUtils.mkdir_p(directory)
project = Xcodeproj::Project.new(File.join(directory, 'PadRawScore.xcodeproj'))
target = project.new_target(:application, 'PadRawScore', :ios, '15.0')
%w[PadRawScoreProbeApp.swift PadProbeReport.swift PadRawScoreProbe.mm].each do |file|
  target.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, 'probes/ios-opencv-pad', file)))
end
target.frameworks_build_phase.add_file_reference(project.main_group.new_file(File.join(native, 'libopencv2.a')))
%w[Accelerate AVFoundation CoreMedia CoreVideo CoreGraphics Foundation UIKit].each { |name| target.add_system_framework(name) }
target.build_configurations.each do |config|
  config.build_settings.merge!({
    'SWIFT_VERSION' => '5.0', 'PRODUCT_BUNDLE_IDENTIFIER' => 'io.ondeviceai.pad.rawscore',
    'GENERATE_INFOPLIST_FILE' => 'YES', 'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES',
    'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES',
    'TARGETED_DEVICE_FAMILY' => '1,2', 'SUPPORTED_PLATFORMS' => 'iphoneos', 'ARCHS' => 'arm64',
    'CODE_SIGN_STYLE' => 'Automatic', 'ENABLE_USER_SCRIPT_SANDBOXING' => 'YES',
    'SWIFT_OBJC_BRIDGING_HEADER' => File.join(root, 'probes/ios-opencv-pad/PadRawScoreProbe.h'),
    'HEADER_SEARCH_PATHS' => [File.join(native, 'Headers')],
    'LIBRARY_SEARCH_PATHS' => ['$(inherited)', native],
    'CLANG_CXX_LANGUAGE_STANDARD' => 'c++17',
    'OTHER_LDFLAGS' => ['$(inherited)', '-ObjC', '-lz'],
    'LD_RUNPATH_SEARCH_PATHS' => ['$(inherited)', '@executable_path/Frameworks']
  })
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(target)
scheme.set_launch_target(target)
scheme.save_as(project.path, target.name, true)
puts project.path
