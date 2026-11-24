// Real-service smoke test. Creates three tagged accounts and messages; never uses existing users.
// Run from any directory: node ChatSystem/scripts/friend-live-smoke.cjs
const assert = require('node:assert/strict');
const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { EventEmitter } = require('node:events');

const base = process.env.CHAT_GATEWAY_URL || 'http://127.0.0.1:8080';
const run = Date.now().toString();
const output = process.env.FRIEND_LIVE_REPORT || path.resolve(__dirname, '../../logs/friend-live-20261124', `result-${run}.json`);
const report = { run, startedAt: new Date().toISOString(), gateway: base, users: [], checks: [], messages: [], passed: false };
const sockets = [];

async function check(name, action) {
  try {
    const result = await action();
    report.checks.push({ name, passed: true });
    console.log('PASS ' + name);
    return result;
  } catch (error) {
    report.checks.push({ name, passed: false, error: error.message });
    throw error;
  }
}

async function api(method, route, actor, body, expected = 200, extraHeaders = {}) {
  const headers = { accept: 'application/json', ...extraHeaders };
  if (actor) Object.assign(headers, { authorization: actor.token, 'device-id': actor.device });
  if (body !== undefined) headers['content-type'] = 'application/json';
  const response = await fetch(base + route, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000) });
  const raw = await response.text();
  assert.equal(response.status, expected, `${method} ${route}: HTTP ${response.status}, expected ${expected}`);
  if (!raw) return null;
  try { return JSON.parse(raw); }
  catch { throw new Error(`${method} ${route}: expected JSON response`); }
}

// Dependency-free WebSocket peer using a real HTTP upgrade through the gateway.
class Peer extends EventEmitter {
  constructor(socket, head) {
    super();
    this.socket = socket;
    this.buffer = Buffer.alloc(0);
    this.frames = [];
    this.fragments = [];
    this.failure = null;
    socket.setTimeout(0);
    socket.on('data', bytes => this.read(bytes));
    socket.on('error', error => { this.failure = error; this.emit('update'); });
    socket.on('close', () => { this.failure ||= new Error('WebSocket closed'); this.emit('update'); });
    if (head.length) this.read(head);
  }
  static connect(actor) {
    return new Promise((resolve, reject) => {
      const key = crypto.randomBytes(16).toString('base64');
      const request = http.request(base + '/ws', { headers: {
        connection: 'Upgrade', upgrade: 'websocket', 'sec-websocket-version': '13', 'sec-websocket-key': key,
        authorization: actor.token, 'device-id': actor.device, 'user-info': '999999999',
      } });
      request.setTimeout(15000, () => request.destroy(new Error('WebSocket upgrade timed out')));
      request.on('error', reject);
      request.on('response', response => { response.resume(); reject(new Error('WebSocket HTTP ' + response.statusCode)); });
      request.on('upgrade', (response, socket, head) => {
        const expected = crypto.createHash('sha1').update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
        if (response.headers['sec-websocket-accept'] !== expected) { socket.destroy(); return reject(new Error('Invalid WebSocket handshake')); }
        const peer = new Peer(socket, head);
        sockets.push(peer);
        resolve(peer);
      });
      request.end();
    });
  }
  read(bytes) {
    this.buffer = Buffer.concat([this.buffer, bytes]);
    while (this.buffer.length >= 2) {
      const opcode = this.buffer[0] & 15;
      const fin = (this.buffer[0] & 128) !== 0;
      const masked = (this.buffer[1] & 128) !== 0;
      let size = this.buffer[1] & 127;
      let offset = 2;
      if (size === 126) { if (this.buffer.length < 4) return; size = this.buffer.readUInt16BE(2); offset = 4; }
      if (size === 127) { if (this.buffer.length < 10) return; size = Number(this.buffer.readBigUInt64BE(2)); offset = 10; }
      if (size > 1048576) { this.socket.destroy(new Error('Oversized WebSocket frame')); return; }
      const maskOffset = offset;
      if (masked) offset += 4;
      if (this.buffer.length < offset + size) return;
      const payload = Buffer.from(this.buffer.subarray(offset, offset + size));
      if (masked) for (let i = 0; i < size; i++) payload[i] ^= this.buffer[maskOffset + i % 4];
      this.buffer = this.buffer.subarray(offset + size);
      if (opcode === 8) { this.socket.end(); continue; }
      if (opcode === 9) { this.writeFrame(10, payload); continue; }
      if (opcode === 1 || opcode === 0) {
        this.fragments.push(payload);
        if (!fin) continue;
        const text = Buffer.concat(this.fragments).toString('utf8');
        this.fragments = [];
        try { this.frames.push(JSON.parse(text)); }
        catch { this.frames.push({ type: text }); }
        this.emit('update');
      }
    }
  }
  writeFrame(opcode, payload) {
    const mask = crypto.randomBytes(4);
    const offset = payload.length < 126 ? 2 : 4;
    const frame = Buffer.alloc(offset + 4 + payload.length);
    frame[0] = 128 | opcode;
    frame[1] = 128 | (offset === 2 ? payload.length : 126);
    if (offset === 4) frame.writeUInt16BE(payload.length, 2);
    mask.copy(frame, offset);
    for (let i = 0; i < payload.length; i++) frame[offset + 4 + i] = payload[i] ^ mask[i % 4];
    this.socket.write(frame);
  }
  send(message) { this.writeFrame(1, Buffer.from(JSON.stringify(message))); }
  wait(predicate, description, timeout = 15000) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => finish(new Error('Timed out waiting for ' + description)), timeout);
      const finish = (error, frame) => { clearTimeout(timer); this.removeListener('update', inspect); error ? reject(error) : resolve(frame); };
      const inspect = () => {
        const index = this.frames.findIndex(predicate);
        if (index >= 0) return finish(null, this.frames.splice(index, 1)[0]);
        if (this.failure) finish(this.failure);
      };
      this.on('update', inspect);
      inspect();
    });
  }
  close() { this.socket.destroy(); }
}

