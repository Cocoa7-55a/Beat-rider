package com.example.hyundaiapp

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.io.IOException
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
    private var twinkleAvailable by mutableStateOf(false)
    private var recording: MediaPlayer? = null
    private var recordingReady = false
    private var recordingPlaying = false
    private var recordingComplete = false
    private val gameAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun startGame() {
        if (game.screen != "home" || !inputs.parked) return
        releaseRecording()
        val asset = game.song.recordingAsset
        if (asset != null) {
            val player = MediaPlayer()
            recording = player
            player.setAudioAttributes(gameAudioAttributes)
            player.setOnPreparedListener { recordingReady = true }
            player.setOnCompletionListener {
                recordingComplete = true
                recordingPlaying = false
            }
            player.setOnErrorListener { _, what, extra ->
                recordingFailed("MediaPlayer error $what/$extra")
                true
            }
            try {
                assets.openFd(asset).use { player.setDataSource(it) }
                player.prepareAsync()
            } catch (error: IOException) {
                recordingFailed(error.toString())
                return
            }
        }
        game.start(SystemClock.elapsedRealtime())
    }

    private fun releaseRecording() {
        recording?.release()
        recording = null
        recordingReady = false
        recordingPlaying = false
        recordingComplete = false
    }

    private fun recordingFailed(message: String) {
        android.util.Log.w("FamilyBeat", message)
        releaseRecording()
        twinkleAvailable = false
        if (game.screen == "play") returnToLobby()
        game.song = RhythmSong.FAMILY_BEAT
        pickingSong = true
        notice = UiNotice("반짝반짝 작은 별 음원을 열 수 없어요.")
    }

    private fun recordingPosition(): Long? {
        val player = recording ?: return null
        if (!game.running || !audioFocused || game.elapsed < game.song.countInMs || !recordingReady) return null
        if (recordingComplete) return game.song.musicDurationMs
        if (!recordingPlaying) {
            player.start()
            recordingPlaying = true
        }
        return player.currentPosition.toLong()
    }

    private fun stopGameAudio() {
        tone?.stopTone()
        if (recordingPlaying) {
            recording?.pause()
            recordingPlaying = false
        }
    }

    private fun pauseGame() {
        game.pause()
        stopGameAudio()
    }

    private fun returnToLobby() {
        pauseGame()
        releaseRecording()
        game.screen = "home"
    }

    private fun playSongBeat() {
        if (!audioFocused) return
        val song = game.song
        if (song.recordingAsset != null && game.elapsed >= song.countInMs) return
        val beat = game.elapsed / song.beatMs
        val sound = if (beat % 4 == 0L) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP
        tone?.startTone(sound, 65)
    }

    private var audioFocused = false
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(gameAudioAttributes)
            .setOnAudioFocusChangeListener { change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN) {
                    audioFocused = false
                    stopCalibrationAudio()
                    pauseGame()
                }
            }.build()
    }
    @OptIn(ExperimentalSharedTransitionApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 45) }.getOrNull()
        twinkleAvailable = runCatching {
            assets.openFd(checkNotNull(RhythmSong.TWINKLE.recordingAsset)).close()
        }.isSuccess
        game.timingOffsetMs = timingPreferences.getLong("offset_ms", 0L)
            .coerceIn(-MAX_TIMING_OFFSET_MS, MAX_TIMING_OFFSET_MS)
        inputs = VehicleInputs(this, { seat, time -> game.hit(seat, time, true) }, {
            if (game.running || game.resumeRemainingMs > 0) {
                notice = UiNotice("주차 상태가 바뀌어 연주를 멈췄어요.")
            }
            pauseGame()
            stopCalibrationAudio()
        })
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = navy, surface = Color(0xFF1D2640), primary = Color(0xFF72DDF7))) {
                LaunchedEffect(game.running) {
                    if (game.running) {
                        audioFocused = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                        if (!audioFocused) pauseGame()
                    } else {
                        stopGameAudio()
                        if (game.screen == "result") releaseRecording()
                        audioFocused = false
                        audioManager.abandonAudioFocusRequest(focusRequest)
                    }
                }
                LaunchedEffect(Unit) {
                    while (true) {
                        val position = recordingPosition()
                        game.tick(SystemClock.elapsedRealtime(), position, ::playSongBeat)
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
                                Box(Modifier.fillMaxSize()) {
                                    if (screen == "play") PerformanceBackdrop(Modifier.matchParentSize())
                                    Column(
                                        Modifier.fillMaxSize().padding(24.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (screen != "play") {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = if (screen == "landing") Arrangement.End else Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (screen != "landing") {
                                                    Box(Modifier.weight(1f).padding(end = 12.dp)) {
                                                        BrandHeading(visibilityScope, expanded = false)
                                                    }
                                                }
                                                Text(
                                                    inputs.status,
                                                    color = if (inputs.parked) Color(0xFF9DE5BD) else Color(0xFFFFC857),
                                                    style = if (screen == "landing") MaterialTheme.typography.bodyLarge else
                                                        MaterialTheme.typography.titleMedium
                                                )
                                            }
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
                            }
                            SlideSnackbar(notice, Modifier.align(Alignment.BottomCenter).padding(20.dp))
                        }
                    }
                }
                MotionDialog(diagnosticsOpen, fromBottom = true, onDismissRequest = ::closeDiagnostics) { modifier ->
                    Surface(
                        modifier.widthIn(max = 1120.dp).fillMaxWidth().fillMaxHeight(.88f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        tonalElevation = 6.dp
                    ) {
                        TestPanel(Modifier.padding(28.dp))
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
        pauseGame()
        super.onPause()
    }
    override fun onDestroy() {
        inputs.close()
        audioManager.abandonAudioFocusRequest(focusRequest)
        tone?.release()
        releaseRecording()
        super.onDestroy()
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun Home() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = GameContentMaxWidth).fillMaxWidth().fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = { game.screen = "landing" },
                        modifier = Modifier.heightIn(min = 52.dp)
                    ) { Text("홈으로", fontSize = 17.sp) }
                    FilterChip(
                        selected = pickingSong,
                        onClick = { pickingSong = true },
                        modifier = Modifier.heightIn(min = 52.dp),
                        label = { Text("1. 곡 선택", fontSize = 17.sp) }
                    )
                    FilterChip(
                        selected = !pickingSong,
                        onClick = { pickingSong = false },
                        modifier = Modifier.heightIn(min = 52.dp),
                        label = { Text("2. 자리·게임 설정", fontSize = 17.sp) }
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
                            twinkleAvailable = twinkleAvailable,
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
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
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
                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                subtitleStyle = MaterialTheme.typography.bodyLarge,
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
            Column(Modifier.padding(30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("연주 설정", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${game.song.title} · ${game.difficulty.label} · ${game.song.chart(game.difficulty).size}노트", color = Color(0xFFACB8D4))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(game.practice, { game.practice = it })
                    Text("연습 모드 · 판정 시간을 넉넉하게", fontSize = 18.sp)
                }
                Column {
                    Text("노트 낙하 속도 · " + String.format(Locale.ROOT, "%.2f×", game.noteSpeed), fontSize = 18.sp)
                    Slider(
                        value = game.noteSpeed,
                        onValueChange = { game.noteSpeed = it },
                        valueRange = 0.5f..2f,
                        steps = 5
                    )
                    Text("낮은 배속에서는 노트가 화면에 더 일찍 나타나요.", color = Color(0xFFACB8D4))
                }
                Button(
                    onClick = ::startGame,
                    enabled = inputs.parked,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                ) {
                    Text("함께 시작", fontSize = 24.sp)
                }
                OutlinedButton(
                    onClick = {
                        experimentalBeforeDiagnostics = inputs.experimental
                        diagnosticsOpen = true
                        inputs.probe()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                ) {
                    Text("독서등 입력 테스트", fontSize = 18.sp)
                }
                TextButton(
                    onClick = { openTiming("home") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Text("타이밍 보정", fontSize = 18.sp)
                }
                Text("준비 4박 뒤, 노트가 판정선에 닿으면 내 자리 버튼을 누르세요.", color = Color(0xFFACB8D4))
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun TestPanel(modifier: Modifier) {
        var toast by remember { mutableStateOf<UiNotice?>(null) }
        LaunchedEffect(toast) {
            if (toast != null) {
                delay(1600)
                toast = null
            }
        }
        Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StaggeredHeading(
                        "독서등 입력 테스트", "좌석별 독서등 신호 확인",
                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        subtitleStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = ::closeDiagnostics, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text("닫기", fontSize = 17.sp)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Button(
                        onClick = { inputs.probe() },
                        enabled = !inputs.probing,
                        modifier = Modifier.heightIn(min = 56.dp)
                    ) {
                        if (inputs.probing) LinearSpinner(Modifier.padding(end = 8.dp))
                        Text(if (inputs.probing) "확인 중" else "지원 범위 / 스위치 새로고침", fontSize = 17.sp)
                    }
                    Row(
                        Modifier.heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Switch(inputs.experimental, { inputs.experimental = it })
                        Text("독서등 실험 모드")
                    }
                }
                Text("각 자리의 독서등을 켰다 꺼 주세요. 변화가 확인되면 해당 자리의 연결을 확인하세요. 좌석은 좌핸들 차량 기준입니다.")
                repeat(4) { i ->
                    Row(Modifier.fillMaxWidth().background(Color(0xFF1D2640), RoundedCornerShape(12.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Text("아래 시간은 앱 수신 시각과 이벤트 간격입니다. 물리 스위치부터의 지연은 별도 측정이 필요해요.", fontSize = 14.sp)
                inputs.logs.forEach { Text(it, fontSize = 14.sp, color = Color(0xFFACB8D4)) }
            }
            FadeToast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
    }

    @Composable private fun ColumnScope.Play() {
        PlayHud(game, onPause = ::pauseGame)
        val preparingResume = game.resumeRemainingMs > 0
        val message = when {
            preparingResume -> "곧 연주를 이어갑니다"
            game.running -> game.feedback
            else -> "일시정지 · 주차 확인 후 이어가기를 눌러 주세요"
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = message,
                modifier = Modifier.weight(1f),
                transitionSpec = { uiFade() },
                label = "Judgment feedback fade"
            ) { feedback ->
                val color = when (feedback) {
                    "PERFECT" -> Color(0xFFFFC857)
                    "GREAT" -> Color(0xFF72DDF7)
                    "GOOD" -> Color(0xFFB79CFF)
                    "BAD", "MISS" -> Color(0xFFFF8DA1)
                    else -> Color(0xFFACB8D4)
                }
                Text(feedback, color = color, style = MaterialTheme.typography.titleLarge)
            }
            if (game.combo > 0) {
                Text("${game.combo} COMBO", color = Color(0xFFFFC857), style = MaterialTheme.typography.titleLarge)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    inputs.status,
                    color = if (inputs.parked) Color(0xFF9DE5BD) else Color(0xFFFFC857),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End
                )
                Text(
                    "${(game.duration - game.elapsed).coerceAtLeast(0) / 1000}초",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        val seats = game.active.indices.filter { game.active[it] }
        PerformanceStage(game, seats, Modifier.weight(1f).fillMaxWidth())
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 720.dp
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
                seats.forEach { seat ->
                    Button(
                        onClick = { if (inputs.parked) game.hit(seat, SystemClock.elapsedRealtime()) },
                        enabled = game.running && inputs.parked,
                        colors = ButtonDefaults.buttonColors(containerColor = seatColors[seat], contentColor = navy),
                        modifier = Modifier.weight(1f).heightIn(min = if (compact) 64.dp else 88.dp),
                        contentPadding = PaddingValues(horizontal = if (compact) 4.dp else 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "${seatIcons[seat]} ${seatNames[seat]}",
                            fontSize = if (compact) 15.sp else 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        OutlinedButton(
            onClick = ::returnToLobby,
            modifier = Modifier.heightIn(min = 54.dp)
        ) { Text("로비로", fontSize = 17.sp) }
    }

    @Composable private fun PauseDialog() {
        MotionDialog(
            visible = game.screen == "play" && !game.running && game.resumeRemainingMs == 0L,
            onDismissRequest = {}
        ) { modifier ->
            Surface(
                modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp), tonalElevation = 8.dp
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    StaggeredHeading(
                        "일시정지", if (inputs.parked) "준비되면 연주를 이어가세요." else "주차 상태가 확인되면 이어갈 수 있어요.",
                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        subtitleStyle = MaterialTheme.typography.bodyLarge
                    )
                    Button(
                        onClick = { if (inputs.parked) game.resume(SystemClock.elapsedRealtime()) },
                        enabled = inputs.parked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    ) { Text("이어가기", fontSize = 18.sp) }
                    OutlinedButton(
                        onClick = ::returnToLobby,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    ) { Text("로비로", fontSize = 18.sp) }
                }
            }
        }
    }
    @Composable private fun Result() {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StaggeredHeading(
                if (game.failed) "라이프가 모두 소진됐어요" else "우리 가족, 멋진 연주였어요!",
                "${game.song.title} · ${game.difficulty.label}",
                MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                subtitleStyle = MaterialTheme.typography.titleLarge,
                horizontalAlignment = Alignment.CenterHorizontally
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${game.score}", fontSize = 84.sp, lineHeight = 92.sp, color = Color(0xFFFFC857))
                Text("만점 ${game.maxScore}", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "함께 맞춘 노트 ${game.hits} / ${game.notes.size}    최고 콤보 ${game.best}",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Text(
                if (game.hits * 4 >= game.notes.size * 3) "환상의 가족 밴드 ★★★" else "다음 연주도 함께해요 ★",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = { game.screen = "home" },
                modifier = Modifier.widthIn(min = 220.dp).heightIn(min = 64.dp)
            ) { Text("다시 모이기", fontSize = 20.sp) }
        }
    }
}
