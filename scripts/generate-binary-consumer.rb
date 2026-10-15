#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'
root=File.expand_path('..',__dir__)
directory=File.join(root,'artifacts/BinaryConsumer')
FileUtils.mkdir_p(directory)
FileUtils.cp(File.join(root,'examples/ios-native/NativeConsumerApp.swift'),directory)
project=Xcodeproj::Project.new(File.join(directory,'BinaryConsumer.xcodeproj'))
target=project.new_target(:application,'BinaryConsumer',:ios,'15.0')
target.source_build_phase.add_file_reference(project.main_group.new_file('NativeConsumerApp.swift'))
target.build_configurations.each do |configuration|
 configuration.build_settings.merge!({'SWIFT_VERSION'=>'5.0','ARCHS'=>'arm64','PRODUCT_BUNDLE_IDENTIFIER'=>'io.ondeviceai.binaryconsumer','GENERATE_INFOPLIST_FILE'=>'YES','INFOPLIST_KEY_UILaunchScreen_Generation'=>'YES','INFOPLIST_KEY_UIApplicationSceneManifest_Generation'=>'YES','LD_RUNPATH_SEARCH_PATHS'=>['$(inherited)','@executable_path/Frameworks']})
end
embed=target.new_copy_files_build_phase('Embed SDK');embed.dst_subfolder_spec='10'
['ODAIKitCore','ODAIKitFace','ODAIKitAppleVision'].each do |name|
 ref=project.main_group.new_file("../xcframeworks/#{name}.xcframework")
 target.frameworks_build_phase.add_file_reference(ref)
 file=embed.add_file_reference(ref);file.settings={'ATTRIBUTES'=>['CodeSignOnCopy','RemoveHeadersOnCopy']}
end
project.save
scheme=Xcodeproj::XCScheme.new;scheme.add_build_target(target);scheme.set_launch_target(target);scheme.save_as(project.path,'BinaryConsumer',true)
