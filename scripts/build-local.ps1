param([switch]$CoreOnly)
$ErrorActionPreference = 'Stop'
$taskProjectRoot = Split-Path -Parent $PSScriptRoot
$taskGradle = Join-Path $taskProjectRoot '.tooling/gradle-8.13/bin/gradle.bat'
$taskSdk = Join-Path $taskProjectRoot '.tooling/android-sdk'
if (-not (Test-Path -LiteralPath $taskGradle)) {
    throw '项目内未找到 Gradle 8.13；请使用根目录 gradlew.bat 或先准备 .tooling/gradle-8.13。'
}
if (-not $CoreOnly -and -not (Test-Path -LiteralPath (Join-Path $taskSdk 'platforms/android-36/android.jar'))) {
    throw '项目内未找到 Android SDK 36；请使用 Android Studio 安装 SDK。'
}
$taskPriorCache = $env:GRADLE_USER_HOME
Push-Location -LiteralPath $taskProjectRoot
try {
    $env:GRADLE_USER_HOME = Join-Path $taskProjectRoot '.gradle-user-home'
    if (-not $CoreOnly) {
        Set-Content -LiteralPath 'local.properties' -Value ('sdk.dir=' + $taskSdk.Replace('\','/')) -Encoding utf8
    }
    $taskArguments = if ($CoreOnly) { @('-PcoreOnly', ':core:test') } else { @(':core:test', ':app:assembleDebug', ':app:lintDebug') }
    & $taskGradle @taskArguments --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败（退出码 $LASTEXITCODE）" }
} finally {
    $env:GRADLE_USER_HOME = $taskPriorCache
    Pop-Location
}
