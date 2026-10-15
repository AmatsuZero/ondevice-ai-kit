#!/usr/bin/env ruby
require 'xcodeproj'
root=File.expand_path('..', __dir__)
project=Xcodeproj::Project.new(File.join(root,'apple/OnDeviceAIKit.xcodeproj'))
names=['ODAIKitCore','ODAIKitModelStore','ODAIKitFace','ODAIKitAppleVision']
targets={}
names.each do |name|
 target=project.new_target(:framework,name,:ios,'15.0');targets[name]=target
 target.build_configurations.each do |configuration|
  configuration.build_settings.merge!({'SWIFT_VERSION'=>'5.0','BUILD_LIBRARY_FOR_DISTRIBUTION'=>'YES','DEFINES_MODULE'=>'YES','SKIP_INSTALL'=>'NO','GENERATE_INFOPLIST_FILE'=>'YES','PRODUCT_BUNDLE_IDENTIFIER'=>"io.ondeviceai.#{name.downcase}",'INSTALL_PATH'=>'$(LOCAL_LIBRARY_DIR)/Frameworks','ARCHS'=>'arm64'})
 end
 Dir.glob(File.join(root,"apple/Sources/#{name}/*.swift")).sort.each do |source|
  reference=project.main_group.new_file(source.delete_prefix(File.join(root,'apple/')))
  target.source_build_phase.add_file_reference(reference)
 end
end
{'ODAIKitModelStore'=>['ODAIKitCore'],'ODAIKitFace'=>['ODAIKitCore'],'ODAIKitAppleVision'=>['ODAIKitCore','ODAIKitFace']}.each do |name, dependencies|
 dependencies.each do |dependency|
  targets[name].add_dependency(targets[dependency]);targets[name].frameworks_build_phase.add_file_reference(targets[dependency].product_reference)
 end
end
project.save
scheme=Xcodeproj::XCScheme.new;names.each{|name|scheme.add_build_target(targets[name])};scheme.save_as(project.path,'SDKDistribution',true)
