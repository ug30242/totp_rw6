# Redmi Watch 6 TOTP 인증기

중국판 Redmi Watch 6용 Xiaomi Vela 앱입니다. SHA-1 / SHA-256 / SHA-512, 6·8자리 OTP, 계정별 주기, 순서 변경·삭제를 지원합니다.

## 설치

[릴리스](https://github.com/ug30242/totp_rw6/releases)에서 **`com.vela.rw6totp.release.1.0.1.rpk`**를 다운로드하고 Notify for Xiaomi의 **펌웨어 업데이트 → 타사 앱(RPK)**에서 선택합니다. ZIP이나 소스 코드 압축파일을 선택하지 마세요. 태그의 `releases/v1.0.1/`에도 동일한 파일을 보관합니다.

첨부 원본 RPK는 서명에 포함된 콘텐츠 해시가 실제 패키지 내용과 달랐습니다. 공식 `aiot-toolkit 2.0.5`로 재빌드하고 외부·내부 서명, 패키지·파일 해시와 ZIP CRC를 검증했습니다. 중국판 펌웨어 **3.100.043**과 Notify for Xiaomi에서의 실제 전송·설치는 아직 검증하지 못했습니다. 상세 진단과 재설치 확인 절차는 [설치 안내](docs/notify-installation.md)에 있습니다.

## 계정 등록

1. 워치에서 **+ QR 웹 추가**를 누릅니다. 세션은 5분 동안 유효합니다.
2. GitHub Pages가 설정되어 있다면 QR을 스캔하여 웹 도구를 엽니다. Pages 설정 전에는 릴리스의 `totp-import.html`을 신뢰하는 브라우저에서 열고, 워치 QR을 스캔한 **전체 URL**을 붙여넣으세요. 기기의 로컬 HTML 실행 제한이 있다면 아래 로컬 서버 방식으로 사용합니다.
3. 원본 인증기에서 내보낸 `otpauth://totp/...` URI를 한 줄에 하나씩 입력합니다. QR 이미지 자동 인식과 Google Authenticator의 `otpauth-migration`은 지원하지 않습니다.
4. 외부 릴레이 전송 동의 후 **암호화해서 전송**을 누르고, 워치에서 **지금 수신 확인**을 누릅니다. 중복 계정은 다시 추가하지 않습니다. 전송 크기 제한에 걸리면 계정을 나누어 등록하세요.
5. 워치에서 OTP가 원본 인증기와 일치하는지 확인한 뒤 입력을 지웁니다. 워치와 휴대폰의 시간이 정확해야 합니다.

ntfy.sh에는 암호문이 전송되지만 통신 메타데이터와 암호문은 보관될 수 있습니다. 별도 자체 서버는 필요 없지만 **외부 릴레이에 의존**합니다. 워치 네트워크 또는 휴대폰 앱의 네트워크 중계가 필요하며 Notify가 이 앱에 해당 기능을 제공하는지는 실기기 확인 대상입니다. 호환되는 Android 앱이 없는 `system.interconnect` 경로는 제거했습니다.

RW6E1은 기존 형식과 호환되는 HMAC 기반 전송 방식이며 독립적인 보안 감사를 받지 않았습니다. 브라우저 암호화 nonce는 Web Crypto 난수를 사용합니다. 워치 페어링 값은 20문자 키·짧은 유효시간을 사용하지만 Vela `Math.random`의 암호학적 안전성은 확인되지 않았습니다. 워치의 계정 저장소는 평문이며 보안 저장소를 보장하지 않습니다. 원본 인증기와 복구 코드를 유지하세요.

## 개발·검증

Node.js **24**, npm과 Linux/macOS/Windows가 필요합니다.

```sh
npm ci
npm test
npm run test:web
npm run build
npm run verify:rpk -- dist/com.vela.rw6totp.debug.1.0.1.rpk
```

브라우저 테스트에는 Chromium 또는 Google Chrome이 필요합니다. 경로가 다르면 `CHROMIUM_PATH`로 지정합니다. GUI 에뮬레이터는 선택 사항이며 AIoT IDE/에뮬레이터를 설치한 환경에서 `npm start`를 사용할 수 있습니다. 클라우드에서는 빌드, 로직 테스트와 브라우저 테스트를 검증했습니다.

공식 CLI가 사용자 디렉터리에 `.vela/vvd`를 생성합니다. 해당 경로에 쓰기 권한이 필요합니다. Linux/macOS에서는 다음 명령으로 정적 웹 도구를 로컬에서 실행할 수 있습니다.

```sh
npm run web:build
python3 -m http.server 8080 --bind 127.0.0.1 --directory web
```

### 릴리스 서명

`npm run release`에는 `sign/release/private.pem`과 `sign/release/certificate.pem`이 필요합니다. **개인키를 Git에 올리지 마세요.** 이번 릴리스는 첨부 프로젝트의 기존 인증서를 유지했습니다. 공개 인증서는 `certificates/release.pem`에 있습니다. 기존 앱 업데이트는 같은 서명키를 유지해야 하므로 개인키는 안전하게 별도 보관하세요.

새 프로젝트용 키가 필요한 경우에만 새 키를 생성하세요. 기존 사용자의 업데이트용 키를 대체하지 마세요.

```sh
mkdir -p sign/release
openssl req -newkey rsa:2048 -nodes -keyout sign/release/private.pem -x509 -days 3650 -out sign/release/certificate.pem -subj '/CN=Redmi Watch TOTP Development'
```

`build-rpk.sh` / `build-rpk.bat`는 잠금파일 설치, 테스트, 웹 도구 생성, 공식 릴리스 빌드 및 서명 검증을 순서대로 수행합니다. 새 서명으로 배포할 때는 공개 인증서도 의도적으로 갱신해야 합니다. CI는 개인키 없이 공식 툴체인의 개발용 서명으로 빌드하고, 저장소에 보관된 릴리스 파일을 별도로 검증합니다.

### GitHub 릴리스·웹 배포

- `main` CI: 잠금파일 설치, 단위·브라우저 테스트, debug RPK 빌드, 릴리스 서명·체크섬 검사.
- `v*` 태그: `releases/<tag>/`의 **이미 서명된** RPK와 웹 도구·체크섬을 검증한 뒤 GitHub Release에 게시합니다. 개인키를 Actions에 전송하지 않습니다. 릴리스 파일은 소스와 함께 리뷰할 수 있도록 저장소에도 보관합니다.
- QR의 웹 주소는 `https://ug30242.github.io/totp_rw6/`입니다. **처음에는 저장소 Settings → Pages → Source를 GitHub Actions로 선택한 뒤 `Publish web importer` 워크플로를 실행해야 합니다.** 그 전에는 QR 주소가 열리지 않으므로 릴리스에 포함된 독립 HTML 도구를 사용하세요.

### 알려진 툴체인 의존성 문제

`aiot-toolkit`의 공개 안정 버전은 2.0.5입니다. 2026-10-15의 `npm audit`에서 개발 도구 의존성 경고 **22개(critical 2, high 17, low 3)**가 보고됐습니다. 주로 toolkit의 simple-git, 압축·패턴 처리와 에뮬레이터 관련 경로이며 앱 RPK나 독립 웹 도구에 npm 개발 의존성을 포함하지 않습니다. 빌드 호환성을 확인하지 않은 주요 버전 강제 교체나 `audit fix --force`는 적용하지 않았습니다. 신뢰할 수 없는 프로젝트를 이 툴체인으로 빌드하거나 개발 서버를 외부에 공개하지 마세요.