function chat(to, label) {
  return { type: 'CHAT', to: to.id, senderId: 999999999, clientMessageId: `${run}-${label}`,
    payload: { messageType: 1, content: `friend-live-${run}-${label}` } };
}
async function denied(peer, to, label) {
  const message = chat(to, label);
  peer.send(message);
  const response = await peer.wait(frame => frame.clientMessageId === message.clientMessageId, label);
  assert.equal(response.type, 'SEND_ERROR');
}
async function delivered(sender, receiver, actor, to, label) {
  const message = chat(to, label);
  sender.send(message);
  const ack = await sender.wait(frame => frame.clientMessageId === message.clientMessageId, label + ' acknowledgement');
  assert.equal(ack.type, 'SAVED_ACK', `Expected SAVED_ACK, got ${ack.type}: ${ack.message || ''}`);
  const push = await receiver.wait(frame => frame.type === 'MESSAGE' && frame.messageId === ack.messageId, label + ' recipient push');
  assert.equal(push.senderId, actor.id, 'Gateway/channel identity must override forged senderId');
  assert.equal(push.seq, ack.seq);
  assert.equal(push.payload.content, message.payload.content);
  report.messages.push({ messageId: ack.messageId, conversationId: ack.conversationId, seq: ack.seq, senderId: actor.id, receiverId: to.id, clientMessageId: message.clientMessageId });
  return ack;
}
async function history(actor, conversationId) {
  // /sync/messages starts at the acknowledged device cursor; it is not a history API.
  return api('GET', `/sync/interval/sync?deviceId=${encodeURIComponent(actor.device)}&conversationId=${conversationId}&start=1&end=51`, actor);
}

