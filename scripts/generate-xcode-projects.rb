#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'
root = File.expand_path('..', __dir__)
def create_project(root, relative, name, products, package_path, source)
  directory = File.join(root, relative)
  project_path = File.join(directory, "#{name}.xcodeproj")
  project = Xcodeproj::Project.new(project_path)
  target = project.new_target(:application, name, :ios, '15.0')
  target.build_configurations.each do |config|
    config.build_settings.merge!({
      'PRODUCT_BUNDLE_IDENTIFIER' => "io.ondeviceai.#{name.downcase}",
      'SWIFT_VERSION' => '5.0', 'GENERATE_INFOPLIST_FILE' => 'YES',
      'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES',
      'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES',
      'TARGETED_DEVICE_FAMILY' => '1,2', 'CODE_SIGN_STYLE' => 'Manual'
    })
  end
  package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
  package.relative_path = package_path
  project.root_object.package_references << package
  products.each do |name|
    dependency = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
    dependency.product_name = name; dependency.package = package
    target.package_product_dependencies << dependency
    build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
    build_file.product_ref = dependency
    target.frameworks_build_phase.files << build_file
  end
  source.each { |name| target.source_build_phase.add_file_reference(project.main_group.new_file(name)) }
  if name == 'OnDeviceAIDemo'
    target.build_configurations.each do |config|
      config.build_settings.merge!({
        'ARCHS' => 'arm64',
        'ODAI_KONAN_TARGET[sdk=iphonesimulator*]' => 'iosSimulatorArm64',
        'ODAI_KONAN_TARGET[sdk=iphoneos*]' => 'iosArm64',
        'FRAMEWORK_SEARCH_PATHS' => ['$(inherited)', '$(SRCROOT)/../shared/build/bin/$(ODAI_KONAN_TARGET)/debugFramework'],
        'OTHER_LDFLAGS' => ['$(inherited)', '-ObjC', '-lsqlite3'],
        'INFOPLIST_FILE' => 'Info.plist',
        'INFOPLIST_KEY_NSCameraUsageDescription' => '在设备本地检测人脸，不上传图像。',
        'INFOPLIST_KEY_ODAISDKCommit' => '$(ODAI_SDK_COMMIT)',
        'ODAI_SDK_COMMIT' => 'working-tree'
      })
    end
    framework = project.main_group.new_file('../shared/build/bin/$(ODAI_KONAN_TARGET)/debugFramework/ODAIKitDemo.framework')
    target.frameworks_build_phase.add_file_reference(framework)
  end
  project.save
  scheme = Xcodeproj::XCScheme.new
  scheme.add_build_target(target); scheme.set_launch_target(target)
  scheme.save_as(project_path, target.name, true)
end
create_project(root, 'examples/ios-native', 'NativeConsumer', ['ODAIKitCore', 'ODAIKitFace', 'ODAIKitAppleVision'], '../../apple', ['NativeConsumerApp.swift'])
create_project(root, 'demo/iosApp', 'OnDeviceAIDemo', ['ODAIKitCore', 'ODAIKitFace', 'ODAIKitAppleVision'], '../../apple', ['OnDeviceAIDemoApp.swift', 'DemoController.swift', 'IOSFaceBridge.swift', 'CameraSource.swift', 'CameraLifecycle.swift'])
load File.join(__dir__, 'configure-ios-ui-tests.rb')
