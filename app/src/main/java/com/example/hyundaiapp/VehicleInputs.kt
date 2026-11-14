package com.example.hyundaiapp

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.*
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.LightStateListener

class VehicleInputs(
    context: Context,
    private val onReadingLightChanged: (Int, LightState, Long) -> Unit,
    private val onUnsafe: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context)
    private var car: Car? = null
    private var ux: CarUxRestrictionsManager? = null
    private var closed = false
    private var gearAt = 0L
    private var gearRequest = 0L
    private var gearApplied = 0L
    private var gearPark = false
    private var uxSafe = false
    var parked by mutableStateOf(false)
    var status by mutableStateOf("주차 상태 확인 중")
    var experimental by mutableStateOf(true)
    val supported = mutableStateListOf(false, false, false, false)
    val changed = mutableStateListOf(false, false, false, false)
    val verified = mutableStateListOf(false, false, false, false)
    val states = mutableStateListOf("대기", "대기", "대기", "대기")
    val switches = mutableStateListOf("대기", "대기", "대기", "대기")
    val logs = mutableStateListOf<String>()
    private val values = arrayOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)
    private val areas = values.map { SeatArea(it) }
    private val listeners = mutableMapOf<Int, LightStateListener>()
    private val edges = List(4) { SignalEdges<LightState>() }
    private val lastEvent = LongArray(4)
    var probing by mutableStateOf(false)
        private set
    private fun main(block: () -> Unit) { handler.post { if (!closed) block() } }
    private fun log(message: String) {
        logs.add(0, message); while (logs.size > 8) logs.removeAt(logs.lastIndex)
        android.util.Log.i("FamilyBeat", message)
    }
    private fun invalidateLight(i: Int) {
        supported[i] = false; verified[i] = false; changed[i] = false
        edges[i].reset(); lastEvent[i] = 0; states[i] = "미확인"
        listeners.remove(i)?.let { listener ->
            runCatching { vehicle.getLight().unregisterReadingLightState(areas[i], listener) }
        }
    }
    private fun updateSafety() {
        val wasParked = parked
        parked = gearPark && uxSafe && SystemClock.elapsedRealtime() - gearAt < 2000
        status = if (parked) "P · 주차 확인" else "주차 미확인 / 주행 제한 · 일시정지"
        if (wasParked != parked) log("SAFETY parked=$parked gear=$gearPark ux=$uxSafe")
        if (!parked) onUnsafe()
    }
    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            updateSafety()
            try {
                val request = ++gearRequest
                val requestedAt = SystemClock.elapsedRealtime()
                vehicle.getGear().getCurrentGearSelectionState({ gear -> main {
                    if (request < gearApplied) return@main
                    gearApplied = request; gearAt = requestedAt
                    gearPark = gear == VehicleGear.GEAR_PARK; updateSafety()
                } }, { main { if (request >= gearApplied) { gearApplied = request; gearPark = false; updateSafety() } } })
                uxSafe = ux?.currentCarUxRestrictions?.isRequiresDistractionOptimization == false
                updateSafety()
            } catch (_: Exception) { gearPark = false; uxSafe = false; updateSafety() }
            handler.postDelayed(this, 500)
        }
    }
    init {
        vehicle.initialize()
        try {
            car = Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready -> main {
                uxSafe = false
                if (ready) try {
                    ux = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    ux?.registerListener { restrictions -> main {
                        uxSafe = !restrictions.isRequiresDistractionOptimization; updateSafety()
                    } }
                    uxSafe = ux?.currentCarUxRestrictions?.isRequiresDistractionOptimization == false
                } catch (e: Exception) { log("주행 제한 확인 실패: ${e.message}") }
                else ux = null
                updateSafety()
            } }
        } catch (e: Exception) { log("차량 연결 실패: ${e.message}") }
        handler.post(poll)
        handler.postDelayed({ probe() }, 1500)
    }
    fun probe() {
        if (closed || probing) return
        probing = true
        val light = vehicle.getLight()
        try {
            light.checkReadingLightSwitchCapabilities({ config, map -> main {
                log("스위치 ${config.accessType} / ${map.keys.joinToString { it.values.joinToString() }}")
                areas.forEachIndexed { i, area ->
                    if (map.containsKey(area) && config.accessType in listOf(AccessType.READ, AccessType.READ_WRITE))
                        light.getReadingLightSwitch(area, { value -> main { switches[i] = value.toString() } }, { e -> main { switches[i] = "오류 ${e.message}" } })
                    else switches[i] = "미지원"
                }
            } }, { e -> main { log("스위치 조회 실패: ${e.message}") } })
            light.checkReadingLightStateCapabilities({ config, map -> main {
                probing = false
                log("점등 ${config.accessType} / ${map.keys.joinToString { it.values.joinToString() }}")
                areas.forEachIndexed { i, area ->
                    supported[i] = map.containsKey(area) && config.accessType in listOf(AccessType.READ, AccessType.READ_WRITE)
                    if (!supported[i]) {
                        verified[i] = false; changed[i] = false; edges[i].reset(); states[i] = "미지원"
                        listeners.remove(i)?.let { light.unregisterReadingLightState(area, it) }
                    }
                    if (supported[i] && !listeners.containsKey(i)) {
                        val listener = object : LightStateListener {
                            override fun onLightStateUpdated(state: LightState?) {
                                val value = state
                                val received = SystemClock.elapsedRealtime()
                                main {
                                    if (listeners[i] !== this) return@main
                                    if (value == null) { edges[i].reset(); verified[i] = false; changed[i] = false; states[i] = "미확인"; return@main }
                                    states[i] = value.toString()
                                    val old = edges[i].previous
                                    if (!edges[i].accept(value)) return@main
                                    val interval = if (lastEvent[i] == 0L) 0 else received - lastEvent[i]
                                    lastEvent[i] = received; changed[i] = true
                                    log("${values[i]} $old → $value · 수신 $received ms · 간격 $interval ms")
                                    if (supported[i] && parked) onReadingLightChanged(i, value, received)
                                }
                            }
                            override fun onFailed(e: Exception) = main {
                                if (listeners[i] !== this) return@main
                                invalidateLight(i)
                                states[i] = "오류"; log("${values[i]} ${e.message}")
                            }
                        }
                        listeners[i] = listener
                        light.registerReadingLightState(area, listener)
                    }
                }
            } }, { e -> main { probing = false; areas.indices.forEach { invalidateLight(it) }; log("점등 조회 실패: ${e.message}") } })
        } catch (e: Exception) { probing = false; areas.indices.forEach { invalidateLight(it) }; log("SDK 오류: ${e.message}") }
    }
    fun close() {
        closed = true; handler.removeCallbacksAndMessages(null)
        listeners.forEach { (i, listener) -> runCatching { vehicle.getLight().unregisterReadingLightState(areas[i], listener) } }
        runCatching { ux?.unregisterListener() }; runCatching { car?.disconnect() }; vehicle.release()
    }
}
