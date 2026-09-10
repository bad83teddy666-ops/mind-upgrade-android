(() => {
  'use strict';
  if (window !== window.top || !window.MindNative || window.__mindVoiceInstalled) return;
  window.__mindVoiceInstalled = true;
  const tracks = new Set();
  if (navigator.mediaDevices?.getUserMedia) {
    const capture = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
    navigator.mediaDevices.getUserMedia = async constraints => {
      const stream = await capture(constraints);
      for (const track of stream.getTracks()) {
        tracks.add(track);
        track.addEventListener('ended', () => tracks.delete(track), {once: true});
      }
      return stream;
    };
  }
  window.__mindReleaseMicrophone = () => { for (const t of tracks) t.stop(); tracks.clear(); };
  let current = null;
  let sequence = 0;
  const post = data => window.MindNative.postMessage(JSON.stringify(data));
  class Recognition {
    constructor() { this.lang = 'pl-PL'; this.continuous = false; this.interimResults = false; }
    start() {
      if (current) throw new DOMException('Recognition already active', 'InvalidStateError');
      this.id = String(++sequence);
      current = this;
      post({type: 'listen', id: this.id});
    }
    stop() { if (current === this) post({type: 'stop', id: this.id}); }
    abort() { if (current === this) post({type: 'abort', id: this.id}); }
  }
  window.MindNative.onmessage = event => {
    let message;
    try { message = JSON.parse(event.data); } catch { return; }
    const active = current;
    if (!active || active.id !== message.id) return;
    if (message.type === 'result') {
      active.onresult?.({results: [[{transcript: message.text, confidence: 1}]], resultIndex: 0});
    } else if (message.type === 'error') {
      active.onerror?.({error: message.error});
    } else if (message.type === 'end') {
      current = null;
      active.onend?.();
    }
  };
  window.SpeechRecognition = Recognition;
  window.webkitSpeechRecognition = Recognition;
  class Utterance {
    constructor(text = '') { this.text = text; this.lang = 'pl-PL'; this.rate = 1; this.pitch = 1; }
  }
  const synthesis = {
    cancel() { post({type: 'silence'}); },
    getVoices() { return []; },
    speak(utterance) {
      post({type: 'speak', text: String(utterance.text).slice(0, 3900), rate: utterance.rate, pitch: utterance.pitch});
    },
    addEventListener() {}, removeEventListener() {}
  };
  Object.defineProperty(window, 'SpeechSynthesisUtterance', {value: Utterance, configurable: true});
  Object.defineProperty(window, 'speechSynthesis', {value: synthesis, configurable: true});
})();
