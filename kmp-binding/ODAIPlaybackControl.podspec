Pod::Spec.new do |s|
  s.name = 'ODAIPlaybackControl'
  s.version = '0.1.0'
  s.summary = 'Local iPhone host adapter for KMP realtime playback controls.'
  s.homepage = 'https://clients-gitlab.ugreeniot.com/home/common/ondevice-ai-kit'
  s.source = { :git => 'https://clients-gitlab.ugreeniot.com/home/common/ondevice-ai-kit.git', :tag => s.version.to_s }
  s.license = { :file => 'player-apple/LICENSE' }
  s.author = 'OnDeviceAIKit'
  s.platform = :ios, '15.1'
  s.swift_version = '5.0'
  s.static_framework = true
  s.source_files = 'player-apple/*.swift'
  s.vendored_frameworks = 'build/bin/iosArm64/releaseFramework/ODAIKitBinding.framework'
  s.dependency 'UHPlayerSDK'
  s.libraries = 'c++', 'sqlite3'
end
