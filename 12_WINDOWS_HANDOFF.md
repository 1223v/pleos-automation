# Windows 개발 인수인계

## 2026-11-02 A05

11월3일 재개 확인: A05.1~4 로컬 구현·검증 완료, 다음 구현은 A06이다. 소스126개/APK3개는11월2일 검증본과 해시가 같다. 전체 AVD26개(표적7개 포함), 별도 프로세스 복구1개 통과를 기록했으므로 기존 A05 재현·구현·회귀를 처음부터 반복하지 않는다. 새 변경 또는 실패가 생기면 영향 범위에 맞게 재시험한다.

현재 Room3이며 기존 DB를 삭제하지 말고1→2→3 migration을 사용한다. A05 표적 시험은 `RunHistoryUiTest`, `RunExplanationUiTest`, `JournalMigrationTest`다. 로컬 설명 HTTP 시험만 `-e a05Fixture true`와18787 reverse/fixture가 필요하다. [fixture](evidence/A05/local-explanation-fixture.mjs)는 loopback·모의 provider·외부 fetch 차단을 사용한다. 먼저 server에서 빌드한 뒤 프로젝트 Node로 실행하며 실제 API 키는 넣지 않는다. 시험 후 직접 시작한 fixture 프로세스와18787 reverse만 정리한다. [실제 명령·결과](evidence/A05/verification-2026-11-02.md)를 따른다.

## 2026-11-01 재개 인수인계

[현재 장부](evidence/plan-compliance-2026-10-30/verification-2026-11-01.md), [TODO](13_IMPLEMENTATION_TODO.md)를 우선한다. local도구 버전은 아래10월30일과 동일하다. 새 APK3개/소스115개 해시, core98/app56/server38 단위·모의 시험, debug/test/unsigned release와 offline lint 결과를 기록했다. AVD21PASS/알림권한거부1PASS이며 GNSS stopNmea의 system_server watchdog 최초 중단을 보존했다. 재개 시 장치·user10·진행 중 시험을 확인하고 다른 계측과 동시에 실행하지 않는다. 알림 거부 시험은 원래 권한을 확인한 뒤 복원하며 사용자 데이터를 지우지 않는다. 전체 제품 검증은 아직 미완료다.


## 2026-10-30 재개 기준

현재 브랜치는 `codex/consistency-tdd-security`이며 기존 미커밋·untracked 구현을 보존한 상태다. 전체를 일괄 add/reset하지 않는다. 최신 결과는 [10월29~30일 장부](evidence/implementation/2026-10-29.md), [보안 보고](security_best_practices_report.md)를 따른다. 아래10월28일 환경 버전은 과거 증거다.

프로젝트 로컬 Node24.22.1, Corretto17.0.21.0.11, SHA-256 고정 Gradle8.14.6으로 검증했다. 전역 JDK/Node는 바꾸지 않았으므로 [README](README.md)의 `JAVA_HOME`, `Path`, `GRADLE_USER_HOME` 설정을 해당 PowerShell 프로세스에 적용한다. SDK·AVD·serial/user는 아래 기존 경로를 사용하되 매 실행마다 실제 부팅/사용자를 확인한다.

차량 쓰기 probe 전 `adb -s emulator-5556 forward --list`와 실제 Console 탭을 확인한다.8088 forward가 없으면 다른 프로세스의 포트 사용이 없는지 확인한 뒤 `adb -s emulator-5556 forward tcp:8088 tcp:8088`를 설정하고 브라우저에서 Console을 연다.10월30일에는 이 준비 전1PASS/4FAIL, 복구 후 동일 APK5PASS였다. DDS ONLINE만으로 성공을 판정하지 않고 요청 뒤 SDK 상태와 Console 표시를 함께 확인한다. 내장 Console 탭은 최종 상태 확인용으로 유지했다.

Windows에서 Kotlin daemon이 이전 classes.jar를 잡았던 사례가 있어 `--no-daemon '-Pkotlin.compiler.execution.strategy=in-process' --max-workers=2`로 시험한다. 동시에 두 Gradle 빌드를 실행하지 않는다. 새 의존성/Gradle 최초 다운로드 이후에는 `--offline`으로 재현할 수 있다. 경고0건이라고 주장하지 않는다.

계측 전 debug/test APK를 각각 `adb -s emulator-5556 install -r`로 갱신한다. 사용자10의 `am instrument --user 10 -w -r -e class ...`를 사용하고, 실제 차량 쓰기·시트 probe·장시간 시험은 해당 명시적 인자와 선행 조건이 필요하다. 프로세스 복구는 `python scripts/run_process_recovery.py --evidence-dir evidence/audit-2026-10-29/app`로 별도 증거 경로를 지정한다. READY 후 시험 앱만 중단하며, 사용자 초안/활성 루틴이 있으면 시험을 건너뛴다.

