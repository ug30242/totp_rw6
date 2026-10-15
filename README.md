# RW6 TOTP 동기화

중국판 Redmi Watch 6용 TOTP 워치 앱과 Android 동반 앱입니다. SHA1/256/512, 6·8자리, 5–3600초 주기와 최대 50개 계정을 지원합니다. 워치 UI는 ASCII 영어를 사용합니다.

## 설치와 사용

[릴리스](https://github.com/ug30242/totp_rw6/releases/latest)에서 같은 버전의 APK와 RPK를 설치하세요. 현재 버전은 **1.3.1 / versionCode 9**입니다.

1. `com.vela.rw6totp.release.1.3.1.rpk`를 Notify for Xiaomi의 펌웨어 업데이트 → 타사 앱(RPK)에서 업데이트합니다. 기존 앱을 삭제할 필요가 없습니다.
2. Android 8.0 이상에 `rw6-totp-android-1.3.1.apk`를 설치합니다. 워치는 Mi Fitness / 중국판 운동건강에 연결해야 합니다. Notify 단독 연결은 XMS 통신을 제공하지 않습니다.
3. Android의 **계정 추가**에서 이름·Base32 인증키·해시·자리수·주기를 저장합니다. 기존 계정은 목록에서 선택하고 **수정**합니다.
4. 워치의 TOTP → **+ Phone**을 열고 Android의 **동기화**를 누릅니다. 필요한 최초 Mi Fitness 기기 통신 권한을 허용하세요. 워치 수신 화면이 닫혔다면 응답 확인 후 자동 실행을 시도합니다.
5. 완료 후 워치에서 Close를 눌러 OTP를 확인합니다. 원본 인증기와 설정 및 시간이 같아야 합니다.

휴대폰 목록의 같은 항목은 워치에서 중복 없이 추가·갱신합니다. 워치의 수동 계정은 보존합니다. **휴대폰에서 삭제해도 워치에서는 삭제하지 않습니다.** 워치에서 Edit → Delete → Confirm으로 삭제하세요. Android 없이 입력하려면 워치의 **+ Key**를 사용합니다.

Android 화면은 **RW6 TOTP 동기화**, TOTP 목록과 **계정 추가 / 수정 / 동기화** 버튼으로 구성했습니다. QR 스캔·이미지/URI 가져오기·웹 도우미·카메라 권한 및 관련 실행 라이브러리는 제거했습니다. 과거 릴리스 파일은 이력과 회귀 검사를 위해 보존합니다.

## 1.3.1 워치 응답 오류 1006 수정

워치에 `Phone data received`가 표시되지만 `Reply failed (1006)`으로 끝나는 보고를 바탕으로 Android 수신 연결 절차를 점검했습니다. XMS 서비스의 메시지 리스너만 등록하고, 공식 SDK가 사용하는 `registerServiceConnectedListener` 호출을 빠뜨린 결함을 수정했습니다. 공식 SDK의 Binder 슬롯 1 등록 확인 신호도 처리합니다. 이 등록을 노드 조회·메시지 리스너 등록·동기화보다 먼저 완료하며, 실패하면 준비 완료로 처리하지 않습니다.

새 Android 테스트는 실제 생성 AIDL Proxy/Stub와 Parcel을 통과해 등록 → 준비 응답 → 저장 응답을 검사합니다. 등록 호출을 제거하거나 등록 확인 신호 처리를 제거하면 각각 회귀 테스트가 실패합니다. [분석과 통신 규격 근거](docs/xms-reply-1006.md)를 기록했습니다.

1.3.0의 페이지 종료 시 새 수신 핸들러 보존, 열린 수신 화면에 먼저 준비 요청하기, JSON/data/UTF-8 해제, 워치 수신·응답 진단도 유지합니다. **중국판 펌웨어 3.100.043에서 실제 Bluetooth 왕복은 사용자 기기 확인이 필요합니다.**

## 데이터 보관

휴대폰 계정은 Android Keystore의 AES-256 키와 AES-GCM으로 암호화하여 백업 제외 영역에 저장합니다. 기존 암호화 파일·안정적인 동기화 ID·워치 저장소 키·서명·앱 식별자는 유지합니다. 앱 삭제·키 손실 후 휴대폰 목록 복구를 제공하지 않으므로 원본 인증기와 복구 코드를 보관하세요.

워치 일반 저장소는 암호화 보관을 보장하지 않습니다. 기존 데이터 읽기나 저장에 실패하면 덮어쓰지 않습니다. 동기화는 계정별 저장이며 일부 저장 후 통신이 끊길 수 있습니다. APK는 인터넷·카메라 권한을 사용하지 않고, 워치에는 HTTP·웹소켓 기능이 없습니다. Mi Fitness 자체의 일반 네트워크 정책을 변경하지는 않습니다.

## 개발과 검증

Node.js 24, JDK 17/21, Android SDK 35 및 Build-Tools 35.0.0을 사용합니다.

```sh
npm ci
npm run build
npm run verify:rpk -- dist/com.vela.rw6totp.debug.1.3.1.rpk
npm run smoke:rpk -- dist/com.vela.rw6totp.debug.1.3.1.rpk
npm test
cd android
./gradlew --no-daemon :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
cd ..
npm run test:interop
```

Java 테스트는 계정 검증·암호화·준비 확인·저장 응답·타임아웃·Android 화면 실행과 XMS 네이티브 Binder 등록/왕복을 검사합니다. `test:interop`는 컴파일된 Android 코드의 실제 생성 패킷을 워치 저장 모듈로 보내 RFC OTP와 갱신을 확인합니다. RPK 검사는 Node 모듈 전역 없이 컴파일된 화면과 페이지 종료가 겹치는 경우의 수신 동작을 실행합니다. 실제 기기의 Mi Fitness 버전·Bluetooth·Keystore·터치·화면 배치는 별도 확인 대상입니다.

**루트 package.json에 `type: module`을 추가하지 마세요.** aiot-toolkit의 CommonJS 변환과 충돌해 검은 화면이 재발합니다. Node 24 MODULE_TYPELESS_PACKAGE_JSON 경고는 예상된 동작입니다. CLI에는 쓰기 가능한 `$HOME/.vela/vvd`가 필요합니다. aiot는 체크아웃 전체를 임시 복사·이동하므로 빌드 중 파일 편집이나 Android 빌드를 겹쳐 실행하지 마세요.

## 서명과 배포

`npm run release`는 기존 `sign/release/{private,certificate}.pem`을 사용합니다. Android 빌드 후 루트에서 `bash scripts/sign-apk.sh`를 실행하면 같은 인증서로 서명합니다. 앱 패키지명 `com.vela.rw6totp`와 인증서가 같아야 XMS 통신이 가능합니다. 개인키나 대체 키를 커밋하지 마세요.

`npm run verify:apk -- <apk>`는 서명·앱 식별자·버전·인터넷/카메라 권한 부재를 검사합니다. CI는 테스트와 빌드 및 저장된 설치 파일을 검증하고, v 태그 워크플로가 서명된 APK·RPK·체크섬·라이선스를 게시합니다. 체크섬은 `sha256sum -c SHA256SUMS`로 확인합니다.

통신은 MIT 공개 XMS Binder 규약 소스를 사용합니다. [출처와 고정 커밋](android/xms-contract/NOTICE.md), [라이선스](android/THIRD_PARTY_NOTICES.txt), [Android 개발 안내](android/README.md)를 참고하세요. Xiaomi 바이너리 SDK나 다른 동반 서비스는 포함하지 않습니다. 기존 aiot-toolkit 2.0.5 개발 의존성의 npm audit 경고는 남아 있으며 호환성을 깨는 강제 업데이트는 적용하지 않았습니다. [과거 설치·폰트 문제 이력](docs/notify-installation.md)을 보관합니다.
