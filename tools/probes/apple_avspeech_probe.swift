import AVFoundation
import Foundation

let voices = AVSpeechSynthesisVoice.speechVoices()
print("voiceCount=\(voices.count)")
for voice in voices.prefix(5) { print("voice=\(voice.identifier) locale=\(voice.language)") }
let synthesizer = AVSpeechSynthesizer()
let utterance = AVSpeechUtterance(string: "OnDevice AI Kit Apple speech probe")
utterance.voice = voices.first
synthesizer.speak(utterance)
while synthesizer.isSpeaking { RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.05)) }
print("synthesisCompleted=true")
