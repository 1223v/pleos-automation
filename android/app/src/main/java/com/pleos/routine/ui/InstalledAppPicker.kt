package com.pleos.routine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pleos.routine.data.InstalledApp
import com.pleos.routine.data.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Selection only: discovery and review never launch another app. */
@Composable fun InstalledAppPicker(selected:String?,onSelect:((InstalledApp)->Unit)?=null) {
    val context=LocalContext.current.applicationContext
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(refresh) {
        loaded=false;error=""
        val result=withContext(Dispatchers.IO) { runCatching { InstalledApps.discover(context) } }
        apps=result.getOrDefault(emptyList())
        if(result.isFailure)error="설치된 앱을 조회하지 못했습니다. 다시 조회해 주세요."
        loaded=true
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("이 기기의 실행 가능한 앱",style=MaterialTheme.typography.titleMedium)
        Text("앱 열기만 연결됩니다. 앱 내부 재생·검색·계정 권한은 별도 지원이 필요합니다.")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query,{query=it},Modifier.weight(1f),label={Text("설치된 앱 검색")})
            TextButton(onClick={refresh++},enabled=loaded) { Text("다시 조회") }
        }
        if(!loaded)Text("설치된 앱을 조회하는 중…")
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        if(loaded && error.isBlank() && apps.isEmpty())Text("이 사용자에서 실행 가능한 앱을 찾지 못했습니다.")
        if(selected!=null && loaded && apps.none { it.packageName==selected })Text("선택했던 앱을 현재 목록에서 찾지 못했습니다: $selected",color=MaterialTheme.colorScheme.error)
        val filtered=apps.filter { it.label.contains(query,true) || it.packageName.contains(query,true) }
        if(loaded && apps.isNotEmpty() && filtered.isEmpty())Text("검색 결과가 없습니다.")
        Column(Modifier.fillMaxWidth().heightIn(max=320.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            filtered.forEach { app ->
                if(onSelect!=null)OutlinedButton(onClick={onSelect(app)},modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) { Text((if(app.packageName==selected)"선택됨 · " else "")+app.label);Text(app.packageName,style=MaterialTheme.typography.labelSmall) }
                } else Card { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(app.label);Text(app.packageName,style=MaterialTheme.typography.labelSmall);Text("앱 열기 대상 선택 가능",color=MaterialTheme.colorScheme.secondary) } }
            }
        }
    }
}
