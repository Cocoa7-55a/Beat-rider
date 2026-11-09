package com.example.hyundaiapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val blueprintInk = Color(0xFF8AA8CA)
private val diagramLabel = Color(0xFFB8CAE0)
private val vehicleWidth = 360f
private val vehicleLength = 520f

@Composable
internal fun VehicleSeatMap(
    active: List<Boolean>,
    hasLightInput: (Int) -> Boolean,
    onSeatSelected: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCount = active.count { it }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("▲  차량 앞쪽", color = diagramLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        BoxWithConstraints(Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(vehicleWidth / vehicleLength)) {
            VehicleOutline(Modifier.matchParentSize())
            val buttonWidth = maxWidth * .33f
            val buttonHeight = maxHeight * .155f
            active.forEachIndexed { seat, selected ->
                val centerX = if (seat % 2 == 0) .32f else .68f
                val centerY = if (seat < 2) .39f else .63f
                val color = seatColors[seat]
                val shape = RoundedCornerShape(12.dp)
                Column(
                    Modifier.offset(
                        x = maxWidth * centerX - buttonWidth / 2,
                        y = maxHeight * centerY - buttonHeight / 2
                    )
                        .size(buttonWidth, buttonHeight)
                        .clip(shape)
                        .background(if (selected) color.copy(alpha = .20f) else Color(0xFF18243A))
                        .border(if (selected) 2.dp else 1.dp, if (selected) color else blueprintInk.copy(alpha = .55f), shape)
                        .toggleable(
                            value = selected,
                            enabled = !selected || selectedCount > 1,
                            role = Role.Checkbox,
                            onValueChange = { onSeatSelected(seat, it) }
                        )
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(seatIcons[seat], color = color, fontSize = 18.sp)
                    Text(seatNames[seat], fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    val status = when {
                        !selected -> "참가 안 함"
                        hasLightInput(seat) -> "독서등 + 터치"
                        else -> "터치로 참여"
                    }
                    Text(status, color = if (selected) color else diagramLabel, fontSize = 11.sp)
                }
            }
        }
        Text("${selectedCount}명 선택 · 좌핸들 차량 기준", color = diagramLabel, fontSize = 13.sp)
    }
}

@Composable
private fun VehicleOutline(modifier: Modifier) {
    Canvas(modifier) {
        withTransform({
            scale(size.width / vehicleWidth, size.height / vehicleLength, pivot = Offset.Zero)
        }) {
            // Top view: wheel and mirror placement separates the front and rear of the car.
            for (x in listOf(29f, 305f)) {
                for (y in listOf(75f, 378f)) {
                    drawRoundRect(
                        Color(0xFF0C1221), Offset(x, y), Size(26f, 65f), CornerRadius(8f)
                    )
                    drawRoundRect(
                        blueprintInk.copy(alpha = .36f),
                        Offset(x, y), Size(26f, 65f), CornerRadius(8f), style = Stroke(1.5f)
                    )
                }
            }

            val body = Path().apply {
                moveTo(89f, 19f)
                quadraticTo(180f, 4f, 271f, 19f)
                quadraticTo(310f, 25f, 315f, 70f)
                lineTo(323f, 144f)
                lineTo(318f, 417f)
                quadraticTo(315f, 481f, 283f, 497f)
                quadraticTo(180f, 511f, 77f, 497f)
                quadraticTo(45f, 481f, 42f, 417f)
                lineTo(37f, 144f)
                lineTo(45f, 70f)
                quadraticTo(50f, 25f, 89f, 19f)
                close()
            }
            drawPath(body, Color(0xFF243650))
            drawPath(body, blueprintInk, style = Stroke(2f))

            val cabin = Path().apply {
                moveTo(84f, 106f)
                quadraticTo(180f, 83f, 276f, 106f)
                lineTo(291f, 163f)
                lineTo(289f, 387f)
                quadraticTo(180f, 422f, 71f, 387f)
                lineTo(69f, 163f)
                close()
            }
            drawPath(cabin, Color(0xFF131F34))
            drawPath(cabin, blueprintInk.copy(alpha = .7f), style = Stroke(1.6f))
            val windshield = Path().apply {
                moveTo(84f, 106f)
                quadraticTo(180f, 83f, 276f, 106f)
                lineTo(287f, 146f)
                quadraticTo(180f, 123f, 73f, 146f)
                close()
            }
            drawPath(windshield, Color(0xFF344D6A))
            drawPath(windshield, blueprintInk.copy(alpha = .6f), style = Stroke(1.5f))

            for (x in listOf(21f, 316f)) {
                drawRoundRect(Color(0xFF344D6A), Offset(x, 133f), Size(23f, 16f), CornerRadius(5f))
                drawRoundRect(blueprintInk, Offset(x, 133f), Size(23f, 16f), CornerRadius(5f), style = Stroke(1.5f))
            }

            val cabinLine = blueprintInk.copy(alpha = .38f)
            drawLine(cabinLine, Offset(180f, 148f), Offset(180f, 395f), 1.5f)
            drawLine(cabinLine, Offset(73f, 263f), Offset(287f, 263f), 1.5f)
            drawCircle(blueprintInk, radius = 11f, center = Offset(115f, 149f), style = Stroke(2f))
            drawLine(blueprintInk, Offset(106f, 149f), Offset(124f, 149f), 1.5f)
            drawLine(blueprintInk.copy(alpha = .6f), Offset(74f, 451f), Offset(286f, 451f), 1.5f)
            for (x in listOf(65f, 249f)) {
                drawRoundRect(Color(0xFFC8E7FF), Offset(x, 47f), Size(46f, 8f), CornerRadius(2f))
                drawRoundRect(Color(0xFFEB849E), Offset(x, 466f), Size(46f, 8f), CornerRadius(2f))
            }
        }
    }
}
