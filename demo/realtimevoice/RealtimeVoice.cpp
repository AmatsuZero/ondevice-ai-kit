#include "RealtimeVoice.h"

#include <cmath>
#include <new>
#include <vector>

namespace {
constexpr double kPi = 3.14159265358979323846;
constexpr int kNumRes = 24;
constexpr int kWinTable = 2048;
constexpr int kDec = 4;
constexpr int kDwin = 384;
constexpr int kDbuf = 1024;
constexpr int kMinLag = 22;
constexpr int kMaxLag = 200;
constexpr int kPredBuf = 2048;
constexpr float kHowlMinHz = 900.f;
constexpr float kPredResidualMax = 0.04f;
constexpr float kPredEnergyMin = 0.0025f;
constexpr float kPredLowRatioMax = 0.30f;
constexpr float kResFreqs[kNumRes] = {
  100.f, 121.f, 146.f, 177.f, 214.f, 259.f, 313.f, 379.f,
  459.f, 555.f, 672.f, 813.f, 984.f, 1191.f, 1442.f, 1745.f,
  2111.f, 2555.f, 3092.f, 3741.f, 4527.f, 5478.f, 6629.f, 8021.f
};

struct Resonator {
  float b0, b2, a1, a2;
  float x1, x2, y1, y2;
};

inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
inline float sanitize(float x) { return (!(x == x) || x < -1e12f || x > 1e12f) ? 0.f : x; }
inline float tauCoef(float tauSec, int sr) {
  return 1.f - (float)std::exp(-1.0 / (double)(tauSec * (float)sr));
}
inline float lpCoef(float fcHz, int sr) {
  double fc = fcHz, lim = 0.45 * (double)sr;
  if (fc > lim) fc = lim;
  return 1.f - (float)std::exp(-2.0 * kPi * fc / (double)sr);
}
inline float tap(const std::vector<float>& b, int B, float pos) {
  float x = (float)std::fmod((double)pos, (double)B);
  if (x < 0.f) x += (float)B;
  int i0 = (int)x; if (i0 >= B) i0 = B - 1;
  int i1 = i0 + 1; if (i1 >= B) i1 = 0;
  float f = x - (float)i0;
  return b[i0] + f * (b[i1] - b[i0]);
}
inline float winAt(const std::vector<float>& table, int ph, int span) {
  double u = (double)ph * (double)kWinTable / (double)span;
  int i = (int)u;
  if (i < 0) i = 0; else if (i >= kWinTable) i = kWinTable - 1;
  return table[i];
}
} // namespace

struct RVSession {
  int sr;
  RVRole role;
  double ratio;
  int span, half, B;
  int minSpan, maxSpan;
  float invSpan;
  std::vector<float> buf;
  std::vector<float> winTable;
  int w, ph;
  double D, base;
  double targetPeriod;
  int f0Jump;
  int dCnt;
  float dLp, aDlp;
  int dw, dsN;
  std::vector<float> dsBuf;
  float aLow, aWarmHi, aGirlHi, aAir, aRumble, aPres;
  float atk, rel, aGain;
  float zLow, zWarmHi, zGirlHi, zAir, zRumble, zPres;
  float env, gain;
  Resonator res[kNumRes];
  float aEnvG, aGuardGain, guardGain, totalEnv, resEnv[kNumRes];
  float aPredLp, zPredLp, lpEnv;
  float predBuf[kPredBuf];
  int predW, predN;
  bool predLatch;
  double badTime;
  bool guardTripped;
};