## 2026-10-28 실제 환경으로 재개

작업 폴더 `C:\pleos-automation`, SDK `.tools/android-sdk`, AVD `.tools/avd/PleosRoutine.avd`, 장치 `emulator-5556`, 현재 사용자10이다. JDK17/Gradle8.10.2, Node24.12.0, Android14/Pleos v2.1.0/Vehicle Service2.3.22를 실제 확인했다. [README 실행](README.md)과 [증거 기록](evidence/implementation/2026-10-28.md)이 아래 초기 명령 예시보다 우선한다.

PowerShell 프로세스에서 `GRADLE_USER_HOME=C:\pleos-automation\.tools\gradle-home`을 설정한다. 한글 사용자 경로의 JDK17 worker argfile 문제를 피한다. 기존 HOME/CODEX_HOME은 변경하지 않는다. AVD는 `ANDROID_AVD_HOME=C:\pleos-automation\.tools\avd`로 실행하며 `avdmanager`가 사용자 기본 폴더에 생성한 ini를 작업 AVD 폴더로 복사했다. 공식 zip 내부 태그는 android-automotive다.

앱 실행은 AVD 홈 초기화 후 MAIN/LAUNCHER Intent 또는 런처 아이콘을 사용한다. UI·SDK 설치·빌드·모의 시험을 이미 수행했으므로 아래 ‘문서만/명령 미실행/Git 없음’은 **초기 이력**이다. 현재 Git 변경을 확인하고 다른 작업을 되돌리지 않는다. SDK/AVD/키/캐시를 인수인계 저장소에 포함하지 않는다.

현재 debug·unsigned release 빌드는 통과했고, 공조 목표 온도·조명 밝기·색상은 실제 상태 변경을 관찰했다. 시트 이동·STOP 때문에 전체 [제어 게이트](evidence/W05/control-gate.md) 미통과다. `am instrument`의 `-e` 인자는 runner component 앞에 두며, 별도 명시 인자가 없는 vehicle write/seat/30분/복구 probe는 skipped다. skipped를 PASS로 세지 않는다. 실행 중인 장시간 시험이 있으면 APK 재설치·앱 강제 종료로 중단하지 않는다.

실제30분 시험은 이미 통과했으며 [별도 APK 해시](evidence/W14/long-timer-apk-hashes.json)와 [측정값](evidence/W14/long-timer-persisted-result.txt)을 확인한다. 프로세스 복구 시험은 test APK 설치 뒤 `python scripts/run_process_recovery.py`로 READY→해당 앱 force-stop→다른 PID의 검증을 동기화한다. 이 명령은 현재 사용자10의 공식 Pleos AVD에서 실행 중인 시험용 루틴을 의도적으로 중단한다. 사용자 초안과 ID가 다른 시험 fixture를 삭제하지 않는다.

`python scripts/configure_local_avd.py`는 작업 폴더의 정확한 AVD 설정만 출력하며 `--apply`로 Cold boot를 저장한다. 이미 실행 중인 AVD는 바꾸지 않는다. 재시작에는 `-no-snapshot`도 유지한다. 검증 후 `--apply --gpu host`로 hw.gpu.enabled=yes / mode=host도 저장했다. 실행 예시는 `-avd PleosRoutine -no-window -no-audio -no-snapshot -gpu host -port 5556`이다. GPU 로그의 external memory import 오류가 있었으나 후속 실제 앱 화면은 표시됐다. 최신 비교 결과는 검증 장부를 따른다. Console의 HTTP 접근 성공이나 DDS ready만으로 SDK 목표 온도·시트 상태 반영을 통과 처리하지 않는다.

Console은 현재 내장 브라우저 탭에서 직접 조작할 수 있다. Windows Chrome의 URL 식별 오류를 내장 브라우저 전체의 불가로 확대하지 않는다. [주행 입력 검증](evidence/W04/console-driving-verification.md)에서 D/ACCEL→실제 AAOS 속도/UX 제한→BRAKE/정지/P를 확인했다. 이 이미지의 `cmd car_service` 시험 명령은 non-user build 제약으로 거부돼 사용하지 않는다. Console의 Cruise Target Speed를 실제 속도로 대체하지 않는다.

확인일: **2026-10-25 KST**. 이 문서는 후속 개발 절차다. **이번 작업에서는 Windows PC의 설치 상태를 확인하지 않았으며 아래 PowerShell·Gradle·ADB 명령, APK 설치, 에뮬레이터 시험도 실행하지 않았다. PowerShell 런타임도 없어 파서 기반 구문 검증은 미실행이며 문서의 명령은 정적 검토만 수행했다.** 현재는 문서만 있고 앱·서버·Git 저장소는 없다. 개발 실행이 요청되면 [구현 TODO](13_IMPLEMENTATION_TODO.md)의 환경·제어 검증 작업부터 진행한다.

