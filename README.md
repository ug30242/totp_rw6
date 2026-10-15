# Redmi Watch 6 TOTP 인증기

중국판 Redmi Watch 6용 Xiaomi Vela 앱과 Android 동반 앱입니다. SHA-1 / SHA-256 / SHA-512, 6·8자리 OTP, 계정별 주기, 순서 변경·삭제를 지원합니다. 워치 UI는 기본 폰트에 맞춘 ASCII 영어를 사용합니다.

## 1.2.0: QR 없이 Android에서 동기화

**Android 앱에서 계정을 추가한 뒤 ‘워치로 동기화’를 누르세요.** QR이나 웹사이트, 계정 중계 서버가 필요하지 않습니다. QR 카메라·이미지·`otpauth` URI 가져오기는 기존 계정을 입력하는 선택 기능입니다.

휴대폰 목록은 Android Keystore의 AES-256 키와 AES-GCM으로 암호화하여 보관합니다. 동기화는 Mi Fitness / 중국판 운동건강의 로컬 XMS 서비스와 Bluetooth를 사용합니다. APK에는 인터넷 권한이 없고 워치에도 HTTP·웹소켓 기능이 없습니다. Mi Fitness 자체의 서비스 동작과 네트워크 정책은 변경하지 않습니다.

## 설치와 사용

