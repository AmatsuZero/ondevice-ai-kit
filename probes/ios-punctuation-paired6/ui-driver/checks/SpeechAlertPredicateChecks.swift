import Foundation

// The device failure supplied the RED case: evaluating this predicate on an
// NSString raises NSUnknownKeyException because the predicate reads `label`.
let predicate = NSPredicate(
    format: "label CONTAINS[c] %@ OR label CONTAINS[c] %@",
    "speech recognition", "语音识别"
)
for (label, expected) in [
    ("Speech Recognition", true),
    ("语音识别", true),
    ("Allow this app to use speech recognition?", true),
    ("Microphone", false),
    ("Camera", false),
    ("", false)
] {
    precondition(predicate.evaluate(with: ["label": label]) == expected)
}
print("PASS: six exact Speech predicate cases")
