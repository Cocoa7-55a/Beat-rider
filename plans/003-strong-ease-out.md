# 003 — 퇴장 ease-in 제거, 강한 ease-out 하나로 통일

- **Status**: DONE
- **Commit**: bcddfd2
- **Severity**: MEDIUM
- **Category**: Easing & duration
- **Estimated scope**: 2 files, ~6줄

## Problem

```kotlin
// UiMotion.kt:52-55 — current
private const val EnterMs = 280
private const val ExitMs = 220
private val EnterEase = LinearOutSlowInEasing   // (0, 0, 0.2, 1) — 약한 ease-out
private val ExitEase = FastOutLinearInEasing    // (0.4, 0, 1, 1) — ease-in: 천천히 시작해 굼뜸
```

```kotlin
// GameHome.kt:106-107 — current
enter = fadeIn(tween(250, easing = LinearOutSlowInEasing)),
exit = fadeOut(tween(220, easing = FastOutLinearInEasing)),
```

UI에 ease-in은 사용자가 보는 순간을 늦춘다. 화면 전환, 토스트, 스낵바, 다이얼로그 모두 해당.

## Target

```kotlin
// Strong ease-out for both directions: exits must start fast too, never ease-in.
internal val UiEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
```

`EnterEase`/`ExitEase`의 모든 사용처를 `UiEase`로, `GameHome.kt:106-107`도 `UiEase`로. 지속 시간(280/220/250ms)은 그대로.

## Steps

1. `UiMotion.kt`: 두 이징 선언을 `UiEase` 하나로 교체, 파일 내 `EnterEase`/`ExitEase` 전부 `UiEase`로.
2. `GameHome.kt:106-107`: 이징을 `UiEase`로, 안 쓰게 된 `FastOutLinearInEasing` import 삭제.

## Boundaries

- `GameHome.kt:105`의 boundsTransform(340ms, 화면 위 이동)은 범위 밖 — 감사 #6에서 별도 처리.
- `DrawnCheckbox`(`FastOutSlowInEasing`)는 건드리지 않는다.

## Verification

- **Mechanical**: `./gradlew :app:compileDebugKotlin` 성공, `grep -rn "FastOutLinearInEasing" app/src` 결과 없음.
- **Feel check**: 일시정지 다이얼로그 닫기, 진단 패널 내리기, 화면 전환을 개발자 옵션 “애니메이터 길이 배율 10x”로 보며 퇴장이 처음부터 빠르게 움직이는지 확인.
- **Done when**: 앱 코드에 ease-in 계열 이징이 없다.
