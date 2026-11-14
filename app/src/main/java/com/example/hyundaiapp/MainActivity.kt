package com.example.hyundaiapp

import ai.pleos.playground.vehicle.constant.control.LightState
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
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
import androidx.compose.foundation.selection.selectableGroup
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

class MainActivity : ComponentActivity() {
    private var pickingSong by mutableStateOf(true)
    private var seatSetupSince = 0L

    private val showingSeatSetup get() = game.screen == "home" && !pickingSong && !diagnosticsOpen

    private fun showLobbyPage(showSongs: Boolean) {
        pickingSong = showSongs
        seatSetupSince = SystemClock.elapsedRealtime()
    }

    private fun openLobby() {
        game.resetReadiness()
        showLobbyPage(showSongs = true)
        game.screen = "home"
    }

    private fun onReadingLightChanged(seat: Int, state: LightState, receivedAt: Long) {
        if (!foreground) return
        if (showingSeatSetup && receivedAt >= seatSetupSince) {
            when (state) {
                LightState.ON -> game.setSeatReady(seat, true)
                LightState.OFF -> game.setSeatReady(seat, false)
                else -> Unit
            }
        } else if (game.screen == "play" && inputs.experimental && inputs.verified[seat]) {
            game.hit(seat, receivedAt, vehicle = true)
        }
    }

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
        if (!foreground || !inputs.parked || !stickSoundReady || game.screen != "timing") return false
        calibrationAudioReady = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return calibrationAudioReady
    }

    private fun stopCalibrationAudio() {
        if (!calibrationAudioReady) return
        calibrationAudioReady = false
        stopStickSound()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun closeTiming() {
        stopCalibrationAudio()
        seatSetupSince = SystemClock.elapsedRealtime()
        game.screen = timingReturnScreen
    }
    private var diagnosticsOpen by mutableStateOf(false)
    private var experimentalBeforeDiagnostics = false
    private var notice by mutableStateOf<UiNotice?>(null)

    private fun closeDiagnostics() {
        if (!diagnosticsOpen) return
        diagnosticsOpen = false
        seatSetupSince = SystemClock.elapsedRealtime()
        if (inputs.experimental != experimentalBeforeDiagnostics) {
            notice = UiNotice(if (inputs.experimental) "독서등 연주를 켰습니다." else "독서등 연주를 껐습니다. 화면 버튼으로만 연주합니다.")
        }
    }
    private val game = RhythmGame { android.util.Log.i("FamilyBeat", it) }
    private lateinit var inputs: VehicleInputs
    private lateinit var stickPool: SoundPool
    private var stickSoundId = 0
    private var stickSoundReady by mutableStateOf(false)
    private var stickStreamId = 0
    private var availableSongs by mutableStateOf(emptyList<RhythmSong>())
    private var recording: MediaPlayer? = null
    private var recordingReady = false
    private var recordingPlaying = false
    private var recordingComplete = false
    private val gameAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun startGame() {
        if (!showingSeatSetup || !inputs.parked || !game.hasReadySeats || game.song !in availableSongs) return
        releaseRecording()
        val player = MediaPlayer()
        recording = player
        player.setAudioAttributes(gameAudioAttributes)
        player.setOnPreparedListener { if (it === recording) recordingReady = true }
        player.setOnCompletionListener {
            if (it === recording) {
                recordingComplete = true
                recordingPlaying = false
            }
        }
        player.setOnErrorListener { source, what, extra ->
            if (source === recording) recordingFailed("MediaPlayer error $what/$extra")
            true
        }
        try {
            assets.openFd(game.song.recordingAsset).use { player.setDataSource(it) }
            player.prepareAsync()
        } catch (error: IOException) {
            recordingFailed(error.toString())
            return
        }
        game.start(SystemClock.elapsedRealtime(), laneIntroMs = LANE_INTRO_MS)
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
        val failedSong = game.song
        releaseRecording()
        availableSongs = availableSongs - failedSong
        if (game.screen == "play") returnToLobby()
        availableSongs.firstOrNull()?.let { game.song = it }
        showLobbyPage(showSongs = true)
        notice = UiNotice("${failedSong.title} 음원을 열 수 없어요.")
    }

    private fun recordingPosition(): Long? {
        val player = recording ?: return null
        val startThreshold = (game.song.countInMs - game.timingOffsetMs).coerceAtLeast(0L)
        if (!game.running || !audioFocused || game.elapsed < startThreshold || !recordingReady) return null
        if (recordingComplete) return game.song.musicDurationMs
        if (!recordingPlaying) {
            player.start()
            recordingPlaying = true
        }
        return player.currentPosition.toLong()
    }

    private fun stopGameAudio() {
        stopStickSound()
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
        game.resetReadiness()
        showLobbyPage(showSongs = availableSongs.isEmpty())
        game.screen = "home"
    }

    private fun playStickSound() {
        if (!stickSoundReady) return
        stickStreamId = stickPool.play(stickSoundId, .9f, .9f, 1, 0, 1f)
    }

    private fun stopStickSound() {
        if (stickStreamId == 0) return
        stickPool.stop(stickStreamId)
        stickStreamId = 0
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
        stickPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(gameAudioAttributes).build()
        stickPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (sampleId == stickSoundId && status == 0) stickSoundReady = true
        }
        stickSoundId = stickPool.load(this, R.raw.stick_count, 1)
        availableSongs = RhythmSong.entries.filter { song ->
            runCatching { assets.openFd(song.recordingAsset).close() }.isSuccess
        }
        availableSongs.firstOrNull()?.let { game.song = it }
        game.timingOffsetMs = timingPreferences.getLong("offset_ms", 0L)
            .coerceIn(-MAX_TIMING_OFFSET_MS, MAX_TIMING_OFFSET_MS)
        inputs = VehicleInputs(this, ::onReadingLightChanged, {
            if (game.running || game.resumeRemainingMs > 0) {
                notice = UiNotice("주차 상태가 바뀌어 연주를 멈췄어요.")
            }
            pauseGame()
            stopCalibrationAudio()
        })
        setContent {
            RhythmTheme {
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
                        game.tick(SystemClock.elapsedRealtime(), position) {
                            if (audioFocused) playStickSound()
                        }
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
                Surface(Modifier.fillMaxSize(), color = RhythmColors.Background) {
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
                                        Modifier.fillMaxSize().then(if (screen == "play") Modifier else Modifier.padding(24.dp)),
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
                                                    color = if (inputs.parked) RhythmColors.Positive else RhythmColors.Warning,
                                                    style = if (screen == "landing") MaterialTheme.typography.bodyLarge else
                                                        MaterialTheme.typography.titleMedium
                                                )
                                            }
                                        }
                                        Box(Modifier.weight(1f).fillMaxWidth()) {
                                            when (screen) {
                                                "landing" -> GameHome(
                                                    onPlay = ::openLobby,
                                                    onTiming = { openTiming("landing") },
                                                    noteSpeed = game.noteSpeed
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
                                                        if (calibrationAudioReady) playStickSound()
                                                    },
                                                    onBack = ::closeTiming
                                                )
                                                "play" -> Play()
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
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = RhythmColors.Panel,
                        shadowElevation = 10.dp
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
        seatSetupSince = SystemClock.elapsedRealtime()
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
        stickPool.release()
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
                FlowRow(
                    Modifier.selectableGroup().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RhythmButton(
                        onClick = { game.screen = "landing" },
                        primary = false,
                        modifier = Modifier.heightIn(min = 52.dp)
                    ) { Text("홈으로", fontSize = 17.sp) }
                    RhythmSegmented(
                        listOf("1. 곡 선택", "2. 자리·게임 설정"), if (pickingSong) 0 else 1,
                        { showLobbyPage(showSongs = it == 0 || availableSongs.isEmpty()) },
                        Modifier.widthIn(max = 460.dp).fillMaxWidth()
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
                            songs = availableSongs,
                            difficulty = game.difficulty,
                            onSongSelected = { game.song = it },
                            onDifficultySelected = { game.difficulty = it },
                            onContinue = { showLobbyPage(showSongs = false) },
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
        BoxWithConstraints(modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
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
                "어디에 앉아 있나요?",
                if (inputs.supported.any { it }) "독서등을 켜거나 자리 카드를 눌러 준비해 주세요."
                else "자리 카드를 눌러 준비해 주세요.",
                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                subtitleStyle = MaterialTheme.typography.bodyLarge,
                horizontalAlignment = Alignment.CenterHorizontally
            )
            VehicleSeatMap(
                readySeats = game.readySeats,
                canReadyWithLight = { inputs.supported[it] },
                hasLightInput = { inputs.experimental && inputs.verified[it] },
                onSeatReady = { seat, ready ->
                    if (showingSeatSetup) game.setSeatReady(seat, ready)
                },
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
            )
        }
    }

    @Composable private fun LobbySettings(modifier: Modifier) {
        Box(modifier.raisedSurface()) {
            Column(Modifier.padding(30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("연주 설정", style = MaterialTheme.typography.headlineMedium)
                    Text("${game.song.title} · ${game.difficulty.label} · ${game.song.chart(game.difficulty).size}노트", color = RhythmColors.Muted)
                }
                Row(
                    Modifier.fillMaxWidth().insetSurface(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(game.practice, { game.practice = it })
                    Text("연습 모드 · 판정 시간을 넉넉하게", fontSize = 18.sp)
                }
                Column(
                    Modifier.fillMaxWidth().insetSurface(RoundedCornerShape(24.dp)).padding(18.dp)
                ) {
                    Text("노트 낙하 속도 · " + String.format(Locale.ROOT, "%.2f×", game.noteSpeed), fontSize = 18.sp)
                    Slider(
                        value = game.noteSpeed,
                        onValueChange = { game.noteSpeed = it },
                        valueRange = 0.5f..2f,
                        steps = 5,
                        colors = SliderDefaults.colors(inactiveTrackColor = RhythmColors.Line)
                    )
                    Text("낮은 배속에서는 노트가 화면에 더 일찍 나타나요.", color = RhythmColors.Muted)
                }
                RhythmButton(
                    onClick = ::startGame,
                    enabled = inputs.parked && game.hasReadySeats && game.song in availableSongs,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                ) {
                    Text(if (game.soloReady) "솔로 시작" else "함께 시작", fontSize = 24.sp)
                }
                if (!game.hasReadySeats) {
                    Text("한 자리 이상 준비되면 시작할 수 있어요.", color = RhythmColors.Muted)
                } else if (game.soloReady) {
                    Text("운전석 혼자 화면을 터치해 4레인을 연주해요.", color = RhythmColors.Muted)
                }
                RhythmButton(
                    primary = false,
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
                Text("준비 4박 뒤, 노트가 판정선에 닿으면 내 자리 독서등을 켜거나 화면 버튼을 누르세요.", color = RhythmColors.Muted)
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
                    RhythmButton(onClick = ::closeDiagnostics, primary = false, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text("닫기", fontSize = 17.sp)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    RhythmButton(
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
                        Text("독서등으로 연주")
                    }
                }
                Text("각 자리의 독서등을 켰다 꺼 주세요. 변화가 확인되면 해당 자리의 연결을 확인하세요. 좌석은 좌핸들 차량 기준입니다.")
                repeat(4) { i ->
                    Row(
                        Modifier.fillMaxWidth().insetSurface(RoundedCornerShape(18.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${seatIcons[i]} ${seatNames[i]}", color = RhythmColors.Blue, modifier = Modifier.weight(1f))
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
                inputs.logs.forEach { Text(it, fontSize = 14.sp, color = RhythmColors.Muted) }
            }
            FadeToast(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
    }

    @Composable private fun Play() {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, top = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PlayHud(game, onPause = ::pauseGame)
                PlayStatus()
            }
            PerformanceStage(
                game = game,
                canTap = game.running && inputs.parked,
                onLanePress = { lane, at -> if (inputs.parked) game.hit(lane, at) },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }

    @Composable private fun PlayStatus() {
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
                transitionSpec = {
                    // Judgments change several times a second: swap instantly, fade only status messages.
                    if (Judgment.entries.any { it.name == targetState }) uiInstant() else uiFade()
                },
                label = "Judgment feedback fade"
            ) { feedback ->
                if (feedback.isNotEmpty()) {
                    val color = when (feedback) {
                        "PERFECT" -> Color(0xFFFFC857)
                        "GREAT" -> Color(0xFF72DDF7)
                        "GOOD" -> Color(0xFFB79CFF)
                        "BAD", "MISS" -> Color(0xFFFF8DA1)
                        else -> RhythmColors.StageInk
                    }
                    Text(feedback, color = color, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (game.combo > 0) {
                Text(
                    "${game.combo} COMBO", style = MaterialTheme.typography.titleLarge, color = Color(0xFFFFC857),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    inputs.status,
                    color = if (inputs.parked) Color(0xFF9DE5BD) else Color(0xFFFFC857),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End
                )
                Text(
                    "${(game.duration - game.elapsed.coerceAtLeast(0)).coerceAtLeast(0) / 1000}초",
                    color = RhythmColors.StageInk,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    @Composable private fun PauseDialog() {
        MotionDialog(
            visible = game.screen == "play" && !game.running && game.resumeRemainingMs == 0L,
            onDismissRequest = {}
        ) { modifier ->
            Surface(
                modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth(),
                shape = PanelShape, color = RhythmColors.Panel, shadowElevation = 12.dp
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
                    RhythmButton(
                        onClick = { if (inputs.parked) game.resume(SystemClock.elapsedRealtime()) },
                        enabled = inputs.parked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    ) { Text("이어가기", fontSize = 18.sp) }
                    RhythmButton(
                        onClick = ::returnToLobby,
                        primary = false,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    ) { Text("로비로", fontSize = 18.sp) }
                }
            }
        }
    }
    @Composable private fun Result() {
        Box(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.widthIn(max = 790.dp).fillMaxWidth().raisedSurface().padding(34.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StaggeredHeading(
                    when {
                        game.failed -> "라이프가 모두 소진됐어요"
                        game.isSolo -> "멋진 솔로 연주였어요!"
                        else -> "우리 가족, 멋진 연주였어요!"
                    },
                    "${game.song.title} · ${game.difficulty.label}",
                    MaterialTheme.typography.headlineLarge,
                    subtitleStyle = MaterialTheme.typography.titleLarge,
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val shownScore = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(game.score) {
                        shownScore.animateTo(game.score.toFloat(), androidx.compose.animation.core.tween(900))
                    }
                    Text(
                        "${shownScore.value.toInt()}", fontSize = 84.sp, lineHeight = 92.sp, color = RhythmColors.Ink,
                        fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Text("만점 ${game.maxScore}", color = RhythmColors.Muted, style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ResultMetric(
                        if (game.isSolo) "맞춘 노트" else "함께 맞춘 노트",
                        "${game.hits} / ${game.notes.size}",
                        Modifier.weight(1f)
                    )
                    ResultMetric("최고 콤보", "${game.best}", Modifier.weight(1f))
                }
                JudgmentBreakdown()
                if (game.hits * 4 >= game.notes.size * 3) {
                    Text(
                        if (game.isSolo) "멋진 솔로 연주 ★★★" else "환상의 가족 밴드 ★★★",
                        color = RhythmColors.Muted,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
                RhythmButton(
                    onClick = ::returnToLobby,
                    modifier = Modifier.widthIn(min = 240.dp).heightIn(min = 64.dp)
                ) { Text(if (game.isSolo) "로비로" else "다시 모이기", fontSize = 20.sp) }
            }
        }
    }

    /** Segmented bar + legend of how every note was judged. */
    @Composable private fun JudgmentBreakdown() {
        val pill = RoundedCornerShape(50)
        fun tint(judgment: Judgment) = if (judgment == Judgment.MISS) Color(0xFFC9CDD4) else judgmentColor(judgment)
        Column(
            Modifier.fillMaxWidth().insetSurface(RoundedCornerShape(24.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("판정 분포", color = RhythmColors.Muted, style = MaterialTheme.typography.titleMedium)
                Text(
                    "정확도 ${game.score * 100 / game.maxScore.coerceAtLeast(1)}%",
                    color = RhythmColors.Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
            }
            Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Judgment.entries.forEach { judgment ->
                    val count = game.judgments[judgment.ordinal]
                    if (count > 0) {
                        Spacer(Modifier.weight(count.toFloat()).fillMaxHeight().background(tint(judgment), pill))
                    }
                }
                // Notes never reached because the run ended early.
                val unplayed = game.notes.size - game.judgments.sum()
                if (unplayed > 0) {
                    Spacer(Modifier.weight(unplayed.toFloat()).fillMaxHeight().background(RhythmColors.Panel, pill))
                }
            }
            Judgment.entries.forEach { judgment ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(10.dp).background(tint(judgment), pill))
                    Text(judgment.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${game.judgments[judgment.ordinal]}", color = RhythmColors.Muted,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }

    @Composable private fun ResultMetric(label: String, value: String, modifier: Modifier) {
        Column(
            modifier.insetSurface(RoundedCornerShape(24.dp)).padding(horizontal = 12.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = RhythmColors.Muted, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(value, color = RhythmColors.Ink, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        }
    }
}
