#include "PadPreprocessing.hpp"
#include <cassert>
#include <cmath>
#include <iostream>
#include <vector>

int main() {
    // Deliberately padded rows catch incorrect assumptions about cv::Mat stride.
    constexpr int stride = 128 * 3 + 7;
    std::vector<unsigned char> bgr(stride * 128, 0);
    for (int y = 0; y < 128; ++y) for (int x = 0; x < 128; ++x) {
        bgr[y * stride + x * 3] = 11;
        bgr[y * stride + x * 3 + 1] = 73;
        bgr[y * stride + x * 3 + 2] = 201;
    }
    bgr[127 * stride + 127 * 3] = 255;
    std::vector<float> tensor(3 * 128 * 128);
    pad_probe::rgbNchw(bgr.data(), stride, tensor.data());
    auto near = [](float a, double b) { assert(std::abs(a - b) < 0.000001); };
    near(tensor[0], (201.0 - 151.2405) / 63.0105);
    near(tensor[16384], (73.0 - 119.5950) / 56.4570);
    near(tensor[32768], (11.0 - 107.8395) / 55.0035);
    near(tensor[49151], (255.0 - 107.8395) / 55.0035);
    near(tensor[16383], tensor[0]);
    for (float v : tensor) assert(std::isfinite(v));
    std::cout << "PAD RGB/NCHW channel, normalization, stride and extent checks passed\n";
}
