# 手写构建流程：aapt2 -> javac -> d8 -> zip -> zipalign -> apksigner
# 不依赖 Gradle/AGP，减少环境失败面
$ErrorActionPreference = 'Stop'

$root   = 'd:\pro\RGCalendar'
$tools  = Join-Path $root '.tools'
$sdk    = Join-Path $tools 'android-sdk'
$bt     = Join-Path $sdk 'build-tools\34.0.0'
$jdk    = Join-Path $tools 'jdk17'
$androidJar = Join-Path $sdk 'platforms\android-34\android.jar'
$proj   = Join-Path $root 'probe'
$work   = Join-Path $proj '.build'
$minSdk = 24
$targetSdk = 33

$env:JAVA_HOME = $jdk
$env:PATH = (Join-Path $jdk 'bin') + ';' + $env:PATH

function Assert-Ok($name) {
    if ($LASTEXITCODE -ne 0) { throw ("step failed: " + $name + " (exit " + $LASTEXITCODE + ")") }
}

if (Test-Path $work) { Remove-Item -Recurse -Force $work }
New-Item -ItemType Directory -Force -Path "$work\compiled", "$work\gen", "$work\classes", "$work\dex" | Out-Null

Write-Host '[1/7] aapt2 compile'
& "$bt\aapt2.exe" compile --dir "$proj\res" -o "$work\compiled\res.zip"
Assert-Ok 'aapt2 compile'

Write-Host '[2/7] aapt2 link'
& "$bt\aapt2.exe" link `
    -o "$work\base.apk" `
    -I $androidJar `
    --manifest "$proj\AndroidManifest.xml" `
    -R "$work\compiled\res.zip" `
    --java "$work\gen" `
    --min-sdk-version $minSdk `
    --target-sdk-version $targetSdk `
    --auto-add-overlay
Assert-Ok 'aapt2 link'

Write-Host '[3/7] javac'
$srcs = @(Get-ChildItem "$proj\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$gens = @(Get-ChildItem "$work\gen" -Recurse -Filter *.java -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
& "$jdk\bin\javac.exe" -encoding UTF-8 -source 8 -target 8 -bootclasspath $androidJar -nowarn `
    -d "$work\classes" ($srcs + $gens)
Assert-Ok 'javac'

Write-Host '[4/7] d8'
$cls = @(Get-ChildItem "$work\classes" -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& "$bt\d8.bat" --min-api $minSdk --lib $androidJar --output "$work\dex" $cls
Assert-Ok 'd8'
Write-Host ("      classes.dex = " + [math]::Round((Get-Item "$work\dex\classes.dex").Length / 1KB, 1) + " KB")

Write-Host '[5/7] assemble apk (resources.arsc 保持未压缩)'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$unsigned = "$work\unsigned.apk"
if (Test-Path $unsigned) { Remove-Item -Force $unsigned }
$srcZip = [System.IO.Compression.ZipFile]::OpenRead("$work\base.apk")
$dstZip = [System.IO.Compression.ZipFile]::Open($unsigned, 'Create')
try {
    foreach ($e in $srcZip.Entries) {
        $level = if ($e.FullName -eq 'resources.arsc') {
            [System.IO.Compression.CompressionLevel]::NoCompression
        } else {
            [System.IO.Compression.CompressionLevel]::Optimal
        }
        $ne = $dstZip.CreateEntry($e.FullName, $level)
        try { $ne.LastWriteTime = $e.LastWriteTime } catch { }
        $is = $e.Open()
        $os = $ne.Open()
        $is.CopyTo($os)
        $os.Dispose()
        $is.Dispose()
    }
    $de = $dstZip.CreateEntry('classes.dex', [System.IO.Compression.CompressionLevel]::Optimal)
    $os = $de.Open()
    $bytes = [System.IO.File]::ReadAllBytes("$work\dex\classes.dex")
    $os.Write($bytes, 0, $bytes.Length)
    $os.Dispose()
} finally {
    $dstZip.Dispose()
    $srcZip.Dispose()
}

Write-Host '[6/7] zipalign'
& "$bt\zipalign.exe" -f -p 4 $unsigned "$work\aligned.apk"
Assert-Ok 'zipalign'

Write-Host '[7/7] apksigner'
$ks = Join-Path $tools 'debug.keystore'
if (-not (Test-Path $ks)) {
    & "$jdk\bin\keytool.exe" -genkeypair -keystore $ks -storepass android -keypass android `
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=Android Debug,O=Android,C=US"
    Assert-Ok 'keytool'
}
$apk = Join-Path $proj 'CalendarProbe.apk'
if (Test-Path $apk) { Remove-Item -Force $apk }
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out $apk "$work\aligned.apk"
Assert-Ok 'apksigner sign'
& "$bt\apksigner.bat" verify "$apk"
Assert-Ok 'apksigner verify'

Write-Host ''
Write-Host ("APK  : " + $apk)
Write-Host ("SIZE : " + [math]::Round((Get-Item $apk).Length / 1KB, 1) + " KB")
Write-Host 'BUILD_OK'