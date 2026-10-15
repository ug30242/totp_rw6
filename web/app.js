const form = document.getElementById('import-form');
const pairing = document.getElementById('pairing');
const accountsInput = document.getElementById('accounts');
const consent = document.getElementById('consent');
const send = document.getElementById('send');
const status = document.getElementById('status');
let generation = 0;
let controller = null;

function readPairing(value) {
  const url = new URL(value.trim());
  const params = new URLSearchParams(url.hash.slice(1));
  const sid = params.get('sid') || '';
  const key = params.get('key') || '';
  if (!/^RW6[A-HJ-NP-Z2-9]{16}$/.test(sid) || !/^[A-HJ-NP-Z2-9]{20}$/.test(key)) {
    throw new Error('워치에서 새 QR을 생성하고 전체 URL을 입력하세요.');
  }
  return {sid, key};
}
if (location.hash) {
  try { readPairing(location.href); pairing.value = location.href; }
  catch (_) { status.textContent = 'QR URL을 확인하세요.'; }
  // Do not leave session material in browser history or referrers.
  try { history.replaceState(null, '', location.pathname); } catch (_) {}
}
function clearInputs() {
  generation++;
  if (controller) controller.abort();
  controller = null; form.reset(); send.disabled = false;
  status.textContent = '모든 입력을 지웠습니다.';
}
document.getElementById('clear').addEventListener('click', clearInputs);
window.addEventListener('pagehide', clearInputs);
form.addEventListener('submit', async event => {
  event.preventDefault();
  if (send.disabled) return;
  const requestGeneration = ++generation;
  try {
    if (!consent.checked) throw new Error('외부 릴레이 전송 동의를 확인하세요.');
    const {sid, key} = readPairing(pairing.value);
    const uris = accountsInput.value.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
    if (!uris.length || uris.length > 50) throw new Error('한 번에 1~50개 URI를 입력하세요.');
    const accounts = uris.map(parseOtpAuthUri);
    const packet = encryptPayload({sid, expiresAt:Date.now() + 300000, accounts}, key);
    if (packet.length > 4000) throw new Error('릴레이 크기 제한입니다. 계정을 나누어 등록하세요.');
    send.disabled = true;
    status.textContent = '암호화한 계정 데이터를 전송하는 중…';
    const requestController = new AbortController();
    controller = requestController;
    const timeout = setTimeout(() => requestController.abort(), 15000);
    let response;
    try {
      response = await fetch('https://ntfy.sh/rw6_totp_' + sid, {
        method:'POST', headers:{'Content-Type':'text/plain'}, body:packet,
        credentials:'omit', referrerPolicy:'no-referrer', signal:requestController.signal
      });
    } finally { clearTimeout(timeout); }
    if (!response.ok) throw new Error('릴레이 전송 실패 (HTTP ' + response.status + ')');
    if (requestGeneration !== generation) return;
    accountsInput.value = '';
    status.textContent = '전송 완료. 워치에서 [지금 수신 확인]을 누르세요.\n등록이 확인되면 이 페이지의 입력을 모두 지우세요.';
  } catch (e) {
    if (requestGeneration === generation) status.textContent = e.name === 'AbortError' ? '전송 시간이 초과되었습니다. 연결 상태를 확인하세요.' : e.message;
  } finally {
    if (requestGeneration === generation) { send.disabled = false; controller = null; }
  }
});
