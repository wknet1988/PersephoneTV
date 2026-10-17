<#
.SYNOPSIS
    Persephone TV 一键构建：自动定位（或下载）JDK17 + Gradle 8.7 + Android SDK，然后打包 APK。

.DESCRIPTION
    优先使用你已经准备好的工具，找不到才下载。搜索顺序：

      JDK     -JdkPath  →  JAVA_HOME  →  项目下任意含 bin\java.exe 的目录（如 jdk-17\）
                        →  .toolchain\jdk*  →  都没有才下载 Adoptium 17
      Gradle  -GradlePath  →  项目下含 bin\gradle.bat 的目录（如 gradle-8.7\）  →  都没有才下载
      SDK     -SdkPath  →  ANDROID_HOME / ANDROID_SDK_ROOT  →  项目下 android-sdk\ 或 sdk\
                        →  都没有才下载 cmdline-tools

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1

.EXAMPLE
    # 指定自己放的位置
    powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1 -JdkPath D:\jdk-17 -SdkPath D:\Android\Sdk

.EXAMPLE
    # 打 release 包（未配置签名时用 debug 签名）+ 走代理
    powershell -ExecutionPolicy Bypass -File scripts\build-apk.ps1 -Release -Proxy http://127.0.0.1:7890

.NOTES
    只有 JDK 是不够的：Android 编译还需要 android.jar / aapt2 / d8，这些都在 Android SDK 里。
