#!/usr/bin/env ruby
require 'xcodeproj'
require 'fileutils'

# Isolated local consumer. Never rewrites the checked-in Demo project or embeds test portraits.
root = File.expand_path('..', __dir__)
directory = File.expand_path(ENV.fetch('ODAI_OPENCV_APP_DIR'), root)
abort('Refusing to replace existing OpenCV app output') if File.exist?(directory)
kind = ENV.fetch('ODAI_OPENCV_APP_KIND', 'probe')
abort('Unsupported app kind') unless %w[probe demo].include?(kind)
sources = if kind == 'probe'
  ['probes/ios-opencv-identity/OpenCvIdentityProbeApp.swift',
   'probes/ios-opencv-identity/IdentitySessionChecks.swift', 'demo/iosApp/IdentityOpenCvSession.swift']
else
  %w[demo/iosApp/OnDeviceAIDemoApp.swift demo/iosApp/DemoController.swift
     demo/iosApp/IdentityOpenCvHost.swift demo/iosApp/IdentityOpenCvSession.swift
     demo/iosApp/IOSFaceBridge.swift demo/iosApp/CameraSource.swift
     demo/iosApp/CameraLifecycle.swift demo/iosApp/PhotoSubmission.swift
     kmp-binding/apple/IOSIdentityBridge.swift]
end
validate_host = kind == 'demo' && ENV['ODAI_OPENCV_VALIDATE_HOST'] == '1'
sources << 'probes/ios-opencv-identity/IdentityDemoChecks.swift' if validate_host
sources.each { |s| abort("Missing input: #{s}") unless File.file?(File.join(root, s)) }
FileUtils.mkdir_p(directory)
project = Xcodeproj::Project.new(File.join(directory, 'OpenCvIdentity.xcodeproj'))
target = project.new_target(:application, 'OpenCvIdentity', :ios, '15.0')
%w[apple apple-opencv].each do |path|
  package = project.new(Xcodeproj::Project::Object::XCLocalSwiftPackageReference)
  package.relative_path = File.join(root, path)
  project.root_object.package_references << package
  names = path == 'apple' ? %w[ODAIKitCore ODAIKitIdentity ODAIKitModelStore] : ['ODAIKitOpenCVIdentity']
  names += %w[ODAIKitFace ODAIKitAppleVision] if path == 'apple' && kind == 'demo'
  names.each do |name|
    dependency = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
    dependency.product_name = name; dependency.package = package
    target.package_product_dependencies << dependency
    build = project.new(Xcodeproj::Project::Object::PBXBuildFile)
    build.product_ref = dependency; target.frameworks_build_phase.files << build
  end
end
sources.each { |s| target.source_build_phase.add_file_reference(project.main_group.new_file(File.join(root, s))) }
target.build_configurations.each do |config|
  config.build_settings.merge!({
    'SWIFT_VERSION' => '5.0', 'PRODUCT_BUNDLE_IDENTIFIER' => "io.ondeviceai.opencv.#{kind}",
    'GENERATE_INFOPLIST_FILE' => 'YES', 'INFOPLIST_KEY_UILaunchScreen_Generation' => 'YES',
    'INFOPLIST_KEY_UIApplicationSceneManifest_Generation' => 'YES',
    'TARGETED_DEVICE_FAMILY' => '1,2', 'SUPPORTED_PLATFORMS' => 'iphoneos iphonesimulator',
    'CODE_SIGN_STYLE' => 'Automatic', 'ENABLE_USER_SCRIPT_SANDBOXING' => 'YES',
    'OTHER_LDFLAGS' => ['$(inherited)', '-ObjC'],
    'LD_RUNPATH_SEARCH_PATHS' => ['$(inherited)', '@executable_path/Frameworks']
  })
end
if kind == 'demo'
  framework = File.join(root, 'demo/shared/build/bin/iosArm64/debugFramework/ODAIKitDemo.framework')
  abort('Build :demo:shared:linkDebugFrameworkIosArm64 first') unless File.exist?(framework)
  target.frameworks_build_phase.add_file_reference(project.main_group.new_file(framework))
  target.build_configurations.each do |config|
    config.build_settings.merge!({
      'SWIFT_ACTIVE_COMPILATION_CONDITIONS' => '$(inherited) ODAI_DEMO_BINDING ODAI_OPENCV_IDENTITY_DEMO',
      'FRAMEWORK_SEARCH_PATHS' => ['$(inherited)', File.dirname(framework)],
      'SUPPORTED_PLATFORMS' => 'iphoneos', 'ARCHS' => 'arm64',
      'INFOPLIST_FILE' => File.join(root, 'demo/iosApp/Info.plist'),
      'ODAI_SDK_COMMIT' => 'working-tree',
      'INFOPLIST_KEY_NSCameraUsageDescription' => '在设备本地检测人脸，不上传图像。',
      'OTHER_LDFLAGS' => ['$(inherited)', '-ObjC', '-lsqlite3']
    })
    if validate_host
      config.build_settings['SWIFT_ACTIVE_COMPILATION_CONDITIONS'] += ' ODAI_OPENCV_IDENTITY_VALIDATION'
    end
  end
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(target)
scheme.set_launch_target(target)
scheme.save_as(project.path, target.name, true)
puts project.path
