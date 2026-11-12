const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
const start = html.indexOf('<script>') + '<script>'.length;
const end = html.indexOf('</script>', start);
assert.ok(start >= '<script>'.length && end > start, 'inline app script exists');
const script = `${html.slice(start, end)}\nglobalThis.__getSegments = () => segments; globalThis.__getAllowed = () => allowedSegments(); globalThis.__getVoice = speaker => selectedModelVoice(speaker);`;

const elements = new Map();
function element(id) {
  if (!elements.has(id)) {
    elements.set(id, {
      id,
      value: '',
      checked: id === 'includeNarration',
      textContent: '',
      innerHTML: '',
      dataset: {},
      classList: { add() {}, remove() {}, toggle() {} },
      addEventListener() {},
      querySelectorAll() { return []; },
      querySelector() { return null; },
      scrollIntoView() {},
    });
  }
  return elements.get(id);
}

const voice = {name:'Chinese test voice', lang:'zh-CN', voiceURI:'zh-test'};
const speechSynthesis = {
  paused:false,
  speaking:false,
  getVoices:() => [voice],
  addEventListener() {},
  cancel() {},
  speak() {},
  pause() {},
  resume() {},
};
class SpeechSynthesisUtterance {
  constructor(text) { this.text = text; }
}

const context = {
  document:{getElementById:element},
  window:{speechSynthesis, location:{protocol:'http:', hostname:'localhost'}},
  speechSynthesis,
  SpeechSynthesisUtterance,
  setTimeout:() => 0,
  setInterval:() => 0,
  clearTimeout() {},
  console,
  Number,
  String,
  Math,
  JSON,
};
vm.runInNewContext(script, context);

element('sourceText').value = '沈砚：「你真的决定要走吗？」\n（他攥紧了袖口，压低声音。）\n沈砚：哪怕我求你留下？\n雨声渐渐停了。';
context.parseScript();

const parsed = JSON.parse(JSON.stringify(context.__getSegments()));
assert.deepEqual(parsed.map(s => [s.type, s.speaker, s.text]), [
  ['dialogue', '沈砚', '你真的决定要走吗？'],
  ['dialogue', '沈砚', '哪怕我求你留下？'],
  ['narration', '旁白', '雨声渐渐停了。'],
]);
assert.equal(parsed[0].direction, '压低声音');
assert.equal(parsed[0].stageDirection, '他攥紧了袖口，压低声音。');
assert.equal(parsed[0].genderHint, 'male');
assert.equal(context.__getVoice('沈砚'), 'Dylan', 'male pronouns select a male voice by default');
assert.equal(context.__getAllowed().length, 3, 'attached stage directions are not spoken as narration');
element('includeAction').checked = true;
assert.equal(context.__getAllowed().length, 3, 'attached acting directions do not become separate audio');
element('includeNarration').checked = false;
assert.equal(context.__getAllowed().length, 2, 'narration can still be excluded');

element('sourceText').value = '林澄：你来了。\n（她轻声说。）';
context.parseScript();
assert.equal(context.__getSegments()[0].genderHint, 'female');
assert.equal(context.__getVoice('林澄'), 'Serena', 'female pronouns select a female voice by default');

console.log('MVP smoke test passed: voice gender defaults, acting-direction attachment, and queue filtering.');
