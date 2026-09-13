(() => {
  'use strict';
  if (window !== window.top || !window.MindNative || window.__mindVoiceInstalled) return;
  window.__mindVoiceInstalled = true;
  const tracks = new Set();
  if (navigator.mediaDevices?.getUserMedia) {
    const capture = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
    navigator.mediaDevices.getUserMedia = async constraints => {
      if(constraints.audio) await window.MindAudio.prepare('auto');
      const stream = await capture(constraints);
      if(constraints.audio && window.MindAudio.state.strict && !window.MindAudio.state.ready){stream.getTracks().forEach(t=>t.stop());throw Error('Słuchawki rozłączone.');}
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
  const waiting = new Map();
  let playback = null;
  window.MindAudio = {
    state: {strict: false, ready: false, label: ''},
    prepare(mode = 'auto') { return new Promise((resolve, reject) => {
      const id = 'audio-' + (++sequence);
      const timer = setTimeout(() => { waiting.delete(id); reject(Error('Nie udało się potwierdzić urządzenia audio.')); }, 8000);
      waiting.set(id, {resolve, reject, timer});post({type:'audio-prepare',id,mode});
    }); },
    stop() { playback = null; post({type:'audio-stop'}); },
    play(audio, callbacks) { this.stop();const id='play-'+(++sequence);playback={id,...callbacks};post({type:'audio-play',id,audio}); }
  };
  window.__mindAudioEvent = message => {
    if(message.type==='audio-state'){
      window.MindAudio.state=message;
      try{localStorage.setItem('mind-headset-only',message.strict?'true':'false');}catch{}
      if(message.lost){window.MindAudio.stop();window.__mindReleaseMicrophone();}
      window.dispatchEvent(new CustomEvent('mind-audio-state',{detail:message}));
      const pending=waiting.get(message.requestId);if(pending){clearTimeout(pending.timer);waiting.delete(message.requestId);if(message.ready)pending.resolve(message);else pending.reject(Error(message.note||'Połącz słuchawki i włącz rozmowę ponownie.'));}
    }else if(message.type==='audio-playback'&&playback?.id===message.id){
      if(message.event==='playing')playback.onplaying?.();
      if(message.event==='ended'){const done=playback.onended;playback=null;done?.();}
      if(message.event==='error'){const failed=playback.onerror;playback=null;failed?.(Error(message.note));}
    }
  };
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
    if(message.type==='audio-state'||message.type==='audio-playback'){window.__mindAudioEvent(message);return;}
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

