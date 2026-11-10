package com.example.hyundaiapp

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.pow
import kotlinx.coroutines.delay

internal val seatNames = listOf("운전석", "조수석", "뒷좌석 왼쪽", "뒷좌석 오른쪽")
internal val seatIcons = listOf("★", "◆", "●", "▲")
internal val seatColors = listOf(Color(0xFFFFC857), Color(0xFF72DDF7), Color(0xFFB79CFF), Color(0xFFFF8DA1))
private val navy = Color(0xFF101629)

class MainActivity : ComponentActivity() {
    private val game = RhythmGame { android.util.Log.i("FamilyBeat", it) }
    private lateinit var inputs: VehicleInputs
    private var tone: ToneGenerator? = null
    private lateinit var melody: SoundPool
    private var chimeSample = 0
    private var melodyStream = 0

    private fun stopGameAudio() {
        tone?.stopTone()
        if (melodyStream != 0) {
            melody.stop(melodyStream)
            melodyStream = 0
        }
    }

    private fun playSongBeat() {
        if (!audioFocused) return
        val song = game.song
        val beat = (game.elapsed / song.beatMs).toInt()
        if (beat < COUNT_IN_BEATS || song == RhythmSong.FAMILY_BEAT) {
            val sound = if (beat % 4 == 0) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP
            tone?.startTone(sound, 65)
            return
        }
        val pitch = song.cueAt(beat - COUNT_IN_BEATS)?.semitones ?: return
        if (melodyStream != 0) melody.stop(melodyStream)
        melodyStream = melody.play(chimeSample, .75f, .75f, 1, 0, 2.0.pow(pitch / 12.0).toFloat())
    }

