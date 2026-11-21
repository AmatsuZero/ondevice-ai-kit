#pragma once
#include <cstddef>

namespace pad_probe {
// Fixed 128x128 OpenCV BGR input -> original ONNX RGB 1x3x128x128.
inline void rgbNchw(const unsigned char *bgr, std::size_t stride, float *output) {
    constexpr double means[] = {151.2405, 119.5950, 107.8395};
    constexpr double stds[] = {63.0105, 56.4570, 55.0035};
    for (int c = 0; c < 3; ++c)
        for (int y = 0; y < 128; ++y)
            for (int x = 0; x < 128; ++x)
                output[c * 16384 + y * 128 + x] = static_cast<float>(
                    (bgr[y * stride + x * 3 + (2 - c)] - means[c]) / stds[c]);
}
}
