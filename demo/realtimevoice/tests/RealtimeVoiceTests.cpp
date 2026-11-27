#include "RealtimeVoice.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <vector>

static int gFailures = 0;
#define CHECK(cond, msg) do { if (!(cond)) { std::printf("FAIL: %s (line %d)\n", (msg), __LINE__); ++gFailures; } } while (0)

static const double kPi = 3.14159265358979323846;

static std::vector<float> makeTone(int sr, double f, double amp, int n, double phase) {
  std::vector<float> x(n);
  for (int i = 0; i < n; ++i) x[i] = (float)(amp * std::sin(2 * kPi * f * i / sr + phase));
  return x;
}

static std::vector<float> makeVowel(int sr, double f0, double amp, int n) {
  std::vector<float> x(n, 0.f);
  for (int h = 1; h <= 10; ++h) {
    double f = f0 * h;
    if (f >= 0.45 * sr) break;
    double ph = 0.37 * h;
    for (int i = 0; i < n; ++i) x[i] += (float)(amp * std::sin(2 * kPi * f * i / sr + ph));
  }
  return x;
}

static double rmsOf(const std::vector<float>& x, int start, int n) {
  if (start < 0) start = 0;
  double e = 0; int c = 0;
  for (int i = start; i < start + n && i < (int)x.size(); ++i) { e += (double)x[i] * x[i]; ++c; }
  return c ? std::sqrt(e / c) : 0.0;
}

static double dominantFreq(const std::vector<float>& x, int sr, int start, int n, double fmin, double fmax) {
  if (start < 0 || n <= 0 || start + n > (int)x.size()) return 0.0;
  double best = -1.0, bf = fmin;
  const int steps = 480;
  for (int s = 0; s <= steps; ++s) {
    double f = fmin + (fmax - fmin) * s / steps;
    double w = 2 * kPi * f / sr;
    double re = 0, im = 0;
    for (int i = 0; i < n; ++i) {
      double win = 0.5 - 0.5 * std::cos(2 * kPi * i / n);
      double v = (double)x[start + i] * win;
      re += v * std::cos(w * i);
      im -= v * std::sin(w * i);
    }
    double mag = re * re + im * im;
    if (mag > best) { best = mag; bf = f; }
  }
  return bf;
}

struct GuardResult { bool tripped; int tripSample; double outRms, inRms; };

static GuardResult runGuard(int sr, const std::vector<float>& mic, int block, bool* allSafe, RVRole role = RV_ANNOUNCER) {
  RVSession* s = rv_create(sr, role);
  GuardResult r; r.tripped = false; r.tripSample = -1; r.outRms = 0.0; r.inRms = 0.0;
  bool anyUnsafe = false;
  double energy = 0, inEnergy = 0; int cnt = 0;
  if (!s) { if (allSafe) *allSafe = false; return r; }
  std::vector<float> proc(mic.size(), 0.f);
  rv_process(s, mic.data(), proc.data(), mic.size());
  for (size_t i = 0; i < proc.size(); ++i) inEnergy += (double)proc[i] * proc[i];
  r.inRms = proc.empty() ? 0.0 : std::sqrt(inEnergy / proc.size());
  for (int i = 0; i < (int)proc.size(); i += block) {
    int n = std::min(block, (int)proc.size() - i);
    std::vector<float> out(proc.begin() + i, proc.begin() + i + n);
    int rc = rv_speaker_guard(s, mic.data() + i, out.data(), (size_t)n);
    if (rc != 0) { anyUnsafe = true; if (r.tripSample < 0) r.tripSample = i; }
    for (int j = 0; j < n; ++j) { energy += (double)out[j] * out[j]; ++cnt; }
    r.tripped = (rc != 0);
  }
  r.outRms = cnt ? std::sqrt(energy / cnt) : 0.0;
  if (allSafe) *allSafe = !anyUnsafe;
  rv_destroy(s);
  return r;
}

static void checkPitch(int sr, RVRole role, double ratio, double freq, const char* label) {
  const int n = sr * 3 / 2;
  std::vector<float> x = makeTone(sr, freq, 0.6, n, 0.0);
  RVSession* s = rv_create(sr, role);
  CHECK(s != nullptr, label);
  if (!s) return;
  std::vector<float> y(n, 0.f);
  rv_process(s, x.data(), y.data(), (size_t)n);
  const int wlen = 4096;
  double fd = dominantFreq(y, sr, n - wlen, wlen, 70.0, 600.0);
  double expect = freq * ratio;
  CHECK(std::fabs(fd - expect) < 0.06 * expect, label);
  std::printf("  %s sr=%d f0=%.0f expect=%.1f got=%.1f\n", label, sr, freq, expect, fd);
  rv_destroy(s);
}

