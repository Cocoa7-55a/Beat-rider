# 002 — 플레이 중 판정 글자는 크로스페이드 없이 즉시 교체

- **Status**: DONE
- **Commit**: bcddfd2
- **Severity**: HIGH
- **Category**: Purpose & frequency
- **Estimated scope**: 2 files, ~8줄

## Problem

```kotlin
// MainActivity.kt:577-582 — current
AnimatedContent(
    targetState = message,
    modifier = Modifier.weight(1f),
    transitionSpec = { uiFade() },   // 280ms 등장 + 220ms 퇴장
    label = "Judgment feedback fade"
) { … }
```

`message`는 판정마다 `PERFECT/GREAT/GOOD/BAD/MISS`로 바뀐다(`RhythmGame.kt:138`). 초당 여러 번 바뀌는 값에 0.5초짜리 크로스페이드가 걸려 두 글자가 겹쳐 보인다. 같은 판정은 레인 위(`PerformanceStage.kt:137`)에도 표시된다.

## Target

- 새 값이 판정 이름이면 즉시 교체(Enter/Exit None).
- 그 외 상태 문구(“곧 연주를 이어갑니다”, 일시정지 안내)로 바뀔 때는 기존 `uiFade()` 유지.

```kotlin
// UiMotion.kt — uiFade() 옆에 추가
internal fun uiInstant() = ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)

// MainActivity.kt
transitionSpec = {
    // Judgments change several times a second: swap instantly, fade only status messages.
    if (Judgment.entries.any { it.name == targetState }) uiInstant() else uiFade()
},
```

## Steps

1. `UiMotion.kt`: `uiFade()` 아래에 `uiInstant()` 추가.
2. `MainActivity.kt:580`: 위 `transitionSpec`으로 교체.

## Boundaries

- 판정 색상, 레인 판정 글자는 건드리지 않는다.

## Verification

- **Mechanical**: `./gradlew :app:compileDebugKotlin` 성공.
- **Feel check**: 연타 시 상단 판정 글자가 겹쳐 보이지 않고 바로 바뀐다. 일시정지/재개 시 안내 문구는 여전히 부드럽게 페이드된다.
- **Done when**: 판정 → 판정 전환에 애니메이션이 없다.
