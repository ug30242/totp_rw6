# RW6 TOTP Android companion

Android 8.0 이상에서 계정을 직접 추가·수정하고 **워치로 동기화**합니다. QR은 선택 사항입니다. 휴대폰 계정은 Android Keystore의 비내보내기 AES-256 키와 AES-GCM으로 암호화하여 `noBackupFilesDir`에 보관합니다. 백업·기기 간 복원은 차단하며, 휴대폰 앱 삭제 시 계정 복구를 제공하지 않습니다. 원본 인증기와 복구 코드를 유지하세요.

Mi Fitness / 중국판 운동건강(`com.mi.health` 또는 `com.xiaomi.wearable`)의 XMS 서비스에 연결합니다. Notify는 RPK 설치에 사용할 수 있지만 XMS 통신 서비스를 대신하지 않습니다. 이 앱은 인터넷 권한, 계정 서버, 분석 SDK, 웹뷰를 사용하지 않습니다. Mi Fitness 자체의 일반 서비스 동작과 네트워크 정책을 변경하지는 않습니다.

## 빌드

JDK 17 또는 21, Android SDK Platform 35, Build-Tools 35.0.0이 필요합니다. SDK 위치는 `ANDROID_HOME`이나 무시 경로인 `local.properties`의 `sdk.dir`로 지정하세요. Gradle Wrapper는 8.13과 공식 SHA256을 고정했습니다.

```sh
cd android
./gradlew --no-daemon :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
cd ..
npm run test:interop
```

클라우드에서는 `python3 scripts/setup-cloud-android.py`로 검증된 도구를 `/workspace`에 준비할 수 있습니다. 환경의 프록시 설정은 전용 임시 Gradle 홈에만 기록합니다. 클라우드 실행 환경에서 사용하는 변수는 저장된 환경 시작 지침을 참고하세요. TLS 검증을 끄지 마세요.

`app/build/outputs/apk/release/app-release-unsigned.apk`는 설치용이 아닙니다. 워치와 **동일한 패키지명 `com.vela.rw6totp` 및 기존 인증서**로 서명해야 합니다.

```sh
bash scripts/sign-apk.sh
npm run verify:apk -- dist/rw6-totp-android-1.2.0.apk
```

위 명령은 저장소 루트에서 실행합니다. 기존 `sign/release/{private,certificate}.pem`을 사용해 임시 PKCS12를 만들고 종료 시 지웁니다. 키·암호를 출력하거나 커밋하지 않습니다. 공개 인증서 `certificates/release.pem`과 APK 서명을 독립 비교합니다.

## 전송 규약

`SyncSession`은 워치 `/receive` 실행을 요청한 뒤, 128비트 난수 ID를 가진 `rw6-totp-hello` / `rw6-totp-ready`로 실제 수신 준비를 확인합니다. 15초 안에 준비되지 않으면 계정 전송 없이 종료합니다. 연결 준비 후 `rw6-totp-sync`로 계정을 하나씩 보냅니다. 요청 ID와 별도로 암호화 목록에 보관한 안정적인 `account.syncId`를 사용해 워치 항목을 추가·갱신합니다. 워치 일반 저장소의 성공 콜백 후에만 `saved` 응답을 보냅니다.

Android → 워치는 UTF-8 JSON 바이트이며, 워치 → Android는 Vela `send({data:{data:json}})` 객체가 직렬화된 JSON입니다. Android는 이 실제 `data` 래퍼를 해제합니다. 타임아웃·잘못된 ID·중복 응답·저장 오류에 대한 테스트가 있습니다. 앱 배경 전환은 진행 중인 세션을 취소하며 이미 워치에 저장된 계정을 되돌리지 않습니다.

휴대폰의 삭제를 워치에 전파하지 않습니다. 워치의 수동 계정과 이전 계정을 보존합니다. 동기화 요청의 각 계정은 원자적으로 저장하지만 전체 배치는 하나의 트랜잭션이 아닙니다. 오류 시 일부 저장이 완료됐을 수 있고, 재동기화는 안정적인 ID로 중복을 방지합니다.

## SDK 출처와 검증 범위

공개 XMS 1.4 Binder 규약과 Parcelable 타입은 MIT 라이선스의 [XMS Wearable SDK Clean Room](https://github.com/OrPudding/XMS_Wearable_SDK_CleanRoom)에서 필요한 소스만 포함했습니다. [고정 커밋 및 고지](xms-contract/NOTICE.md), [MIT 전문](xms-contract/LICENSE), [전체 의존성 고지](THIRD_PARTY_NOTICES.txt)를 참고하세요. Xiaomi 바이너리 SDK나 OronBox 서비스를 배포하지 않습니다. 통신 클라이언트는 Xiaomi 공식 서비스 두 패키지만 조회합니다.

클라우드에서 APK 빌드·Lint·Java 단위 테스트, 실제 Android 생성 패킷의 워치 저장·OTP 생성, 컴파일된 워치의 수신·응답을 검사합니다. Android Keystore 실기기 동작, 카메라, Mi Fitness의 해당 버전 서비스 제공·권한, Redmi Watch 6 펌웨어 3.100.043의 Bluetooth 왕복 및 원격 화면 실행은 실기기 검증이 필요합니다. XMS 서비스가 없는 Mi Fitness 버전이나 Notify 단독 연결에서는 동작하지 않습니다.