static void runSr(int sr) {
  const int n = sr;
  std::vector<float> x = makeTone(sr, 220.0, 0.6, n, 0.0);

  const double ru = std::pow(2.0, -4.0 / 12.0);
  const double rg = std::pow(2.0, 4.0 / 12.0);
  const double freqs[3] = { 130.0, 220.0, 300.0 };
  for (int fi = 0; fi < 3; ++fi) {
    checkPitch(sr, RV_UNCLE, ru, freqs[fi], "uncle pitch");
    checkPitch(sr, RV_GIRL, rg, freqs[fi], "girl pitch");
    checkPitch(sr, RV_ANNOUNCER, 1.0, freqs[fi], "announcer pitch");
  }

  {
    RVSession* s = rv_create(sr, RV_ANNOUNCER);
    std::vector<float> in(n * 2, 0.f);
    for (int i = n / 5; i < n; ++i) in[i] = 0.5f * (float)std::sin(2 * kPi * 180 * i / sr);
    std::vector<float> out(in.size(), 0.f);
    rv_process(s, in.data(), out.data(), in.size());
    double head = rmsOf(out, 0, n / 5 - 512);
    double mid = rmsOf(out, n / 3, n / 4);
    double tail = rmsOf(out, (int)in.size() - n / 3, n / 4);
    CHECK(head < 0.02, "envelope head silent");
    CHECK(mid > 0.05, "envelope mid loud");
    CHECK(tail < 0.02, "envelope tail silent");
    rv_destroy(s);
  }

  {
    std::vector<float> z(n, 0.f), out(n, 1.f);
    RVSession* s1 = rv_create(sr, RV_UNCLE);
    RVSession* s2 = rv_create(sr, RV_GIRL);
    rv_process(s1, z.data(), out.data(), n);
    CHECK(rmsOf(out, 0, n) < 1e-6, "uncle silence");
    std::fill(out.begin(), out.end(), 1.f);
    rv_process(s2, z.data(), out.data(), n);
    CHECK(rmsOf(out, 0, n) < 1e-6, "girl silence");
    rv_destroy(s1); rv_destroy(s2);
  }

  {
    RVSession* a = rv_create(sr, RV_GIRL);
    RVSession* b = rv_create(sr, RV_GIRL);
    std::vector<float> y1(n, 0.f), y2(n, 0.f);
    rv_process(a, x.data(), y1.data(), n);
    for (int i = 0; i < n; i += 128) {
      int m = std::min(128, n - i);
      rv_process(b, x.data() + i, y2.data() + i, m);
    }
    double diff = 0; for (int i = 0; i < n; ++i) diff = std::max(diff, (double)std::fabs(y1[i] - y2[i]));
    CHECK(diff < 1e-5, "split vs whole");
    rv_destroy(a); rv_destroy(b);
  }

  {
    CHECK(rv_create(sr, (RVRole)99) == nullptr, "invalid role rejected");
    CHECK(rv_create(0, RV_UNCLE) == nullptr, "invalid rate zero rejected");
    CHECK(rv_create(-8000, RV_UNCLE) == nullptr, "invalid rate negative rejected");
    CHECK(rv_create(7999, RV_UNCLE) == nullptr, "invalid rate below 8000 rejected");
    RVSession* a = rv_create(sr, RV_UNCLE);
    RVSession* b = rv_create(sr, RV_UNCLE);
    std::vector<float> y1(n, 0.f), y2(n, 0.f);
    rv_process(a, x.data(), y1.data(), n);
    rv_process(b, x.data(), y2.data(), n);
    double diff = 0; for (int i = 0; i < n; ++i) diff = std::max(diff, (double)std::fabs(y1[i] - y2[i]));
    CHECK(diff < 1e-6, "fresh sessions identical");
    rv_destroy(a); rv_destroy(b);
  }

  {
    std::vector<float> fb = makeTone(sr, 800.0, 0.7, n, 0.0);
    bool safe = true;
    GuardResult gr = runGuard(sr, fb, 256, &safe);
    CHECK(gr.tripped, "feedback trips guard");
    CHECK(gr.tripSample >= 0 && gr.tripSample < n, "feedback trips promptly");
    CHECK(!safe, "feedback not safe");

    std::vector<float> off = makeTone(sr, 1777.0, 0.7, n, 0.0);
    GuardResult orr = runGuard(sr, off, 256, nullptr);
    CHECK(orr.tripped, "off-grid tone trips guard");
    CHECK(orr.tripSample >= 0 && orr.tripSample < n, "off-grid tone trips promptly");

    std::vector<float> off2 = makeTone(sr, 1663.0, 0.7, n, 0.0);
    std::vector<float> off3 = makeTone(sr, 2237.0, 0.7, n, 0.0);
    CHECK(runGuard(sr, off2, 256, nullptr).tripped, "1663 Hz feedback trips guard");
    CHECK(runGuard(sr, off3, 256, nullptr).tripped, "2237 Hz feedback trips guard");

    std::vector<float> vowel = makeVowel(sr, 137.0, 0.12, n);
    bool vSafe = true;
    GuardResult vr = runGuard(sr, vowel, 256, &vSafe);
    CHECK(!vr.tripped && vSafe, "vowel stays safe");
    CHECK(vr.inRms > 0.02, "normal speech produces nonzero processed output");
    CHECK(vr.outRms > 0.01 && vr.outRms < 0.6 * vr.inRms + 1e-3, "safe guard gain bounded");

    GuardResult g64 = runGuard(sr, fb, 64, nullptr);
    GuardResult g512 = runGuard(sr, fb, 512, nullptr);
    CHECK(g64.tripped && g512.tripped, "feedback trips at both block sizes");
    CHECK(std::fabs(g64.tripSample - g512.tripSample) < 0.12 * sr, "guard time normalized");
  }

  {
    // Mic and processed output are independent: the guard must shape the
    // processed buffer, never re-amplify the raw mic.
    RVSession* s = rv_create(sr, RV_ANNOUNCER);
    std::vector<float> mic = makeVowel(sr, 137.0, 0.02, n);
    std::vector<float> out = makeTone(sr, 1000.0, 0.15, n, 0.0);
    int rc = rv_speaker_guard(s, mic.data(), out.data(), n);
    CHECK(rc == 0, "quiet vowel mic does not trip guard");
    double fd = dominantFreq(out, sr, n - 4096, 4096, 700.0, 1400.0);
    CHECK(std::fabs(fd - 1000.0) < 100.0, "guard output frequency follows processed buffer");
    CHECK(rmsOf(out, n - 4096, 4096) > 0.01, "guard keeps processed tone audible");
    rv_destroy(s);
  }
  {
    RVSession* s = rv_create(sr, RV_ANNOUNCER);
    std::vector<float> mic(n, 0.f);
    std::vector<float> out = makeTone(sr, 1200.0, 0.15, n, 0.0);
    int rc = rv_speaker_guard(s, mic.data(), out.data(), n);
    CHECK(rc == 0, "silent mic does not trip guard");
    CHECK(rmsOf(out, n / 2, n / 4) > 0.01, "zero mic keeps nonzero processed output");
    rv_destroy(s);
  }
  {
    RVSession* s = rv_create(sr, RV_ANNOUNCER);
    std::vector<float> mic = makeTone(sr, 800.0, 0.7, n, 0.0);
    std::vector<float> out(n, 0.f);
    rv_speaker_guard(s, mic.data(), out.data(), n);
    CHECK(rmsOf(out, 0, n) < 1e-6, "tripped guard keeps processed zeros");
    rv_destroy(s);
  }

  {
    RVSession* s = rv_create(sr, RV_ANNOUNCER);
    std::vector<float> fb = makeTone(sr, 800.0, 0.7, n, 0.0);
    std::vector<float> proc(n, 0.f);
    rv_process(s, fb.data(), proc.data(), n);
    std::vector<float> out(n, 1.f);
    rv_speaker_guard(s, fb.data(), out.data(), n);
    std::vector<float> next(128, 1.f);
    int rc = rv_speaker_guard(s, proc.data(), next.data(), 128);
    CHECK(rc == 1, "sticky trip returns 1");
    bool allZero = true; for (float v : next) if (v != 0.f) { allZero = false; break; }
    CHECK(allZero, "sticky trip zeroes block");
    rv_destroy(s);
  }
}

int main() {
  runSr(44100);
  runSr(48000);
  if (gFailures) { std::printf("%d failure(s)\n", gFailures); return 1; }
  std::printf("All realtimevoice tests passed\n");
  return 0;
}
