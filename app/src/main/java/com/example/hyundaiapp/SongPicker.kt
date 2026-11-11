package com.example.hyundaiapp

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private object SongPalette {
    val panel = Color(0xFF1D2034)
    val row = Color(0xFF282B43)
    val line = Color(0xFF484D67)
    val accent = Color(0xFFFF82AA)
    val onAccent = Color(0xFF301125)
    val accentStripe = Color(0xFFE35C8E)
    val label = Color(0xFFC5CCE4)
    val white = Color(0xFFFFF9FF)
    val night = Color(0xFF1C2249)
    val star = Color(0xFFFFDB78)
    val starlight = Color(0xFFC6DAFF)
    val sea = Color(0xFF183C48)
    val disc = Color(0xFF122133)
    val waveform = Color(0xFF86F2D6)
}

@Composable
internal fun SongPicker(
    selectedSong: RhythmSong,
    difficulty: ChartDifficulty,
    onSongSelected: (RhythmSong) -> Unit,
    onDifficultySelected: (ChartDifficulty) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= 620.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SongList(selectedSong, onSongSelected, Modifier.weight(1f).fillMaxHeight())
                SongDetails(
                    selectedSong, difficulty, onDifficultySelected, onContinue,
                    Modifier.weight(1.18f).fillMaxHeight().verticalScroll(rememberScrollState())
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SongList(selectedSong, onSongSelected, Modifier.fillMaxWidth().height(230.dp))
                SongDetails(selectedSong, difficulty, onDifficultySelected, onContinue, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SongList(selected: RhythmSong, onSelect: (RhythmSong) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StaggeredHeading(
            "곡 목록", "내장곡 ${RhythmSong.entries.size}곡",
            MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(RhythmSong.entries, key = { it.name }) { song ->
                val isSelected = song == selected
                val scale = selectionScale(isSelected)
                val shape = RoundedCornerShape(10.dp)
                val ink = if (isSelected) SongPalette.onAccent else SongPalette.white
                Box(
                    Modifier.fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .clip(shape)
                        .background(if (isSelected) SongPalette.accent else SongPalette.row)
                        .focusBorder(shape)
                        .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(song) })
                ) {
                    if (isSelected) {
                        Canvas(Modifier.matchParentSize()) {
                            repeat(4) { index ->
                                val x = size.width - index * 20.dp.toPx()
                                drawLine(
                                    SongPalette.accentStripe.copy(alpha = .28f),
                                    Offset(x + 10.dp.toPx(), 0f),
                                    Offset(x - 42.dp.toPx(), size.height),
                                    6.dp.toPx()
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArt(song, Modifier.size(54.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(song.title, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${song.bpm} BPM   ${song.durationMs / 1000}초",
                                color = if (isSelected) ink else SongPalette.label, fontSize = 14.sp
                            )
                        }
                        if (isSelected) Text("선택됨", color = ink, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SongDetails(
    song: RhythmSong,
    difficulty: ChartDifficulty,
    onDifficultySelected: (ChartDifficulty) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val detailWidth = Modifier.widthIn(max = 420.dp).fillMaxWidth()
        AnimatedContent(
            targetState = song,
            modifier = Modifier.widthIn(max = 385.dp).fillMaxWidth(),
            transitionSpec = { uiFade() },
            label = "Album art"
        ) { currentSong ->
            AlbumArt(currentSong, Modifier.fillMaxWidth().aspectRatio(1f))
        }
        Column(detailWidth, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StaggeredHeading(
                song.title, "${song.bpm} BPM   ${song.durationMs / 1000}초",
                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text("난이도", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChartDifficulty.entries.forEach { level ->
                    val selected = difficulty == level
                    val shape = RoundedCornerShape(8.dp)
                    val ink = if (selected) SongPalette.onAccent else SongPalette.white
                    Column(
                        Modifier.weight(1f).heightIn(min = 66.dp).clip(shape)
                            .background(if (selected) SongPalette.accent else SongPalette.panel)
                            .border(1.dp, if (selected) SongPalette.accent else SongPalette.line, shape)
                            .focusBorder(shape)
                            .selectable(selected, role = Role.RadioButton, onClick = { onDifficultySelected(level) })
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(level.label, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${song.chart(level).size}노트", color = ink, fontSize = 13.sp)
                    }
                }
            }
            Text(difficulty.description, color = SongPalette.label, fontSize = 14.sp)
            val buttonShape = RoundedCornerShape(8.dp)
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).focusBorder(buttonShape),
                shape = buttonShape,
                colors = ButtonDefaults.buttonColors(containerColor = SongPalette.accent, contentColor = SongPalette.onAccent)
            ) {
                Text("자리·게임 설정으로", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Modifier.focusBorder(shape: RoundedCornerShape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }
        .border(2.dp, if (focused) SongPalette.white else Color.Transparent, shape)
}

@Composable
private fun AlbumArt(song: RhythmSong, modifier: Modifier) {
    val twinkle = song == RhythmSong.TWINKLE
    Canvas(modifier.clip(RoundedCornerShape(10.dp))) {
        drawRect(if (twinkle) SongPalette.night else SongPalette.sea)
        val unit = size.minDimension
        if (twinkle) {
            val sky = listOf(.14f to .23f, .26f to .69f, .43f to .18f, .74f to .28f, .87f to .58f, .66f to .77f)
            sky.forEachIndexed { index, (x, y) ->
                val p = Offset(size.width * x, size.height * y)
                val radius = unit * if (index % 2 == 0) .022f else .012f
                drawCircle(SongPalette.starlight, radius, p)
            }
            drawCircle(SongPalette.star.copy(alpha = .14f), unit * .35f, center)
            drawCircle(SongPalette.star.copy(alpha = .26f), unit * .39f, center, style = Stroke(unit * .005f))
            val star = Path().apply {
                repeat(10) { point ->
                    val angle = -PI / 2 + point * PI / 5
                    val radius = unit * if (point % 2 == 0) .28f else .125f
                    val x = center.x + cos(angle).toFloat() * radius
                    val y = center.y + sin(angle).toFloat() * radius
                    if (point == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(star, SongPalette.star)
        } else {
            drawCircle(SongPalette.disc, unit * .34f, center)
            for (ratio in listOf(.30f, .26f, .22f)) {
                drawCircle(SongPalette.waveform.copy(alpha = .42f), unit * ratio, center, style = Stroke(unit * .008f))
            }
            val bars = listOf(.08f, .19f, .31f, .23f, .12f)
            bars.forEachIndexed { index, height ->
                val x = center.x + (index - 2) * unit * .083f
                drawRoundRect(
                    SongPalette.waveform, Offset(x - unit * .021f, center.y - unit * height / 2),
                    Size(unit * .042f, unit * height), CornerRadius(unit * .02f)
                )
            }
        }
    }
}