    private var audioFocused = false
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setOnAudioFocusChangeListener { change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN) {
                    audioFocused = false; game.pause(); stopGameAudio()
                }
            }.build()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 45) }.getOrNull()
        melody = SoundPool.Builder().setMaxStreams(1)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .build()
        chimeSample = melody.load(this, R.raw.chime_c4, 1)
        inputs = VehicleInputs(this, { seat, time -> game.hit(seat, time, true) }, { game.pause() })
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = navy, surface = Color(0xFF1D2640), primary = Color(0xFF72DDF7))) {
                LaunchedEffect(game.running) {
                    if (game.running) {
                        audioFocused = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                        if (!audioFocused) game.pause()
                    } else {
                        stopGameAudio(); audioFocused = false
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }
                }
                LaunchedEffect(Unit) {
                    while (true) {
                        game.tick(SystemClock.elapsedRealtime(), ::playSongBeat)
                        delay(16)
                    }
                }
                Surface(Modifier.fillMaxSize(), color = navy) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("FAMILY BEAT", fontSize = 28.sp, fontWeight = FontWeight.Black)
                                Text("함께 만드는 우리 가족의 리듬", color = Color(0xFFACB8D4))
                            }
                            Text(inputs.status, color = if (inputs.parked) Color(0xFF9DE5BD) else Color(0xFFFFC857))
                        }
                        when (game.screen) {
                            "home" -> Home()
                            "test" -> TestPanel()
                            "play" -> Play()
                            "result" -> Result()
                        }
                    }
                }
            }
        }
    }
    override fun onPause() { game.pause(); stopGameAudio(); super.onPause() }
    override fun onDestroy() { inputs.close(); audioManager.abandonAudioFocusRequest(focusRequest); tone?.release(); melody.release(); super.onDestroy() }

    @Composable private fun Home() {
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            BoxWithConstraints(Modifier.widthIn(max = 1040.dp).fillMaxWidth()) {
                if (maxWidth >= 760.dp) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LobbySeatSelection(Modifier.weight(1f))
                        LobbySettings(Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        LobbySeatSelection(Modifier.fillMaxWidth())
                        LobbySettings(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    @Composable private fun LobbySeatSelection(modifier: Modifier) {
        Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("어디에 앉아 있나요?", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("도면에서 함께 연주할 자리를 눌러 주세요.", color = Color(0xFFACB8D4))
            VehicleSeatMap(
                active = game.active,
                hasLightInput = { inputs.experimental && inputs.verified[it] },
                onSeatSelected = { seat, selected ->
                    if (selected || game.active.count { it } > 1) game.active[seat] = selected
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable private fun LobbySettings(modifier: Modifier) {
        Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFF1D2640))) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("연주 설정", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("1–4명 · ${game.song.bpm} BPM · ${game.song.durationMs / 1000}초 · ${game.song.cues.size}노트", color = Color(0xFFACB8D4))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("곡 선택", fontWeight = FontWeight.Bold)
                    RhythmSong.entries.forEach { song ->
                        FilterChip(
                            selected = game.song == song,
                            onClick = { game.song = song },
                            label = { Text(song.title) }
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(game.practice, { game.practice = it })
                    Text("연습 모드 · 판정 시간을 넉넉하게")
                }
                Column {
                    Text("노트 낙하 속도 · " + String.format(Locale.ROOT, "%.2f×", game.noteSpeed))
                    Slider(
                        value = game.noteSpeed,
                        onValueChange = { game.noteSpeed = it },
                        valueRange = 0.5f..2f,
                        steps = 5
                    )
                    Text("낮은 배속에서는 노트가 화면에 더 일찍 나타나요.", color = Color(0xFFACB8D4))
                }
                Button(
                    onClick = { if (inputs.parked) game.start(SystemClock.elapsedRealtime()) },
                    enabled = inputs.parked,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("함께 시작", fontSize = 20.sp)
                }
                OutlinedButton(
                    onClick = { inputs.probe(); game.screen = "test" },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("독서등 입력 테스트")
                }
                Text("준비 4박 뒤, 노트가 판정선에 닿으면 내 자리 버튼을 누르세요.", color = Color(0xFFACB8D4))
            }
        }
    }

    @Composable private fun TestPanel() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { game.screen = "home" }) { Text("돌아가기") }
                Button(onClick = { inputs.probe() }) { Text("지원 범위 / 스위치 새로고침") }
                Switch(inputs.experimental, { inputs.experimental = it })
                Text("독서등 실험 모드")
            }
            Text("각 자리의 독서등을 켰다 꺼 주세요. 변화가 확인되면 해당 자리의 연결을 확인하세요. 좌석은 좌핸들 차량 기준입니다.")
            repeat(4) { i ->
                Row(Modifier.fillMaxWidth().background(Color(0xFF1D2640), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${seatIcons[i]} ${seatNames[i]}", color = seatColors[i], modifier = Modifier.weight(1f))
                    Text("스위치 조회값 ${inputs.switches[i]}  /  현재 점등 ${inputs.states[i]}", Modifier.weight(2f))
                    Checkbox(inputs.verified[i], { inputs.verified[i] = it }, enabled = inputs.supported[i] && inputs.changed[i])
                    Text("자리 확인")
                }
            }
            Text("독서등 지연 보정 ${game.offsetMs} ms · ${if(game.practice) "연습 ±350" else "기본 ±220"} ms 판정")
            Slider(game.offsetMs.toFloat(), { game.offsetMs = it.toLong() }, valueRange = -300f..300f)
            Text("아래 시간은 앱 수신 시각과 이벤트 간격입니다. 물리 스위치부터의 지연은 별도 측정이 필요해요.", fontSize = 12.sp)
            inputs.logs.forEach { Text(it, fontSize = 12.sp, color = Color(0xFFACB8D4)) }
        }
    }

    @Composable private fun ColumnScope.Play() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("가족 점수 ${game.score}    ${game.combo} COMBO", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("${(game.duration - game.elapsed).coerceAtLeast(0) / 1000}초", fontSize = 24.sp)
        }
        val preparingResume = game.resumeRemainingMs > 0
        val message = when {
            preparingResume -> "곧 연주를 이어갑니다"
            game.running -> game.feedback
            else -> "일시정지 · 주차 확인 후 이어가기를 눌러 주세요"
        }
        Text(message, color = Color(0xFFFFC857))
        val seats = game.active.indices.filter { game.active[it] }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF171F35), RoundedCornerShape(16.dp))) {
            Canvas(Modifier.fillMaxSize().clipToBounds()) {
                val width = size.width / seats.size
                val line = size.height * .82f
                val remainingMs = game.resumeRemainingMs
                val rewindMs = if (remainingMs > RESUME_COUNTDOWN_MS) {
                    val liftProgress = 1f - (remainingMs - RESUME_COUNTDOWN_MS) / RESUME_LIFT_MS.toFloat()
                    RESUME_COUNTDOWN_MS * FastOutSlowInEasing.transform(liftProgress)
                } else {
                    remainingMs.toFloat()
                }
                // Advance the visual clock at the same rate as gameplay while scoring stays paused.
                val visualElapsed = game.elapsed - rewindMs
                val travelMs = 2000f / game.noteSpeed
                seats.forEachIndexed { lane, seat ->
                    drawLine(seatColors[seat].copy(alpha=.15f), Offset(width * lane, 0f), Offset(width * lane, size.height), 2f)
                    drawLine(seatColors[seat], Offset(width * lane + 14, line), Offset(width * (lane + 1) - 14, line), 5f)
                    game.notes.filter { it.seat == seat && !it.judged }.forEach { note ->
                        val y = line - (note.time - visualElapsed) / travelMs * line
                        if (y in -24f..size.height) drawRoundRect(seatColors[seat], Offset(width * lane + width * .2f, y - 10), Size(width * .6f, 20f), CornerRadius(10f))
                    }
                }
            }
            if (game.resumeRemainingMs in 1..RESUME_COUNTDOWN_MS) {
                Text(
                    text = ((game.resumeRemainingMs + 999) / 1000).toString(),
                    modifier = Modifier.align(Alignment.Center)
                        .background(navy.copy(alpha = .92f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            seats.forEach { seat ->
                Button(onClick = { if(inputs.parked) game.hit(seat, SystemClock.elapsedRealtime()) }, enabled = game.running && inputs.parked,
                    colors = ButtonDefaults.buttonColors(containerColor=seatColors[seat], contentColor=navy), modifier=Modifier.weight(1f).height(72.dp)) {
                    Text("${seatIcons[seat]} ${seatNames[seat]}", fontSize=18.sp, fontWeight=FontWeight.Bold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick={ game.pause(); game.screen="home" }) { Text("로비로") }
            if (game.running) OutlinedButton(onClick={ game.pause() }) { Text("잠깐 쉬기") }
            else Button(onClick={ if(inputs.parked) game.resume(SystemClock.elapsedRealtime()) }, enabled=inputs.parked && !preparingResume) { Text(if (preparingResume) "재개 준비 중" else "이어가기") }
        }
    }
    @Composable private fun Result() {
        Column(Modifier.fillMaxSize(), verticalArrangement=Arrangement.spacedBy(20.dp), horizontalAlignment=Alignment.CenterHorizontally) {
            Text("우리 가족, 멋진 연주였어요!", fontSize=30.sp, fontWeight=FontWeight.Bold)
            Text(game.song.title, fontSize = 18.sp)
            Text("${game.score}", fontSize=64.sp, color=Color(0xFFFFC857))
            Text("함께 맞춘 노트 ${game.hits} / ${game.notes.size}    최고 콤보 ${game.best}", fontSize=22.sp)
            Text(if (game.hits * 4 >= game.notes.size * 3) "환상의 가족 밴드 ★★★" else "다음 연주도 함께해요 ★", fontSize=22.sp)
            Button(onClick={game.screen="home"}) { Text("다시 모이기") }
        }
    }
}
