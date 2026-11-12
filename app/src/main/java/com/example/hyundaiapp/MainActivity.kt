package com.example.hyundaiapp

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.updateTransition
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
    private var pickingSong by mutableStateOf(true)
    private var foreground by mutableStateOf(false)
    private var calibrationAudioReady by mutableStateOf(false)
    private var timingReturnScreen = "landing"
    private val timingPreferences by lazy { getSharedPreferences("timing", MODE_PRIVATE) }

    private fun applyTimingOffset(offset: Long) {
        val value = offset.coerceIn(-MAX_TIMING_OFFSET_MS, MAX_TIMING_OFFSET_MS)
        game.timingOffsetMs = value
        timingPreferences.edit().putLong("offset_ms", value).apply()
    }

    private fun openTiming(returnScreen: String) {
        timingReturnScreen = returnScreen
        game.screen = "timing"
    }

    private fun startCalibrationAudio(): Boolean {
        if (!foreground || !inputs.parked || tone == null || game.screen != "timing") return false
        calibrationAudioReady = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return calibrationAudioReady
    }

    private fun stopCalibrationAudio() {
        if (!calibrationAudioReady) return
        calibrationAudioReady = false
        tone?.stopTone()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun closeTiming() {
        stopCalibrationAudio()
        game.screen = timingReturnScreen
    }
    private var diagnosticsOpen by mutableStateOf(false)
    private var experimentalBeforeDiagnostics = false
    private var notice by mutableStateOf<UiNotice?>(null)

    private fun closeDiagnostics() {
        if (!diagnosticsOpen) return
        diagnosticsOpen = false
        if (inputs.experimental != experimentalBeforeDiagnostics) {
            notice = UiNotice(if (inputs.experimental) "독서등 실험 모드를 켰습니다." else "독서등 실험 모드를 껐습니다.")
        }
    }
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
                    audioFocused = false
                    stopCalibrationAudio()
                    game.pause()
                    stopGameAudio()
                }
            }.build()
    }
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 45) }.getOrNull()
        melody = SoundPool.Builder().setMaxStreams(1)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .build()
        chimeSample = melody.load(this, R.raw.chime_c4, 1)
        game.timingOffsetMs = timingPreferences.getLong("offset_ms", 0L)
            .coerceIn(-MAX_TIMING_OFFSET_MS, MAX_TIMING_OFFSET_MS)
        inputs = VehicleInputs(this, { seat, time -> game.hit(seat, time, true) }, {
            if (game.running || game.resumeRemainingMs > 0) {
                notice = UiNotice("주차 상태가 바뀌어 연주를 멈췄어요.")
            }
            game.pause()
            stopCalibrationAudio()
        })
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
                LaunchedEffect(notice) {
                    if (notice != null) {
                        delay(2600)
                        notice = null
                    }
                }
                BackHandler(game.screen == "home" || game.screen == "timing") {
                    if (game.screen == "timing") closeTiming() else game.screen = "landing"
                }
                Surface(Modifier.fillMaxSize(), color = navy) {
                    SharedTransitionLayout(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize()) {
                            val screenTransition = updateTransition(game.screen, label = "Screen transition")
                            screenTransition.AnimatedContent(
                                modifier = Modifier.fillMaxSize(),
                                transitionSpec = { uiFade() }
                            ) { screen ->
                                val visibilityScope = this
                                Column(
                                    Modifier.fillMaxSize().padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (screen == "landing") Arrangement.End else Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (screen != "landing") BrandHeading(visibilityScope, expanded = false)
                                        Text(inputs.status, color = if (inputs.parked) Color(0xFF9DE5BD) else Color(0xFFFFC857))
                                    }
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        when (screen) {
                                            "landing" -> GameHome(
                                                onPlay = { pickingSong = true; game.screen = "home" },
                                                onTiming = { openTiming("landing") }
                                            ) {
                                                BrandHeading(
                                                    visibilityScope, expanded = true,
                                                    animateEntrance = screenTransition.currentState == "landing" &&
                                                        screenTransition.targetState == "landing"
                                                )
                                            }
                                            "home" -> Home()
                                            "timing" -> TimingCalibrationPage(
                                                offsetMs = game.timingOffsetMs,
                                                onOffsetChanged = ::applyTimingOffset,
                                                canMeasure = inputs.parked && foreground && game.screen == "timing",
                                                audioReady = calibrationAudioReady,
                                                startAudio = ::startCalibrationAudio,
                                                stopAudio = ::stopCalibrationAudio,
                                                playCue = {
                                                    if (calibrationAudioReady) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 65)
                                                },
                                                onBack = ::closeTiming
                                            )
                                            "play" -> Column(
                                                Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) { Play() }
                                            "result" -> Result()
                                        }
                                    }
                                }
                            }
                            SlideSnackbar(notice, Modifier.align(Alignment.BottomCenter).padding(20.dp))
                        }
                    }
                }
                MotionDialog(diagnosticsOpen, fromBottom = true, onDismissRequest = ::closeDiagnostics) { modifier ->
                    Surface(
                        modifier.widthIn(max = 960.dp).fillMaxWidth().fillMaxHeight(.82f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        tonalElevation = 6.dp
                    ) {
                        TestPanel(Modifier.padding(24.dp))
                    }
                }
                PauseDialog()
            }
        }
    }
    override fun onResume() {
        super.onResume()
        foreground = true
    }

    override fun onPause() {
        foreground = false
        stopCalibrationAudio()
        game.pause()
        stopGameAudio()
        super.onPause()
    }
    override fun onDestroy() { inputs.close(); audioManager.abandonAudioFocusRequest(focusRequest); tone?.release(); melody.release(); super.onDestroy() }

    @Composable private fun Home() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 1100.dp).fillMaxWidth().fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { game.screen = "landing" }) { Text("홈으로") }
                    FilterChip(
                        selected = pickingSong,
                        onClick = { pickingSong = true },
                        label = { Text("1. 곡 선택") }
                    )
                    FilterChip(
                        selected = !pickingSong,
                        onClick = { pickingSong = false },
                        label = { Text("2. 자리·게임 설정") }
                    )
                }
                AnimatedContent(
                    targetState = pickingSong,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = { uiFade() },
                    label = "Lobby page transition"
                ) { showSongs ->
                    if (showSongs) {
                        SongPicker(
                            selectedSong = game.song,
                            difficulty = game.difficulty,
                            onSongSelected = { game.song = it },
                            onDifficultySelected = { game.difficulty = it },
                            onContinue = { pickingSong = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LobbySetup(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    @Composable private fun LobbySetup(modifier: Modifier) {
        BoxWithConstraints(modifier.verticalScroll(rememberScrollState())) {
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

    @Composable private fun LobbySeatSelection(modifier: Modifier) {
        Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StaggeredHeading(
                "어디에 앉아 있나요?", "도면에서 함께 연주할 자리를 눌러 주세요.",
                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                horizontalAlignment = Alignment.CenterHorizontally
            )
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
                    Text("${game.song.title} · ${game.difficulty.label} · ${game.song.chart(game.difficulty).size}노트", color = Color(0xFFACB8D4))
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
                    onClick = { if (game.screen == "home" && inputs.parked) game.start(SystemClock.elapsedRealtime()) },
                    enabled = inputs.parked,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("함께 시작", fontSize = 20.sp)
                }
                OutlinedButton(
                    onClick = {
                        experimentalBeforeDiagnostics = inputs.experimental
                        diagnosticsOpen = true
                        inputs.probe()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("독서등 입력 테스트")
                }
                TextButton(onClick = { openTiming("home") }, modifier = Modifier.fillMaxWidth()) {
                    Text("타이밍 보정")
                }
                Text("준비 4박 뒤, 노트가 판정선에 닿으면 내 자리 버튼을 누르세요.", color = Color(0xFFACB8D4))
            }
        }
    }

    @Composable private fun TestPanel(modifier: Modifier) {
        var toast by remember { mutableStateOf<UiNotice?>(null) }
        LaunchedEffect(toast) {
            if (toast != null) {
                delay(1600)
                toast = null
            }
        }
        Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StaggeredHeading(
                        "독서등 입력 테스트", "좌석별 독서등 신호 확인",
                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedButton(onClick = ::closeDiagnostics) { Text("닫기") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { inputs.probe() }, enabled = !inputs.probing) {
                        if (inputs.probing) LinearSpinner(Modifier.padding(end = 8.dp))
                        Text(if (inputs.probing) "확인 중" else "지원 범위 / 스위치 새로고침")
                    }
                    Switch(inputs.experimental, { inputs.experimental = it })
                    Text("독서등 실험 모드")
                }
                Text("각 자리의 독서등을 켰다 꺼 주세요. 변화가 확인되면 해당 자리의 연결을 확인하세요. 좌석은 좌핸들 차량 기준입니다.")
                repeat(4) { i ->
                    Row(Modifier.fillMaxWidth().background(Color(0xFF1D2640), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${seatIcons[i]} ${seatNames[i]}", color = seatColors[i], modifier = Modifier.weight(1f))
                        Text("스위치 조회값 ${inputs.switches[i]}  /  현재 점등 ${inputs.states[i]}", Modifier.weight(2f))
                        DrawnCheckbox(inputs.verified[i], inputs.supported[i] && inputs.changed[i]) { checked ->
                            inputs.verified[i] = checked
                            if (checked) toast = UiNotice("${seatNames[i]} 확인 완료")
                        }
                        Text("자리 확인")
                    }
                }
                Text("독서등 지연 보정 ${game.offsetMs} ms · ${if(game.practice) "연습 ±350" else "기본 ±220"} ms 판정")
                Slider(game.offsetMs.toFloat(), { game.offsetMs = it.toLong() }, valueRange = -300f..300f)
                Text("아래 시간은 앱 수신 시각과 이벤트 간격입니다. 물리 스위치부터의 지연은 별도 측정이 필요해요.", fontSize = 12.sp)
                inputs.logs.forEach { Text(it, fontSize = 12.sp, color = Color(0xFFACB8D4)) }
            }
            FadeToast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
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
        AnimatedContent(
            targetState = message,
            transitionSpec = { uiFade() },
            label = "Judgment feedback fade"
        ) { feedback -> Text(feedback, color = Color(0xFFFFC857)) }
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
            OutlinedButton(onClick = { game.pause() }, enabled = game.running) {
                Text(if (preparingResume) "재개 준비 중" else "잠깐 쉬기")
            }
        }
    }

    @Composable private fun PauseDialog() {
        MotionDialog(
            visible = game.screen == "play" && !game.running && game.resumeRemainingMs == 0L,
            onDismissRequest = {}
        ) { modifier ->
            Surface(
                modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp), tonalElevation = 8.dp
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StaggeredHeading(
                        "일시정지", if (inputs.parked) "준비되면 연주를 이어가세요." else "주차 상태가 확인되면 이어갈 수 있어요.",
                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Button(
                        onClick = { if (inputs.parked) game.resume(SystemClock.elapsedRealtime()) },
                        enabled = inputs.parked,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("이어가기") }
                    OutlinedButton(onClick = { game.pause(); game.screen = "home" }, modifier = Modifier.fillMaxWidth()) {
                        Text("로비로")
                    }
                }
            }
        }
    }
    @Composable private fun Result() {
        Column(Modifier.fillMaxSize(), verticalArrangement=Arrangement.spacedBy(20.dp), horizontalAlignment=Alignment.CenterHorizontally) {
            StaggeredHeading(
                "우리 가족, 멋진 연주였어요!", "${game.song.title} · ${game.difficulty.label}",
                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                horizontalAlignment = Alignment.CenterHorizontally
            )
            Text("${game.score}", fontSize=64.sp, color=Color(0xFFFFC857))
            Text("함께 맞춘 노트 ${game.hits} / ${game.notes.size}    최고 콤보 ${game.best}", fontSize=22.sp)
            Text(if (game.hits * 4 >= game.notes.size * 3) "환상의 가족 밴드 ★★★" else "다음 연주도 함께해요 ★", fontSize=22.sp)
            Button(onClick={game.screen="home"}) { Text("다시 모이기") }
        }
    }
}
