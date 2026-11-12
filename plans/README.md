# Animation plans

`improve-animations` 감사(커밋 bcddfd2) 결과에서 선택된 계획.

| # | 제목 | 심각도 | 상태 |
|---|---|---|---|
| [001](001-combo-gauge-instant.md) | 플레이 중 콤보·게이지 숫자 즉시 갱신, RollingNumber 지연 제거 | HIGH | DONE |
| [002](002-judgment-instant-swap.md) | 판정 글자 즉시 교체 | HIGH | DONE |
| [003](003-strong-ease-out.md) | 퇴장 ease-in 제거, 강한 ease-out 통일 | MEDIUM | DONE |
| [004](004-button-press-snap.md) | 버튼 누름 즉시, 뗄 때만 이징 | MEDIUM | DONE |

**실행 순서**: 003 → 001 → 002 → 004. 001·004는 003의 `UiEase`를 사용한다.

**보류 (감사 #5, #6)**: 레인 판정 글자 `offset` → `graphicsLayer`, `GameHome` 하드코딩 값 토큰화.
