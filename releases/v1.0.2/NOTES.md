## 설치 후 검은 화면 수정

중국판 Redmi Watch 6 / 사용자 보고 펌웨어 3.100.043 / Notify for Xiaomi 대상 패치입니다.

**설치 파일: com.vela.rw6totp.release.1.0.2.rpk** (versionCode 3). Notify의 **펌웨어 업데이트 → 타사 앱(RPK)**에서 선택하세요. 기존 앱과 같은 식별자·인증서·저장소 키를 사용합니다. 먼저 데이터 삭제 없이 업데이트하세요.

1.0.1 배포 RPK의 진입점을 실행해 `ReferenceError: exports is not defined`를 재현했습니다. 루트의 `type: module` 설정이 aiot-toolkit의 CommonJS 중간 코드와 충돌해 `exports`와 상대 `require()`가 번들에 남아 앱 시작 시 중단됐습니다. 해당 설정을 제거하고 재빌드하여 수정했습니다.

검증:
- 실제 RPK의 앱·페이지 번들을 Node의 exports/require 없이 실행: 1.0.1 실패, 1.0.2 통과
- 첫 화면·빈 홈·저장된 RFC 계정 OTP·저장 오류·QR 화면 바인딩과 타이머 정리 검사
- 소스/패키지 회귀 테스트와 Chromium 웹 도구 테스트
- 외부·내부 RSA 서명, 패키지·파일 해시, ZIP CRC와 기존 인증서 검증

화면 실행 검사는 시스템 API와 VDOM을 모사합니다. 물리 워치의 네이티브 렌더링·레이아웃은 아직 검증하지 못했으므로 실제 기기에서 업데이트 후 재확인이 필요합니다. 이전 검증에서 놓친 컴파일 산출물 실행 검사를 CI와 릴리스 필수 검사에 추가했습니다.

등록 도구와 SHA256SUMS도 포함합니다. 개인키는 포함하지 않습니다. 웹 배포 및 전송·저장 보안의 알려진 한계는 README를 참고하세요.
