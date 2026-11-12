# 001 — 플레이 중 콤보·게이지 숫자를 즉시 갱신하고 RollingNumber 지연 제거

- **Status**: DONE
- **Commit**: bcddfd2
- **Severity**: HIGH
- **Category**: Purpose & frequency / Performance
- **Estimated scope**: 3 files, ~10줄

## Problem

`RollingNumber`는 글자마다 `fromEnd * 35`ms 지연을 준다. 지연이 **바뀌지 않는 글자까지 포함한 위치**로 계산되므로, 숫자 뒤에 고정 텍스트가 붙으면 숫자가 그만큼 늦게 움직인다.

```kotlin
// app/src/main/java/com/example/hyundaiapp/UiMotion.kt:261-262 — current
val slide = tween<IntOffset>(260, fromEnd * 35, EnterEase)
val fade = tween<Float>(200, fromEnd * 35)
```

```kotlin
// MainActivity.kt:595 — current ("12 COMBO": 숫자는 fromEnd 6~7 → 210~245ms 지연 후 260ms 슬라이드)
RollingNumber("${game.combo} COMBO", style = MaterialTheme.typography.titleLarge, color = Color(0xFFFFC857))
```

```kotlin
// PlayHud.kt:82-85 — current ("$value / $maximum": value 숫자도 같은 문제)
RollingNumber(
    "$value / $maximum",
    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White
)
```

콤보·게이지는 노트를 칠 때마다(초당 여러 번) 바뀌는 고빈도 표시라 애니메이션 자체가 불필요하다. 약 0.5초 걸리는 롤링이 계속 끊기며 쌓인다.

## Target

- 플레이 HUD(콤보, 게이지 숫자): 애니메이션 없이 일반 `Text`, 폭 흔들림 방지를 위해 `FontFamily.Monospace`.
- `RollingNumber`: 글자별 지연 제거(근본 원인). 남는 사용처(`VehicleSeatMap.kt:39`, `TimingCalibration.kt:129`)는 저빈도라 롤링 유지.

```kotlin
val slide = tween<IntOffset>(260, easing = UiEase)
val fade = tween<Float>(200)
```

## Steps

1. `UiMotion.kt` `RollingNumber`: 위 Target대로 지연 제거, KDoc에서 "staggered from the right" 삭제.
2. `MainActivity.kt:595`: `Text("${game.combo} COMBO", style = …titleLarge, color = Color(0xFFFFC857), fontFamily = FontFamily.Monospace)`.
3. `PlayHud.kt:82`: `Text("$value / $maximum", style = …, color = Color.White, fontFamily = FontFamily.Monospace)`.

## Boundaries

- `VehicleSeatMap.kt`, `TimingCalibration.kt` 호출부는 건드리지 않는다.
- 게이지 바(`animateFloatAsState`, `PlayHud.kt:69`)는 범위 밖.

## Verification

- **Mechanical**: `./gradlew :app:compileDebugKotlin` 성공.
- **Feel check**: 빠른 곡에서 연타 → 콤보 숫자가 입력과 동시에 바뀌고 흔들리지 않는다. 좌석 선택 화면에서 인원 숫자는 여전히 지연 없이 굴러간다.
- **Done when**: 플레이 화면에 `RollingNumber` 호출이 없고, `RollingNumber`에 `fromEnd * 35`가 없다.
