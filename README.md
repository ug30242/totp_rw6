# Redmi Watch 6 TOTP 인증기

중국판 Redmi Watch 6용 Xiaomi Vela 앱입니다. SHA-1 / SHA-256 / SHA-512, 6·8자리 OTP, 계정별 주기, 순서 변경·삭제를 지원합니다. 워치 UI는 기본 폰트에서 표시할 수 있는 ASCII 영어를 사용합니다.

## 1.1.0: 외부 서버 없이 등록

**등록 경로가 변경됐습니다.** 이전의 워치 QR → GitHub Pages → ntfy 중계 방식을 제거하고 **워치 직접 입력 + 오프라인 QR 도우미**로 변경했습니다. 휴대폰에서 워치로 자동 전송하는 기능은 이 버전에 없습니다.

404는 워치가 배포되지 않은 GitHub Pages 주소를 QR에 넣던 문제였습니다. 이전 구현에는 외부 ntfy 중계도 있어 서버를 거치지 않는다는 요구에 맞지 않았습니다. 현재 앱에는 네트워크 권한·웹 주소·중계 코드가 없고, 정적 웹 배포 워크플로도 제거했습니다. 과거 릴리스 파일은 회귀 검사와 이력 보존을 위해 그대로 보관합니다.

## 설치와 등록

