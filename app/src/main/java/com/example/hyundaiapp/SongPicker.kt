package com.example.hyundaiapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun SongPicker(
    selectedSong: RhythmSong,
    difficulty: ChartDifficulty,
    onSongSelected: (RhythmSong) -> Unit,
    onDifficultySelected: (ChartDifficulty) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        if (maxWidth >= 620.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SongList(selectedSong, onSongSelected, Modifier.weight(1f).fillMaxHeight())
                SongDetails(
                    selectedSong, difficulty, onDifficultySelected,
                    Modifier.weight(1.25f).fillMaxHeight().verticalScroll(rememberScrollState())
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SongList(selectedSong, onSongSelected, Modifier.fillMaxWidth().height(200.dp))
                SongDetails(selectedSong, difficulty, onDifficultySelected, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SongList(selected: RhythmSong, onSelect: (RhythmSong) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("곡 목록", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("내장곡 ${RhythmSong.entries.size}곡", color = Color(0xFFACB8D4))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(RhythmSong.entries, key = { it.name }) { song ->
                val isSelected = song == selected
                val shape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(song) })
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArt(song, Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("${song.bpm} BPM · ${song.durationMs / 1000}초", color = Color(0xFFBFCDE5), fontSize = 13.sp)
                    }
                    if (isSelected) Text("선택됨", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongDetails(
    song: RhythmSong,
    difficulty: ChartDifficulty,
    onDifficultySelected: (ChartDifficulty) -> Unit,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AlbumArt(song, Modifier.fillMaxWidth().aspectRatio(1.65f))
        Text(song.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("${song.bpm} BPM · ${song.durationMs / 1000}초", color = Color(0xFFACB8D4))
        HorizontalDivider()
        Text("난이도", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.selectableGroup()) {
            ChartDifficulty.entries.forEach { level ->
                FilterChip(
                    selected = difficulty == level,
                    onClick = { onDifficultySelected(level) },
                    label = { Text("${level.label} · ${song.chart(level).size}노트") }
                )
            }
        }
        Text(difficulty.description, color = Color(0xFFACB8D4))
    }
}

@Composable
private fun AlbumArt(song: RhythmSong, modifier: Modifier) {
    val twinkle = song == RhythmSong.TWINKLE
    Canvas(modifier.clip(RoundedCornerShape(14.dp))) {
        val gradient = if (twinkle) listOf(Color(0xFF11183C), Color(0xFF483874))
            else listOf(Color(0xFF162C43), Color(0xFF275C69))
        drawRect(Brush.linearGradient(gradient))
        val unit = size.minDimension
        val center = this.center
        if (twinkle) {
            val sky = listOf(.14f to .23f, .26f to .69f, .43f to .18f, .74f to .28f, .87f to .58f, .66f to .77f)
            sky.forEachIndexed { index, (x, y) ->
                val p = Offset(size.width * x, size.height * y)
                val radius = unit * if (index % 2 == 0) .026f else .014f
                drawCircle(Color(0xFFC5D9FF), radius, p)
            }
            drawCircle(Color(0xFFFFCF67).copy(alpha = .16f), unit * .34f, center)
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
            drawPath(star, Brush.verticalGradient(listOf(Color(0xFFFFF0AD), Color(0xFFFFB64B))))
        } else {
            drawCircle(Color(0xFF122133), unit * .33f, center)
            for (ratio in listOf(.30f, .26f, .22f)) {
                drawCircle(Color(0xFF69CDCA).copy(alpha = .42f), unit * ratio, center, style = Stroke(unit * .008f))
            }
            val bars = listOf(.08f, .19f, .31f, .23f, .12f)
            bars.forEachIndexed { index, height ->
                val x = center.x + (index - 2) * unit * .083f
                drawRoundRect(
                    Color(0xFF86F2D6), Offset(x - unit * .021f, center.y - unit * height / 2),
                    Size(unit * .042f, unit * height), CornerRadius(unit * .02f)
                )
            }
        }
    }
}