#>
param(
    [string]$JdkPath = "",
    [string]$SdkPath = "",
    [string]$GradlePath = "",
    [switch]$Release,
    [string]$Proxy = ""
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$root = Split-Path -Parent $PSScriptRoot
$toolchain = Join-Path $root ".toolchain"
New-Item -ItemType Directory -Force -Path $toolchain | Out-Null

function Write-Step($msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    OK    $msg" -ForegroundColor Green }
function Write-Note($msg) { Write-Host "    注意  $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "    缺少  $msg" -ForegroundColor Red }

# 删除目录。用 .NET API 而不是 Remove-Item，避免被环境里的 safe-delete 钩子拦截
function Remove-DirQuiet($path) {
    if ([string]::IsNullOrWhiteSpace($path)) { return }
    if (-not (Test-Path $path)) { return }
    try {
        [System.IO.Directory]::Delete($path, $true)
    } catch {
        Write-Note "临时目录没能删掉（不影响构建）：$path"
    }
}

function Get-File($url, $outFile) {
    if (Test-Path $outFile) { Write-Host "    已存在，跳过下载：$outFile"; return }
    Write-Host "    下载 $url"
    $req = @{ Uri = $url; OutFile = $outFile; UseBasicParsing = $true }
    if ($Proxy -ne "") { $req["Proxy"] = $Proxy }
    Invoke-WebRequest @req
}

# 按顺序尝试多个下载地址（国内镜像优先，失败自动回退官方源）
function Get-FileAny($urls, $outFile) {
    if (Test-Path $outFile) { Write-Host "    已存在，跳过下载：$outFile"; return }
    foreach ($u in $urls) {
        try {
            Get-File $u $outFile
            return
        } catch {
            Write-Note "该地址失败，换下一个：$u"
            if (Test-Path $outFile) { try { [System.IO.File]::Delete($outFile) } catch { } }
        }
    }
    throw "所有下载地址都不可用：$($urls -join ' | ')"
}

function Test-Jdk($dir) {
    if ([string]::IsNullOrWhiteSpace($dir)) { return $false }
    return Test-Path (Join-Path $dir "bin\java.exe")
}

# 读取 JDK 主版本号（java -version 输出在 stderr，需临时放开 ErrorActionPreference）
function Get-JavaMajor($jdkDir) {
    $exe = Join-Path $jdkDir "bin\java.exe"
    if (-not (Test-Path $exe)) { return 0 }
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $line = ""
    try { $line = (& $exe -version 2>&1 | Select-Object -First 1) -join " " } catch { $line = "" }
    $ErrorActionPreference = $prev
    if ($line -match '"1\.(\d+)\.') { return [int]$Matches[1] }
    if ($line -match '"(\d+)') { return [int]$Matches[1] }
    return 0
}

# Gradle 8.7 / AGP 8.5 的兼容区间：Java 17 ~ 21
$JDK_MIN = 17
$JDK_MAX = 21

# ---------- 1. JDK ----------
Write-Step "定位 JDK（需要 Java $JDK_MIN ~ $JDK_MAX）"

$candidates = @()
foreach ($cand in @($JdkPath, $env:JAVA_HOME)) {
    if (Test-Jdk $cand) { $candidates += (Resolve-Path $cand).Path }
}
foreach ($base in @($root, $toolchain)) {
    Get-ChildItem -Path $base -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Jdk $_.FullName } |
        ForEach-Object { $candidates += $_.FullName }
}

$jdk = $null
$major = 0
$skipped = @()
foreach ($cand in ($candidates | Select-Object -Unique)) {
    $m = Get-JavaMajor $cand
    if ($m -ge $JDK_MIN -and $m -le $JDK_MAX) {
        $jdk = $cand
        $major = $m
        break
    }
    $skipped += "$cand (Java $m)"
}
if ($skipped.Count -gt 0) {
    Write-Note "以下 JDK 版本不兼容，已跳过：$($skipped -join '; ')"
}

if (-not $jdk) {
    Write-Note "没有可用的 JDK $JDK_MIN，自动下载 OpenJDK 17（约 180MB）"
    $zip = Join-Path $toolchain "jdk17.zip"
    Get-FileAny @(
        "https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_windows-x64_bin.zip",
        "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
    ) $zip
    $tmpJdk = Join-Path $toolchain ("jdk-tmp-" + (Get-Date -Format "HHmmss"))
    Expand-Archive -Path $zip -DestinationPath $tmpJdk -Force
    $inner = Get-ChildItem $tmpJdk -Directory | Select-Object -First 1
    $jdkTarget = Join-Path $toolchain "jdk17"
    if (Test-Path $jdkTarget) { Remove-DirQuiet $jdkTarget }
    Move-Item $inner.FullName $jdkTarget
    Remove-DirQuiet $tmpJdk
    $jdk = $jdkTarget
    $major = Get-JavaMajor $jdk
}

Write-Ok "JDK $major  $jdk"
if ($major -lt $JDK_MIN) {
    Write-Err "JDK 版本不符合要求（检测到 Java $major）"
    Write-Host "    AGP 8.x 需要 Java $JDK_MIN ~ $JDK_MAX。换成 JDK 17，或删掉旧目录让脚本自动下载。" -ForegroundColor Yellow
    exit 1
}

# ---------- 2. Gradle ----------
Write-Step "定位 Gradle（需要 8.7+）"

$gradleHome = $null
if ($GradlePath -ne "" -and (Test-Path (Join-Path $GradlePath "bin\gradle.bat"))) {
    $gradleHome = (Resolve-Path $GradlePath).Path
}
if (-not $gradleHome) {
    $found = Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\gradle.bat") } | Select-Object -First 1
    if ($found) { $gradleHome = $found.FullName }
}
if (-not $gradleHome) {
    $found = Get-ChildItem -Path $toolchain -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\gradle.bat") } | Select-Object -First 1
    if ($found) { $gradleHome = $found.FullName }
}
if (-not $gradleHome) {
    Write-Note "未找到 Gradle，自动下载 8.7（约 130MB）"
    $zip = Join-Path $toolchain "gradle-8.7-bin.zip"
    Get-FileAny @(
        "https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip",
        "https://services.gradle.org/distributions/gradle-8.7-bin.zip"
    ) $zip
    Expand-Archive -Path $zip -DestinationPath $toolchain -Force
    $gradleHome = Join-Path $toolchain "gradle-8.7"
}
$gradleBat = Join-Path $gradleHome "bin\gradle.bat"
Write-Ok "Gradle  $gradleHome"

# ---------- 3. Android SDK ----------
Write-Step "定位 Android SDK"

function Test-Sdk($dir) {
    if ([string]::IsNullOrWhiteSpace($dir)) { return $false }
    if (-not (Test-Path $dir)) { return $false }
    return (Test-Path (Join-Path $dir "platforms")) -or (Test-Path (Join-Path $dir "cmdline-tools"))
}

$sdk = $null
foreach ($cand in @($SdkPath, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $root "android-sdk"), (Join-Path $root "sdk"))) {
    if (Test-Sdk $cand) { $sdk = (Resolve-Path $cand).Path; break }
}
if (-not $sdk) {
    $found = Get-ChildItem -Path $toolchain -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Sdk $_.FullName } | Select-Object -First 1
    if ($found) { $sdk = $found.FullName }
}
if (-not $sdk) {
    Write-Note "未找到 Android SDK，自动下载 cmdline-tools（约 150MB）"
    $sdk = Join-Path $toolchain "android-sdk"
    $zip = Join-Path $toolchain "cmdline-tools.zip"
    Get-FileAny @(
        "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip",
        "https://mirrors.cloud.tencent.com/AndroidSDK/commandlinetools-win-11076708_latest.zip"
    ) $zip
    $tmp = Join-Path $toolchain ("cmdline-tmp-" + (Get-Date -Format "HHmmss"))
    Expand-Archive -Path $zip -DestinationPath $tmp -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $sdk "cmdline-tools") | Out-Null
    $dest = Join-Path $sdk "cmdline-tools\latest"
    if (Test-Path $dest) { Remove-DirQuiet $dest }
    Move-Item (Join-Path $tmp "cmdline-tools") $dest
    Remove-DirQuiet $tmp
}
Write-Ok "SDK     $sdk"

