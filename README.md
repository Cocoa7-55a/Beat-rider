# Beat Rider — 박자 탈래 가족 협동 리듬게임

기존 HyundaiApp의 Kotlin / Jetpack Compose 프로젝트에 구현한 주차 전용 협동 리듬게임 MVP입니다.  
Vehicle SDK 2.3.2, Pleos Connect v2.1.0 에뮬레이터에서 검증했습니다.

---

## 플레이

1. **주차 확인**: 기어 P와 주행 제한 해제가 확인되면 상단에 `P · 주차 확인`이 표시됩니다.
2. **곡 및 난이도 선택**: 홈에서 **곡 선택**을 누르고 곡과 난이도를 고릅니다. 왼쪽은 곡 목록, 오른쪽은 앨범 아트와 난이도별 노트 수입니다.
3. **자리 및 연주 설정**: 처음에는 네 자리 모두 준비 대기입니다. 주차 상태에서 해당 자리의 독서등을 켜면 준비 완료, 끄면 준비 대기로 돌아갑니다. 자리 카드를 눌러서도 준비 상태를 바꿀 수 있습니다. 한 자리 이상 준비되면 연주를 시작할 수 있으며, 한 판을 마치거나 로비로 나오면 준비 상태가 초기화됩니다.
4. **연주 시작**: **노트 낙하 속도**(0.50~2.00배, 기본 1.00배)를 정하고 `함께 시작`을 누릅니다. 4박자 카운트다운 후 3D 원근 레인을 따라 내려오는 노트가 판정선에 닿을 때 화면 좌석 버튼을 누릅니다. 연주 후 가족 공동 점수, 최고 콤보, 판정 분포를 확인합니다.
5. **판정 기준**:
   * 판정 오차가 ±100ms 이하이면 **PERFECT** (100점, 콤보 유지)
   * ±140ms 이하이면 **GREAT** (80점, 콤보 유지)
   * ±180ms 이하이면 **GOOD** (50점, 콤보 초기화)
   * ±220ms 이하이면 **BAD** (20점, 라이프 -50, 콤보 초기화)
   * 판정선을 벗어나면 **MISS** (0점, 라이프 -100, 콤보 초기화)
6. **점수 및 라이프**:
   * 만점은 $\text{총 노트 수} \times 100\text{점}$입니다.
   * 라이프는 1,000에서 시작하며, 0이 되면 게임이 종료되고 결과 화면이 표시됩니다.
   * **연습 모드**에서는 판정 폭이 ±350ms로 넓어지고 라이프가 0이 되어도 끝까지 연주할 수 있습니다.

| 곡명 | 쉬움 | 보통 | 어려움 | BPM 및 특징 |
|:---|---:|---:|---:|:---|
| **가족 비트** | 16노트 | 32노트 | 48노트 | 120 BPM · 19초 · 내장 비프음 합성 큐 |
| **반짝반짝 작은 별** | 80노트 | 140노트 | 176노트 | 100 BPM · 111초 · MP3 스트리밍 및 멜로디 차트 |

* **안전 주행 인터럽트**: 기어가 P에서 벗어나거나, 주행 제한이 걸리거나, 차량 상태 조회가 만료되면 게임이 즉시 멈춥니다. 백그라운드 전환 및 오디오 포커스 상실 시에도 일시정지되며, 주차 상태가 복구되어도 사용자가 직접 `이어가기`를 눌러야 재개됩니다. 이어가기 시 0.25초 동안 노트가 위로 올라간 뒤 3초 카운트다운 동안 원래 위치로 강하합니다.

---

## 타이밍 및 음원 싱크 보정

홈 또는 자리·게임 설정에서 **타이밍 보정**을 열어 조절할 수 있습니다.

* **음원 싱크(Audio Sync) 원리**:
  * 안드로이드 IVI/차량 오디오 출력 버퍼 지연(Audio Latency)을 상쇄하기 위해, 보정값만큼 **음원 재생 타임라인(`MediaPlayer`) 및 비트 큐(`ToneGenerator`)를 사전에 선반영**합니다.
  * 따라서 화면의 시각 노트가 판정선에 닿는 순간과 귀에 들리는 음악 비트가 1:1로 일치합니다.
  * 화면 터치는 터치-다운(Touch-down) 즉시 물리 판정선을 기준으로 직접 판정(0ms 오차 기준)되므로 판정선이 밀리거나 늦어지지 않습니다.
* **자동 보정**: 주차 상태에서 **자동 보정 시작**을 눌러 8개 비트에 맞춰 화면을 탭합니다. 유효 탭이 5개 이상이면 오차 중앙값(Median)을 ±500ms 범위에서 자동 산출합니다.
* **수동 보정**: ±1ms 또는 ±10ms 버튼으로 미세 조정할 수 있으며, 0ms 초기화가 가능합니다. 저장된 값은 앱을 다시 실행해도 유지됩니다.

---

## 터치 반응성 및 모션 디자인

* **터치-다운(Touch-down) 즉각 판정**: 버튼에서 손을 뗄 때(Touch-up) 판정하지 않고, 손가락이 닿는 순간의 하드웨어 입력 이벤트 타임스탬프(`awaitFirstDown`, `uptimeMillis` 역산)로 판정하여 릴리즈 지연을 원천 제거했습니다.
* **즉시 피드백 (Zero-latency UI)**:
  * 연타 시 판정 글자가 크로스페이드 지연 없이 즉시 교체(`uiInstant`)됩니다.
  * 플레이 중 콤보와 점수 게이지 숫자가 지연 없이 즉시 갱신됩니다.
  * 버튼을 누를 때 즉시 스냅(Press Snap)되고 손을 뗄 때만 부드럽게 복귀합니다.
