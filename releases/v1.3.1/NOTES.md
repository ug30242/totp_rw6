# RW6 TOTP 동기화 1.3.1

## 워치 응답 전송 실패 수정

워치에 `Phone data received (N)`가 계속 표시되지만 `Reply failed (1006)`으로 준비 응답을 보내지 못하는 보고를 바탕으로 Android 통신 연결 절차를 수정했습니다.

- 빠져 있던 XMS `registerServiceConnectedListener` 호출을 노드 조회와 메시지 수신 등록보다 먼저 실행합니다.
- Xiaomi SDK가 처리하는 Binder 슬롯 1 등록 확인 신호를 지원하고 등록용 Binder를 유지합니다. 빈 AIDL Stub만 사용하면 이 신호를 거부하는 차이를 보완했습니다.
- 연결 등록이 실패하면 준비 완료로 표시하지 않습니다. 준비 응답 전 계정 전송과 저장 응답 없는 성공 처리는 계속 차단합니다.

Android 제목과 TOTP 목록·계정 추가·수정·동기화 버튼, QR 없는 직접 동기화, 워치 영어 UI와 기존 데이터 형식을 유지합니다. [원인 분석과 규격 근거](https://github.com/ug30242/totp_rw6/blob/v1.3.1/docs/xms-reply-1006.md)를 기록했습니다.

## 업데이트

1. **`rw6-totp-android-1.3.1.apk`로 Android 앱을 업데이트하세요. 이번 핵심 수정은 APK에 있습니다.** 기존 앱을 삭제하지 마세요.
2. `com.vela.rw6totp.release.1.3.1.rpk`를 Notify의 펌웨어 업데이트 → 타사 앱(RPK)에서 업데이트합니다.
3. Mi Fitness / 중국판 운동건강에 워치가 연결된 상태에서 워치 TOTP → **+ Phone**을 열고 Android의 **동기화**를 누릅니다.

기존 APK/RPK 인증서·패키지명·휴대폰 암호화 파일과 키·워치 저장소·syncId를 보존합니다. 같은 항목을 다시 동기화하면 갱신합니다. 휴대폰에서 삭제한 항목을 워치에서 자동 삭제하지 않습니다.

## 검증 범위

워치/암호/저장소/컴파일된 화면 검사 60개와 Java 검사 25개가 통과했습니다. Java에는 실제 생성 AIDL Proxy/Stub와 Parcel을 사용하는 XMS 연결 등록·준비/저장 응답 왕복·등록 실패·재연결 검사를 추가했습니다. 등록 호출을 제거하거나 등록 확인 신호 처리를 제거하면 각각 회귀 검사가 실패합니다. Android 생성 패킷 → 워치 저장 → RFC OTP → 갱신, APK/RPK 서명과 권한, Android Lint도 확인했습니다.

테스트의 XMS 서비스는 규격을 구현한 가짜 서비스입니다. Redmi Watch 6 중국판 / 펌웨어 3.100.043과 실제 Mi Fitness의 Bluetooth 왕복은 클라우드에서 직접 검증하지 못했습니다. 확인된 연결 절차 누락을 수정했으며, 실기기에서 동기화 완료 확인이 필요합니다.
