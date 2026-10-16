#!/usr/bin/env ruby
require 'xcodeproj'
root = File.expand_path('..', __dir__)
path = File.join(root, 'demo/iosApp/OnDeviceAIDemo.xcodeproj')
project = Xcodeproj::Project.open(path)
app = project.targets.find { |t| t.name == 'OnDeviceAIDemo' }
unless app.source_build_phase.files_references.any? { |f| f.path == 'CameraLifecycle.swift' }
  app.source_build_phase.add_file_reference(project.main_group.new_file('CameraLifecycle.swift'))
end
app.build_configurations.each do |config|
  config.build_settings['ODAI_APP_BUNDLE_ID'] = 'io.ondeviceai.ondeviceaidemo'
  config.build_settings['PRODUCT_BUNDLE_IDENTIFIER'] = '$(ODAI_APP_BUNDLE_ID)'
end
tests = project.targets.find { |t| t.name == 'OnDeviceAIDemoUITests' }
unless tests
  tests = project.new_target(:ui_test_bundle, 'OnDeviceAIDemoUITests', :ios, '15.0')
  tests.add_dependency(app)
  tests.source_build_phase.add_file_reference(project.main_group.new_file('UITests/CameraLifecycleUITests.swift'))
end
tests.build_configurations.each do |config|
  config.build_settings.merge!({
    'ODAI_APP_BUNDLE_ID' => 'io.ondeviceai.ondeviceaidemo',
    'PRODUCT_BUNDLE_IDENTIFIER' => '$(ODAI_APP_BUNDLE_ID).uitests',
    'TEST_TARGET_NAME' => 'OnDeviceAIDemo', 'GENERATE_INFOPLIST_FILE' => 'YES',
    'SWIFT_VERSION' => '5.0', 'CODE_SIGN_STYLE' => 'Manual', 'TARGETED_DEVICE_FAMILY' => '1,2'
  })
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(app); scheme.set_launch_target(app)
scheme.add_build_target(tests, false); scheme.add_test_target(tests)
scheme.save_as(path, app.name, true)
