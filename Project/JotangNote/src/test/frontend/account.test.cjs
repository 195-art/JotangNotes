const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../../main/resources/static/index.html'), 'utf8')
  .match(/<script>([\s\S]*?)<\/script>/)[1];

function app(fetch = async () => ({ ok: true, status: 200, json: async () => ({ code: 200, data: [] }) })) {
  const elements = new Map(), storage = new Map();
  function element() {
    const classes = new Set();
    return { value: '', textContent: '', innerHTML: '', children: [], handlers: new Map(),
      classList: { add: x => classes.add(x), remove: x => classes.delete(x), contains: x => classes.has(x),
        toggle: (x, on) => on ? classes.add(x) : classes.delete(x) },
      addEventListener(name, handler) { this.handlers.set(name, handler); },
      replaceChildren() { this.children = []; }, append(...items) { this.children.push(...items); },
      focus() {}, remove() {} };
  }
  const context = vm.createContext({ document: {
    getElementById(id) { if (!elements.has(id)) elements.set(id, element()); return elements.get(id); }, createElement: element },
    window: { location: { origin: 'http://localhost:8080' } },
    localStorage: { getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) },
    AbortController, DOMException, fetch, setTimeout, clearTimeout, console });
  vm.runInContext(code, context);
  return { elements, storage, run: code => vm.runInContext(code, context) };
}

test('logout removes private editor, chat, session and input before another login', () => {
  const page = app();
  page.run('token="A"; sessionId="A-session"; renderEditor({id:1,title:"private",content:"secret"}); addBubble("private chat","assistant"); $("chatInput").value="private draft"; localStorage.setItem(SESSION_KEY,sessionId); logout();');
  assert.equal(page.elements.get('editorBody').children.some(child => child.id === 'noteContent'), false);
  assert.equal(page.elements.get('chatMessages').children.length, 0);
  assert.equal(page.elements.get('chatInput').value, '');
  assert.equal(page.run('sessionId'), null);
  assert.equal(page.storage.has('jotangnote_chat_session_id'), false);
  assert.equal(page.elements.get('saveNote').classList.contains('hidden'), true);
});

test('old account response cannot restore private content even if fetch ignores abort', async () => {
  let resolve;
  const page = app(() => new Promise(done => { resolve = done; }));
  page.run('token="A"');
  const opening = page.run('openNote(1)');
  page.run('logout(); token="B"; username="B"');
  resolve({ ok: true, status: 200, json: async () => ({ code: 200, data: { id: 1, title: 'A', content: 'private' } }) });
  await opening;
  assert.equal(page.elements.get('editorBody').children.some(child => child.id === 'noteContent'), false);
  assert.equal(page.run('token'), 'B');
});

test('401 resets login from every protected request', async () => {
  const page = app(async () => ({ ok: false, status: 401, json: async () => ({ code: 401, message: '登录已失效，请重新登录' }) }));
  page.run('token="expired"; renderEditor({id:1,title:"private",content:"private"})');
  await assert.rejects(page.run('request("/notes/my")'), { name: 'AbortError' });
  assert.equal(page.run('token'), null);
  assert.equal(page.elements.get('appView').classList.contains('hidden'), true);
  assert.equal(page.elements.get('authView').classList.contains('hidden'), false);
});

test('late chat reply cannot restore previous account session', async () => {
  let resolve;
  const page = app(() => new Promise(done => { resolve = done; }));
  page.run('token="A"; $("chatInput").value="question"');
  const sending = page.elements.get('chatForm').handlers.get('submit')({ preventDefault() {} });
  page.run('logout(); token="B"');
  resolve({ ok: true, status: 200, json: async () => ({ code: 200, data: { sessionId: 'A-session', reply: 'private' } }) });
  await sending;
  assert.equal(page.elements.get('chatMessages').children.length, 0);
  assert.equal(page.run('sessionId'), null);
  assert.equal(page.run('busy'), false);
});

test('401 from an old request must not log out the new account', async () => {
  let resolve;
  const page = app(() => new Promise(done => { resolve = done; }));
  page.run('token="A"');
  const reading = page.run('loadNotes()');
  page.run('logout(); token="B"');
  resolve({ ok: false, status: 401, json: async () => ({ code: 401 }) });
  await reading;
  assert.equal(page.run('token'), 'B');
});