extern "C" RVSession* rv_create(int sample_rate, RVRole role) {
  if (sample_rate < 8000 || sample_rate > 768000) return nullptr;
  if (role != RV_UNCLE && role != RV_GIRL && role != RV_ANNOUNCER) return nullptr;
  RVSession* s = new (std::nothrow) RVSession();
  if (!s) return nullptr;

  s->sr = sample_rate;
  s->role = role;
  s->ratio = (role == RV_UNCLE) ? std::pow(2.0, -4.0 / 12.0)
           : (role == RV_GIRL)  ? std::pow(2.0, 4.0 / 12.0) : 1.0;

  s->maxSpan = (int)std::llround((double)sample_rate * 0.10);
  s->minSpan = 64;
  double denom = std::fabs(1.0 - s->ratio);
  if (denom < 1e-6) denom = 1e-6;
  double initPeriod = (double)sample_rate / 180.0;
  int span = (int)std::llround(2.0 * initPeriod / denom);
  if (span < s->minSpan) span = s->minSpan;
  if (span > s->maxSpan) span = s->maxSpan;
  if (span & 1) ++span;
  s->span = span;
  s->half = span >> 1;
  s->B = s->maxSpan * 2;
  s->invSpan = 1.f / (float)span;
  s->buf.assign((size_t)s->B, 0.f);
  s->winTable.resize(kWinTable);
  for (int i = 0; i < kWinTable; ++i)
    s->winTable[i] = 0.5f - 0.5f * (float)std::cos(2.0 * kPi * (double)i / (double)kWinTable);

  const double D = (1.0 - s->ratio) * (double)span;
  s->D = D;
  s->base = 4.0 + (D < 0.0 ? -D : 0.0); /* minimal readhead delay */
  s->w = 0; s->ph = 0;
  s->targetPeriod = 0.0; s->f0Jump = 0;
  s->dCnt = 0; s->dLp = 0.f; s->aDlp = lpCoef(800.f, sample_rate);
  s->dw = 0; s->dsN = 0;
  s->dsBuf.assign(kDbuf, 0.f);

  s->aLow = lpCoef(380.f, sample_rate);
  s->aWarmHi = lpCoef(3200.f, sample_rate);
  s->aGirlHi = lpCoef(2400.f, sample_rate);
  s->aAir = lpCoef(6000.f, sample_rate);
  s->aRumble = lpCoef(110.f, sample_rate);
  s->aPres = lpCoef(2800.f, sample_rate);
  s->atk = tauCoef(0.006f, sample_rate);
  s->rel = tauCoef(0.16f, sample_rate);
  s->aGain = tauCoef(0.03f, sample_rate);
  s->zLow = s->zWarmHi = s->zGirlHi = s->zAir = s->zRumble = s->zPres = 0.f;
  s->env = 0.f; s->gain = 1.f;

  s->aEnvG = tauCoef(0.05f, sample_rate);
  s->aGuardGain = tauCoef(0.005f, sample_rate);
  s->guardGain = 0.f;
  s->totalEnv = 0.f;
  for (int i = 0; i < kNumRes; ++i) {
    s->resEnv[i] = 0.f;
    Resonator& r = s->res[i];
    double f0 = kResFreqs[i], lim = 0.45 * (double)sample_rate;
    if (f0 > lim) f0 = lim;
    double w0 = 2.0 * kPi * f0 / (double)sample_rate;
    double alpha = std::sin(w0) / (2.0 * 4.0);
    double a0 = 1.0 + alpha;
    r.b0 = (float)(alpha / a0);
    r.b2 = (float)(-alpha / a0);
    r.a1 = (float)(-2.0 * std::cos(w0) / a0);
    r.a2 = (float)((1.0 - alpha) / a0);
    r.x1 = r.x2 = r.y1 = r.y2 = 0.f;
  }
  s->aPredLp = lpCoef(600.f, sample_rate);
  s->zPredLp = 0.f; s->lpEnv = 0.f;
  for (int i = 0; i < kPredBuf; ++i) s->predBuf[i] = 0.f;
  s->predW = 0; s->predN = 0; s->predLatch = false;
  s->badTime = 0.0;
  s->guardTripped = false;
  return s;
}

extern "C" void rv_destroy(RVSession* s) { delete s; }

