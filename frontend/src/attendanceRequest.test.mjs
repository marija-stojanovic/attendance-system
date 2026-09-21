import test from 'node:test';
import assert from 'node:assert/strict';
import { sendAttendance } from './attendanceRequest.ts';

const attempt = { token: 'qr-token', pin: null, requestId: '11111111-1111-4111-8111-111111111111' };
const saved = {
  courseCode: 'ATT-1', courseName: 'Predmet', activityType: 'LECTURE',
  groupNumber: 'ALL', recordedAt: '2026-09-21T10:00:00Z'
};
const reply = (status, body) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' }
});

test('uspješna prijava šalje isti identifikator zahtjeva', async () => {
  let body;
  const send = async (_url, options) => {
    body = JSON.parse(options.body);
    return reply(200, saved);
  };
  const result = await sendAttendance('http://localhost:8081', 'login-token', attempt, 100, send);
  assert.equal(result.kind, 'success');
  assert.equal(result.result.courseCode, 'ATT-1');
  assert.deepEqual(body, attempt);
});

test('prekid veze i oporavak ne mijenjaju identifikator pokušaja', async () => {
  const bodies = [];
  let calls = 0;
  const send = async (_url, options) => {
    bodies.push(JSON.parse(options.body));
    if (calls++ === 0) throw new TypeError('Network failed');
    return reply(200, saved);
  };
  assert.equal((await sendAttendance('http://localhost:8081', 'login-token', attempt, 100, send)).kind,
    'network-error');
  assert.equal((await sendAttendance('http://localhost:8081', 'login-token', attempt, 100, send)).kind,
    'success');
  assert.equal(bodies[0].requestId, bodies[1].requestId);
});

test('zahtjev koji predugo traje prekida se', async () => {
  const send = (_url, options) => new Promise((_resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
  });
  const result = await sendAttendance('http://localhost:8081', 'login-token', attempt, 10, send);
  assert.equal(result.kind, 'timeout');
});

test('odbijena prijava razlikuje se od potvrđenog duplikata', async () => {
  const rejected = await sendAttendance('http://localhost:8081', 'login-token', attempt, 100,
    async () => reply(400, { detail: 'QR token nije važeći.' }));
  const duplicate = await sendAttendance('http://localhost:8081', 'login-token', attempt, 100,
    async () => reply(409, { code: 'ATTENDANCE_ALREADY_RECORDED' }));
  assert.deepEqual(rejected, { kind: 'rejected', message: 'QR token nije važeći.' });
  assert.equal(duplicate.kind, 'already-recorded');
});

test('nedostupna baza i oporavak prikazuju različita stanja', async () => {
  let calls = 0;
  const send = async () => calls++ === 0
    ? reply(503, { detail: 'Baza trenutno nije dostupna. Pokušajte ponovo.' })
    : reply(200, saved);
  const unavailable = await sendAttendance('http://localhost:8081', 'login-token', attempt, 100, send);
  const recovered = await sendAttendance('http://localhost:8081', 'login-token', attempt, 100, send);
  assert.equal(unavailable.kind, 'unavailable');
  assert.equal(recovered.kind, 'success');
});