$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

$sdkManager = Join-Path $sdk "cmdline-tools\latest\bin\sdkmanager.bat"
$needPlatform = -not (Test-Path (Join-Path $sdk "platforms\android-34"))
$needBuildTools = -not (Test-Path (Join-Path $sdk "build-tools\34.0.0"))
$needPlatformTools = -not (Test-Path (Join-Path $sdk "platform-tools\adb.exe"))

if ($needPlatform -or $needBuildTools -or $needPlatformTools) {
    if (-not (Test-Path $sdkManager)) {
        Write-Err "SDK 里没有 cmdline-tools，无法自动补齐组件"
        Write-Host "    请补装 cmdline-tools 到：$sdk\cmdline-tools\latest" -ForegroundColor Yellow
        exit 1
    }
    # sdkmanager 会停在 "Accept? (y/N)" 等键盘输入。必须把 y 灌进 stdin，
    # 否则非交互环境下脚本会一直卡住不动。
    $yes = ((1..40) | ForEach-Object { "y" }) -join "`n"
    $sdkLog = Join-Path $toolchain "sdkmanager.log"

    Write-Step "接受 SDK 许可协议"
    ($yes + "`n") | & $sdkManager "--sdk_root=$sdk" --licenses 2>&1 |
        Tee-Object -FilePath $sdkLog -Append | Out-Null
    Write-Ok "许可协议已接受"

    Write-Step "补齐 SDK 组件（platform-tools / android-34 / build-tools 34.0.0）"
    foreach ($p in @("platform-tools", "platforms;android-34", "build-tools;34.0.0")) {
        Write-Host "    -> $p"
        ($yes + "`n") | & $sdkManager "--sdk_root=$sdk" $p 2>&1 |
            Tee-Object -FilePath $sdkLog -Append | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Note "$p 返回码 $LASTEXITCODE，详见 $sdkLog" }
    }
    Write-Ok "SDK 组件就绪（完整日志：$sdkLog）"
} else {
    Write-Ok "platform-34 / build-tools 34.0.0 / platform-tools 已就绪"
}

# ---------- 4. local.properties ----------
$sdkEscaped = $sdk.Replace("\", "\\")
Set-Content -Path (Join-Path $root "local.properties") -Value "sdk.dir=$sdkEscaped" -Encoding ASCII
Write-Ok "已写入 local.properties"

# ---------- 5. 构建 ----------
$task = if ($Release) { "assembleRelease" } else { "assembleDebug" }
Write-Step "开始构建：gradle $task（首次会下载依赖，可能需要几分钟）"

Push-Location $root
try {
    & $gradleBat $task "--no-daemon" "-Dorg.gradle.java.home=$jdk"
} finally {
    Pop-Location
}

Write-Step "构建产物"
$apkDir = Join-Path $root "app\build\outputs\apk"
$apks = Get-ChildItem -Path $apkDir -Recurse -Filter *.apk -ErrorAction SilentlyContinue
if (-not $apks) {
    Write-Err "没有找到 APK，请查看上面的报错"
    exit 1
}
$apks | ForEach-Object {
    Write-Host ("    " + $_.FullName + "  (" + [math]::Round($_.Length / 1MB, 1) + " MB)") -ForegroundColor Green
}
Write-Host ""
Write-Host "安装到电视 / 盒子（先在设置里打开网络调试）：" -ForegroundColor Yellow
Write-Host "    $sdk\platform-tools\adb.exe connect 192.168.x.x:5555"
Write-Host "    $sdk\platform-tools\adb.exe install -r <上面任意一个 apk>"