extern "C" void rv_process(RVSession* s, const float* input, float* output, size_t frames) {
  if (!s || !output) return;

  if (s->role == RV_ANNOUNCER) {
    float zRumble = s->zRumble, env = s->env, gain = s->gain, zPres = s->zPres;
    const float aRumble = s->aRumble, atk = s->atk, rel = s->rel, aGain = s->aGain, aPres = s->aPres;
    for (size_t k = 0; k < frames; ++k) {
      float x = input ? sanitize(input[k]) : 0.f;
      zRumble += aRumble * (x - zRumble);
      float v = x - zRumble;
      float av = std::fabs(v);
      env += ((av > env) ? atk : rel) * (av - env);
      float target = 0.22f / (env > 0.02f ? env : 0.02f);
      if (target > 2.4f) target = 2.4f; else if (target < 0.45f) target = 0.45f;
      gain += aGain * (target - gain);
      float y = v * gain;
      zPres += aPres * (y - zPres);
      y += 0.20f * (y - zPres);
      output[k] = clampf(y, -0.99f, 0.99f);
    }
    s->zRumble = zRumble; s->env = env; s->gain = gain; s->zPres = zPres;
    return;
  }

  std::vector<float>& buf = s->buf;
  const std::vector<float>& wt = s->winTable;
  const int B = s->B;
  int span = s->span, half = s->half;
  float invSpan = s->invSpan;
  double D = s->D, base = s->base;
  int w = s->w, ph = s->ph;
  const bool girl = (s->role == RV_GIRL);
  const double denom = std::fabs(1.0 - s->ratio);
  float zLow = s->zLow, zHi = girl ? s->zGirlHi : s->zWarmHi, zAir = s->zAir;
  const float aLow = s->aLow, aWarmHi = s->aWarmHi, aGirlHi = s->aGirlHi, aAir = s->aAir;
  int dCnt = s->dCnt;
  float dLp = s->dLp;
  const float aDlp = s->aDlp;
  int dw = s->dw, dsN = s->dsN;
  std::vector<float>& ds = s->dsBuf;

  for (size_t k = 0; k < frames; ++k) {
    float x = input ? sanitize(input[k]) : 0.f;

    dLp += aDlp * (x - dLp);
    if (++dCnt >= kDec) {
      dCnt = 0;
      ds[dw] = dLp;
      if (++dw >= kDbuf) dw = 0;
      if (dsN < kDbuf) ++dsN;
    }

    if (ph == 0) {
      if (s->targetPeriod > 0.0) {
        int ns = (int)std::llround(2.0 * s->targetPeriod / denom);
        if (ns < s->minSpan) ns = s->minSpan;
        if (ns > s->maxSpan) ns = s->maxSpan;
        if (ns & 1) ++ns;
        if (ns != span) {
          span = ns; half = span >> 1; invSpan = 1.f / (float)span;
          D = (1.0 - s->ratio) * (double)span;
          base = 4.0 + (D < 0.0 ? -D : 0.0);
        }
      }
      if (dsN >= kDwin + kMaxLag) {
        double e0 = 0.0;
        for (int i = 0; i < kDwin; ++i) {
          float v = ds[(dw - 1 - i + kDbuf) % kDbuf];
          e0 += (double)v * v;
        }
        if (e0 > 1e-5) {
          double corr[kMaxLag + 1];
          double best = 0.0;
          int bestLag = -1;
          for (int L = kMinLag; L <= kMaxLag; ++L) {
            double cr = 0.0, eL = 0.0;
            for (int i = 0; i < kDwin; ++i) {
              float a = ds[(dw - 1 - i + kDbuf) % kDbuf];
              float b = ds[(dw - 1 - i - L + kDbuf) % kDbuf];
              cr += (double)a * b;
              eL += (double)b * b;
            }
            double n = cr / std::sqrt(e0 * eL + 1e-12);
            corr[L] = n;
            if (L == kMinLag || n > best) { best = n; bestLag = L; }
          }
          (void)bestLag;
          if (best > 0.55) {
            int chosen = -1;
            for (int L = kMinLag; L <= kMaxLag; ++L)
              if (corr[L] >= 0.85 * best) { chosen = L; break; }
            if (chosen > 0) {
              int l0 = chosen > kMinLag ? chosen - 1 : chosen;
              int l1 = chosen < kMaxLag ? chosen + 1 : chosen;
              double den = corr[l0] - 2.0 * corr[chosen] + corr[l1];
              double d = (std::fabs(den) > 1e-12) ? 0.5 * (corr[l0] - corr[l1]) / den : 0.0;
              double est = ((double)chosen + d) * (double)kDec;
              double lo = (double)s->sr / 600.0, hi = (double)s->sr / 70.0;
              if (est < lo) est = lo; else if (est > hi) est = hi;
              if (s->targetPeriod <= 0.0) {
                s->targetPeriod = est;
              } else if (std::fabs(est - s->targetPeriod) < 0.15 * s->targetPeriod) {
                s->targetPeriod = 0.7 * s->targetPeriod + 0.3 * est;
              } else if (++s->f0Jump >= 2) {
                s->targetPeriod = est; s->f0Jump = 0;
              }
            }
          }
        }
      }
    }

    buf[w] = x;
    if (++w >= B) w = 0;

    float fa = (float)ph * invSpan;
    float sa = tap(buf, B, (float)((double)w - (base + D * (double)fa))) * winAt(wt, ph, span);
    int pb = ph + half; if (pb >= span) pb -= span;
    float fb = (float)pb * invSpan;
    float sb = tap(buf, B, (float)((double)w - (base + D * (double)fb))) * winAt(wt, pb, span);
    float y = sa + sb;

    if (girl) {
      zHi += aGirlHi * (y - zHi);
      zAir += aAir * (y - zAir);
      y += 0.30f * (y - zHi) + 0.14f * (y - zAir);
    } else {
      zLow += aLow * (y - zLow);
      zHi += aWarmHi * (y - zHi);
      y = 0.80f * y + 0.28f * zLow + 0.12f * zHi;
    }
    output[k] = clampf(y, -1.f, 1.f);
    if (++ph >= span) ph = 0;
  }

  s->w = w; s->ph = ph; s->span = span; s->half = half;
  s->invSpan = invSpan; s->D = D; s->base = base;
  s->zLow = zLow; s->zAir = zAir;
  s->dCnt = dCnt; s->dLp = dLp; s->dw = dw; s->dsN = dsN;
  if (girl) s->zGirlHi = zHi; else s->zWarmHi = zHi;
}

