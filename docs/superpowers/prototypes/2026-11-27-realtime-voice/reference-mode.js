(function () {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };
  var host = $('referenceMode');
  var submodes = document.querySelectorAll('[data-file-submode]');
  var filePanel = $('filePanel');
  if (!host || !submodes.length) return;

  var S_REF = 'clips/reference-sample.wav';
  var S_CONTENT = 'clips/reference-content.wav';
  var S_OUT = 'clips/reference-converted.wav';


  var CSS = '.rm{--f:#123329;--m:#A8E6C8;max-width:560px;margin:0 auto;color:var(--ink,#1c2b24);font-size:14px;line-height:1.5}' +
    '.rm *{box-sizing:border-box}.rm button,.rm a{font:inherit}.rm audio{display:block;width:100%;height:36px;margin-top:10px}' +
    '.rm audio[hidden]{display:none}.rm .rm-wave{display:block;width:100%;height:58px;margin-top:10px;border-radius:10px;background:linear-gradient(180deg,rgba(168,230,200,.16),rgba(168,230,200,.04));}' +
    '.rm .rm-hero{background:linear-gradient(135deg,#123329,#1d4b3d);color:#fff;border-radius:18px;padding:20px 22px;display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:14px 20px}' +
    '.rm .rm-hero-copy{min-width:0}.rm .rm-hero-title{margin:0;font-size:21px;line-height:1.3;font-weight:700;color:#fff;text-align:left}.rm .rm-hero .rm-desc{opacity:.85;font-size:12px;line-height:1.5;margin:6px 0 0;text-align:left}.rm .rm-hero .rm-btn{justify-self:end}' +
    '.rm .rm-btn{border:1px solid var(--line,#d8e2dc);background:#fff;color:var(--forest,#123329);border-radius:10px;padding:8px 12px;min-height:44px;cursor:pointer;font-size:14px;line-height:1.35;font-weight:600;display:inline-flex;align-items:center;justify-content:center;text-align:center;gap:6px}' +
    '.rm .rm-btn:hover:not(:disabled){border-color:var(--m,#A8E6C8);background:#f4fbf7}.rm .rm-btn:focus-visible{outline:3px solid rgba(18,51,41,.35);outline-offset:2px}.rm .rm-btn:disabled{opacity:.55;cursor:not-allowed;background:#eef2ef;color:#8b9a91;border-color:#dfe7e2}' +
    '.rm .rm-primary{background:var(--m,#A8E6C8);border-color:var(--m,#A8E6C8);min-width:116px}.rm .rm-primary:hover:not(:disabled){background:#96dbb8}' +
    '.rm .rm-note{margin:12px 2px 2px;color:var(--muted,#6b7d73);font-size:11px}' +
    '.rm .rm-card{background:var(--surface,#fff);border:1px solid var(--line,#e3ece7);border-radius:14px;padding:14px 16px;margin-top:12px;box-shadow:0 2px 10px rgba(18,51,41,.04)}' +
    '.rm .rm-head{display:flex;align-items:center;gap:9px}.rm .rm-num{width:24px;height:24px;border-radius:8px;background:var(--forest,#123329);color:#fff;font-size:12px;font-weight:700;display:grid;place-items:center;flex:none}' +
    '.rm .rm-title{font-weight:700;font-size:15px}.rm .rm-hint{color:var(--muted,#6b7d73);font-size:11px;margin:4px 0 10px}' +
    '.rm .rm-row{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;align-items:center;max-width:360px}.rm .rm-name{font-size:12px;min-height:18px;margin-top:8px}' +
    '.rm .rm-status{font-size:11px;color:#9a5b2e;min-height:16px;margin-top:6px}.rm .rm-timer{grid-column:1/-1;font-variant-numeric:tabular-nums;font-size:11px;color:var(--muted,#6b7d73)}.rm .rm-timer:empty{display:none}' +
    '.rm .rm-out{display:block;margin-top:10px}.rm .rm-out>div{width:100%;margin-bottom:12px}.rm .rm-wave[hidden]{display:none}' +
    '.rm .rm-label{font-size:11px;font-weight:700;color:var(--muted,#6b7d73)}.rm .rm-dur{font-size:11px;color:var(--muted,#6b7d73)}' +
    '.rm .rm-badge2{display:inline-block;margin-top:10px;font-size:11px;background:#eef7f1;color:var(--forest,#123329);border-radius:999px;padding:3px 9px}' +
    '.rm a.rm-dl{display:inline-block;margin-top:10px;color:var(--forest,#123329);font-weight:700;font-size:12px;text-decoration:underline}' +
    '@media(max-width:480px){.rm .rm-hero{grid-template-columns:1fr;gap:12px}.rm .rm-hero .rm-btn{justify-self:start}.rm .rm-row{gap:6px}.rm .rm-row .rm-btn{padding:8px 6px}}';

  function card(num, title, hint, key, empty) {
    var what = key === 'ref' ? '参考音色' : '内容';
    return '<div class="rm-card"><div class="rm-head"><span class="rm-num">' + num + '</span><span class="rm-title">' + title + '</span></div>' +
      '<p class="rm-hint">' + hint + '</p><div class="rm-row">' +
      '<button type="button" class="rm-btn" id="' + key + 'UploadBtn" aria-label="导入' + what + ' WAV">导入 WAV</button>' +
      '<input id="' + key + 'Upload" type="file" accept=".wav,audio/wav" aria-label="导入' + what + ' WAV" hidden>' +
      '<button type="button" class="rm-btn" id="' + key + 'Record" aria-label="录制' + what + '">录制声音</button>' +
      '<button type="button" class="rm-btn" id="' + key + 'Clear" aria-label="清空' + what + '" disabled>清空</button>' +
      '<span class="rm-timer" id="' + key + 'Timer"></span></div>' +
      '<div class="rm-name" id="' + key + 'Name">' + empty + '</div>' +
      '<canvas class="rm-wave" id="' + key + 'Wave" width="640" height="116" aria-label="' + what + '波形"></canvas>' +
      '<audio id="' + key + 'Audio" controls preload="metadata" aria-label="' + what + '试听" hidden></audio>' +
      '<div class="rm-status" id="' + key + 'Status" role="status" aria-live="polite"></div></div>';
  }

  var HTML = '<div class="rm">' +
    '<div class="rm-hero"><div class="rm-hero-copy"><h2 class="rm-hero-title">参考音色</h2><p class="rm-desc">先录参考音色，再录要说的话。</p></div>' +
    '<button type="button" class="rm-btn rm-primary" id="loadSamples">载入样例</button></div>' +
    '<p class="rm-note">导入仅在本机处理，不上传；样例结果本机预生成，可试听。</p>' +
    card('01', '想变成谁的声音', '上传或录一段参考声音，建议 5–30 秒；至少 0.5 秒，最多 30 秒。', 'ref', '还没有参考音色') +
    card('02', '想说些什么', '上传或录一段要说的内容，时长同样在 0.5–30 秒之间。', 'content', '还没有内容') +
    '<div class="rm-card"><div class="rm-head"><span class="rm-num">03</span><span class="rm-title">听听变化</span></div>' +
    '<p class="rm-hint">样例可查看本机预生成的结果；自定义音频可试听，转换暂未接入。</p>' +
    '<button type="button" class="rm-btn rm-primary" id="convertBtn" disabled>先准备两段音频</button>' +
    '<div class="rm-status" id="convertStatus" role="status" aria-live="polite"></div>' +
    '<div id="resultBox" hidden><span class="rm-badge2">本机预生成样例 · 22.05 kHz</span>' +
    '<p class="rm-note">两轨共用时间轴。波形显示声音强弱，音色请对照试听。</p>' +
    '<div class="rm-out"><div><div class="rm-label">原始内容</div><canvas class="rm-wave" id="resultOriginalWave" width="640" height="116" aria-label="原始内容波形"></canvas><audio id="resultOriginal" controls preload="metadata" aria-label="原始内容试听"></audio><div class="rm-dur" id="resultOriginalDur"></div></div>' +
    '<div><div class="rm-label">转换结果</div><canvas class="rm-wave" id="resultConvertedWave" width="640" height="116" aria-label="转换结果波形"></canvas><audio id="resultConverted" controls preload="metadata" aria-label="转换结果试听"></audio><div class="rm-dur" id="resultConvertedDur"></div></div></div>' +
    '<a class="rm-dl" id="download" href="' + S_OUT + '" download="reference-converted.wav">下载样例结果</a></div></div></div>';

  host.innerHTML = HTML;
  var style = document.createElement('style');
  style.textContent = CSS;
  host.appendChild(style);

  function mk(key) {
    return { key: key, upload: $(key + 'Upload'), uploadBtn: $(key + 'UploadBtn'), rec: $(key + 'Record'),
      clear: $(key + 'Clear'), name: $(key + 'Name'), status: $(key + 'Status'), audio: $(key + 'Audio'),
      wave: $(key + 'Wave'), timer: $(key + 'Timer'), peaks: null, waveToken: 0,
      ready: false, isSample: false, url: null };
  }
  var ref = mk('ref'), content = mk('content');
  ref.color = '#1f9e8e'; content.color = '#2c3e7a';
  var resultBox = $('resultBox'), convertBtn = $('convertBtn'), convertStatus = $('convertStatus');
  var loadSamplesBtn = $('loadSamples');
  var resultOriginal = { audio: $('resultOriginal'), wave: $('resultOriginalWave'), peaks: null, waveToken: 0, color: '#2c3e7a', result: true };
  var resultConverted = { audio: $('resultConverted'), wave: $('resultConvertedWave'), peaks: null, waveToken: 0, color: '#123329', result: true };
  var resultStates = [resultOriginal, resultConverted];
  var ownAudios = [ref.audio, content.audio, resultOriginal.audio, resultConverted.audio].filter(Boolean);
  var waveStates = [ref, content, resultOriginal, resultConverted];

  var active = false, gen = 0, capGen = 0, captureBusy = false, importPending = false;
  var recording = null, stream = null, captureTarget = null, chunks = [];
  var tickTimer = null, stopTimer = null, demoTimer = null;
  var globalNodes = [];
  ['.file-head', '#fixedEffects', '.file-tip', '#fileAudio', '.file-actions', '#fileStatus', '.file-caption'].forEach(function (s) {
    var list = document.querySelectorAll(s);
    for (var i = 0; i < list.length; i++) globalNodes.push({ el: list[i], hidden: list[i].hidden });
  });

  function revoke(u) { if (u && u.indexOf('blob:') === 0) { try { URL.revokeObjectURL(u); } catch (e) {} } }
  function say(s, t) { if (s && s.status) s.status.textContent = t || ''; }
  function setTimer(s, t) { if (s.timer) s.timer.textContent = t || ''; }
  function pauseAll() { ownAudios.forEach(function (a) { try { a.pause(); } catch (e) {} }); }
  var PEAK_COUNT = 160;
  function timeline(state) {
    if (!state || !state.result) return 0;
    var d = 0;
    resultStates.forEach(function (s) {
      var a = s.audio;
      if (a && isFinite(a.duration) && a.duration > d) d = a.duration;
    });
    return d;
  }
  function drawWave(state) {
    if (!state || !state.wave) return;
    var canvas = state.wave, peaks = state.peaks;
    if (!peaks || !peaks.length) { canvas.hidden = true; return; }
    canvas.hidden = false;
    var rect = canvas.getBoundingClientRect();
    var dpr = Math.min(2, window.devicePixelRatio || 1);
    var width = Math.max(1, Math.floor((rect.width || 1) * dpr));
    var height = Math.max(1, Math.floor((rect.height || 1) * dpr));
    if (canvas.width !== width || canvas.height !== height) { canvas.width = width; canvas.height = height; }
    var g = canvas.getContext('2d');
    if (!g) return;
    g.clearRect(0, 0, width, height);
    var mid = height / 2;
    g.strokeStyle = 'rgba(18,51,41,.14)'; g.lineWidth = Math.max(1, dpr);
    g.beginPath(); g.moveTo(0, mid + .5); g.lineTo(width, mid + .5); g.stroke();
    var own = state.audio && isFinite(state.audio.duration) ? state.audio.duration : 0;
    var scale = timeline(state) || own;
    var ratio = scale > 0 && own > 0 ? Math.min(1, own / scale) : 1;
    var endX = Math.max(1, Math.floor(width * ratio));
    g.strokeStyle = state.color || '#2c3e7a'; g.lineWidth = Math.max(1, dpr * 1.2);
    g.beginPath();
    for (var x = 0; x < endX; x++) {
      var peak = peaks[Math.min(peaks.length - 1, Math.floor(x / endX * peaks.length))] || 0;
      var h = Math.max(1, Math.min(1, peak) * mid * .86);
      g.moveTo(x + .5, mid - h); g.lineTo(x + .5, mid + h);
    }
    g.stroke();
    var audio = state.audio;
    if (audio && scale > 0 && isFinite(audio.currentTime) && audio.currentTime > 0) {
      var px = Math.max(0, Math.min(width, audio.currentTime / scale * width));
      g.strokeStyle = '#d86f4b'; g.lineWidth = Math.max(1, dpr * 1.5);
      g.beginPath(); g.moveTo(px, 0); g.lineTo(px, height); g.stroke();
    }
  }
  function redrawAllWaves() { waveStates.forEach(drawWave); }
  function peakData(buffer, count) {
    count = count || PEAK_COUNT;
    var channels = Math.max(1, buffer.numberOfChannels || 1), arrays = [], len = 0, c, i;
    for (c = 0; c < channels; c++) { var ch = buffer.getChannelData(c); arrays.push(ch); if (ch.length > len) len = ch.length; }
    var out = new Array(count);
    for (i = 0; i < count; i++) {
      var start = Math.floor(i * len / count);
      var end = i === count - 1 ? len : Math.floor((i + 1) * len / count);
      var max = 0;
      for (var k = 0; k < arrays.length; k++) {
        var arr = arrays[k];
        for (var j = start; j < end && j < arr.length; j++) {
          var v = Math.abs(arr[j]);
          if (v > max) max = v;
        }
      }
      var db = max > 0 ? 20 * Math.log10(max) : -60;
      out[i] = Math.max(0, Math.min(1, (db + 60) / 60));
    }
    return out;
  }
  function decodePeaks(arrayBuffer) {
    var Ctx = window.OfflineAudioContext || window.webkitOfflineAudioContext;
    if (!Ctx) return Promise.reject(new Error('audio context unavailable'));
    var ctx;
    try { ctx = new Ctx(1, 1, 44100); } catch (e) { return Promise.reject(e); }
    return ctx.decodeAudioData(arrayBuffer.slice(0)).then(function (decoded) {
      var peaks = peakData(decoded, PEAK_COUNT);
      try { if (ctx.close) ctx.close(); } catch (e) {}
      return peaks;
    }, function (error) {
      try { if (ctx.close) ctx.close(); } catch (e) {}
      throw error;
    });
  }
  function setWaveFromBuffer(state, buffer) {
    state.waveToken++;
    state.peaks = peakData(buffer, PEAK_COUNT);
    drawWave(state);
  }
  function setWaveFromUrl(state, url) {
    var token = ++state.waveToken;
    return fetch(url).then(function (response) {
      if (!response.ok) throw new Error('waveform ' + response.status);
      return response.arrayBuffer();
    }).then(decodePeaks).then(function (peaks) {
      if (token !== state.waveToken) return;
      state.peaks = peaks;
      drawWave(state);
    }).catch(function () {
      if (token !== state.waveToken) return;
      state.peaks = null; drawWave(state);
    });
  }
  function clearWave(state) {
    if (!state) return;
    state.waveToken++;
    state.peaks = null;
    drawWave(state);
  }
  function bindWave(state) {
    if (!state || !state.audio) return;
    ['loadedmetadata', 'timeupdate', 'play', 'pause', 'ended', 'seeking'].forEach(function (event) {
      state.audio.addEventListener(event, function () { if (state.result && event === 'loadedmetadata') resultStates.forEach(drawWave); else drawWave(state); });
    });
    drawWave(state);
  }
  function isBusy() { return captureBusy || !!demoTimer; }
  function canUse() {
    var ex = $('panel-explore');
    return active && !document.hidden && !!filePanel && !filePanel.hidden && !!ex && !ex.hidden;
  }
  function invalidate() { gen++; importPending = false; if (demoTimer) { clearTimeout(demoTimer); demoTimer = null; } refreshConvert(); }

  function refreshConvert() {
    if (!convertBtn) return;
    if (importPending || isBusy()) {
      convertBtn.disabled = true; convertBtn.textContent = '处理中…';
      if (convertStatus) convertStatus.textContent = '正在处理音频，请稍候。';
      return;
    }
    if (!ref.ready || !content.ready) {
      convertBtn.disabled = true; convertBtn.textContent = '先准备两段音频';
      if (convertStatus) convertStatus.textContent = '先准备参考音色和内容，再查看效果。';
    } else if (!ref.isSample || !content.isSample) {
      convertBtn.disabled = true; convertBtn.textContent = '自定义转换待接入';
      if (convertStatus) convertStatus.textContent = '自定义音频可试听；正式 Demo 将接入转换，当前不提供结果。';
    } else {
      convertBtn.disabled = false; convertBtn.textContent = '查看样例转换结果';
      if (convertStatus) convertStatus.textContent = '样例已就绪，点此查看本机预生成的结果。';
    }
  }
  function clearResult() {
    if (demoTimer) { clearTimeout(demoTimer); demoTimer = null; }
    ['resultOriginal', 'resultConverted'].forEach(function (id) {
      var a = $(id); if (a) { a.removeAttribute('src'); try { a.load(); } catch (e) {} }
    });
    clearWave(resultOriginal);
    clearWave(resultConverted);
    if (resultBox) resultBox.hidden = true;
    refreshConvert();
  }

  function clearSlot(s) {
    invalidate(); cancelCapture(); clearResult();
    revoke(s.url); s.url = null;
    s.audio.removeAttribute('src'); try { s.audio.load(); } catch (e) {}
    s.audio.hidden = true; s.ready = false; s.isSample = false;
    clearWave(s);
    s.name.textContent = s === ref ? '还没有参考音色' : '还没有内容';
    s.clear.disabled = true; say(s, ''); setTimer(s, ''); refreshConvert();
  }
  function setSample(s, url, label) {
    revoke(s.url); s.url = null;
    s.ready = true; s.isSample = true;
    clearWave(s);
    s.audio.src = url; s.audio.hidden = false;
    s.name.textContent = label + ' · 示例 · 可试听';
    s.clear.disabled = false;
    var g = gen;
    s.audio.onloadedmetadata = function () {
      if (g !== gen) return;
      if (isFinite(this.duration)) s.name.textContent = label + ' · 示例 · ' + this.duration.toFixed(1) + ' 秒 · 可试听';
    };
    return setWaveFromUrl(s, url);
  }
  function loadSamples() {
    if (!canUse()) return Promise.resolve(false);
    invalidate(); cancelCapture(); clearResult();
    var p1 = setSample(ref, S_REF, '示例参考音色');
    var p2 = setSample(content, S_CONTENT, '示例内容');
    refreshConvert();
    return Promise.all([p1, p2]).then(function () { return true; });
  }

  function hookDur(a, el, g) {
    a.onloadedmetadata = function () {
      if (g !== gen) return;
      if (el && isFinite(this.duration)) el.textContent = this.duration.toFixed(1) + ' 秒';
    };
  }
  function revealSample() {
    if (!canUse() || !ref.ready || !content.ready || !ref.isSample || !content.isSample) return Promise.resolve(false);
    var g = ++gen;
    return new Promise(function (resolve) {
      demoTimer = setTimeout(function () {
        demoTimer = null;
        if (g !== gen || !canUse() || !ref.isSample || !content.isSample) { resolve(false); return; }
        var o = $('resultOriginal'), c = $('resultConverted');
        o.src = S_CONTENT; c.src = S_OUT;
        resultBox.hidden = false;
        if (content.peaks && content.peaks.length) {
          resultOriginal.waveToken++;
          resultOriginal.peaks = content.peaks;
          drawWave(resultOriginal);
        } else {
          setWaveFromUrl(resultOriginal, S_CONTENT);
        }
        setWaveFromUrl(resultConverted, S_OUT);
        hookDur(o, $('resultOriginalDur'), g); hookDur(c, $('resultConvertedDur'), g);
        convertBtn.disabled = false; convertBtn.textContent = '重新查看样例结果';
        if (convertStatus) convertStatus.textContent = '已展示本机预生成样例，可试听原始内容与转换结果。';
        redrawAllWaves();
        resolve(true);
      }, 650);
      refreshConvert();
    });
  }

  function readHeader(file) {
    if (!file.slice) return Promise.resolve(false);
    return file.slice(0, 12).arrayBuffer().then(function (b) {
      var d = new DataView(b), a = '', c = '', i;
      for (i = 0; i < 4; i++) a += String.fromCharCode(d.getUint8(i));
      for (i = 8; i < 12; i++) c += String.fromCharCode(d.getUint8(i));
      return a === 'RIFF' && c === 'WAVE';
    }).catch(function () { return false; });
  }
  function adoptBlob(blob, g) {
    return new Promise(function (resolve, reject) {
      var Ctx = window.AudioContext || window.webkitAudioContext;
      if (!Ctx) { reject('当前浏览器不支持音频解码。'); return; }
      var ctx; try { ctx = new Ctx(); } catch (e) { reject('无法创建音频解码器。'); return; }
      var done = false;
      function finish(fn, val) { if (done) return; done = true; try { ctx.close(); } catch (e) {} fn(val); }
      function decode(buf) {
        try {
          ctx.decodeAudioData(buf, function (dec) {
            var d = dec.duration;
            finish(function () {
              if (g !== gen || !active) resolve(null);
              else if (!Number.isFinite(d) || d < 0.5 || d > 30.02) reject('音频时长需在 0.5 到 30 秒之间（当前 ' + d.toFixed(1) + ' 秒）。');
              else resolve({ duration: d, buffer: dec });
            });
          }, function () { finish(function () { reject('音频解码失败，请换一个文件。'); }); });
        } catch (e) { finish(function () { reject('音频解码失败，请重试。'); }); }
      }
      if (blob.arrayBuffer) blob.arrayBuffer().then(function (b) { decode(b.slice(0)); })
        .catch(function () { finish(function () { reject('读取音频失败。'); }); });
      else {
        var fr = new FileReader();
        fr.onload = function () { decode(fr.result); };
        fr.onerror = function () { finish(function () { reject('读取音频失败。'); }); };
        fr.readAsArrayBuffer(blob);
      }
    });
  }
  function adoptSuccess(s, blob, dur, label, buffer) {
    importPending = false;
    revoke(s.url);
    s.url = URL.createObjectURL(blob);
    s.audio.onloadedmetadata = null;
    s.audio.src = s.url; s.audio.hidden = false;
    s.ready = true; s.isSample = false;
    s.name.textContent = label + ' · ' + dur.toFixed(1) + ' 秒 · 可试听';
    s.clear.disabled = false;
    if (buffer) setWaveFromBuffer(s, buffer); else clearWave(s);
    clearResult();
    say(s, (s === ref ? '参考音色' : '内容') + '已准备好，可试听。');
    refreshConvert();
  }
  function onImport(s, file) {
    if (!canUse() || !file) return;
    invalidate(); cancelCapture(); clearResult();
    if (file.size > 16 * 1024 * 1024) { say(s, '文件超过 16 MB，请换一个更小的 WAV。'); refreshConvert(); return; }
    var g = gen;
    importPending = true; refreshConvert();
    say(s, '正在检查 WAV 文件…');
    readHeader(file).then(function (ok) {
      if (g !== gen || !canUse()) return;
      if (!ok) { say(s, '这不是有效的 WAV 文件（需 RIFF/WAVE 头）。'); importPending = false; refreshConvert(); return; }
      return adoptBlob(file, g).then(function (d) {
        if (g !== gen) return;
        if (d == null || !canUse()) { importPending = false; refreshConvert(); return; }
        adoptSuccess(s, file, d.duration, file.name || '导入的音频', d.buffer);
      });
    }).catch(function (msg) {
      if (g !== gen) return;
      importPending = false;
      if (typeof msg === 'string') say(s, msg);
      refreshConvert();
    });
  }
  function stopStream(st) {
    if (!st || !st.getTracks) return;
    st.getTracks().forEach(function (t) { try { t.stop(); } catch (e) {} });
    if (st === stream) stream = null;
  }
  function updateRecButtons() {
    [ref, content].forEach(function (s) {
      var busy = captureBusy, recActive = !!(recording && recording.state !== 'inactive'), target = s === captureTarget;
      if (s.rec) {
        s.rec.textContent = busy ? (recActive ? '停止录音' : '取消录音') : '录制声音';
        s.rec.disabled = busy && !target;
      }
    });
  }
  function cancelCapture() {
    capGen++;
    if (tickTimer) { clearInterval(tickTimer); tickTimer = null; }
    if (stopTimer) { clearTimeout(stopTimer); stopTimer = null; }
    if (recording) { try { if (recording.state !== 'inactive') recording.stop(); } catch (e) {} recording = null; }
    if (stream) stopStream(stream);
    chunks = []; captureBusy = false; captureTarget = null;
    updateRecButtons(); setTimer(ref, ''); setTimer(content, '');
  }
  function startCapture(s) {
    if (captureBusy || !canUse()) return;
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || !window.MediaRecorder) {
      say(s, '当前浏览器不支持录音，请改用上传 WAV。'); return;
    }
    invalidate(); clearResult();
    var g = ++capGen;
    captureBusy = true; captureTarget = s; updateRecButtons(); refreshConvert();
    pauseAll(); say(s, '正在请求麦克风权限…'); setTimer(s, '等待授权');
    navigator.mediaDevices.getUserMedia({ audio: true }).then(function (st) {
      if (g !== capGen || !canUse()) { stopStream(st); return; }
      stream = st; chunks = [];
      var rec;
      try { rec = new MediaRecorder(st); } catch (e) {
        stopStream(st); captureBusy = false; captureTarget = null; updateRecButtons(); refreshConvert(); say(s, '录音启动失败，请重试。'); return;
      }
      recording = rec;
      rec.ondataavailable = function (e) {
        if (g !== capGen || recording !== rec) return;
        if (e.data && e.data.size) chunks.push(e.data);
      };
      rec.onerror = function () {
        if (g !== capGen || recording !== rec) return;
        clearResult(); cancelCapture(); refreshConvert();
        say(s, '录音出错，请重试。');
      };
      rec.onstop = function () {
        stopStream(st);
        if (g !== capGen || recording !== rec) return;
        var localChunks = chunks; chunks = [];
        recording = null;
        captureBusy = false; captureTarget = null; updateRecButtons(); refreshConvert(); setTimer(s, '');
        if (tickTimer) { clearInterval(tickTimer); tickTimer = null; }
        if (stopTimer) { clearTimeout(stopTimer); stopTimer = null; }
        var blob = new Blob(localChunks, { type: rec.mimeType || 'audio/webm' });
        if (!blob.size) { say(s, '没有录到声音，请重试。'); return; }
        var dg = gen;
        importPending = true;
        refreshConvert();
        adoptBlob(blob, dg).then(function (d) {
          if (dg !== gen) return;
          if (d == null || !canUse()) { importPending = false; refreshConvert(); return; }
          adoptSuccess(s, blob, d.duration, s === ref ? '麦克风录制的参考音色' : '麦克风录制的内容', d.buffer);
        }).catch(function (msg) { if (dg === gen) { importPending = false; refreshConvert(); if (typeof msg === 'string') say(s, msg); } });
      };
      try { rec.start(); } catch (e) {
        stopStream(st); recording = null; captureBusy = false; captureTarget = null; updateRecButtons(); refreshConvert(); say(s, '录音启动失败，请重试。'); return;
      }
      updateRecButtons();
      say(s, '录音中… 说完请点“停止录音”。');
      var t0 = Date.now(); setTimer(s, '0.0s');
      tickTimer = setInterval(function () { if (g === capGen) setTimer(s, ((Date.now() - t0) / 1000).toFixed(1) + 's'); }, 100);
      stopTimer = setTimeout(function () { if (g === capGen && rec.state !== 'inactive') { try { rec.stop(); } catch (e) {} } }, 30000);
    }).catch(function (err) {
      if (g === capGen && stream) stopStream(stream);
      if (g !== capGen) return;
      captureBusy = false; captureTarget = null; updateRecButtons(); refreshConvert(); setTimer(s, '');
      say(s, '麦克风不可用：' + (err && err.message ? err.message : '请检查权限') + '。可重试或上传 WAV。');
    });
  }
  function toggleRecord(s) {
    if (captureBusy) {
      if (s !== captureTarget) return;
      if (recording && recording.state !== 'inactive') {
        try { recording.stop(); } catch (e) {}
        stopStream(stream);
      } else { cancelCapture(); refreshConvert(); say(s, '已取消录音。'); }
      return;
    }
    startCapture(s);
  }

  Array.prototype.forEach.call(submodes, function (btn) {
    btn.addEventListener('click', function () { setSubmode(btn.getAttribute('data-file-submode')); });
  });
  if (loadSamplesBtn) loadSamplesBtn.addEventListener('click', loadSamples);
  if (convertBtn) convertBtn.addEventListener('click', revealSample);
  [ref, content].forEach(function (s) {
    if (s.uploadBtn) s.uploadBtn.addEventListener('click', function () { if (active) s.upload.click(); });
    if (s.upload) s.upload.addEventListener('change', function (e) {
      var f = e.target.files && e.target.files[0]; e.target.value = ''; onImport(s, f);
    });
    if (s.rec) s.rec.addEventListener('click', function () { toggleRecord(s); });
    if (s.clear) s.clear.addEventListener('click', function () { clearSlot(s); });
  });
  ownAudios.forEach(function (a) {
    a.addEventListener('play', function () {
      if (isBusy()) { try { a.pause(); } catch (e) {} return; }
      ownAudios.forEach(function (o) { if (o !== a) { try { o.pause(); } catch (e) {} } });
    });
  });
  waveStates.forEach(bindWave);
  window.addEventListener('resize', redrawAllWaves);

  function setGlobalHidden(on) {
    globalNodes.forEach(function (n) { n.el.hidden = on ? true : n.hidden; });
  }
  function cancelPending() {
    var t = captureBusy ? captureTarget : null;
    invalidate(); cancelCapture(); pauseAll(); refreshConvert();
    say(ref, ''); say(content, '');
    if (t) say(t, '录音已取消');
  }
  function setSubmode(mode) {
    var reference = mode === 'reference';
    cancelPending();
    active = reference;
    host.hidden = !reference;
    setGlobalHidden(reference);
    if (reference) redrawAllWaves();
    var fa = $('fileAudio'); if (fa) { try { fa.pause(); } catch (e) {} }
    Array.prototype.forEach.call(submodes, function (btn) {
      var on = btn.getAttribute('data-file-submode') === mode;
      btn.classList.toggle('is-active', on);
      btn.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
    window.dispatchEvent(new CustomEvent('voice-file-kind', { detail: { kind: mode } }));
  }
  function releaseAll() {
    if (stream) stopStream(stream);
    if (recording) { try { if (recording.state !== 'inactive') recording.stop(); } catch (e) {} }
    revoke(ref.url); revoke(content.url);
    ref.url = null; content.url = null;
  }

  window.addEventListener('voice-mode', function (e) {
    cancelPending();
  });
  window.addEventListener('prototype-nav', function () {
    cancelPending();
  });
  document.addEventListener('visibilitychange', function () { if (document.hidden) cancelPending(); });
  window.addEventListener('pagehide', function () { cancelPending(); releaseAll(); });

  function autoloadCompare() {
    if (!canUse()) return;
    loadSamples().then(function (ok) {
      if (!ok || !canUse() || !ref.isSample || !content.isSample) return;
      return revealSample();
    }).then(function (ok) {
      if (!ok || !resultBox || resultBox.hidden) return;
      var scroll = function () {
        try { resultBox.scrollIntoView({ behavior: 'smooth', block: 'center' }); }
        catch (e) { resultBox.scrollIntoView(); }
      };
      if (document.hidden) document.addEventListener('visibilitychange', function onVis() {
        if (document.hidden) return;
        document.removeEventListener('visibilitychange', onVis);
        scroll();
      });
      else scroll();
    });
  }

  try {
    var params = new URLSearchParams(location.search);
    if (params.get('mode') === 'reference') {
      var fm = document.querySelector('.mode-switch [data-mode="file"]');
      if (fm) fm.click();
      setSubmode('reference');
      if (params.get('sample') === 'compare' && location.hash === '#resultBox') {
        if (document.hidden) document.addEventListener('visibilitychange', function onVis() {
          if (document.hidden) return;
          document.removeEventListener('visibilitychange', onVis);
          autoloadCompare();
        });
        else autoloadCompare();
      }
    } else {
      setSubmode('fixed');
    }
  } catch (e) {
    setSubmode('fixed');
  }
})();
