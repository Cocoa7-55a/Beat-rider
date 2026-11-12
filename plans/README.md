# Animation plans

`improve-animations` 감사(커밋 bcddfd2) 결과에서 선택된 계획.

| # | 제목 | 심각도 | 상태 |
|---|---|---|---|
| [001](001-combo-gauge-instant.md) | 플레이 중 콤보·게이지 숫자 즉시 갱신, RollingNumber 지연 제거 | HIGH | DONE |
| [002](002-judgment-instant-swap.md) | 판정 글자 즉시 교체 | HIGH | DONE |
| [003](003-strong-ease-out.md) | 퇴장 ease-in 제거, 강한 ease-out 통일 | MEDIUM | DONE |
| [004](004-button-press-snap.md) | 버튼 누름 즉시, 뗄 때만 이징 | MEDIUM | DONE |
| 005 | 레인 판정 텍스트의 이동과 페이드를 `graphicsLayer`에서 처리 | MEDIUM | DONE |
| 006 | `GameHome`의 크기와 간격을 용도별 토큰으로 정리 | MEDIUM | DONE |

**실행 순서**: 003 → 001 → 002 → 004. 001·004는 003의 `UiEase`를 사용한다.

## 실차 검증

- 주차 상태에서 좌석별 독서등 버튼을 켜고 끄며 물리 조작 시각과 앱의 `FamilyBeat` 수신 로그를 같은 시간 기준으로 기록합니다. 좌석별로 여러 번 반복해 지연 분포와 누락 여부를 확인합니다. 앱 진단 화면의 이벤트 간격은 콜백 간격입니다.
- 독서등 버튼을 건드리지 않고 문을 열고 닫거나 실내등 자동 점등을 유발합니다. 독서등 영역에 같은 ON/OFF 이벤트가 오면 좌석 준비와 협동 연주 판정에 들어가는지 확인합니다. `registerReadingLightState`는 점등 상태만 제공하므로, 필터를 적용하기 전에 원인을 구분할 수 있는 별도 신호와 수동 조작 누락 여부를 확인합니다.
- Vehicle SDK 2.3.2는 `getCurrentGearSelectionState`의 대체 API로 AAOS [`VehiclePropertyIds.GEAR_SELECTION`](https://developer.android.com/reference/android/car/VehiclePropertyIds#GEAR_SELECTION)을 안내합니다. 현재 Pleos Virtual Console의 기어 조작은 별도 벤더 속성 `VENDOR_GEAR_SELECTION`을 대상으로 하므로, 표준 속성이 같은 값으로 갱신되는지 검증합니다. 교체 전에 대상 실차에서 `android.car.permission.CAR_POWERTRAIN`으로 속성을 읽을 수 있는지 확인하고, P 진입·이탈, 조회 불가, 연결 끊김, 주행 제한 각각에서 시작과 일시정지 동작을 검증합니다.
