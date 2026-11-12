# 004 — 버튼 누름은 즉시, 뗄 때만 이징 (비대칭 타이밍)

- **Status**: DONE
- **Commit**: bcddfd2
- **Severity**: MEDIUM
- **Category**: Interruptibility (asymmetric timing)
- **Estimated scope**: 1 file, ~4줄

## Problem

```kotlin
// RhythmTheme.kt:125-135 — current
val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(160), label = "Button press")
val fill by animateColorAsState(…, tween(180), label = "Button fill")
```

누름과 뗌이 같은 160~180ms. 레인 버튼(`MainActivity.kt:617`)은 리듬 입력이라 누르는 순간 시각 반응이 있어야 박자가 맞는 느낌이 난다.

## Target

```kotlin
// Press snaps instantly (rhythm input must feel immediate); release eases back.
val scale by animateFloatAsState(
    if (pressed) .97f else 1f, if (pressed) snap() else tween(160, easing = UiEase), label = "Button press"
)
val fill by animateColorAsState(…, if (pressed) snap() else tween(180, easing = UiEase), label = "Button fill")
```

`UiEase` = `CubicBezierEasing(0.23f, 1f, 0.32f, 1f)` (plan 003에서 `UiMotion.kt`에 추가됨). 의존: 003 먼저.

## Steps

1. `RhythmTheme.kt`: 위 Target대로 두 animationSpec 교체, `androidx.compose.animation.core.snap` import 추가.

## Boundaries

- 버튼 모양/색 로직은 건드리지 않는다. `RhythmButton` 외 컴포넌트는 범위 밖.

## Verification

- **Mechanical**: `./gradlew :app:compileDebugKotlin` 성공.
- **Feel check**: 실기기에서 레인 버튼 연타 → 누르는 즉시 작아지고 색이 바뀌며, 떼면 160ms에 걸쳐 돌아온다.
- **Done when**: press 방향 spec이 `snap()`이다.
