# 남부터미널 출퇴근 버스 안내

남부터미널과 한국제약바이오협회를 오가는 **461·641·4319번 버스 도착정보**를 보여주는 개인용 앱입니다. 경복궁–남부터미널 구간의 지하철 3호선 도착정보도 함께 확인할 수 있습니다.

PC에서는 로컬 웹 앱으로, Android에서는 같은 화면에 네이티브 알림·시간 예약·위치 감지 기능을 결합해 동작합니다. 최초 실행은 API 키 없이 사용할 수 있는 **가상 데이터 모드**입니다.

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| 버스 도착정보 | 첫 번째·두 번째 버스의 남은 시간과 가장 먼저 도착할 노선 표시 |
| 출근·퇴근 방향 | 수동 선택 또는 한국 시간에 따른 자동 전환 |
| 지하철 정보 | 경복궁·남부터미널역의 3호선 상행/하행 도착정보 |
| 활성화 시간 | 기본 출근 06:00–10:00, 퇴근 18:00–24:00 또는 24시간 |
| Android 알림 | 지속 알림, 카운트다운, 시간표 기반 시작·종료 |
| 위치 진입 알림 | Android에서 지정 역·정류장 반경 300m 진입 감지 |
| 오류 처리 | 실시간 조회 실패를 가상 데이터로 대체하지 않으며 오래된 버스정보 표시 제한 |

출근 버스 승차 지점은 한국제약바이오협회, 퇴근 승차 지점은 남부터미널역(한일시멘트)입니다. 실시간 모드에서는 노선 API로 진행 방향과 정류장 순서를 검증합니다.

## PC 실행

Node.js 20 이상과 npm을 준비하고 저장소 루트에서 실행합니다.

```sh
npm ci
npm start
```

브라우저에서 **http://127.0.0.1:4173**에 접속합니다. Windows PowerShell에서 실행 정책 오류가 나면 `npm.cmd`를 사용하세요. 의존성 설치 후에는 `PC 테스트.cmd`로도 실행할 수 있습니다.

PC 서버는 `127.0.0.1` 전용 로컬 도구입니다. PC 알림은 일회성 브라우저 알림이며 실제 GPS 진입 감지는 Android 기능입니다. PC에서는 시간 제한 해제 테스트가 기본으로 켜집니다.

## 실시간 모드

설정에서 API 키를 입력하고 실시간 모드로 전환합니다.

- 버스: 공공데이터포털의 서울시 정류소정보·노선정보 API를 사용하며 두 서비스의 활용 권한이 필요합니다.
- 지하철: 서울 열린데이터광장의 실시간 지하철 도착정보 인증키를 별도로 사용합니다. 선택 사항입니다.
- PC는 `BUS_API_KEY`, `SUBWAY_API_KEY` 환경변수도 읽습니다. 기존 설정 파일 값이 있으면 그 값이 우선합니다.
- `PORT`로 포트, `CONFIG_PATH`로 설정 파일 경로를 바꿀 수 있습니다. `.env` 자동 로딩 기능은 없습니다.

PC 설정은 `.local/settings.json`, Android 설정은 앱 전용 SharedPreferences에 저장됩니다. 로컬 설정 파일은 Git에서 제외됩니다. 현재 외부 교통 API 요청은 HTTP를 사용하므로 전송 구간이 암호화되지 않습니다.

## Android 사용 및 빌드

`dist/homebound.apk`는 기존에 만든 **개인 테스트용 debug APK**입니다. Android 8.0 이상을 대상으로 하며 이번 문서 정리에서는 APK를 다시 빌드하지 않았습니다.

소스 빌드는 JDK 17, Android SDK 35와 Gradle 환경이 필요합니다. `android/`를 Android Studio에서 열어 SDK 경로와 Gradle을 구성하세요. Gradle Wrapper와 로컬 `tools/`는 저장소에 포함되어 있지 않습니다.

기존 Windows 스크립트는 `tools/gradle/gradle-8.11.1/bin/gradle.bat`를 요구합니다. 해당 환경을 별도로 준비했다면 다음 명령으로 빌드할 수 있습니다.

```powershell
.\build-android.ps1
# USB 디버깅 기기에 설치
.\build-android.ps1 -Install
```

권한 설정과 상세 사용법은 [기존 사용 설명서](docs/USAGE.md)에 보존했습니다. Google Play services와 위치 권한이 필요하며 기기의 절전 정책에 따라 알림이 지연될 수 있습니다. 사용 설명서의 과거 API 확인 기록은 이번 검토에서 재확인한 결과가 아닙니다.

## 구조

```text
server.mjs       로컬 서버·설정 저장
lib/             Node.js 교통 API·시간표 정책
web/             공통 화면·정류장 좌표
android/         Java WebView·지속 알림·예약·지오펜싱
test/            Node.js·Playwright·Java 정책 검사
dist/            기존 APK
docs/            사용법·코드리뷰
```

PC는 `web → server.mjs → 교통 API`, Android는 `web → JavaScript bridge → Java → 교통 API` 순서로 동작합니다. API와 시간표 로직이 JavaScript와 Java에 각각 있으므로 변경할 때 양쪽을 확인해야 합니다.

## 검증 및 제한사항

```sh
npm test
# 최초 UI 검사 환경에서 브라우저 설치
npx playwright install chromium
npm run test:ui
```

2026-11-17 검토에서 Node.js 테스트 10개, Playwright UI 검사, Java 시간표·위치 정책 검사가 통과했습니다. 실제 API 호출, Android 전체 빌드 및 실기기 GPS·백그라운드 동작은 검증하지 않았습니다.

[코드리뷰](docs/CODE_REVIEW.md)에 개선 우선순위를 정리했습니다. 이번 변경은 문서화이며 앱 동작은 수정하지 않았습니다.