## 1. 전달할 파일과 작업 위치

폴더 전체의 Markdown과 `references/`를 Windows 로컬 작업 폴더(예: `C:\work\pleos`)로 복사한다. 상대 링크와 파일명을 유지하고 UTF-8로 저장한다. Markdown은 LF를 유지해 불필요한 변경을 줄이며, 향후 `.bat` 파일은 생성 도구의 줄바꿈을 유지한다. 현재 파일 전체가 들어왔는지 목록과 해시를 비교하고 README의 문서 링크를 연다.

현재 전달물은 `AGENTS.md`, `README.md`, `01_*.md`~`13_*.md`, `references/APPLICATION_FORM_NOTES.md`, 참고 DOCX다. 아래 디렉터리는 **후속 개발에서 생성할 구조**이며 현재 존재하는 경로가 아니다.

```text
pleos/                      # 문서 루트, Windows 예: C:\work\pleos
  android/                  # 향후 제품 Android Gradle 프로젝트
  server/                   # 향후 AI 초안 생성 서버, 공급자 선정 뒤 생성
  evidence/                 # 향후 환경·시험·APK 해시·비식별 로그
    W02/                    # 환경 증거
    W05/                    # 제어 스파이크 증거
    W21/                    # 최종 실행별 증거
  references/               # 현재 존재하는 참고 문서
```

공식 샘플은 제품 폴더와 섞지 않고 별도 폴더에 둔다(예: `C:\work\pleos-sample-vehicle-app`). 샘플 코드에는 출처·라이선스를 유지한다. 전달 대상에서 API 키, `.env`의 실제 값, 서명 keystore, 사용자별 `local.properties`, SDK·AVD 전체, Gradle 캐시를 제외한다. 현재 폴더에 Git이 없으므로 존재하지 않는 커밋 ID를 적지 않는다. 후속 저장소를 만들면 전달 기준 커밋과 작업 중 변경을 함께 기록한다.

## 2. 호스트와 버전 기준

