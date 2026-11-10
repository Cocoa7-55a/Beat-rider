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
private val vehicleWidth = 340f
private val vehicleLength = 670f

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
        BoxWithConstraints(Modifier.widthIn(max = 330.dp).fillMaxWidth().aspectRatio(vehicleWidth / vehicleLength)) {
            VehicleOutline(Modifier.matchParentSize())
            val buttonWidth = maxWidth * .235f
            val buttonHeight = maxHeight * .117f
            active.forEachIndexed { seat, selected ->
                val centerX = if (seat % 2 == 0) .37f else .63f
                val centerY = if (seat < 2) .49f else .674f
                val color = seatColors[seat]
                val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 9.dp, bottomEnd = 9.dp)
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
                        .padding(horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(seatIcons[seat], color = color, fontSize = 18.sp, lineHeight = 20.sp)
                    Text(seatNames[seat], fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                    val status = when {
                        !selected -> "참가 안 함"
                        hasLightInput(seat) -> "독서등 + 터치"
                        else -> "터치로 참여"
                    }
                    Text(status, color = if (selected) color else diagramLabel, fontSize = 11.sp, lineHeight = 14.sp)
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
            // Rotate the reference top view so its left-facing nose points up.
            translate(682f, -19f)
            rotate(90f, pivot = Offset.Zero)
        }) {
            val body = Path().apply {
                moveTo(43f, 512f)
                cubicTo(43f, 481f, 44f, 450f, 55f, 428f)
                cubicTo(63f, 408f, 77f, 393f, 100f, 389f)
                cubicTo(125f, 386f, 154f, 386f, 188f, 386f)
                lineTo(580f, 386f)
                cubicTo(606f, 386f, 627f, 387f, 640f, 402f)
                cubicTo(655f, 418f, 660f, 455f, 662f, 491f)
                quadraticTo(663f, 512f, 662f, 533f)
                cubicTo(660f, 569f, 655f, 606f, 640f, 622f)
                cubicTo(627f, 637f, 606f, 638f, 580f, 638f)
                lineTo(188f, 638f)
                cubicTo(154f, 638f, 125f, 638f, 100f, 635f)
                cubicTo(77f, 631f, 63f, 616f, 55f, 596f)
                cubicTo(44f, 574f, 43f, 543f, 43f, 512f)
                close()
            }
            drawPath(body, Color(0xFF243650))
            drawPath(body, blueprintInk, style = Stroke(2f))

            for (side in listOf(-1f, 1f)) {
                val shoulder = Path().apply {
                    moveTo(58f, 512f + 82f * side)
                    cubicTo(97f, 512f + 104f * side, 149f, 512f + 109f * side, 208f, 512f + 108f * side)
                    cubicTo(273f, 512f + 106f * side, 320f, 512f + 92f * side, 408f, 512f + 91f * side)
                    cubicTo(491f, 512f + 91f * side, 564f, 512f + 101f * side, 620f, 512f + 111f * side)
                }
                drawPath(shoulder, blueprintInk.copy(alpha = .48f), style = Stroke(1.5f))

                val mirror = Path().apply {
                    moveTo(245f, 512f + 105f * side)
                    lineTo(253f, 512f + 126f * side)
                    quadraticTo(267f, 512f + 145f * side, 280f, 512f + 145f * side)
                    lineTo(267f, 512f + 104f * side)
                    close()
                }
                drawPath(mirror, Color(0xFF344D6A))
                drawPath(mirror, blueprintInk, style = Stroke(1.5f))

                val headlight = Path().apply {
                    moveTo(55f, 512f + 57f * side)
                    quadraticTo(56f, 512f + 83f * side, 72f, 512f + 105f * side)
                    lineTo(89f, 512f + 105f * side)
                    quadraticTo(77f, 512f + 82f * side, 75f, 512f + 57f * side)
                    close()
                }
                drawPath(headlight, Color(0xFF111B2A))
                drawPath(headlight, Color(0xFF96B6D3), style = Stroke(1.4f))
                for (pixel in 0..3) {
                    val center = 512f + (63f + pixel * 10f) * side
                    val x = 60f + pixel * pixel * 1.35f
                    drawRoundRect(Color(0xFFE3F4FF), Offset(x, center - 3.5f), Size(6.5f, 7f), CornerRadius(1f))
                    drawRoundRect(Color(0xFF84C1F2), Offset(x + 9f, center - 3.5f), Size(3f, 7f), CornerRadius(1f))
                }
            }

            val tailPanel = Path().apply {
                moveTo(619f, 403f)
                quadraticTo(632f, 422f, 634f, 512f)
                quadraticTo(632f, 602f, 619f, 621f)
                lineTo(633f, 621f)
                quadraticTo(648f, 591f, 650f, 512f)
                quadraticTo(648f, 433f, 633f, 403f)
                close()
            }
            drawPath(tailPanel, Color(0xFF301E35))
            drawPath(tailPanel, Color(0xFFAC6C88), style = Stroke(1.4f))
            val tailStrip = Path().apply {
                moveTo(627f, 410f)
                quadraticTo(641f, 439f, 641f, 512f)
                quadraticTo(641f, 585f, 627f, 614f)
            }
            drawPath(tailStrip, Color(0xFFCB4B6E), style = Stroke(3f))
            for (side in listOf(-1f, 1f)) {
                for (pixel in 0..5) {
                    val center = 512f + (45f + pixel * 10f) * side
                    val x = 638f - pixel * pixel * .42f
                    drawRoundRect(Color(0xFFFF95A8), Offset(x, center - 3.5f), Size(6f, 7f), CornerRadius(1f))
                }
            }

            val cabin = Path().apply {
                moveTo(225f, 414f)
                cubicTo(268f, 415f, 313f, 428f, 367f, 428f)
                lineTo(529f, 428f)
                quadraticTo(562f, 428f, 572f, 461f)
                quadraticTo(583f, 512f, 572f, 563f)
                quadraticTo(562f, 596f, 529f, 596f)
                lineTo(367f, 596f)
                cubicTo(313f, 596f, 268f, 609f, 225f, 610f)
                cubicTo(196f, 601f, 181f, 562f, 181f, 512f)
                cubicTo(181f, 462f, 196f, 423f, 225f, 414f)
                close()
            }
            drawPath(cabin, Color(0xFF131F34))
            drawPath(cabin, blueprintInk.copy(alpha = .7f), style = Stroke(1.6f))

            val windshield = Path().apply {
                moveTo(225f, 416f)
                quadraticTo(266f, 418f, 306f, 432f)
                quadraticTo(295f, 512f, 306f, 592f)
                quadraticTo(266f, 606f, 225f, 608f)
                cubicTo(200f, 594f, 185f, 557f, 185f, 512f)
                cubicTo(185f, 467f, 200f, 430f, 225f, 416f)
                close()
            }
            val hatch = Path().apply {
                moveTo(531f, 432f)
                cubicTo(578f, 419f, 597f, 430f, 611f, 453f)
                quadraticTo(631f, 512f, 611f, 571f)
                cubicTo(597f, 594f, 578f, 605f, 531f, 592f)
                quadraticTo(574f, 512f, 531f, 432f)
                close()
            }
            for (glass in listOf(windshield, hatch)) {
                drawPath(glass, Color(0xFF344D6A))
                drawPath(glass, blueprintInk.copy(alpha = .55f), style = Stroke(1.5f))
            }

            val cabinLine = blueprintInk.copy(alpha = .38f)
            drawLine(cabinLine, Offset(313f, 512f), Offset(551f, 512f), 1.5f)
            drawLine(cabinLine, Offset(407f, 430f), Offset(407f, 594f), 1.5f)
        }
    }
}
