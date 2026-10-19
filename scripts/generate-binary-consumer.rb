#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'
root=File.expand_path('..',__dir__)
speech=!!ARGV.delete('--speech')
identity=!!ARGV.delete('--identity')
abort('Usage: generate-binary-consumer.rb [--speech | --identity]') unless ARGV.empty? && !(speech && identity)
artifacts=File.expand_path(ENV.fetch('ODAI_ARTIFACTS_DIR','artifacts'),root)
name=identity ? 'IdentityBinaryConsumer' : speech ? 'SpeechBinaryConsumer' : 'BinaryConsumer'
source=identity ? 'examples/ios-identity-native/IdentityConsumerApp.swift' : speech ? 'examples/ios-speech-native/SpeechConsumerApp.swift' : 'examples/ios-native/NativeConsumerApp.swift'
products=identity ? ['ODAIKitCore','ODAIKitModelStore','ODAIKitIdentity'] : speech ? ['ODAIKitCore','ODAIKitSpeech','ODAIKitAppleSpeech'] : ['ODAIKitCore','ODAIKitFace','ODAIKitAppleVision']
directory=File.join(artifacts,name)
abort('Refusing to replace existing binary consumer output') if File.exist?(directory)
abort("Missing source: #{source}") unless File.file?(File.join(root,source))
products.each do |product|
 path=File.join(artifacts,"xcframeworks/#{product}.xcframework")
 abort("Missing framework: #{path}") unless File.directory?(path)
end
FileUtils.mkdir_p(directory)
FileUtils.cp(File.join(root,source),directory)
project=Xcodeproj::Project.new(File.join(directory,"#{name}.xcodeproj"))
target=project.new_target(:application,name,:ios,'15.0')
target.source_build_phase.add_file_reference(project.main_group.new_file(File.basename(source)))
target.build_configurations.each do |configuration|
 configuration.build_settings.merge!({'SWIFT_VERSION'=>'5.0','ARCHS'=>'arm64','PRODUCT_BUNDLE_IDENTIFIER'=>"io.ondeviceai.#{name.downcase}",'GENERATE_INFOPLIST_FILE'=>'YES','INFOPLIST_KEY_UILaunchScreen_Generation'=>'YES','INFOPLIST_KEY_UIApplicationSceneManifest_Generation'=>'YES','LD_RUNPATH_SEARCH_PATHS'=>['$(inherited)','@executable_path/Frameworks']})
end
embed=target.new_copy_files_build_phase('Embed SDK');embed.dst_subfolder_spec='10'
products.each do |name|
 ref=project.main_group.new_file("../xcframeworks/#{name}.xcframework")
 target.frameworks_build_phase.add_file_reference(ref)
 file=embed.add_file_reference(ref);file.settings={'ATTRIBUTES'=>['CodeSignOnCopy','RemoveHeadersOnCopy']}
end
project.save
scheme=Xcodeproj::XCScheme.new;scheme.add_build_target(target);scheme.set_launch_target(target);scheme.save_as(project.path,target.name,true)
