## 외부 서버 없는 등록으로 전환

설치 파일: **com.vela.rw6totp.release.1.1.0.rpk** (versionCode 5)

중요: 이전 QR 웹 자동 전송을 **워치 직접 입력 + 오프라인 QR 도우미**로 변경했습니다. 이 릴리스는 QR을 통해 워치로 자동 전송하지 않습니다.

- 404가 나는 워치의 GitHub Pages QR 주소, ntfy 중계, 네트워크 권한과 Pages 배포 의존성 제거
- 워치의 + Add에서 Base32 키보드로 Secret → Name → Period → Review → Save
- SHA1/SHA256/SHA512, 6/8자리와 5–3600초 주기 지원
- 포함된 totp-import.html은 기존 OTP QR 이미지나 URI를 이 기기에서 읽고 입력할 값을 표시. QR 디코더와 라이선스가 HTML 안에 포함됨
- 도우미의 연결 정책은 connect-src 'none'. 브라우저를 오프라인으로 바꾼 뒤 QR/URI 읽기 중 외부 HTTP 요청 0건 검증
- 회귀 검사 44개와 Chromium 테스트 1개, 실제 RPK의 서명·해시·앱 시작·32개 키 바인딩 검사 통과

Redmi Watch 6의 문서상 BLE API 지원 및 interconnect의 동반 앱/서명 제약 때문에 브라우저만으로 서버 없는 자동 전송을 구현했다고 주장하지 않습니다. 자동 전송 유지에는 추가 Android 동반 앱과 실기기 검증이 필요합니다. 자세한 설명과 입력 절차는 README에 있습니다.

기존 앱을 삭제하지 않고 Notify의 펌웨어 업데이트 → 타사 앱(RPK)에서 업데이트하세요. 기존 앱 식별자·서명·저장소 키와 저장된 계정을 유지했습니다. 실제 워치 키보드의 터치/배치는 업데이트 후 확인이 필요합니다.
