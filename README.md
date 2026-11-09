# Beat Rider — 박자 탈래 가족 협동 리듬게임

기존 HyundaiApp의 Kotlin / Jetpack Compose 프로젝트에 구현한 주차 전용 MVP입니다.
Vehicle SDK 2.3.2, Pleos Connect v2.1.0 에뮬레이터에서 검증했습니다.

## 플레이

1. 기어 P와 주행 제한 해제가 확인되면 `P · 주차 확인`이 표시됩니다.
2. 차량 도면에서 운전석 ★, 조수석 ◆, 뒷좌석 왼쪽 ●, 뒷좌석 오른쪽 ▲ 중 1–4명을 선택합니다. 차량 앞쪽은 도면 위에 표시됩니다.
3. 로비의 **노트 낙하 속도**를 0.50~2.00배(기본 1.00배)에서 선택합니다. 이 설정은 화면에서 노트가 내려오는 속도에만 적용됩니다. `함께 시작`을 누르고 4박자 준비 뒤 각자의 노트가 판정선에 도착할 때 화면 버튼을 누릅니다.
4. 120 BPM, 32노트, 19초의 내장 4박 비트 한 곡을 연주하고 공동 점수·최고 콤보를 확인합니다. 오디오는 ToneGenerator로 생성하며 별도 음원 다운로드가 없습니다.
5. 연습 모드는 판정 폭을 ±350ms로 넓힙니다. 기본은 ±220ms, PERFECT는 ±100ms입니다.

기어가 P가 아니거나, 주행 제한이 있거나, 차량 상태 조회가 실패/만료되면 게임이 멈춥니다.
백그라운드 이동과 오디오 포커스 상실 때도 멈추고, 주차가 다시 확인돼도 사용자가 `이어가기`를 눌러야 합니다. 노트가 0.25초 동안 위로 올라간 뒤, 3초 카운트다운 동안 로비에서 설정한 낙하 속도로 원래 위치에 내려옵니다. 카운트다운 중 곡 시간과 점수 판정은 멈춥니다.

## 독서등 실험 모드

기본 입력은 터치입니다. `독서등 입력 테스트`에서 실제 상태 변화와 좌석 대응을 확인한 뒤에만 사용합니다.

- `checkReadingLightSwitchCapabilities`와 `checkReadingLightStateCapabilities`를 각각 조회합니다.
- `getReadingLightSwitch`는 새로고침 시점의 스위치 설정입니다. 화면의 `현재 점등`은 `registerReadingLightState`의 실제 상태입니다. 두 값은 같다고 가정하지 않습니다.
- 한 자리씩 켜고 끄면서 영역/값/수신 시각을 확인합니다. 지원되고 실제 변화가 관찰된 자리만 `자리 확인`을 선택할 수 있습니다.
- `독서등 실험 모드`를 켜면 확인한 자리의 ON↔OFF 변화가 터치와 동일한 판정 경로에 들어갑니다. 초기값·동일값 반복은 입력이 아닙니다.
- 미지원·미확인 자리는 터치를 사용합니다. 자리 확인은 현재 앱 세션에만 유지하며 SDK 오류 시 해제합니다.
- 지연 보정 슬라이더는 차량 입력에만 적용됩니다. 양수는 수신된 입력을 그만큼 앞선 곡 시각으로 판정합니다. 기본값은 0ms입니다.

좌석 이름은 좌핸들 차량 기준입니다. 신호 영역은 조작한 사람을 알려 주지 않습니다. 자동 점등으로도 상태가 변할 수 있으므로 독서등은 선택적 실험 입력입니다.
앱에는 차량 상태를 바꾸는 setter가 없고 창문 위치 입력도 사용하지 않습니다. 원래 버튼 기능을 막거나 장치 상태를 복원/강제하지 않습니다.

## 빌드와 실행

Android Studio에서 프로젝트를 열고 Android SDK 37, JDK 및 Pleos 에뮬레이터를 설정합니다.
`local.properties`의 SDK 경로는 각 PC에서 생성합니다. 의존성은 프로젝트에 설정된 Pleos Maven 저장소에서 받습니다.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.hyundaiapp/.MainActivity
adb forward tcp:8088 tcp:8088
```

Virtual Console: http://localhost:8088/ — 테스트 중 브라우저를 열어 활성 세션을 유지합니다.
앱 실행 이름/패키지는 기존 `HyundaiApp` / `com.example.hyundaiapp`입니다.
Pleos v2.1.0은 android.car 클래스를 부트 클래스 경로에서 제공합니다. 따라서 공유 라이브러리는 optional로 선언하고, 상태 확인 자체는 우회하지 않습니다.
필요한 차량 권한은 읽기용 `CAR_LIGHTS`, `CAR_DRIVE`뿐이며 `CONTROL_CAR_*` 권한은 없습니다.

## 코드

- `MainActivity.kt`: Compose 로비·테스트·플레이·결과 화면, 비트, 오디오 포커스와 생명주기
- `VehicleSeatMap.kt`: 차량 상면도와 실제 좌석 위치의 참가 버튼
- `RhythmGame.kt`: 단조 시계 기반 곡 시간, 터치/차량 공통 판정, 일시정지
- `VehicleInputs.kt`: 주차/UX 제한 확인, capability 조회, 독서등 콜백과 좌석 확인
- `SignalEdges.kt`: 최초값·중복값 제외
- `RhythmGameTest.kt`: 판정, 시간 보정, 오래된 입력, 일시정지, 신호 필터 회귀 테스트 8개

[실제 검증 결과와 추가 확인 사항](docs/verification.md)
