const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const script = fs.readFileSync('app/src/main/assets/voice-bridge.js', 'utf8');
function setup(frame = false) {
  const sent = [];
  const window = {MindNative: {postMessage: data => sent.push(JSON.parse(data))}};
  window.top = frame ? {} : window;
  vm.runInNewContext(script, {window, DOMException});
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
