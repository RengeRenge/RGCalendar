# 把 classes.dex 合入 APK，并保证 resources.arsc 未压缩（targetSdk>=30 的硬性要求）
$ErrorActionPreference = 'Stop'
$root   = 'd:\pro\RGCalendar'
$tools  = Join-Path $root '.tools'
$proj   = Join-Path $root 'probe'
$work   = Join-Path $proj '.build'
$base   = Join-Path $work 'base.apk'
$dex    = Join-Path $work 'dex\classes.dex'
$out    = Join-Path $work 'unsigned.apk'

Add-Type -AssemblyName System.IO.Compression.FileSystem

if (Test-Path $out) { Remove-Item -Force $out }

$src = [System.IO.Compression.ZipFile]::OpenRead($base)
$dst = [System.IO.Compression.ZipFile]::Open($out, 'Create')
try {
    foreach ($e in $src.Entries) {
        $level = if ($e.FullName -eq 'resources.arsc') {
            [System.IO.Compression.CompressionLevel]::NoCompression
        } else {
            [System.IO.Compression.CompressionLevel]::Optimal
        }
        $ne = $dst.CreateEntry($e.FullName, $level)
        try { $ne.LastWriteTime = $e.LastWriteTime } catch { }
        $is = $e.Open()
        $os = $ne.Open()
        $is.CopyTo($os)
        $os.Dispose()
        $is.Dispose()
    }
    $de = $dst.CreateEntry('classes.dex', [System.IO.Compression.CompressionLevel]::Optimal)
    $os = $de.Open()
    $bytes = [System.IO.File]::ReadAllBytes($dex)
    $os.Write($bytes, 0, $bytes.Length)
    $os.Dispose()
} finally {
    $dst.Dispose()
    $src.Dispose()
}

# 校验
$chk = [System.IO.Compression.ZipFile]::OpenRead($out)
try {
    foreach ($e in $chk.Entries) {
        if ($e.FullName -eq 'resources.arsc' -and $e.CompressedLength -ne $e.Length) {
            throw 'resources.arsc 被压缩了，安装会失败'
        }
    }
    Write-Host ("      entries = " + $chk.Entries.Count + " , resources.arsc stored = OK")
} finally {
    $chk.Dispose()
}