기본 경로는 **Windows x64의 PowerShell → Windows Android Studio/SDK/Emulator**다. WSL은 필수 경로가 아니다. Windows ARM은 동일한 방법으로 지원된다고 가정하지 않는다. 설치 전 CPU 가상화, 메모리·디스크·GPU를 [Android Studio 요구사항(D37)](https://developer.android.com/studio/install)과 대조한다. Studio와 Emulator 동시 실행 최소 RAM은 16 GB, 권장 32 GB라는 현재 문서와 Pleos AVD RAM 4 GB 설정을 구분한다.

| 구분 | 초기 재현 기준 | 확인할 것 |
|---|---|---|
| JDK / Gradle Wrapper | 17 / 8.10.2 | 실제 Gradle 실행 JVM과 wrapper 파일 |
| AGP / Kotlin | 8.8.2 / 2.0.0 | Gradle 설정·version catalog |
| compileSdk / targetSdk | 35 / 35 | 빌드 도구 API이며 실행 AVD API와 별개 |
| 제품 minSdk | 28 이상 | 공식 제품 요구사항; 샘플의 23을 그대로 복사하지 않음 |
| Pleos AVD | 2.1.0 / Android API 34 / x86_64 | 설치 이미지·AVD 설정·실행 중 속성 |
| Vehicle SDK | 2.3.2 | 의존성 해석 결과와 런타임 표시 |
| Vehicle Service | 샘플 가이드 기준 2.3.22 | 실제 설치 버전 별도 기록; 소개 문서의 2.3.21과 구분 |

이 표는 최신 버전 추종 지시가 아니라 재현 시작점이다. [공식 샘플 가이드(D26)](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk-sample-app)와 [샘플 저장소(D34)](https://github.com/Pleos-Playground/pleos-sample-vehicle-app)의 실제 checkout 설정을 먼저 읽고 불일치하면 출처·원인·변경 여부를 기록한다. 업데이트와 제품 구조 생성은 후속 개발 범위다.

가상화는 BIOS/UEFI의 Intel VT-x 또는 AMD-V와 **Windows Hypervisor Platform(WHPX)** 가용성을 확인한다. 공식 가속 안내대로 기능 활성화 후 재부팅이 필요할 수 있다. `emulator -accel-check` 성공으로 확인한다. 호스트 보안 기능을 임의로 끄거나 오래된 HAXM 설치를 첫 해결책으로 삼지 않는다. [가속 설정(D38)](https://developer.android.com/studio/run/emulator-acceleration)

## 3. PowerShell 사전 점검

아래 블록은 순서대로 **같은 PowerShell 세션**에서 실행한다. 문서 루트에서 시작하고, 네이티브 도구 오류는 즉시 중단한다. 예시 값을 그대로 실행하지 못하도록 `REPLACE_` 검사를 둔다. 한 세션이 종료되면 경로·장치 변수를 다시 점검한다. 명령의 예상 결과는 성공 증거가 아니므로 실제 출력을 보관한다.

```powershell
# 실행 위치: 전달받은 pleos 문서 폴더
$ErrorActionPreference = 'Stop'
$PleosRoot = (Get-Location).Path
if (-not (Test-Path -LiteralPath (Join-Path $PleosRoot '13_IMPLEMENTATION_TODO.md'))) {
    throw '문서 루트로 이동하고 전달 파일을 확인하세요.'
}
function Invoke-PleosNative {
    param([string]$Tool, [string[]]$ToolArgs)
    & $Tool @ToolArgs
    if ($LASTEXITCODE -ne 0) { throw "명령 실패: $Tool (exit $LASTEXITCODE)" }
}
function Assert-PleosGradleBaseline {
    $PleosVersionLines = @(Invoke-PleosNative -Tool '.\gradlew.bat' -ToolArgs @('--version'))
    $PleosVersionLines
    $PleosVersionText = $PleosVersionLines -join "`n"
    if ($PleosVersionText -notmatch '(?m)^Gradle 8\.10\.2\s*$' -or
        $PleosVersionText -notmatch '(?m)^(?:Launcher JVM|JVM):\s*17(?:[.\s]|$)') {
        throw '초기 Gradle/JVM 기준과 다릅니다. 버전 차이를 해결하거나 근거를 남기고 기준을 갱신하세요.'
    }
}
Get-CimInstance Win32_OperatingSystem | Select-Object Caption, OSArchitecture
Get-CimInstance Win32_Processor | Select-Object Name, Architecture, VirtualizationFirmwareEnabled
Get-Command java.exe, adb.exe -All -ErrorAction SilentlyContinue |
    Select-Object Name, Source

# SDK Manager에 표시된 Android SDK Location과 반드시 일치시킨다.
$PleosSdk = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($PleosSdk)) {
    $PleosSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
if (-not (Test-Path -LiteralPath $PleosSdk -PathType Container)) {
    throw 'SDK Manager에서 실제 SDK 경로를 확인하고 $PleosSdk에 지정하세요.'
}
if ($env:ANDROID_SDK_ROOT -and
    ($env:ANDROID_SDK_ROOT.TrimEnd('\') -ne $PleosSdk.TrimEnd('\'))) {
    throw 'ANDROID_HOME과 ANDROID_SDK_ROOT 경로 충돌을 먼저 해결하세요.'
}
$PleosAdb = Join-Path $PleosSdk 'platform-tools\adb.exe'
$PleosEmulator = Join-Path $PleosSdk 'emulator\emulator.exe'
foreach ($PleosTool in @($PleosAdb, $PleosEmulator)) {
    if (-not (Test-Path -LiteralPath $PleosTool -PathType Leaf)) {
        throw "SDK 도구가 없습니다: $PleosTool"
    }
}
Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('version')
Invoke-PleosNative -Tool $PleosEmulator -ToolArgs @('-version')
Invoke-PleosNative -Tool $PleosEmulator -ToolArgs @('-accel-check')
```

예상: Windows x64와 설치 도구의 경로·버전, 사용 가능한 가속 상태. 실패: SDK Manager에서 플랫폼 도구/Emulator 설치 상태, 실제 SDK 위치, 가상화/WHPX 순서로 확인한다. `ANDROID_HOME`은 SDK 위치 변수이며 `ANDROID_SDK_ROOT`는 폐기 예정 변수이므로 둘이 남아 있으면 불일치를 제거한다. 이 문서는 `HOME`, `home`, `CODEX_HOME`을 수정하지 않는다. [환경변수(D39)](https://developer.android.com/tools/variables)

JDK 17의 **실제 설치 폴더**를 확인한 뒤 현재 세션의 Gradle용 Java를 지정한다. Android Studio 자체 실행 JDK와 Gradle JDK는 다를 수 있으므로 Studio의 Gradle JDK 설정도 같은 17로 맞추고 후속 `gradlew.bat --version`으로 최종 확인한다.

```powershell
# 실행 위치: 문서 루트. 이 한 경로를 실제 JDK 17 폴더로 바꾼다.
$PleosJdk17 = 'C:\REPLACE_WITH_ACTUAL_JDK17'
if ($PleosJdk17 -match 'REPLACE_' -or
    -not (Test-Path -LiteralPath (Join-Path $PleosJdk17 'bin\java.exe'))) {
    throw '설치된 JDK 17의 실제 경로를 지정하세요.'
}
$PleosJdkRelease = Join-Path $PleosJdk17 'release'
if (-not (Test-Path -LiteralPath $PleosJdkRelease) -or
    (Get-Content -LiteralPath $PleosJdkRelease -Raw) -notmatch '(?m)^JAVA_VERSION="17(?:[.+"]|$)') {
    throw '선택한 JDK의 release 메타데이터에서 Java 17을 확인하지 못했습니다.'
}
$env:JAVA_HOME = $PleosJdk17
Invoke-PleosNative -Tool (Join-Path $PleosJdk17 'bin\java.exe') -ToolArgs @('-version')
```

예상: Java 17 출력. 다른 버전이면 빌드로 넘어가지 않는다. 임의 JDK 다운로드나 자동 버전 업그레이드로 문제를 덮지 않는다.

## 4. Pleos 이미지와 AVD

후속 환경 구성에서 Android Studio SDK Manager의 **SDK Update Sites**에 아래 공식 Windows/Linux 저장소를 추가한다. **SDK Platforms → Show Package Details → API 34**에서 Pleos 이미지를 선택한다. Mac ARM 저장소를 Windows에 적용하지 않는다. [설치 안내(D05)](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)

```text
Name: Pleos Connect System Image
URL: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml
Package: system-images;android-34;pleos_car_emulator;x86_64
```

패키지 값은 [공식 Windows/Linux XML(D36)](https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml)을 2026-10-25 조회한 결과다. 실제 설치 때 XML과 설치 패키지의 버전·ABI를 다시 확인한다. 이미 있는 AVD를 삭제하면 데이터가 사라지므로 재현 가능한 새 AVD를 먼저 만들고 기존 데이터 처리 필요성을 구분한다.

Device Manager에서 Automotive → Pleos Connect v2.1.0, API 34 이미지를 선택한다. Advanced Settings에서 **Cold boot**, **RAM 4 GB**로 만들고 설정 화면을 증거로 남긴다. AVD 이름은 임의로 추정하지 않고 아래 목록에서 찾는다.

```powershell
# 실행 위치: 문서 루트, 3절의 도구 변수를 유지
Invoke-PleosNative -Tool $PleosEmulator -ToolArgs @('-list-avds')
$PleosAvdName = 'REPLACE_WITH_NAME_FROM_LIST'
$PleosAvds = @(Invoke-PleosNative -Tool $PleosEmulator -ToolArgs @('-list-avds'))
if ($PleosAvdName -match 'REPLACE_' -or $PleosAvdName -notin $PleosAvds) {
    throw '목록에서 확인한 Pleos AVD 이름을 지정하세요.'
}
```

Device Manager에서 그 AVD를 시작하고 부팅 완료를 기다린다. 다음 블록은 **온라인 emulator 중 AVD 이름이 일치하는 한 장치**만 선택한다. 둘 이상이거나 없으면 임의 장치에 설치하지 않는다.

```powershell
Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('devices', '-l')
$PleosDeviceLines = @(Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('devices'))
$PleosMatches = @()
foreach ($PleosLine in $PleosDeviceLines) {
    if ($PleosLine -match '^(emulator-\d+)\s+device$') {
        $PleosCandidate = $Matches[1]
        $PleosNames = @(Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('-s', $PleosCandidate, 'emu', 'avd', 'name'))
        if ($PleosNames -contains $PleosAvdName) { $PleosMatches += $PleosCandidate }
    }
}
if ($PleosMatches.Count -ne 1) { throw '해당 AVD가 정확히 한 개 실행 중인지 확인하세요.' }
$PleosSerial = $PleosMatches[0]
$PleosBoot = @(Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('-s', $PleosSerial, 'shell', 'getprop', 'sys.boot_completed'))
if ($PleosBoot -notcontains '1') { throw 'AVD 부팅이 아직 완료되지 않았습니다.' }
Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('-s', $PleosSerial, 'shell', 'getprop', 'ro.build.version.sdk')
Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('-s', $PleosSerial, 'shell', 'getprop', 'ro.product.cpu.abi')
```

예상: 선택한 AVD, API `34`, ABI `x86_64`. 이 값만으로 Pleos Service 사용 가능을 입증하지는 못한다. 이어서 샘플의 SDK/Service 버전과 기능별 capability를 확인한다.

## 5. 공식 샘플 → 제품 프로젝트

먼저 [공식 샘플(D34)](https://github.com/Pleos-Playground/pleos-sample-vehicle-app)을 별도 폴더에 확보하고 README·Gradle 설정·Manifest·Vehicle 호출과 콜백을 읽는다. 체크아웃의 커밋 SHA를 기록한다. 샘플의 기어·속도 지원을 가정하지 말고 [기능 표](05_CAPABILITY_MATRIX.md)의 AAOS 경로를 따른다. 본 문서의 샘플 빌드와 설치는 후속 작업이며 이번에 수행한 일이 아니다.

```powershell
# $PleosSampleRoot를 실제 공식 샘플 checkout 위치로 수정
$PleosSampleRoot = 'C:\REPLACE_WITH_SAMPLE_CHECKOUT'
if ($PleosSampleRoot -match 'REPLACE_' -or
    -not (Test-Path -LiteralPath (Join-Path $PleosSampleRoot 'gradlew.bat'))) {
    throw '공식 샘플 checkout과 wrapper를 먼저 확인하세요.'
}
Push-Location $PleosSampleRoot
try {
    Assert-PleosGradleBaseline
    Invoke-PleosNative -Tool '.\gradlew.bat' -ToolArgs @(':app:assembleDebug')
    $PleosSampleApk = Join-Path $PleosSampleRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $PleosSampleApk)) { throw '빌드 출력 APK 경로를 확인하세요.' }
    Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('-s', $PleosSerial, 'install', '-r', $PleosSampleApk)
} finally { Pop-Location }
```

예상: wrapper/JVM 기준 일치, `BUILD SUCCESSFUL`, ADB `Success`. Launcher에서 샘플을 열고 공조 목표 온도·앰비언트 밝기/색상·시트 앞뒤/등받이의 지원·영역·범위·읽기·쓰기·반영·STOP을 확인한다. **SET 응답 성공과 상태 반영은 별도 증거**다. 샘플로 읽을 수 없는 신호·확인 불가능한 시트 STOP은 통과가 아니라 후속 조사 항목이다. [샘플의 요청/조회 구분(D34)](https://github.com/Pleos-Playground/pleos-sample-vehicle-app)

제품 프로젝트를 **향후 `android/`에 생성한 뒤에만** 다음을 사용한다. 실제 모듈명·variant·시험 task를 읽어서 명령과 산출 경로를 함께 갱신한다. 초기 단일 `app` 모듈 기준 예시다. 계측 시험은 시험 코드와 runner가 구현된 후 실행하며, 시험이 없어서 `NO-SOURCE`가 된 task를 통과 증거로 세지 않는다.

```powershell
# 실행 위치: 문서 루트. 제품 소스가 없으면 즉시 중단한다.
$PleosAndroidRoot = Join-Path $PleosRoot 'android'
if (-not (Test-Path -LiteralPath (Join-Path $PleosAndroidRoot 'gradlew.bat'))) {
    throw '아직 제품 프로젝트가 없습니다. 승인된 TODO에 따라 먼저 구현하세요.'
}
Push-Location $PleosAndroidRoot
try {
    Assert-PleosGradleBaseline
    Invoke-PleosNative -Tool '.\gradlew.bat' -ToolArgs @(':app:lintDebug', ':app:testDebugUnitTest', ':app:assembleDebug')
    $PleosDebugApk = Join-Path $PleosAndroidRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $PleosDebugApk)) { throw '제품 APK가 생성되지 않았습니다.' }
    Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('-s', $PleosSerial, 'install', '-r', $PleosDebugApk)
} finally { Pop-Location }
```

예상: lint 보고서, 실행된 단위시험 수/결과, APK와 설치 성공. 실패하면 해당 task의 첫 원인을 고치고 관련 검사만 다시 실행한다. IDE sync 성공은 컴파일·시험 성공을 대체하지 않는다. 서버 실행 명령은 공급자·런타임 선정 후 `server/`의 실제 manifest와 공식 문서에서 도출해 추가한다. 존재하지 않는 `npm`/서버 task를 미리 만든 사실처럼 안내하지 않는다.

계측 시험은 runner·시험 코드가 있고 관련 단위시험을 통과한 후 수행한다. 아래 예시는 실수로 다른 연결 장치에 시험을 배포하지 않도록 **온라인 장치가 선택한 AVD 하나뿐일 때만** 실행한다. 다른 장치를 임의 종료·분리하지 말고 프로젝트가 지원하는 대상 선택 설정을 조사하거나 시험 세션을 따로 준비한다.

```powershell
# 실행 위치: 문서 루트, 3절 도구 함수·선택 장치 변수가 유지된 세션
$PleosOnline = @(Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('devices') |
    Where-Object { $_ -match '^\S+\s+device$' } |
    ForEach-Object { ($_ -split '\s+')[0] })
