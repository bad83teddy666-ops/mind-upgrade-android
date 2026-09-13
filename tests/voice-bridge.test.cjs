const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const script = fs.readFileSync('app/src/main/assets/voice-bridge.js', 'utf8');
function setup(frame = false, navigator = {}) {
  const sent = [];
  const window = {dispatchEvent(){}, MindNative: {postMessage: data => sent.push(JSON.parse(data))}};
  window.top = frame ? {} : window;
  vm.runInNewContext(script, {window, DOMException, navigator, setTimeout, clearTimeout, CustomEvent:class {constructor(type,options){this.type=type;this.detail=options?.detail;}},localStorage:{setItem(){}}});
  const receive = data => window.MindNative.onmessage({data: JSON.stringify(data)});
  return {window, sent, receive};
}
test('delivers one transcript and permits another turn after end', () => {
  const {window, sent, receive} = setup();
  const recognition = new window.SpeechRecognition();
  const results = [];
  recognition.onresult = event => results.push(event.results[0][0].transcript);
  recognition.start();
  assert.throws(() => recognition.start(), {name: 'InvalidStateError'});
  receive({type: 'result', id: 'wrong', text: 'ignore'});
  receive({type: 'result', id: sent[0].id, text: 'Dzień dobry'});
  receive({type: 'end', id: sent[0].id});
  recognition.start();
  receive({type: 'result', id: sent[0].id, text: 'stale'});
  assert.deepEqual(results, ['Dzień dobry']);
  assert.notEqual(sent[0].id, sent[1].id);
});
test('permission denial releases recognition for retry', () => {
  const {window, sent, receive} = setup();
  const recognition = new window.SpeechRecognition();
  let error;
  recognition.onerror = event => error = event.error;
  recognition.start();
  receive({type: 'error', id: sent[0].id, error: 'not-allowed'});
  receive({type: 'end', id: sent[0].id});
  assert.equal(error, 'not-allowed');
  recognition.start();
  recognition.abort();
  assert.equal(sent[2].type, 'abort');
});
test('subframes cannot install bridge', () => {
  assert.equal(setup(true).window.SpeechRecognition, undefined);
});
test('speech and cancellation use structured messages', () => {
  const {window, sent} = setup();
  const utterance = new window.SpeechSynthesisUtterance('Cześć "Piotrek"');
  utterance.rate = 0.9;
  window.speechSynthesis.speak(utterance);
  window.speechSynthesis.cancel();
  assert.equal(sent[0].text, 'Cześć "Piotrek"');
  assert.equal(sent[0].rate, 0.9);
  assert.equal(sent[1].type, 'silence');
});

test('releases microphone tracks before background wake detection', async () => {
  let stops = 0;
  const track = {stop: () => stops++, addEventListener() {}};
  const navigator = {mediaDevices: {getUserMedia: async () => ({getTracks: () => [track]})}};
  const {window,sent,receive} = setup(false, navigator);
  const capture=navigator.mediaDevices.getUserMedia({audio:true});
  receive({type:'audio-state',requestId:sent[0].id,strict:true,ready:true,label:'Headset'});
  await capture;
  window.__mindReleaseMicrophone();
  window.__mindReleaseMicrophone();
  assert.equal(stops, 1);
});


test('headset route denial never opens the microphone',async()=>{
  let opened=0;const navigator={mediaDevices:{getUserMedia:async()=>{opened++;}}};
  const {sent,receive}=setup(false,navigator);
  const capture=navigator.mediaDevices.getUserMedia({audio:true});
  receive({type:'audio-state',requestId:sent[0].id,strict:true,ready:false,note:'Połącz słuchawki.'});
  await assert.rejects(capture,/Połącz słuchawki/);assert.equal(opened,0);
});
test('headset loss releases capture and stops native playback',async()=>{
  let stops=0;const navigator={mediaDevices:{getUserMedia:async()=>({getTracks:()=>[{stop(){stops++;},addEventListener(){}}]})}};
  const {window,sent,receive}=setup(false,navigator);const capture=navigator.mediaDevices.getUserMedia({audio:true});
  receive({type:'audio-state',requestId:sent[0].id,strict:true,ready:true,label:'Headset'});await capture;
  window.__mindAudioEvent({type:'audio-state',strict:true,ready:false,lost:true});
  assert.equal(stops,1);assert.equal(sent.at(-1).type,'audio-stop');
});
