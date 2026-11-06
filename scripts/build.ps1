param([string[]]$Tasks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug'))
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$previousLocation = Get-Location
$previousJava = $env:JAVA_HOME
$previousGradle = $env:GRADLE_USER_HOME
$propertiesPath = Join-Path $project 'local.properties'
$previousProperties = if (Test-Path -LiteralPath $propertiesPath) { [IO.File]::ReadAllBytes($propertiesPath) } else { $null }
$drive = $null
try {
    # A temporary alias avoids Windows Java argument-file failures with Chinese paths.
    foreach ($letter in @('Z','Y','X','W','V','U')) {
        if (-not (Test-Path ($letter + ':\'))) { $drive = $letter + ':'; break }
    }
    if (-not $drive) { throw '没有可用的临时盘符。请将项目解压到英文目录后运行 gradlew.bat。' }
    & subst.exe $drive $project
    if ($LASTEXITCODE -ne 0) { throw '无法建立临时路径别名。' }
    $base = $drive + '\'
    Set-Location $base
    $localJdk = Join-Path $base '.tools\jdk\jdk-17.0.17+10'
    if (Test-Path -LiteralPath $localJdk) { $env:JAVA_HOME = $localJdk }
    $env:GRADLE_USER_HOME = Join-Path $base '.tools\gradle-cache'
    if (Test-Path '.tools/sdk/platforms/android-35/android.jar') {
        [IO.File]::WriteAllText($propertiesPath, ('sdk.dir=' + $drive + '/.tools/sdk'), [Text.Encoding]::ASCII)
    }
    $localGradle = '.tools/gradle/gradle-8.11.1/bin/gradle.bat'
    if (Test-Path $localGradle) { & $localGradle --no-daemon @Tasks }
    else { & '.\gradlew.bat' --no-daemon @Tasks }
    if ($LASTEXITCODE -ne 0) { throw '构建或测试失败，请查看上方日志。' }
} finally {
    Set-Location $previousLocation
    if ($null -ne $previousProperties) { [IO.File]::WriteAllBytes($propertiesPath, $previousProperties) }
    elseif (Test-Path -LiteralPath $propertiesPath) { Remove-Item -LiteralPath $propertiesPath }
    if ($drive) { & subst.exe $drive /D }
    $env:JAVA_HOME = $previousJava
    $env:GRADLE_USER_HOME = $previousGradle
}