extern "C" int rv_speaker_guard(RVSession* s, const float* mic, float* output, size_t frames) {
  if (!s) return 1;
  if (s->guardTripped) {
    if (output) for (size_t i = 0; i < frames; ++i) output[i] = 0.f;
    return 1;
  }
  if (!output) return 0;

  float g = s->guardGain;
  const float targetGain = 0.26f;
  const float aGG = s->aGuardGain;
  const float aEnvG = s->aEnvG;
  const float aPredLp = s->aPredLp;
  float totalEnv = s->totalEnv;
  float lpEnv = s->lpEnv;
  float zPredLp = s->zPredLp;
  double badTime = s->badTime;
  const double tripAt = 0.25 * (double)s->sr;
  int predW = s->predW, predN = s->predN;
  bool predLatch = s->predLatch;
  int pw = (int)std::llround(0.020 * (double)s->sr);
  if (pw < 32) pw = 32;
  if (pw > kPredBuf - 2) pw = kPredBuf - 2;

  for (size_t k = 0; k < frames; ++k) {
    float x = mic ? sanitize(mic[k]) : 0.f;
    totalEnv += aEnvG * (x * x - totalEnv);
    zPredLp += aPredLp * (x - zPredLp);
    lpEnv += aEnvG * (zPredLp * zPredLp - lpEnv);

    float* pb = s->predBuf;
    pb[predW] = x;
    if (++predW >= kPredBuf) predW = 0;
    if (predN < kPredBuf) ++predN;

    float maxRes = 0.f;
    for (int i = 0; i < kNumRes; ++i) {
      Resonator& r = s->res[i];
      float y = r.b0 * x + r.b2 * r.x2 - r.a1 * r.y1 - r.a2 * r.y2;
      r.x2 = r.x1; r.x1 = x;
      r.y2 = r.y1; r.y1 = y;
      float e = y * y;
      float& re = s->resEnv[i];
      re += aEnvG * (e - re);
      if (re > maxRes) maxRes = re;
    }
    float conc = maxRes / (totalEnv + 1e-12f);

    // A single sinusoid satisfies x[n] + x[n-2] = a*x[n-1] with a = 2*cos(w).
    // Fit `a` over a rolling 20 ms window and test the normalized residual.
    if ((k & 15) == 0 && predN >= pw + 2) {
      double suu = 0.0, suv = 0.0, svv = 0.0, sxx = 0.0;
      int end = predW - 1; if (end < 0) end += kPredBuf;
      for (int t = 0; t < pw; ++t) {
        int i1 = end - t; if (i1 < 0) i1 += kPredBuf;
        int i0 = i1 - 1; if (i0 < 0) i0 += kPredBuf;
        int i2 = i1 - 2; if (i2 < 0) i2 += kPredBuf;
        double u = pb[i1] + pb[i2];
        double v = pb[i0];
        suu += u * u; suv += u * v; svv += v * v; sxx += v * v;
      }
      if (svv > 1e-12 && sxx > 1e-12) {
        double a = suv / svv;
        double residual = suu - suv * a;
        if (residual < 0.0) residual = 0.0;
        double rel = residual / sxx;
        double ca = 0.5 * a;
        if (ca > 1.0) ca = 1.0; else if (ca < -1.0) ca = -1.0;
        double freq = std::acos(ca) * (double)s->sr / (2.0 * kPi);
        double meanEnergy = sxx / pw;
        predLatch = (rel < (double)kPredResidualMax &&
                     freq > (double)kHowlMinHz &&
                     meanEnergy > (double)kPredEnergyMin &&
                     lpEnv < kPredLowRatioMax * totalEnv);
      } else {
        predLatch = false;
      }
    }

    if ((conc > 0.55f && totalEnv > 0.0064f) || predLatch) badTime += 1.0;
    else badTime -= 2.0;
    if (badTime < 0.0) badTime = 0.0;
    if (badTime > tripAt) { s->guardTripped = true; break; }

    g += aGG * (targetGain - g);
    float y = sanitize(output[k]);
    output[k] = clampf(g * y, -0.22f, 0.22f);
  }

  if (s->guardTripped)
    for (size_t i = 0; i < frames; ++i) output[i] = 0.f;
  s->guardGain = g; s->totalEnv = totalEnv; s->badTime = badTime;
  s->zPredLp = zPredLp; s->lpEnv = lpEnv;
  s->predW = predW; s->predN = predN; s->predLatch = predLatch;
  return s->guardTripped ? 1 : 0;
}
