#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
npm ci
npm test
npm run release
rpk_version=$(node -p "require('./package.json').version")
npm run verify:rpk -- "dist/com.vela.rw6totp.release.${rpk_version}.rpk"
npm run smoke:rpk -- "dist/com.vela.rw6totp.release.${rpk_version}.rpk"
