#!/usr/bin/env ruby
require 'xcodeproj'
root=File.expand_path('..', __dir__)
project_path=File.join(root,'apple/OnDeviceAIKit.xcodeproj')
project=File.exist?(project_path) ? Xcodeproj::Project.open(project_path) : Xcodeproj::Project.new(project_path)
names=['ODAIKitCore','ODAIKitModelStore','ODAIKitFace','ODAIKitAppleVision','ODAIKitSpeech','ODAIKitAppleSpeech']
targets={}
names.each do |name|
 target=project.targets.find{|candidate| candidate.name==name} || project.new_target(:framework,name,:ios,'15.0');targets[name]=target
 target.build_configurations.each do |configuration|
  configuration.build_settings.merge!({'SWIFT_VERSION'=>'5.0','BUILD_LIBRARY_FOR_DISTRIBUTION'=>'YES','DEFINES_MODULE'=>'YES','SKIP_INSTALL'=>'NO','GENERATE_INFOPLIST_FILE'=>'YES','PRODUCT_BUNDLE_IDENTIFIER'=>"io.ondeviceai.#{name.downcase}",'INSTALL_PATH'=>'$(LOCAL_LIBRARY_DIR)/Frameworks','ARCHS'=>'arm64'})
 end
 Dir.glob(File.join(root,"apple/Sources/#{name}/*.swift")).sort.each do |source|
  relative=source.delete_prefix(File.join(root,'apple/'))
  reference=project.files.find{|candidate| candidate.path==relative} || project.main_group.new_file(relative)
  target.source_build_phase.add_file_reference(reference) unless target.source_build_phase.files.any?{|build| build.file_ref==reference}
 end
end
{'ODAIKitModelStore'=>['ODAIKitCore'],'ODAIKitFace'=>['ODAIKitCore'],'ODAIKitAppleVision'=>['ODAIKitCore','ODAIKitFace'],'ODAIKitSpeech'=>['ODAIKitCore'],'ODAIKitAppleSpeech'=>['ODAIKitCore','ODAIKitSpeech']}.each do |name, dependencies|
 dependencies.each do |dependency|
  targets[name].add_dependency(targets[dependency])
  unless targets[name].frameworks_build_phase.files.any?{|build| build.file_ref==targets[dependency].product_reference}
   targets[name].frameworks_build_phase.add_file_reference(targets[dependency].product_reference)
  end
 end
end
project.save
scheme=Xcodeproj::XCScheme.new;names.each{|name|scheme.add_build_target(targets[name])};scheme.save_as(project.path,'SDKDistribution',true)
