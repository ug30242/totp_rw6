#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
npm ci
npm test
npm run web:build
npm run release
npm run verify:rpk -- dist/com.vela.rw6totp.release.1.0.1.rpk
