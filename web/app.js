const form = document.getElementById('import-form');
const accountsInput = document.getElementById('accounts');
const imageInput = document.getElementById('qr-image');
const status = document.getElementById('status');
const results = document.getElementById('results');
let generation = 0;

function clearInputs() {
  generation++;
  form.reset(); results.replaceChildren();
  status.textContent = '모든 입력과 표시된 인증키를 지웠습니다.';
}
document.getElementById('clear').addEventListener('click', clearInputs);
window.addEventListener('pagehide', clearInputs);
try { if (location.hash) history.replaceState(null, '', location.pathname); } catch (_) {}

function addLine(container, label, value, className) {
  const p = document.createElement('p');
  const title = document.createElement('strong'); title.textContent = label + ': ';
  const text = document.createElement('span'); text.textContent = value;
  if (className) text.className = className;
  p.append(title, text); container.append(p);
}
function renderAccounts() {
  results.replaceChildren();
  const uris = accountsInput.value.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
  if (!uris.length || uris.length > 50) throw new Error('한 번에 1~50개 URI를 입력하세요.');
  const accounts = uris.map(parseOtpAuthUri);
  for (const account of accounts) if (account.secret.length > 256) throw new Error('인증키가 워치의 256자 입력 한도를 초과합니다.');
  accounts.forEach((account, index) => {
    const card = document.createElement('article'); card.className = 'account';
    const heading = document.createElement('h2'); heading.textContent = (index + 1) + '. ' + account.name;
    card.append(heading);
    addLine(card, 'Secret', account.secret.match(/.{1,4}/g).join(' '), 'secret');
    addLine(card, 'Name (권장)', displayAccountName(account.name, index).toUpperCase().replace(/[^A-Z0-9 ._@-]/g, '').slice(0, 64));
    addLine(card, 'Hash', account.algorithm);
    addLine(card, 'Digits', String(account.digits));
    addLine(card, 'Period (초)', String(account.period));
    results.append(card);
  });
  status.textContent = accounts.length + '개 계정의 입력 정보를 준비했습니다. 워치에서 직접 입력 후 Save를 누르세요.';
}
form.addEventListener('submit', event => {
  event.preventDefault();
  try { renderAccounts(); }
  catch (e) { results.replaceChildren(); status.textContent = e.message; }
});

imageInput.addEventListener('change', async () => {
  const current = ++generation;
  results.replaceChildren(); accountsInput.value = '';
  const file = imageInput.files[0]; if (!file) return;
  let objectUrl;
  try {
    if (!/^image\/(png|jpeg|webp|bmp)$/.test(file.type) || file.size > 10 * 1024 * 1024) throw new Error('10MB 이하의 PNG/JPEG/WebP/BMP 이미지를 선택하세요.');
    status.textContent = '이 기기에서 QR 이미지를 읽는 중…';
    objectUrl = URL.createObjectURL(file);
    const image = new Image();
    await new Promise((resolve,reject) => { image.onload = resolve; image.onerror = () => reject(new Error('이미지를 열 수 없습니다.')); image.src = objectUrl; });
    if (current !== generation) return;
    if (!image.width || !image.height || image.width * image.height > 16000000) throw new Error('이미지 해상도가 너무 큽니다. QR 부분을 잘라 다시 시도하세요.');
    const canvas = document.createElement('canvas'); canvas.width = image.width; canvas.height = image.height;
    const ctx = canvas.getContext('2d', {willReadFrequently:true});
    ctx.drawImage(image,0,0);
    const pixels = ctx.getImageData(0,0,canvas.width,canvas.height);
    const decoded = jsQR(pixels.data,pixels.width,pixels.height,{inversionAttempts:'attemptBoth'});
    canvas.width = canvas.height = 0;
    if (!decoded) throw new Error('QR 코드를 찾지 못했습니다. 더 선명한 이미지나 URI를 사용하세요.');
    // Only accept TOTP account data. Never navigate to a URL from an image.
    parseOtpAuthUri(decoded.data);
    accountsInput.value = decoded.data;
    renderAccounts();
  } catch (e) { if (current === generation) { results.replaceChildren(); status.textContent = e.message; } }
  finally { if (objectUrl) URL.revokeObjectURL(objectUrl); }
});