async function main() {
  await check('unauthenticated request returns 401', () => api('GET', '/friend/list', null, undefined, 401));
  await check('invalid token returns 401', () => api('GET', '/friend/list', { token: 'invalid', device: 'invalid' }, undefined, 401));
  const actors = [];
  for (const [index, role] of ['A', 'B', 'C'].entries()) {
    const password = crypto.randomBytes(15).toString('base64url');
    const actor = await check(`register and login dedicated account ${role}`, async () => {
      const registration = await api('POST', '/user/register', null, { phone: '19' + run.slice(-8) + index, nickname: `friend-live-${run}-${role}`, password });
      assert.ok(registration.account && registration.accessToken);
      const login = await api('POST', '/user/login', null, { account: registration.account, password });
      assert.ok(login.accessToken);
      const id = Number(JSON.parse(Buffer.from(login.accessToken.split('.')[1], 'base64url').toString()).user);
      assert.ok(Number.isSafeInteger(id) && id > 0);
      const result = { role, id, account: registration.account, token: login.accessToken, device: `friend-live-${run}-${role}` };
      report.users.push({ role, id, account: registration.account, device: result.device });
      return result;
    });
    actors.push(actor);
  }
  const [a, b, c] = actors;
  await check('exact account lookup returns public information', async () => {
    const found = await api('GET', '/friend/search?account=' + b.account, a);
    assert.equal(found.friendId, b.id);
    assert.equal(found.account, b.account);
    assert.ok(!('password' in found) && !('phone' in found));
  });
  await check('exact user ID lookup works', async () => assert.equal((await api('GET', '/friend/search?userId=' + b.id, a)).friendId, b.id));
  await check('unrelated accounts cannot chat', async () => assert.equal(await api('POST', '/friend/verify', a, { toId: b.id }), false));
  await check('self application is rejected', () => api('POST', '/friend/apply', a, { friendId: a.id }, 400));
  await check('unknown account is rejected', () => api('POST', '/friend/apply', a, { account: 'missing' + run }, 404));
  await check('ambiguous target is rejected', () => api('POST', '/friend/apply', a, { account: b.account, friendId: b.id }, 400));
  await check('oversized request message is rejected', () => api('POST', '/friend/apply', a, { friendId: b.id, message: 'x'.repeat(101) }, 400));
  const request = await check('application remains pending without creating friendship', async () => {
    const result = await api('POST', '/friend/apply', a, { account: b.account, message: 'live smoke test' });
    assert.equal(result.status, 'PENDING');
    assert.equal(result.applicantId, a.id);
    assert.equal(result.receiverId, b.id);
    return result;
  });
  await check('concurrent duplicate applications reuse one ID', async () => {
    const duplicates = await Promise.all(Array.from({ length: 6 }, () => api('POST', '/friend/apply', a, { friendId: b.id })));
    duplicates.forEach(value => assert.equal(value.id, request.id));
  });
  await check('incoming and outgoing request lists are scoped', async () => {
    const incoming = await api('GET', '/friend/requests', b);
    const outgoing = await api('GET', '/friend/requests?direction=outgoing', a);
    assert.equal(incoming.length, 1);
    assert.equal(incoming[0].id, request.id);
    assert.equal(outgoing[0].id, request.id);
    assert.deepEqual(await api('GET', '/friend/requests', c), []);
  });
  await check('applicant cannot accept their own request', () => api('POST', `/friend/requests/${request.id}/accept`, a, undefined, 403));
  await check('third party cannot accept or reject', async () => {
    await api('POST', `/friend/requests/${request.id}/accept`, c, undefined, 403);
    await api('POST', `/friend/requests/${request.id}/reject`, c, undefined, 403);
  });
  await check('sender ID cannot be forged for verification', () => api('POST', '/friend/verify', c, { senderId: a.id, toId: b.id }, 403));
  const pa = await check('A WebSocket connects through authenticated gateway', async () => { const peer = await Peer.connect(a); await peer.wait(frame => frame.type === 'CONNECTION_READY', 'A connection'); return peer; });
  const pb = await check('B WebSocket connects through authenticated gateway', async () => { const peer = await Peer.connect(b); await peer.wait(frame => frame.type === 'CONNECTION_READY', 'B connection'); return peer; });
  await check('pending request does not permit WebSocket chat', () => denied(pa, b, 'before-accept'));
  const crossed = await check('crossed request also waits for consent', async () => {
    const result = await api('POST', '/friend/apply', b, { friendId: a.id });
    assert.equal(result.status, 'PENDING');
    assert.equal(await api('POST', '/friend/verify', a, { toId: b.id }), false);
    return result;
  });
  await check('receiver acceptance creates bilateral friendship', async () => {
    assert.equal((await api('POST', `/friend/requests/${request.id}/accept`, b)).status, 'ACCEPTED');
    assert.equal(await api('POST', '/friend/verify', a, { toId: b.id }), true);
    assert.equal(await api('POST', '/friend/verify', b, { toId: a.id }), true);
    assert.equal((await api('GET', '/friend/list', a))[0].friendId, b.id);
    assert.equal((await api('GET', '/friend/list', b))[0].friendId, a.id);
  });
  await check('repeated acceptance and crossed decision are idempotent', async () => {
    assert.equal((await api('POST', `/friend/requests/${request.id}/accept`, b)).status, 'ACCEPTED');
    assert.equal((await api('POST', `/friend/requests/${crossed.id}/accept`, a)).status, 'ACCEPTED');
  });
  const first = await check('accepted friends receive real persisted WebSocket messages', () => delivered(pa, pb, a, b, 'accepted-chat'));
  await check('recipient can pull the message from MySQL', async () => {
    const batch = await history(b, first.conversationId);
    assert.ok(batch.messages.some(message => message.messageId === first.messageId && message.seq === first.seq));
  });
  await check('recipient delivery and read ACK advance successfully', async () => {
    pb.send({ type: 'DELIVERY_ACK', payload: { conversationId: first.conversationId, ackSeq: first.seq } });
    await pb.wait(frame => frame.type === 'DELIVERY_ACK' && frame.ackSeq === first.seq, 'delivery ACK');
    pb.send({ type: 'READ_ACK', payload: { conversationId: first.conversationId, readSeq: first.seq } });
    await pb.wait(frame => frame.type === 'READ_ACK' && frame.readSeq === first.seq, 'read ACK');
  });
  await check('delete removes both directions and is idempotent', async () => {
    await api('DELETE', '/friend/' + b.id, a, undefined, 204);
    await api('DELETE', '/friend/' + b.id, a, undefined, 204);
    assert.equal(await api('POST', '/friend/verify', a, { toId: b.id }), false);
    assert.equal(await api('POST', '/friend/verify', b, { toId: a.id }), false);
    assert.deepEqual(await api('GET', '/friend/list', a), []);
    assert.deepEqual(await api('GET', '/friend/list', b), []);
  });
  await check('both already-connected sockets are denied after deletion', async () => {
    await denied(pa, b, 'deleted-A');
    await denied(pb, a, 'deleted-B');
  });
  await check('old accepted requests cannot resurrect friendship', async () => {
    await api('POST', `/friend/requests/${request.id}/accept`, b);
    await api('POST', `/friend/requests/${crossed.id}/accept`, a);
    assert.equal(await api('POST', '/friend/verify', a, { toId: b.id }), false);
  });
  await check('deletion preserves readable message history', async () => {
    assert.ok((await history(b, first.conversationId)).messages.some(message => message.messageId === first.messageId));
  });
  const reapplied = await check('new application gets a new ID after deletion', async () => {
    const result = await api('POST', '/friend/apply', a, { friendId: b.id });
    assert.notEqual(result.id, request.id);
    assert.equal(result.status, 'PENDING');
    return result;
  });
  await check('receiver can reject, and rejected requests stay final', async () => {
    assert.equal((await api('POST', `/friend/requests/${reapplied.id}/reject`, b)).status, 'REJECTED');
    assert.equal((await api('POST', `/friend/requests/${reapplied.id}/accept`, b)).status, 'REJECTED');
    assert.equal(await api('POST', '/friend/verify', a, { toId: b.id }), false);
  });
  await check('fresh consent restores friendship', async () => {
    const fresh = await api('POST', '/friend/apply', a, { friendId: b.id });
    assert.notEqual(fresh.id, reapplied.id);
    assert.equal((await api('POST', `/friend/requests/${fresh.id}/accept`, b)).status, 'ACCEPTED');
  });
  await check('restored friendship reuses original conversation and advances seq', async () => {
    const next = await delivered(pa, pb, a, b, 'restored-chat');
    assert.equal(next.conversationId, first.conversationId);
    assert.ok(next.seq > first.seq);
  });
  await check('deleting a pending request prevents later acceptance', async () => {
    const pending = await api('POST', '/friend/apply', a, { friendId: c.id });
    await api('DELETE', '/friend/' + c.id, a, undefined, 204);
    assert.equal((await api('POST', `/friend/requests/${pending.id}/accept`, c)).status, 'CANCELLED');
  });
  await check('final cleanup leaves dedicated accounts without active friendships', async () => {
    await api('DELETE', '/friend/' + b.id, a, undefined, 204);
    assert.equal(await api('POST', '/friend/verify', a, { toId: b.id }), false);
  });
  report.passed = true;
}

main().catch(error => { report.error = error.message; process.exitCode = 1; console.error('FAIL ' + error.message); }).finally(() => {
  sockets.forEach(peer => peer.close());
  report.finishedAt = new Date().toISOString();
  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify({ passed: report.passed, checks: report.checks.length, users: report.users, messages: report.messages, report: output }));
});