if ($PleosOnline.Count -ne 1 -or $PleosOnline[0] -ne $PleosSerial) {
    throw '연결 시험의 대상이 선택한 AVD 한 대인지 확인하세요.'
}
Push-Location $PleosAndroidRoot
try {
    Assert-PleosGradleBaseline
    Invoke-PleosNative -Tool '.\gradlew.bat' -ToolArgs @(':app:connectedDebugAndroidTest')
} finally { Pop-Location }
```

예상: 실제 실행된 계측 시험 수와 통과/실패 보고서. runner 누락·NO-SOURCE·0개 실행은 합격 증거가 아니다. AndroidTest APK와 제품 release APK의 최종 시연 검증도 구분한다. [공식 샘플 시험 명령](https://github.com/Pleos-Playground/pleos-sample-vehicle-app)

## 6. Virtual Console 및 시험 증거

```powershell
# 실행 위치: 문서 루트, 선택한 장치 변수가 유효한 같은 세션
if ([string]::IsNullOrWhiteSpace($PleosSerial)) { throw '먼저 AVD 장치를 선택하세요.' }
Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('-s', $PleosSerial, 'get-state')
Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('-s', $PleosSerial, 'forward', 'tcp:8088', 'tcp:8088')
Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('forward', '--list')
Start-Process 'http://127.0.0.1:8088/'
```

예상: 포워드 목록에 `$PleosSerial tcp:8088 tcp:8088`, 브라우저에 Console. 연결 안 되면 AVD·이미지 버전·ADB·포트 순서로 확인한다. `8088`은 Console 포트다. AI 서버용 포트로 중복 사용하지 않는다. [Console 셋업(D40)](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)

Console의 기어·공조 목표 온도·앰비언트 상태를 조작한 후 SDK/AAOS가 받은 값과 제품 화면·이력을 비교한다. **Cruise Target Speed는 실제 속도 입력이 아니다.** 실제 속도 입력 경로와 시트 움직임·정지 재현은 대상 AVD에서 따로 입증한다. Seat Occupancy 카드가 있다고 시트 이동을 재현할 수 있다고 해석하지 않는다. [Console 사용(D25)](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)

후속 시험에서 증거 폴더를 생성한다. 전체 로그는 먼저 로컬 보관하고 공유 전 비밀값·개인정보를 제거한다. 실행 전후 시각, 실제 선택 장치, 입력 경로, 권한·영역·값, 요청/응답/반영 결과를 함께 적는다.

```powershell
# 실행 위치: 문서 루트. 후속 개발에서 실제 시험을 수행할 때 사용
$PleosWorkId = 'W21' # 실제 작업 ID로 변경: W01~W22
if ($PleosWorkId -notmatch '^W(0[1-9]|1[0-9]|2[0-2])$') { throw '유효한 작업 ID를 지정하세요.' }
$PleosRunDir = Join-Path $PleosRoot ('evidence\' + $PleosWorkId + '\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $PleosRunDir -ErrorAction Stop | Out-Null
Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('-s', $PleosSerial, 'logcat', '-d', '-v', 'threadtime') |
    Set-Content -LiteralPath (Join-Path $PleosRunDir 'logcat-local.txt') -Encoding UTF8
# 서명된 제품 release APK의 실제 경로로 수정한다. debug APK로 대체하지 않는다.
$PleosReleaseApk = 'C:\REPLACE_WITH_SIGNED_RELEASE_APK.apk'
if ($PleosReleaseApk -match 'REPLACE_' -or
    -not (Test-Path -LiteralPath $PleosReleaseApk -PathType Leaf)) {
    throw '실제 서명된 release APK 경로를 지정하세요.'
}
Get-FileHash -Algorithm SHA256 -LiteralPath $PleosReleaseApk |
    Format-List Algorithm, Hash, Path |
    Out-String | Set-Content -LiteralPath (Join-Path $PleosRunDir 'apk-sha256.txt') -Encoding UTF8
Invoke-PleosNative -Tool $PleosAdb -ToolArgs @('-s', $PleosSerial, 'install', '-r', $PleosReleaseApk)
```

release는 실제 서명 설정을 준비한 후 `android/`에서 `gradlew.bat :app:assembleRelease`로 빌드하고 빌드·서명 결과를 확인한다. 비밀번호를 명령줄이나 문서에 넣지 않는다. 서명 불일치로 `install -r`이 실패하면 무조건 삭제하지 않는다. 데이터 백업 여부를 결정하고 검증용 새 AVD/새 설치를 사용한다. fresh install 시험과 기존 데이터 유지 시험을 구분한다. 설치 뒤 필수 흐름을 10회 반복하고 [검증 계획](07_VALIDATION_AND_DEMO.md)의 실패 시나리오와 증거 양식을 채운다. 설치 성공·해시 생성만으로 전체 통과를 표시하지 않는다.

## 7. 장애 진단 순서

| 증상 | 확인 순서 | 통과/다음 단계 |
|---|---|---|
| Java/Gradle 버전 불일치 | `java -version` → `JAVA_HOME` → Studio Gradle JDK → wrapper 설정 | `gradlew.bat --version`의 JVM/Gradle이 기준과 일치해야 빌드 |
| 서로 다른 adb 발견 | `Get-Command adb.exe -All` → 각 경로 → SDK Manager 경로 | 이 문서의 `$PleosAdb` 절대경로로 통일; 다른 세션 영향 확인 없이 ADB 서버 종료 금지 |
| 가속 실패 | CPU 가상화 → WHPX → 재부팅 → `-accel-check` | 오류가 남으면 호스트 환경 차단으로 기록 |
| Pleos 이미지 없음 | Update Site URL → 네트워크 → Show Package Details/API34 → Studio 재시작 | XML 패키지·x86_64 확인, Mac용 feed 혼용 제거 |
| AVD offline/미부팅 | Device Manager → 선택 AVD → 부팅 상태 → Cold boot | 데이터 삭제보다 로그/새 AVD 우선 |
| 8088 충돌 | `adb forward --list` → `Get-NetTCPConnection -LocalPort 8088 -ErrorAction SilentlyContinue` → 소유 PID 확인 | 내 테스트 forward일 때만 해당 `-s` 장치에서 `forward --remove tcp:8088`; 다른 프로세스를 임의 종료하지 않음 |
| Console 열리지만 값 불변 | 대상 장치 → 지원 카드/영역 → SDK callback/재조회 → API activity 오류 | 요청 성공·반영 실패를 분리 기록 |
| SDK permission/capability 실패 | Manifest 선언 → 계정 제공 상태 → 영역/접근 방식 → 설치 Service 버전 | 승인 필요 또는 미지원이면 제어 게이트 차단, 코드 우회 금지 |
| 시트 STOP 결과 불명 | 새 이동 차단 → STOP 요청/콜백/관찰 로그 → 연결 상태 | 정지 입증 전 재이동·완료 처리 금지 |

## 8. 첫 실행과 재개 프롬프트

아래는 **후속 개발 실행을 요청할 때** 사용할 문안이다. 이번 문서 작성 요청에 자동 적용되는 실행 지시가 아니다.

```text
이 폴더의 AGENTS.md, README.md, 03~07, 10~13 문서와 전체 파일 목록을 읽고 실제 구조를 확인해줘.
이번에는 후속 개발을 시작한다. 13_IMPLEMENTATION_TODO.md의 선행 조건 순서대로 진행해줘.
Windows 호스트와 SDK 설치 여부부터 읽기 점검하고, 없는 환경과 실제 설치된 환경을 구분해줘.
사용할 코드/API는 해당 버전의 공식 문서·소스로 사실 확인한 뒤 TODO와 검증 기준을 갱신해줘.
공조·조명·시트 앞뒤/등받이 이동·STOP은 필수이며, 시트는 매 실행 전 확인과 제한 이동을 적용해줘.
10월 28일 제어 게이트가 막히면 목표 차단을 기록하고 독립 작업을 진행하되 완료 기준을 낮추지 마.
외부 신청·결제·필수 기능 변경·새 의존성 선택에 필요한 누락된 결정만 질문해줘.
구현 후 요구사항별 시험을 실제 실행하고 로그·APK 해시·미검증 사항을 남겨줘.
```

```text
현재 폴더를 재개한다. AGENTS.md와 TODO, 최근 증거, 변경된 파일을 먼저 읽어줘.
Git이 있으면 작업 중 변경을 보존하고, 현재 구조·버전·장치를 새로 확인해줘.
마지막 검증 완료 작업과 차단 조건을 증거로 확인한 뒤 다음 실행 가능한 TODO를 처리해줘.
기존 실패와 최신 로그를 우선 반영하고, 수행하지 않은 시험을 통과로 표시하지 마.
공조·조명 상태 반영과 시트 이동·STOP 증거, release 10회 반복 기준을 유지해줘.
```

## 9. 인수인계 완료 기록

| 항목 | 지금 상태 | 후속 기록 위치 |
|---|---|---|
| 문서·공식 링크·명령 작성 | 문서 작성, Windows 실행 미검증 | 이 문서·출처 색인 |
| 호스트·가상화·SDK/JDK/AVD | 미확인 | 향후 `evidence/W02/`, `evidence/W03/` |
| 샘플 빌드·장치 설치 | 미실행 | 향후 `evidence/W02/`, `evidence/W03/` |
| 계정 권한·3종 제어·실제 속도 입력 | 미검증 | 향후 `evidence/W04/`, `evidence/W05/` |
| 제품 코드·서버·release APK | 미구현 | 향후 `android/`, `server/`, `evidence/W20/`, `evidence/W21/` |

후속 담당자는 [출처 색인](11_SOURCES.md)의 확인일과 실제 설치 버전 차이를 기록한다. 이 문서에서 제시한 경로·명령을 읽었다는 사실만으로 환경 설정이나 요구사항 검증을 완료 처리하지 않는다.
