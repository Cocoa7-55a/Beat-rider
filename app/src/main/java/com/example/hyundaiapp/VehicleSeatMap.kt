package com.example.hyundaiapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Bento seat picker: a gray tile holding a 2×2 grid of white seat cards laid out like the cabin. */
@Composable
internal fun VehicleSeatMap(
    readySeats: List<Boolean>,
    canReadyWithLight: (Int) -> Boolean,
    hasLightInput: (Int) -> Boolean,
    onSeatReady: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val readyCount = readySeats.count { it }
    Column(modifier.raisedSurface().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("함께 연주", color = RhythmColors.Muted, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                    RollingNumber("$readyCount", style = MaterialTheme.typography.headlineLarge)
                    Text("명 준비 완료", Modifier.padding(bottom = 6.dp), color = RhythmColors.Muted, fontSize = 17.sp)
                }
            }
            // Streak dots: one per seat, lit in that seat's lane colour.
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                readySeats.forEachIndexed { seat, on ->
                    val dot by animateColorAsState(if (on) seatColors[seat] else RhythmColors.Line, label = "Seat dot")
                    Spacer(Modifier.size(22.dp).background(dot, RoundedCornerShape(7.dp)))
                }
            }
        }
        Text(
            "▲ 차량 앞쪽", Modifier.fillMaxWidth(),
            color = RhythmColors.Muted, fontSize = 15.sp, textAlign = TextAlign.Center
        )
        for (row in 0..1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (seat in row * 2..row * 2 + 1) {
                    val ready = readySeats[seat]
                    SeatCard(
                        seat = seat,
                        ready = ready,
                        inputHint = when {
                            ready && hasLightInput(seat) -> "연주 · 독서등 + 터치"
                            ready -> "연주 · 터치"
                            canReadyWithLight(seat) -> "준비 · 독서등 또는 터치"
                            else -> "준비 · 카드 터치"
                        },
                        onToggle = { onSeatReady(seat, it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (readySeats.indices.any(canReadyWithLight)) {
            Text("이미 켜진 독서등은 껐다가 다시 켜 주세요.", color = RhythmColors.Muted, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SeatCard(
    seat: Int,
    ready: Boolean,
    inputHint: String,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val scale = selectionScale(ready)
    val fill by animateColorAsState(if (ready) RhythmColors.Selected else RhythmColors.Inset, label = "Seat fill")
    val badge by animateColorAsState(
        lerp(Color.White, seatColors[seat], if (ready) .9f else .3f), label = "Seat badge"
    )
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .insetSurface(shape, fill)
            .focusRing(shape)
            .clip(shape)
            .toggleable(ready, role = Role.Switch, onValueChange = onToggle)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).background(badge, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text(seatIcons[seat], color = RhythmColors.Ink, fontSize = 18.sp)
            }
            Switch(ready, onCheckedChange = null)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                seatNames[seat], color = if (ready) RhythmColors.Blue else RhythmColors.Ink,
                fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1
            )
            Text(
                if (ready) "준비 완료" else "준비 대기",
                color = if (ready) RhythmColors.Positive else RhythmColors.Muted,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(inputHint, color = RhythmColors.Muted, fontSize = 14.sp)
        }
    }
}