[1.2.0 릴리스](https://github.com/ug30242/totp_rw6/releases/tag/v1.2.0)에서 **APK와 RPK를 함께** 받으세요.

1. `com.vela.rw6totp.release.1.2.0.rpk`를 Notify for Xiaomi의 **펌웨어 업데이트 → 타사 앱(RPK)**에서 설치합니다. 기존 앱을 삭제하지 않고 업데이트하세요. 앱 식별자·기존 서명·워치 저장소 키를 유지했습니다.
2. Android 8.0 이상 휴대폰에 `rw6-totp-android-1.2.0.apk`를 설치합니다. 워치는 **Mi Fitness(중국판 운동건강)**에 연결되어 있어야 합니다. Notify는 RPK 설치에 사용할 수 있지만, Notify 단독 연결은 이 동기화 통신을 제공하지 않습니다.
3. Android의 **계정 직접 추가**에서 이름·Base32 인증키를 입력합니다. 기본은 SHA1 / 6자리 / 30초이며 원본 인증기의 설정과 맞추세요. **휴대폰에 저장**을 누릅니다. QR은 없어도 됩니다.
4. **워치로 동기화**를 누릅니다. 처음에는 Mi Fitness의 기기 통신 권한을 허용하세요. 연결된 워치가 여러 개면 대상을 선택합니다. 앱이 워치 TOTP의 수신 화면을 열고 준비 응답을 받은 후 전송합니다.
5. 자동 실행이 지원되지 않으면 워치에서 **TOTP → + Phone**을 직접 열고 다시 동기화하세요. 수신 화면이 열린 상태에서 동기화 요청을 자동 저장합니다. 워치에서 별도로 Save를 누를 필요가 없습니다.
6. Android에 **동기화 완료**가 표시되면 워치에서 **Close**를 눌러 목록으로 돌아갑니다. 원본 인증기와 OTP가 같은지 확인하세요. 워치 시간이 정확해야 합니다.

같은 휴대폰 항목을 다시 동기화하면 중복 없이 갱신합니다. 휴대폰에서 이름·인증키·설정을 수정해도 같은 워치 항목을 업데이트합니다. 워치의 수동 계정은 유지하며, **휴대폰에서 삭제해도 워치에서는 삭제하지 않습니다.** 워치의 Edit → Delete → Confirm으로 직접 삭제할 수 있습니다. 최대 50개 계정을 지원합니다.

저장 응답을 받기 전에는 성공을 표시하지 않습니다. 일부 계정 저장 후 연결이 끊길 수 있으므로 오류가 나면 표시된 완료 개수와 워치 목록을 확인하고 다시 동기화하세요. 기존 데이터가 읽히지 않으면 덮어쓰지 않습니다. Android 앱을 나가면 임시 입력·진행 중인 동기화를 정리하지만 이미 워치에 저장된 계정은 유지합니다.

## 선택 입력 방법

Android 앱의 **계정 QR 카메라 스캔**, **QR 이미지 선택**, **URI 확인**으로 읽은 계정을 휴대폰 목록에 추가할 수 있습니다. QR을 웹 주소로 열지 않습니다. 일반 TOTP `otpauth://totp/...`만 지원하며 Google Authenticator `otpauth-migration`과 암호화된 백업 형식은 지원하지 않습니다. 이미지는 10MB / 1600만 화소 이하, 인증키는 256자, 이름은 64자까지입니다.

Android 없이 등록하려면 워치의 **+ Key**를 사용하세요. Secret → Name → Period → Review에서 Hash·Digits 확인 → Save 순서입니다. Secret은 `A–Z`, `2–7` 버튼으로 입력하고 공백은 건너뜁니다. Period는 5–3600초입니다. 릴리스의 `totp-import.html`은 기존 QR 이미지/URI에서 직접 입력할 값을 확인하는 독립 오프라인 보조 도구이며 앱 동기화에는 필요하지 않습니다.

계정 이름 원문은 저장하고 워치 화면에서는 ASCII 부분 또는 `Account N`을 표시합니다. 워치의 일반 로컬 저장소는 암호화 보관을 보장하지 않습니다. Android의 암호화 계정 파일은 백업·기기 이전 대상에서 제외하며 앱 삭제·키 손실 후 복구하지 못합니다. **원본 인증기와 복구 코드를 유지하세요.**

## 개발과 검증

워치는 Node.js **24**, Android는 JDK 17/21, SDK 35, Build-Tools 35.0.0을 사용합니다.

```sh
npm ci
npm test
npm run test:web
npm run build
npm run verify:rpk -- dist/com.vela.rw6totp.debug.1.2.0.rpk
npm run smoke:rpk -- dist/com.vela.rw6totp.debug.1.2.0.rpk
cd android
./gradlew --no-daemon :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
cd ..
npm run test:interop
```

브라우저 검사에는 Chromium / Google Chrome 또는 `CHROMIUM_PATH`가 필요합니다. 페이지를 로드한 뒤 오프라인으로 바꿔 QR·URI 읽기와 외부 HTTP 요청 0건을 검사합니다. 별도 웹 서버는 제품 사용에 필요하지 않습니다.

Java 테스트는 직접 입력·URI 검증·암호화 무결성·동기화 준비·저장 응답·중복·실패·타임아웃을 검사합니다. `test:interop`는 **컴파일된 Android 코드가 생성한 실제 패킷**을 워치 저장 모듈로 보내 RFC 6238 코드와 항목 갱신까지 확인합니다. RPK 실행 검사는 Node 모듈 전역 없이 컴파일된 홈·직접 입력·동기화 화면을 실행합니다.

실제 Redmi Watch 6 / 펌웨어 **3.100.043**와 Android·Mi Fitness 사이의 Bluetooth 왕복, 원격 화면 실행, 카메라·Keystore·터치 동작은 이 클라우드에서 검증하지 못했습니다. 해당 Mi Fitness 버전이 XMS 서비스를 제공해야 합니다. 자동화 검사는 실기기 호환성 보증을 대신하지 않습니다. [Android 빌드·전송 규약·검증 범위](android/README.md)를 참고하세요.

**루트 package.json에 `type: module`을 추가하지 마세요.** aiot-toolkit의 CommonJS 중간 코드와 충돌해 검은 화면이 재발합니다. Node 24의 MODULE_TYPELESS_PACKAGE_JSON 경고는 예상된 동작입니다. CLI에는 쓰기 가능한 `$HOME/.vela/vvd`가 필요합니다. [설치·실행·폰트 문제 이력](docs/notify-installation.md)을 보관합니다.

## 서명과 릴리스

`npm run release`는 기존 `sign/release/{private,certificate}.pem`으로 RPK를 서명합니다. Android 빌드 후 저장소 루트에서 `bash scripts/sign-apk.sh`를 실행하면 같은 인증서로 APK를 서명하고 검증합니다. **패키지명과 인증서가 같아야 XMS 통신이 가능합니다.** 개인키나 새 대체 키를 커밋하지 마세요. 공개 인증서는 `certificates/release.pem`입니다.

CI는 워치·Android 빌드와 검사, 저장된 RPK·APK의 서명·식별자·권한·버전을 검증합니다. `v*` 태그 워크플로는 `releases/<tag>/`의 이미 서명된 파일과 체크섬·고지를 게시합니다. 다운로드한 파일은 `sha256sum -c SHA256SUMS`로 확인할 수 있습니다.

Android는 MIT 공개 XMS Binder 규약 소스와 Apache-2.0 ZXing/AndroidX를 사용합니다. Xiaomi 바이너리 SDK나 다른 동반 서비스를 배포하지 않습니다. 오프라인 HTML은 Apache-2.0 jsQR을 포함합니다. [Android 고지](android/THIRD_PARTY_NOTICES.txt)와 [HTML 고지](web/THIRD_PARTY_NOTICES.txt)를 제공합니다.

고정 개발 도구 aiot-toolkit 2.0.5의 기존 npm audit 경고 22개(critical 2, high 17, low 3)는 남아 있습니다. 이 도구를 RPK에 포함하지 않으며 호환성을 깨는 자동 강제 업데이트는 적용하지 않았습니다.
