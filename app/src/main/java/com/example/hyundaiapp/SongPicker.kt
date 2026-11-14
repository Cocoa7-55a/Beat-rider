package com.example.hyundaiapp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    val night = Color(0xFFD6E4FC)
    val star = Color(0xFF2C64C4)
    val starlight = Color(0xFF528BF2)
    val sea = Color(0xFFDCE9FF)
    val disc = Color(0xFF2758A8)
    val waveform = Color(0xFFC7DEFF)
    val evergreen = Color(0xFF12483C)
    val bell = Color(0xFFFFD685)
}

@Composable
internal fun SongPicker(
    selectedSong: RhythmSong,
    songs: List<RhythmSong>,
    difficulty: ChartDifficulty,
    onSongSelected: (RhythmSong) -> Unit,
    onDifficultySelected: (ChartDifficulty) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= 620.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                SongList(selectedSong, songs, onSongSelected, Modifier.weight(1f).fillMaxHeight())
                SongDetails(
                    selectedSong, difficulty, onDifficultySelected, onContinue,
                    Modifier.weight(1.18f).verticalScroll(rememberScrollState())
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SongList(selectedSong, songs, onSongSelected, Modifier.fillMaxWidth().height(290.dp))
                SongDetails(selectedSong, difficulty, onDifficultySelected, onContinue, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SongList(
    selected: RhythmSong,
    songs: List<RhythmSong>,
    onSelect: (RhythmSong) -> Unit,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StaggeredHeading(
            "곡 목록", "내장곡 ${songs.size}곡",
            MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().selectableGroup(),
            contentPadding = PaddingValues(9.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(songs, key = { it.name }) { song ->
                val isSelected = song == selected
                val scale = selectionScale(isSelected)
                val shape = RoundedCornerShape(24.dp)
                val ink = if (isSelected) RhythmColors.Blue else RhythmColors.Ink
                Box(
                    Modifier.fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .then(if (isSelected) Modifier.insetSurface(shape, RhythmColors.Selected) else Modifier.raisedSurface(shape))
                        .focusRing(shape)
                        .clip(shape)
                        .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(song) })
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArt(song, Modifier.size(68.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(song.title, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${song.bpm} BPM   ${song.durationMs / 1000}초",
                                color = RhythmColors.Muted, fontSize = 17.sp
                            )
                        }
                        if (isSelected) {
                            Spacer(Modifier.size(18.dp).raisedSurface(CircleShape, RhythmColors.Blue))
                        }
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
        modifier.padding(10.dp).raisedSurface().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val detailWidth = Modifier.widthIn(max = 540.dp).fillMaxWidth()
        AnimatedContent(
            targetState = song,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().insetSurface(PanelShape).padding(12.dp),
            transitionSpec = { uiFade() },
            label = "Album art"
        ) { currentSong ->
            AlbumArt(currentSong, Modifier.fillMaxWidth().aspectRatio(1f))
        }
        Column(detailWidth, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StaggeredHeading(
                song.title, "${song.bpm} BPM   ${song.durationMs / 1000}초",
                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text("난이도", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChartDifficulty.entries.forEach { level ->
                    val selected = difficulty == level
                    val shape = RoundedCornerShape(22.dp)
                    val ink by animateColorAsState(
                        if (selected) androidx.compose.ui.graphics.Color.White else RhythmColors.Ink, label = "Difficulty ink"
                    )
                    val fill by animateColorAsState(
                        if (selected) RhythmColors.Blue else RhythmColors.Inset, label = "Difficulty fill"
                    )
                    Column(
                        Modifier.weight(1f).heightIn(min = 82.dp)
                            .insetSurface(shape, fill)
                            .focusRing(shape)
                            .clip(shape)
                            .selectable(selected, role = Role.RadioButton, onClick = { onDifficultySelected(level) })
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(level.label, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${song.chart(level).size}노트", color = ink, fontSize = 16.sp)
                    }
                }
            }
            Text(difficulty.description, color = RhythmColors.Muted, fontSize = 17.sp)
            RhythmButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
            ) {
                Text("자리·게임 설정으로", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AlbumArt(song: RhythmSong, modifier: Modifier) {
    val twinkle = song == RhythmSong.TWINKLE
    Canvas(modifier.clip(RoundedCornerShape(20.dp))) {
        val unit = size.minDimension
        if (song == RhythmSong.JINGLE_BELLS) {
            drawJingleBellsCover(unit)
            return@Canvas
        }
        drawRect(if (twinkle) SongPalette.night else SongPalette.sea)
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

private fun DrawScope.drawJingleBellsCover(unit: Float) {
    drawRect(SongPalette.evergreen)
    val x = center.x
    val y = center.y
    val bell = Path().apply {
        moveTo(x - unit * .23f, y + unit * .16f)
        cubicTo(x - unit * .09f, y + unit * .07f, x - unit * .22f, y - unit * .22f, x, y - unit * .22f)
        cubicTo(x + unit * .22f, y - unit * .22f, x + unit * .09f, y + unit * .07f, x + unit * .23f, y + unit * .16f)
        close()
    }
    drawRoundRect(
        SongPalette.bell,
        topLeft = Offset(x - unit * .045f, y - unit * .28f),
        size = Size(unit * .09f, unit * .13f),
        cornerRadius = CornerRadius(unit * .045f)
    )
    drawPath(bell, SongPalette.bell)
    drawCircle(SongPalette.bell, unit * .065f, Offset(x, y + unit * .205f))
    drawCircle(SongPalette.bell.copy(alpha = .3f), unit * .35f, center, style = Stroke(unit * .008f))
}
