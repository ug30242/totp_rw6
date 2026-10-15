// Watch firmware fonts may lack Hangul and other non-ASCII glyphs.
// Adapt only the displayed label; keep the original stored account untouched.
export function displayAccountName(name, index) {
  const ascii = String(name || '').replace(/[^\x20-\x7e]/g, '').replace(/\s+/g, ' ').trim();
  return /[A-Za-z0-9]/.test(ascii) ? ascii : 'Account ' + (index + 1);
}
