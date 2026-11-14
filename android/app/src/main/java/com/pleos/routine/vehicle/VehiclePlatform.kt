package com.pleos.routine.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.VehicleGear
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.*
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.PleosPermission
import com.pleos.routine.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Capability(val type:ActionType,val access:String,val areas:List<String>,val ranges:Map<String,Pair<Double,Double>> = emptyMap(),val message:String="",val seatAxis:SeatAxis?=null)
/** Values below are query observations, never Virtual Console cruise targets. */
class VehiclePlatform(private val context:Context) {
    val automotive=context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var car:Car?=null
    private var properties:CarPropertyManager?=null
    private var restrictions:CarUxRestrictionsManager?=null
    private var sdk:Vehicle?=null
    private val mutable=MutableStateFlow(ExecutionContext(System.currentTimeMillis(),SystemClock.elapsedRealtime()))
    val state=mutable.asStateFlow()
    val capabilities=MutableStateFlow<List<Capability>>(emptyList())
    val connection=MutableStateFlow("차량 연결 확인 중")
    @Volatile private var moving:Pair<SeatArea,SeatAxis>?=null
    private val generation=AtomicLong()
    internal fun connectionGeneration():Long=generation.get()
    private val motion=SeatMotionCoordinator(SystemClock::elapsedRealtime)
    private val stopMutex=Mutex()
    private var invalidMotionJournal=false
    private var temperatureSteps:HvacTemperatureConfigArray?=null
    private val motionJournal=context.getSharedPreferences("motion_journal",Context.MODE_PRIVATE)
    // Must be backed by target-AVD movement/STOP evidence; no user preference can bypass this gate.
    internal var seatPolicy:SeatPolicy?=null
    data class SeatPolicy(val limits:Map<Pair<String,SeatAxis>,SeatMotionPolicy>)
    fun seatApprovalPlan(actions:List<ActionSpec>):String? {
        val policy=seatPolicy ?: return null
        return actions.map { a ->
            val limit=policy.limits[a.area to a.axis] ?: return null
            if(a.direction !in limit.directions || limit.evidenceId.isBlank() || limit.min>=limit.max ||
                limit.windowMillis !in 1..limit.maxMoveMillis || limit.maxMoveMillis !in 1..1000 || limit.windowMillis+limit.sampleMillis>limit.maxMoveMillis)return null
            "${a.area} · ${a.axis} · ${a.direction} · 최대 ${limit.maxMoveMillis}ms · 원시 위치 ${limit.min}~${limit.max} (거리/각도 아님)"
        }.joinToString("\n")
    }
    /** Includes calibration evidence and sampling policy even when the displayed limit is unchanged. */
    internal fun seatApprovalKey(actions:List<ActionSpec>):String? {
        val policy=seatPolicy ?: return null
        if(seatApprovalPlan(actions)==null)return null
        return actions.joinToString("\n") { a ->
            val limit=policy.limits.getValue(a.area to a.axis!!)
            "$a:${limit.copy(directions=limit.directions.sortedBy { it.name }.toSet())}"
        }
    }
    fun start() {
        if(!automotive) { connection.value="차량 밖 편집 모드 · 실제 차량 실행 불가";return }
        try {
            sdk=Vehicle(context).also { it.initialize() }
            val interruptedArea=motionJournal.getString("area",null)
            val interruptedAxis=motionJournal.getString("axis",null)
            if(interruptedArea!=null || interruptedAxis!=null) {
                moving=runCatching { area(requireNotNull(interruptedArea)) to SeatAxis.valueOf(requireNotNull(interruptedAxis)) }.getOrNull()
                invalidMotionJournal=moving==null
                if(!invalidMotionJournal)scope.launch { delay(1500);stopSeat() }
            }
            car=Car.createCar(context,Handler(Looper.getMainLooper()),Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected,ready ->
                generation.incrementAndGet()
                if(ready) {
                    properties=connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                    restrictions=connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    restrictions?.registerListener { r -> mutable.value=mutable.value.copy(uxRestricted=r.isRequiresDistractionOptimization) }
                    connection.value="차량 서비스 연결됨 · 권한·지원 조회 필요"
                } else { properties=null;restrictions=null;mutable.value=ExecutionContext(System.currentTimeMillis(),SystemClock.elapsedRealtime());connection.value="차량 연결 끊김" }
            }
            scope.launch { while(isActive) { snapshot();delay(500) } }
            scope.launch { delay(1500);refreshCapabilities() }
        } catch(_:Exception) { connection.value="차량 서비스 연결 실패" }
    }
    suspend fun snapshot():ExecutionContext=withContext(Dispatchers.IO) {
        val observedGeneration=generation.get()
        fun <T,R> query(type:Class<T>,id:Int,continuous:Boolean=false,convert:(T)->R?):StateValue<R> = try {
            val property=properties?.getProperty(type,id,0)
            val now=SystemClock.elapsedRealtime()
            val value=property?.takeIf { it.status==CarPropertyValue.STATUS_AVAILABLE }?.value?.let(convert)
            val sourceTime=property?.timestamp
            StateValue(value,if(continuous)(sourceTime ?: 0)/1_000_000 else now,
                value!=null && observedGeneration==generation.get() && (!continuous || (sourceTime ?: 0)>0),InputSource.REAL,sourceTime)
        } catch(_:Exception) { StateValue() }
        // ONCHANGE gear timestamps can legitimately be old. Continuous speed must be fresh at source.
        val gearState=query(Int::class.javaObjectType,VehiclePropertyIds.GEAR_SELECTION) { when(it) { VehicleGear.GEAR_PARK->"P";VehicleGear.GEAR_REVERSE->"R";VehicleGear.GEAR_NEUTRAL->"N";VehicleGear.GEAR_DRIVE->"D";else->null } }
        val speedState=query(Float::class.javaObjectType,VehiclePropertyIds.PERF_VEHICLE_SPEED,true) { it.toDouble().takeIf(Double::isFinite) }
        val outsideState=query(Float::class.javaObjectType,VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE) { it.toDouble().takeIf(Double::isFinite) }
        val restricted=try { restrictions?.currentCarUxRestrictions?.isRequiresDistractionOptimization ?: true } catch(_:Exception) { true }
        // SDK 2.3.2 collapses an unavailable occupancy value into false in its implementation.
        // Keep UNKNOWN until a target-specific raw-property validity path has been verified.
        ExecutionContext(System.currentTimeMillis(),SystemClock.elapsedRealtime(),InputSource.REAL,gearState,speedState,outsideState,uxRestricted=restricted).also { mutable.value=it }
    }
    private fun areaName(a:SeatArea)=a.values.joinToString("+") { it.name }
    private fun area(name:String):SeatArea {
        require(name!="DRIVER") { "운전석 별칭 대신 확인한 좌석 영역을 선택하세요." }
        return SeatArea(*name.split('+').map { SeatValue.valueOf(it) }.toTypedArray())
    }
    private suspend fun <T> callback(block:((T)->Unit,(Exception)->Unit)->Unit):T=withTimeout(4000) { suspendCancellableCoroutine { c ->
        val expected=generation.get()
        try { block({ if(c.isActive) { if(generation.get()==expected)c.resume(it) else c.resumeWithException(IllegalStateException("Connection changed")) } },{ if(c.isActive)c.resumeWithException(it) }) } catch(e:Exception) { if(c.isActive)c.resumeWithException(e) }
    } }
    suspend fun refreshCapabilities() {
        val v=sdk ?: return
        val results=mutableListOf<Capability>()
        suspend fun bounded(type:ActionType,body:suspend()->Capability) { try { results+=body() } catch(e:CancellationException) { if(e !is TimeoutCancellationException)throw e;results+=Capability(type,"UNKNOWN",emptyList(),message="조회 시간 초과") } catch(_:Exception) { results+=Capability(type,"UNKNOWN",emptyList(),message="권한 또는 지원 조회 실패") } }
        fun <T:Number> cap(t:ActionType,c:CapabilityConfig,m:Map<SeatArea,AreaBounds<T>>) = Capability(t,c.accessType.name,m.keys.map(::areaName),m.mapKeys { areaName(it.key) }.mapValues { it.value.minValue.toDouble() to it.value.maxValue.toDouble() })
        temperatureSteps=null
        bounded(ActionType.SET_HVAC_TARGET) { callback { ok,fail -> v.getHVAC().checkHvacTemperatureCapabilities({ c:CapabilityConfig,m:Map<SeatArea,AreaBounds<Float>>,steps:HvacTemperatureConfigArray? -> temperatureSteps=steps;ok(cap(ActionType.SET_HVAC_TARGET,c,m).copy(message=steps?.let { "설정 간격 ${it.incrementCelsius}°C" } ?: "설정 간격 미제공")) },fail) } }
        bounded(ActionType.SET_AMBIENT_BRIGHTNESS) { callback { ok,fail -> v.getLight().checkAmbientLightBrightnessCapabilities({ c,m -> ok(cap(ActionType.SET_AMBIENT_BRIGHTNESS,c,m)) },fail) } }
        bounded(ActionType.SET_AMBIENT_COLOR) { callback { ok,fail -> v.getLight().checkAmbientLightBrightnessColorCapabilities({ c,m -> ok(Capability(ActionType.SET_AMBIENT_COLOR,c.accessType.name,m.map(::areaName))) },fail) } }
        bounded(ActionType.SET_SEAT_HEAT) { callback { ok,fail -> v.getSeat().checkSeatTemperatureCapabilities({ c,m -> ok(cap(ActionType.SET_SEAT_HEAT,c,m)) },fail) } }
        for (axis in SeatAxis.entries) {
            bounded(ActionType.MOVE_SEAT_LIMITED) {
                callback { ok,fail ->
                    val done:(CapabilityConfig,List<SeatArea>)->Unit = { config,areas ->
                        val label=if(axis==SeatAxis.FORE_AFT)"앞뒤" else "등받이"
                        ok(Capability(ActionType.MOVE_SEAT_LIMITED,config.accessType.name,areas.map(::areaName),
                            message="$label 지원 영역 · 이동·STOP 실증 전 실행 잠금",seatAxis=axis))
                    }
                    if(axis==SeatAxis.FORE_AFT)v.getSeat().checkMoveSeatForeAftCapabilities(done,fail)
                    else v.getSeat().checkMoveSeatBackrestAngleCapabilities(done,fail)
                }
            }
        }
        capabilities.value=results
    }
    suspend fun preflight(a:ActionSpec,x:ExecutionContext):ActionResult? {
        if(a.type !in ActionResources.parkedTypes)return null
        fun blocked(s:String)=ActionResult(ActionStatus.BLOCKED,s)
        if(x.source!=InputSource.REAL || !x.parkedAndStationary())return blocked("실제 주차·정차 및 UX 상태가 확인되지 않았습니다.")
        val permission=when(a.type) {
            ActionType.SET_HVAC_TARGET -> PleosPermission.CONTROL_CAR_CLIMATE
            ActionType.SET_AMBIENT_BRIGHTNESS,ActionType.SET_AMBIENT_COLOR -> PleosPermission.CONTROL_CAR_LIGHTS
            else -> PleosPermission.CONTROL_CAR_SEATS
        }
        if(context.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED)return blocked("차량 제어 권한이 없습니다: $permission")
        if(a.type==ActionType.MOVE_SEAT_LIMITED && seatPolicy==null)return blocked("이 AVD의 제한 이동·STOP 관찰 시험이 완료되지 않아 시트 이동이 잠겨 있습니다.")
        if(a.type==ActionType.MOVE_SEAT_LIMITED && invalidMotionJournal)return blocked("중단된 시트 명령 기록을 해석할 수 없습니다. 실제 정지 상태와 복구 기록을 점검해야 합니다.")
        if(a.type==ActionType.MOVE_SEAT_LIMITED && moving!=null)return blocked("이전 시트 명령의 정지가 확인되지 않았습니다.")
        if(a.type==ActionType.MOVE_SEAT_LIMITED)return null // Selected axis and position bounds are checked just before MOVE.
        refreshCapabilities()
        val c=capabilities.value.find { it.type==a.type } ?: return blocked("기능 지원 여부를 확인하지 못했습니다.")
        if(c.access!="READ_WRITE" || a.area !in c.areas)return blocked("${a.type}: 읽기·쓰기 권한과 ${a.area} 영역을 확인하세요. (${c.access})")
        c.ranges[a.area]?.let { range -> val value=a.value;if(value==null || value !in range.first..range.second)return blocked("허용 범위 ${range.first}–${range.second} 밖의 값입니다.") }
        if(a.type==ActionType.SET_HVAC_TARGET)temperatureSteps?.let { steps ->
            val value=a.value ?: return blocked("목표 온도가 필요합니다.")
            val increment=steps.incrementCelsius.toDouble()
            if(!increment.isFinite() || increment<=0 || value !in steps.minCelsius.toDouble()..steps.maxCelsius.toDouble())return blocked("차량이 반환한 온도 설정 범위를 확인하세요.")
            val index=(value-steps.minCelsius)/increment
            if(kotlin.math.abs(index-kotlin.math.round(index))>0.0001)return blocked("차량의 온도 설정 간격 ${steps.incrementCelsius}°C에 맞춰 주세요.")
        }
        return null
    }
    internal suspend fun observedValue(a:ActionSpec):Double? {
        val v=sdk ?: return null;val s=area(a.area)
        return when(a.type) {
            ActionType.SET_HVAC_TARGET -> callback<Float?> { ok,fail -> v.getHVAC().getHvacTemperature(s,TemperatureUnit.CELSIUS,ok,fail) }?.toDouble()
            ActionType.SET_AMBIENT_BRIGHTNESS -> callback<Int?> { ok,fail -> v.getLight().getAmbientLightBrightness(s,ok,fail) }?.toDouble()
            ActionType.SET_AMBIENT_COLOR -> callback<Int?> { ok,fail -> v.getLight().getAmbientLightBrightnessColor(s,ok,fail) }?.let { raw ->
                // API alpha is fixed FF. Connect v2.1.0 returns RGB24 (observed 0xDB854B).
                // Normalize omitted alpha only; unexpected partial alpha stays unmatched.
                (if(raw ushr 24==0)raw or 0xFF000000.toInt() else raw).toDouble()
            }
            ActionType.SET_SEAT_HEAT -> callback<Int?> { ok,fail -> v.getSeat().getHvacSeatTemperature(s,ok,fail) }?.toDouble()
            else -> null
        }
    }
    suspend fun execute(a:ActionSpec,beforeDispatch:suspend()->ActionResult?={null},progress:suspend (ActionProgress)->Unit):ActionResult {
        preflight(a,snapshot())?.let { return it }
        val v=sdk ?: return ActionResult(ActionStatus.BLOCKED,"차량 서비스 미연결")
        if(a.type==ActionType.MOVE_SEAT_LIMITED)return move(a,progress,beforeDispatch)
        val s=area(a.area)
        val target=if(a.type==ActionType.SET_AMBIENT_COLOR) android.graphics.Color.parseColor(a.text!!).toDouble() else a.value!!
        try {
            // Capability describes support, not current availability (HVAC may be powered off).
            val before=try { observedValue(a) } catch(_:TimeoutCancellationException) { null } catch(e:CancellationException) { throw e } catch(_:Exception) { null }
            if(before==null)return ActionResult(ActionStatus.BLOCKED,if(a.type==ActionType.SET_HVAC_TARGET)"현재 공조 목표 온도를 읽을 수 없습니다. 차량의 공조 전원·연결을 확인하세요." else "현재 차량 상태를 읽을 수 없어 변경을 보류했습니다.")
            if(!snapshot().parkedAndStationary())return ActionResult(ActionStatus.BLOCKED,"전송 직전 주차·정차 상태가 바뀌었습니다.")
            beforeDispatch()?.let { return it }
            callback<Unit> { ok,fail ->
                when(a.type) {
                    ActionType.SET_HVAC_TARGET -> v.getHVAC().setHvacTemperature(s,target.toFloat(),TemperatureUnit.CELSIUS,{ actual,_ -> if(actual==s)ok(Unit) else fail(IllegalStateException()) },fail)
                    ActionType.SET_AMBIENT_BRIGHTNESS -> v.getLight().setAmbientLightBrightness(s,target.toInt(),{ actual,_ -> if(actual==s)ok(Unit) else fail(IllegalStateException()) },fail)
                    ActionType.SET_AMBIENT_COLOR -> v.getLight().setAmbientLightBrightnessColor(s,target.toInt(),{ actual,_ -> if(actual==s)ok(Unit) else fail(IllegalStateException()) },fail)
                    ActionType.SET_SEAT_HEAT -> v.getSeat().setHvacSeatTemperature(s,target.toInt(),{ actual,_ -> if(actual==s)ok(Unit) else fail(IllegalStateException()) },fail)
                    else -> fail(IllegalArgumentException())
                }
            }
            progress(ActionProgress(ActionStatus.ACKNOWLEDGED,"요청 응답 수신 · 상태 재조회 중"))
            repeat(8) { if(!snapshot().parkedAndStationary())return ActionResult(ActionStatus.UNKNOWN,"요청 후 주차 상태 상실 · 결과 확인 중단");val value=observedValue(a);if(value==target)return ActionResult(ActionStatus.VERIFIED,"목표 상태 반영 확인",value.toString());delay(250) }
            return ActionResult(ActionStatus.UNKNOWN,"응답은 수신했지만 목표 상태 반영을 확인하지 못했습니다.")
        } catch(_:TimeoutCancellationException) {
            return ActionResult(ActionStatus.UNKNOWN,"차량 응답 시간이 초과되었습니다. 적용 결과 불명 · 다시 실행하기 전 상태를 확인하세요.")
        } catch(e:CancellationException) { throw e } catch(e:Exception) {
            if(com.pleos.routine.BuildConfig.DEBUG)android.util.Log.w("RoutineEvidence","WRITE ${a.type} error=${e.javaClass.simpleName}")
            return ActionResult(ActionStatus.UNKNOWN,"차량 요청 또는 상태 조회 실패 · SDK의 처리 결과를 확인할 수 없습니다.")
        }
    }
    private fun dispatch(s:SeatArea,axis:SeatAxis,direction:SeatCommand):Deferred<MotionAck> {
        val done=CompletableDeferred<MotionAck>();val expected=generation.get()
        val control=when(direction) { SeatCommand.FORWARD->MoveControl.FORWARD;SeatCommand.BACKWARD->MoveControl.BACKWARD;SeatCommand.STOP->MoveControl.STOP }
        val success:(SeatArea,MoveControl?)->Unit={ actual,value -> done.complete(MotionAck(actual==s && value==control && generation.get()==expected,SystemClock.elapsedRealtime(),generation.get())) }
        val failure:(Exception)->Unit={ done.complete(MotionAck(false,SystemClock.elapsedRealtime(),generation.get())) }
        try {
            val seat=requireNotNull(sdk).getSeat()
            if(axis==SeatAxis.FORE_AFT)seat.moveSeatForeAft(s,control,success,failure) else seat.moveSeatBackrestAngle(s,control,success,failure)
        } catch(e:Exception) { failure(e) }
        return done
    }
    private suspend fun position(s:SeatArea,axis:SeatAxis)=callback<Int?> { ok,fail ->
        if(axis==SeatAxis.FORE_AFT)sdk!!.getSeat().getCurrentSeatForeAftPosition(s,ok,fail) else sdk!!.getSeat().getCurrentSeatBackrestAnglePosition(s,ok,fail)
    }
    private suspend fun move(a:ActionSpec,progress:suspend (ActionProgress)->Unit,beforeDispatch:suspend()->ActionResult?):ActionResult {
        val policy=seatPolicy?.limits?.get(a.area to a.axis) ?: return ActionResult(ActionStatus.BLOCKED,"시트 교정 증거 없음")
        val s=area(a.area);val axis=a.axis!!
        val access=callback<Boolean> { ok,fail ->
            val done:(CapabilityConfig,List<SeatArea>)->Unit={ c,areas -> ok(c.accessType==AccessType.READ_WRITE && s in areas) }
            if(axis==SeatAxis.FORE_AFT)sdk!!.getSeat().checkMoveSeatForeAftCapabilities(done,fail) else sdk!!.getSeat().checkMoveSeatBackrestAngleCapabilities(done,fail)
        }
        val bounds=callback<AreaBounds<Int>?> { ok,fail ->
            val done:(CapabilityConfig,Map<SeatArea,AreaBounds<Int>>)->Unit={ c,areas -> ok(areas[s].takeIf { c.accessType in setOf(AccessType.READ,AccessType.READ_WRITE) }) }
            if(axis==SeatAxis.FORE_AFT)sdk!!.getSeat().checkSeatForeAftPositionCapabilities(done,fail) else sdk!!.getSeat().checkSeatBackrestAnglePositionCapabilities(done,fail)
        }
        if(!access || bounds==null || policy.min<bounds.minValue || policy.max>bounds.maxValue)
            return ActionResult(ActionStatus.BLOCKED,"선택한 축의 권한·조회 범위·교정 범위 불일치")
        return motion.move(object:SeatMotionPort {
            override fun generation()=this@VehiclePlatform.generation.get()
            override fun pending()=moving!=null || invalidMotionJournal
            override suspend fun persist()=withContext(Dispatchers.IO) {
                motionJournal.edit().putString("area",a.area).putString("axis",axis.name).commit().also { if(it)moving=s to axis }
            }
            override suspend fun clear()=withContext(Dispatchers.IO) { motionJournal.edit().clear().commit().also { if(it)moving=null } }
            override suspend fun safe()=snapshot().parkedAndStationary() && beforeDispatch()==null
            override suspend fun position()=this@VehiclePlatform.position(s,axis)
            override fun dispatch(command:SeatCommand)=this@VehiclePlatform.dispatch(s,axis,command)
        },policy,a.direction!!,progress)
    }
    /** STOP deliberately bypasses parked, permission-cache and regular movement gates. */
    suspend fun stopSeat():ActionResult? {
        motion.stop()?.let { return it }
        return stopMutex.withLock {
            if(invalidMotionJournal)return@withLock ActionResult(ActionStatus.UNKNOWN,"시트 복구 기록 불명 · 정지 확인 필요")
            val previous=moving ?: return@withLock null
            try {
                withTimeout(4000) { dispatch(previous.first,previous.second,SeatCommand.STOP).await() }
                // Recovery has no proof that a previous MOVE cannot still be queued in the SDK.
                // Even stable samples cannot authorize clearing this journal or sending another MOVE.
                ActionResult(ActionStatus.UNKNOWN,"복구 STOP 요청 · 이전 MOVE 순서 불명, 기록 유지 및 추가 이동 차단")
            } catch(_:Exception) { ActionResult(ActionStatus.UNKNOWN,"복구 STOP 전달 실패 · 기록 유지") }
        }
    }
}
