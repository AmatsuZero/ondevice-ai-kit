#!/usr/bin/env ruby
require 'xcodeproj'
root = File.expand_path('..', __dir__)
path = File.join(root, 'demo/iosApp/OnDeviceAIDemo.xcodeproj')
project = Xcodeproj::Project.open(path)
app = project.targets.find { |t| t.name == 'OnDeviceAIDemo' }
development_team = ENV.fetch('ODAI_DEVELOPMENT_TEAM', '4X996UV8LC') # UGREEN GROUP LIMITED
unless app.source_build_phase.files_references.any? { |f| f.path == 'CameraLifecycle.swift' }
  app.source_build_phase.add_file_reference(project.main_group.new_file('CameraLifecycle.swift'))
end
app.build_configurations.each do |config|
  config.build_settings['CODE_SIGN_STYLE'] = 'Automatic'
  config.build_settings['DEVELOPMENT_TEAM'] = development_team
  config.build_settings['ODAI_APP_BUNDLE_ID'] = 'io.ondeviceai.ondeviceaidemo'
  config.build_settings['PRODUCT_BUNDLE_IDENTIFIER'] = '$(ODAI_APP_BUNDLE_ID)'
end
tests = project.targets.find { |t| t.name == 'OnDeviceAIDemoUITests' }
unless tests
  tests = project.new_target(:ui_test_bundle, 'OnDeviceAIDemoUITests', :ios, '15.0')
  tests.add_dependency(app)
  tests.source_build_phase.add_file_reference(project.main_group.new_file('UITests/CameraLifecycleUITests.swift'))
end
unless tests.source_build_phase.files_references.any? { |f| f.path == 'UITests/VoiceEffectsDemoUITests.swift' }
  tests.source_build_phase.add_file_reference(project.main_group.new_file('UITests/VoiceEffectsDemoUITests.swift'))
end
unless tests.source_build_phase.files_references.any? { |f| f.path == 'UITests/PlaybackEnhancementUITests.swift' }
  tests.source_build_phase.add_file_reference(project.main_group.new_file('UITests/PlaybackEnhancementUITests.swift'))
end
tests.build_configurations.each do |config|
  config.build_settings.merge!({
    'ODAI_APP_BUNDLE_ID' => 'io.ondeviceai.ondeviceaidemo',
    'PRODUCT_BUNDLE_IDENTIFIER' => '$(ODAI_APP_BUNDLE_ID).uitests',
    'TEST_TARGET_NAME' => 'OnDeviceAIDemo', 'GENERATE_INFOPLIST_FILE' => 'YES',
    'SWIFT_VERSION' => '5.0', 'CODE_SIGN_STYLE' => 'Automatic',
    'DEVELOPMENT_TEAM' => development_team, 'TARGETED_DEVICE_FAMILY' => '1,2'
  })
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(app); scheme.set_launch_target(app)
scheme.add_build_target(tests, false); scheme.add_test_target(tests)
scheme.save_as(path, app.name, true)
scheme.save_as(path, 'iOS Demo', true) unless File.exist?(File.join(path, 'xcshareddata/xcschemes/iOS Demo.xcscheme'))
