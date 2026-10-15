# RW6 TOTP 동기화 Android 앱

Android 8.0 이상에서 계정을 직접 추가·수정하고 동기화합니다. 화면에는 제목, TOTP 목록, 추가·수정·동기화 버튼만 둡니다. 스캔·카메라·이미지·URI 입력·웹뷰 기능은 없습니다. SDK 연결 성공은 워치 수신 확인과 다릅니다.

## 통신

Mi Fitness / 중국판 운동건강의 `com.xiaomi.wearable.XMS_WEARABLE_SERVICE`에 연결합니다. Notify는 RPK 설치에 사용할 수 있지만 XMS 서비스를 대신하지 않습니다. 서명과 패키지명을 기존 워치 앱과 일치시켜야 합니다.

SyncSession은 현재 워치 화면에 먼저 `rw6-totp-hello`를 보냅니다. 응답이 없을 때만 `/receive` 실행을 요청합니다. 준비 응답 후 계정을 하나씩 보내고 저장소 성공 이후의 `saved` 응답을 확인합니다. data 객체/문자열 래퍼도 제한된 깊이와 길이 내에서 해제합니다. 저장 요청 실패를 성공으로 처리하지 않습니다. 응답 확인용 초기 probe 실패는 재시도·원격 실행을 막지 않습니다.

워치 수신 콜백은 페이지별 수명을 갖습니다. 이전 페이지의 종료가 새 페이지가 등록한 singleton 수신 핸들러를 덮어쓰지 않습니다. 자동 실행 및 실제 Mi Fitness와 펌웨어의 메시지 전달은 실기기 검증 대상입니다.

## 데이터

`AccountVault`의 기존 파일·Android Keystore 별칭·AES-GCM 형식·안정적인 syncId를 보존합니다. 목록은 noBackupFilesDir에 암호화하고 백업·기기 이전에서 제외합니다. 파일 읽기 실패 시 목록을 새로 덮어쓰지 않습니다. 휴대폰 삭제를 워치에 전파하지 않으며 각 워치 항목의 추가·갱신만 수행합니다.

## 빌드

JDK 17/21, SDK Platform 35, Build-Tools 35.0.0과 Gradle Wrapper 8.13을 사용합니다. SDK는 ANDROID_HOME으로 지정합니다.

```sh
cd android
./gradlew --no-daemon :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
cd ..
npm run test:interop
bash scripts/sign-apk.sh
npm run verify:apk -- dist/rw6-totp-android-1.3.0.apk
```

`app-release-unsigned.apk`는 설치 파일이 아닙니다. 서명 스크립트는 기존 PEM 개인키·인증서로 임시 PKCS12를 만들고 종료 시 지웁니다. 비밀 정보를 출력·커밋하거나 대체 키를 생성하지 마세요. 공개 인증서와 APK 서명을 비교합니다.

클라우드 도구는 `python3 scripts/setup-cloud-android.py`로 준비합니다. 저장된 환경 시작 지침의 프록시·플랫폼 CA·JAVA_HOME/ANDROID_HOME을 사용하며 TLS/체크섬 검증을 끄지 마세요. Robolectric 화면 테스트는 테스트 전용 의존성이며 APK에 포함하지 않습니다.

MIT XMS 공개 Binder/Parcelable 소스의 [고정 출처](xms-contract/NOTICE.md)와 [라이선스](THIRD_PARTY_NOTICES.txt)를 제공합니다. QR·ZXing·AndroidX 실행 의존성은 없습니다. Android Keystore와 Mi Fitness 서비스·실제 Bluetooth·터치는 물리 기기 확인이 필요합니다.
