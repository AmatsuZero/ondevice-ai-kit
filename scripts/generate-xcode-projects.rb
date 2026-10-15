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
  project.save
  scheme = Xcodeproj::XCScheme.new
  scheme.add_build_target(target); scheme.set_launch_target(target)
  scheme.save_as(project_path, target.name, true)
end
create_project(root, 'examples/ios-native', 'NativeConsumer', ['ODAIKitCore'], '../../apple', ['NativeConsumerApp.swift'])
