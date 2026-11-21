#!/usr/bin/env ruby
require 'xcodeproj'

abort('usage: device-project.rb PREPARED_DIRECTORY') unless ARGV.length == 1
directory = File.expand_path(ARGV[0])
project_path = File.join(directory, 'ExportCancellationProbe.xcodeproj')
abort('Refusing to replace an existing project') if File.exist?(project_path) || File.symlink?(project_path)
sources = ['AppleVideoAudioMuxer.swift', 'AppleVideoProfile.swift'].map { |name| File.join(directory, name) }
sources += ['ExportCancellationSuite.swift', 'ProbeApp.swift'].map { |name| File.join(directory, name) }
resources = ['aac.mp4', 'video-only.mov'].map { |name| File.join(directory, name) }
(sources + resources + [File.join(directory, 'source-manifest.json')]).each { |path| abort("Missing input: #{path}") unless File.file?(path) }
project = Xcodeproj::Project.new(project_path)
target = project.new_target(:application, 'ExportCancellationProbe', :ios, '15.0')
sources.each { |path| target.source_build_phase.add_file_reference(project.main_group.new_file(File.expand_path(path))) }
(resources + [File.join(directory, 'source-manifest.json')]).each do |path|
  target.resources_build_phase.add_file_reference(project.main_group.new_file(File.expand_path(path)))
end
target.build_configurations.each do |configuration|
  configuration.build_settings.merge!({
    'SWIFT_VERSION' => '5.0', 'PRODUCT_BUNDLE_IDENTIFIER' => 'io.ondeviceai.probe.videosr',
    'GENERATE_INFOPLIST_FILE' => 'YES', 'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES',
    'TARGETED_DEVICE_FAMILY' => '1', 'SUPPORTED_PLATFORMS' => 'iphoneos iphonesimulator',
    'CODE_SIGN_STYLE' => 'Automatic', 'DEVELOPMENT_TEAM' => ENV.fetch('ODAI_EXPORT_PROBE_TEAM', ''),
    'SWIFT_OPTIMIZATION_LEVEL' => '-Onone', 'ASSETCATALOG_COMPILER_GENERATE_SWIFT_ASSET_SYMBOL_EXTENSIONS' => 'NO'
  })
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(target)
scheme.save_as(project.path, target.name, true)
puts project.path