[릴리스](https://github.com/ug30242/totp_rw6/releases/latest)에서 다음 파일을 받으세요.

- `com.vela.rw6totp.release.1.1.0.rpk`: Notify for Xiaomi의 **펌웨어 업데이트 → 타사 앱(RPK)**에서 선택합니다. 기존 앱을 삭제하지 않고 업데이트하세요. 앱 식별자·기존 서명·저장소 키를 유지했습니다.
- `totp-import.html`: 다운로드한 파일을 신뢰하는 브라우저에서 여는 독립 오프라인 도우미입니다. 실행 파일 안에 QR 디코더가 포함되어 CDN이나 GitHub Pages 설정이 필요하지 않습니다.

1. 원본 인증기에서 **일반 TOTP QR 이미지 또는 `otpauth://totp/...` URI**를 준비합니다. QR 이미지에는 인증키가 있으므로 공유하지 마세요.
2. 오프라인 도우미에서 이미지를 선택하거나 URI를 붙여넣고 **워치 입력 정보 보기**를 누릅니다. 인터넷 연결을 끊어도 동작합니다. 초기 프로그램 파일 다운로드에는 GitHub를 사용하지만 등록 데이터는 외부로 전송하지 않습니다.
3. 워치에서 **+ Add**를 누릅니다. **Secret** 화면에서 인증키를 입력하세요. 공백은 건너뛰며 Base32의 `A–Z`, `2–7`만 입력합니다. 키는 기본적으로 가려져 있고 **Show / Hide**, **Del**, **Clear**를 사용할 수 있습니다.
4. **Next**로 **Name**을 입력합니다. 이름은 선택 사항입니다. **123 / ABC**로 입력 모드를 바꾸고 **SP**로 공백을 입력합니다.
5. **Period**를 도우미에 나온 초 단위 값으로 입력합니다. 기본 30초가 표시됩니다. 변경하려면 **Clear**를 누른 뒤 입력하세요. 5–3600초 정수를 지원합니다.
6. **Review**에서 **Hash**와 **Digits**를 도우미와 맞추고 **Save**를 누릅니다. 기본은 SHA1 / 6자리입니다. 잘못된 입력·저장 실패 시 오류를 표시합니다.
7. 원본 인증기와 코드가 일치하는지 확인하고 도우미의 **모든 입력 지우기**를 누릅니다. 워치 시간이 정확해야 합니다.

도우미는 PNG/JPEG/WebP/BMP 이미지 10MB 이하를 로컬에서 읽습니다. 워치 Secret 입력 한도는 256자, 이름은 64자입니다. Google Authenticator의 `otpauth-migration`, 암호화된 백업 형식과 일반 웹 주소 QR은 지원하지 않습니다. 워치에는 카메라가 없어 휴대폰 화면의 QR을 직접 읽을 수 없습니다.

계정 이름의 저장 원문은 유지하며 화면에서는 ASCII 부분이나 `Account N`을 사용합니다. 계정은 워치 일반 로컬 저장소에 보관되므로 암호화 보관을 보장하지 않습니다. 원본 인증기와 복구 코드를 유지하세요.

## QR 자동 전송을 유지하려면

워치 QR을 휴대폰으로 읽는 것은 워치 → 휴대폰 방향의 정보 전달입니다. 반대 방향으로 계정을 보내려면 별도 통신 경로가 필요합니다. 조회한 [Vela BLE 문서의 기기 지원표](https://github.com/hefeishui0829/xiaomi-vela-docs/blob/main/01-official-quickapp-js/features/system/bluetooth.md)는 Redmi Watch 6를 미지원으로 표시하며, [interconnect 문서](https://github.com/hefeishui0829/xiaomi-vela-docs/blob/main/01-official-quickapp-js/features/network/interconnect.md)는 워치와 Android 동반 앱의 패키지명·서명 일치를 요구합니다. Notify에 이 앱의 임의 브라우저 데이터를 전달할 수 있는 API가 있다고 가정하지 않았습니다.

QR 자동 전송이 필수라면 호환되는 Android 동반 앱과 연결 SDK를 추가로 구현·실기기 검증해야 합니다. 현재 버전은 그 기능을 구현한 것으로 주장하지 않습니다. 현재 서버 없는 경로는 위의 직접 입력 절차입니다.

## 개발·검증

Node.js **24**와 npm을 사용합니다.

```sh
npm ci
npm test
npm run test:web
npm run build
npm run verify:rpk -- dist/com.vela.rw6totp.debug.1.1.0.rpk
npm run smoke:rpk -- dist/com.vela.rw6totp.debug.1.1.0.rpk
```

브라우저 테스트에는 Chromium 또는 Google Chrome이 필요합니다. 경로가 다르면 `CHROMIUM_PATH`로 지정합니다. 페이지를 로컬 테스트 서버에서 로드한 뒤 브라우저를 오프라인으로 바꾸어 QR 이미지/URI 읽기와 외부 요청 0건을 검증합니다. 개발 중 도우미를 확인하려면 `npm run web:build` 후 `python3 -m http.server 8080 --bind 127.0.0.1 --directory web`을 사용할 수 있지만 실제 도우미는 단일 HTML 파일입니다.

소스/컴파일된 화면/저장소/표준 OTP/RPK 회귀 검사 **44개**와 Chromium 테스트 **1개**를 실행했습니다. 컴파일된 화면 검사는 시스템 API와 VDOM을 모사합니다. 실제 워치의 터치·키보드 배치·저장소는 별도 실기기 확인 대상입니다.

**루트 package.json에 `type: module`을 추가하지 마세요.** aiot-toolkit의 CommonJS 중간 코드와 충돌해 `exports is not defined` 검은 화면 오류가 재발합니다. Node 24 테스트의 MODULE_TYPELESS_PACKAGE_JSON 경고는 예상된 동작입니다. 공식 CLI는 쓰기 가능한 `$HOME/.vela/vvd`를 필요로 합니다. GUI 에뮬레이터/AIoT IDE는 선택 사항입니다.

## 빌드·릴리스와 서명

`npm run release`에는 무시 경로인 `sign/release/private.pem`, `sign/release/certificate.pem`이 필요합니다. 기존 키를 유지하고 개인키를 Git에 올리지 마세요. 공개 인증서는 `certificates/release.pem`에 있습니다. `build-rpk.sh` / `.bat`는 잠금파일 설치, 검사, 공식 빌드와 서명·실행 검증을 수행합니다.

CI는 개인키 없이 개발용 RPK를 빌드합니다. `v*` 태그 워크플로는 `releases/<tag>/`의 이미 서명된 RPK, 오프라인 HTML, 체크섬과 라이선스 고지를 검증·게시합니다. `sha256sum -c SHA256SUMS`로 다운로드를 확인할 수 있습니다. HTML에는 jsQR 1.4.0(Apache-2.0)을 수정 없이 포함하며 라이선스를 함께 넣었습니다. [고지](web/THIRD_PARTY_NOTICES.txt)를 참고하세요.

`aiot-toolkit 2.0.5` 개발 도구의 알려진 npm audit 경고는 기존 22개(critical 2, high 17, low 3)입니다. 앱 RPK에 npm 개발 도구를 포함하지 않습니다. 호환성 확인 없이 `audit fix --force`를 적용하지 않았습니다. 신뢰하는 프로젝트 빌드에 사용하세요.

설치·시작·폰트 문제의 이력은 [진단 안내](docs/notify-installation.md)에 있습니다.