* **3D 원근 퍼포먼스 스테이지**: 원근 투영 트랙, 부드러운 지평선 페이드, 레인별 판정 플래시 이펙트 및 플로팅 판정 텍스트를 제공합니다.

---

## 독서등 실험 모드

자리 설정에서는 읽기가 지원되는 좌석의 실제 독서등 점등 변화가 준비 상태에 반영됩니다. 처음 받은 상태와 반복된 동일값은 준비 상태를 바꾸지 않습니다. 연주 입력은 기본 화면 터치이며, 독서등으로 노트를 판정하려면 `독서등 입력 테스트`에서 좌석 대응을 확인하고 실험 모드를 켭니다.

* `checkReadingLightSwitchCapabilities`와 `checkReadingLightStateCapabilities`를 각각 조회합니다.
* `getReadingLightSwitch`는 스위치 설정 스냅샷이며, `registerReadingLightState` 콜백은 실제 점등 상태입니다.
* 한 자리씩 켜고 끄며 신호 수신을 확인하고, 지원 및 변화가 관찰된 좌석만 `자리 확인`을 켤 수 있습니다.
* **독서등 실험 모드**를 켜면 확인된 좌석의 `ON ↔ OFF` 상태 변화가 터치와 동일한 판정 경로로 들어갑니다. (초기값 및 동일값 재전송은 `SignalEdges`에서 필터링)
* **차량 입력 지연 보정**: 하드웨어/CAN 버스 통신 딜레이를 보정하기 위해 독서등 전용 독립 오프셋 슬라이더(-300~+300ms, 기본 0ms)를 제공합니다.
* 앱은 차량 상태를 강제 변경하는 setter를 호출하지 않으며, 원래 버튼 기능을 막거나 복원하지 않는 읽기 전용 구조입니다.

---

## 빌드와 실행

Android Studio에서 프로젝트를 열고 Android SDK 37, JDK 및 Pleos 에뮬레이터를 설정합니다.  
의존성은 프로젝트에 설정된 Pleos Maven 저장소(`nexus-playground.pleos.ai`)에서 다운로드됩니다.

반짝반짝 작은 별 음원을 포함하려면 [White_Records의 원본 음원](https://pixabay.com/music/happy-childrens-tunes-twinkle-twinkle-little-star-background-childrenx27s-music-for-video-178219/)을 내려받아 `app/src/main/assets/twinkle_twinkle.mp3`로 저장한 뒤 빌드합니다. (원본 MP3는 라이선스 정책에 따라 Git 저장소에서 제외됨)

```powershell
# 단위 테스트 및 디버그 APK 빌드
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug

# 에뮬레이터 설치 및 실행
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.example.hyundaiapp/.MainActivity

# Virtual Console 포트 포워딩 (웹 브라우저로 차량 상태 제어)
adb forward tcp:8088 tcp:8088
```

* **Virtual Console**: http://localhost:8088/
* 패키지명: `com.example.hyundaiapp` (앱 라벨: `HyundaiApp` / 게임 내 브랜드명: `FAMILY BEAT`)
* 차량 권한: 읽기용 `pleos.car.permission.CAR_LIGHTS`, `pleos.car.permission.CAR_DRIVE`

---

## 주요 코드 구성

* `MainActivity.kt`: 화면 상태 전환, 오디오 루프(`MediaPlayer` & `ToneGenerator`), 오디오 포커스 및 생명주기 관리
* `RhythmGame.kt`: 단조 시계 기반 게임 엔진 (노트 배치, 곡 진행, 터치/독서등 판정, 콤보/점수/라이프 상태 머신)
* `PerformanceStage.kt`: Canvas 기반 3D 원근 투영(Perspective) 레인, 노트 렌더링, 레인별 판정 플래시
* `VehicleInputs.kt`: Pleos Vehicle SDK 연동 (주차 상태 500ms 주기 폴링, UX 주행 제한 리스너, 독서등 4구역 진단 및 구독)
* `SignalEdges.kt`: 차량 독서등 하드웨어의 최초값/중복값 필터링 및 상태 반전 감지
* `TimingCalibration.kt`: 8비트 기반 중앙값 산출 자동 보정 및 수동 오프셋 조절 UI
* `SongPicker.kt`: 2열 앨범 아트 및 난이도별 차트 선택기
* `VehicleSeatMap.kt`: 2×2 벤토 카드 레이아웃의 캐빈 상면도 및 좌석 선택기
* `PlayHud.kt`: 실시간 점수 게이지, 라이프 바, 일시정지 버튼
* `UiMotion.kt`: 반응성 중심 모션 토큰, 통합 이징(`UiEase`), 다이얼로그 및 스낵바 컴포넌트
* `RhythmTheme.kt`: 터치-다운 즉각 이벤트 역산(`awaitFirstDown`)을 지원하는 공통 테마 및 컴포넌트
* `RhythmGameTest.kt`: 음원 싱크, 판정 윈도우, 차량 지연 보정, 신호 필터, 카운트다운 회귀 테스트 11개

---

[실제 검증 결과와 추가 확인 사항](docs/verification.md)
