// Video formatting/transforms adapted from mlx-swift-lm SmolVLM2.swift,
// revision 6e338e5df49424fc0db7c6d58184d622f92ed18a.
// MIT License
// Copyright (c) 2024 ml-explore
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

import Foundation
import CoreImage
import CoreMedia
import MLX
import MLXLMCommon
import MLXVLM

enum MLXVLMVideoInputAdapterError: Error {
    case invalidInput
}

/// Experimental, fixed-SmolVLM2 video preparation for frames already sampled by us.
/// The video runtime consumes Prepared.input directly. Do not pass these
/// frames back through MediaProcessing.asProcessedSequence: it can silently drop them.
enum MLXVLMVideoInputAdapter {
    struct Prepared {
        let input: LMInput
        let timestamps: [CMTime]
        let clipDuration: CMTime
        let sampledSpan: CMTime
    }

    static func prepare(frames: [MLXVLMVideoSnapshot.Frame], clipDuration: CMTime,
                        prompt: String, configuration: SmolVLMProcessorConfiguration,
                        tokenizer: any MLXLMCommon.Tokenizer) async throws -> Prepared {
        try Task.checkCancellation()
        let mediaTokens = ["<image>", "<fake_token_around_image>", "<global-img>"]
        guard validTime(clipDuration), clipDuration > .zero,
              clipDuration.seconds <= MLXVLMVideoSnapshot.maxDurationSeconds,
              (1...MLXVLMVideoSnapshot.maxFrames).contains(frames.count),
              !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              prompt.utf8.count <= 8192, !mediaTokens.contains(where: prompt.contains),
              configuration.imageMean == [0.5, 0.5, 0.5],
              configuration.imageStd == [0.5, 0.5, 0.5],
              configuration.size.longestEdge == 2048,
              configuration.maxImageSize.longestEdge == 512,
              configuration.imageSequenceLength == 64,
              configuration.videoSampling.fps == 1,
              configuration.videoSampling.maxFrames == 64 else {
            throw MLXVLMVideoInputAdapterError.invalidInput
        }
        var previous: CMTime?
        for frame in frames {
            guard frame.image.width > 0, frame.image.height > 0,
                  frame.image.width <= 512, frame.image.height <= 512,
                  validTime(frame.requestedTime), validTime(frame.actualTime),
                  frame.requestedTime >= .zero, frame.requestedTime < clipDuration,
                  frame.actualTime >= .zero, frame.actualTime < clipDuration,
                  previous.map({ frame.actualTime > $0 }) ?? true else {
                throw MLXVLMVideoInputAdapterError.invalidInput
            }
            previous = frame.actualTime
        }

        // Match the pinned SmolVLM2 video block, using the actual clip duration.
        // The model's textual times round UP to whole seconds; exact times remain below.
        var block = "You are provided the following series of \(frames.count) frames from a \(format(clipDuration)) [H:MM:SS] video.\n"
        let imageBlock = "<fake_token_around_image><global-img>"
            + String(repeating: "<image>", count: 64) + "<fake_token_around_image>"
        for frame in frames {
            block += "\nFrame from \(format(frame.actualTime)):" + imageBlock
        }
        block += "\n\n"
        let videoFrames = frames.map {
            UserInput.VideoFrame(image: .ciImage(CIImage(cgImage: $0.image)), timeStamp: $0.actualTime)
        }
        let input = UserInput(prompt: block + prompt, videos: [.frames(videoFrames)])
        var messages = SmolVLM2MessageGenerator().generate(from: input)
        messages.insert([
            "role": "system", "content": [["type": "text", "text":
                "You are a helpful assistant that can understand videos. Describe what type of video this is and what's happening in it."]]
        ], at: 0)
        let tokens = try tokenizer.applyChatTemplate(messages: messages, tools: nil, additionalContext: nil)
        try Task.checkCancellation()
        var arrays: [MLXArray] = []
        for frame in frames {
            try Task.checkCancellation()
            // Fixed upstream SmolVLM2 video transforms, with no frame selection/tiling.
            let image = CIImage(cgImage: frame.image).toSRGB()
                .resampled(to: CGSize(width: 512, height: 512), method: .lanczos)
                .normalized(mean: configuration.imageMeanTuple, std: configuration.imageStdTuple)
            arrays.append(image.asMLXArray())
            try Task.checkCancellation()
        }
        let pixels = concatenated(arrays, axis: 0).transposed(0, 2, 3, 1)
        let tokenArray = MLXArray(tokens).expandedDimensions(axis: 0)
        let prepared = LMInput(text: .init(tokens: tokenArray, mask: ones(like: tokenArray)),
            image: .init(pixels: pixels, frames: frames.indices.map { THW($0, 512, 512) }))
        try Task.checkCancellation()
        return Prepared(input: prepared, timestamps: frames.map(\.actualTime), clipDuration: clipDuration,
                        sampledSpan: frames.last!.actualTime - frames.first!.actualTime)
    }

    private static func validTime(_ time: CMTime) -> Bool {
        time.isNumeric && time.seconds.isFinite && time.epoch == 0
    }

    private static func format(_ time: CMTime) -> String {
        let seconds = Int(ceil(time.seconds))
        return String(format: "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }
}
