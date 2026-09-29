param([string]$Path = 'd:\pro\RGCalendar\.tools\unfold.png')

Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile($Path)
$w = $bmp.Width; $h = $bmp.Height
$rect = New-Object System.Drawing.Rectangle 0,0,$w,$h
$d = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = $d.Stride
$buf = New-Object byte[] ($stride * $h)
[System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $buf, 0, $buf.Length)
$bmp.UnlockBits($d); $bmp.Dispose()

# 两个窗口：左 = 系统「日历」卡，中 = 我们的卡
$wins = @(
    @{ Name = 'SYSTEM 日历 (left)';  X0 = 0;    X1 = 600 },
    @{ Name = 'OURS   (middle)';     X0 = 600;  X1 = 1300 }
)

foreach ($win in $wins) {
    $ys = @(); $xmin = 999999; $xmax = -1
    $rows = @{}
    for ($y = 900; $y -le 1500; $y++) {
        $base = $y * $stride
        $mn = 999999; $mx = -1; $c = 0
        for ($x = $win.X0; $x -lt [Math]::Min($win.X1, $w); $x++) {
            $i = $base + $x * 4
            $b = $buf[$i]; $g = $buf[$i+1]; $r = $buf[$i+2]
            if ($b -gt 205 -and ($b - $g) -gt 55 -and $r -gt 85 -and $r -lt 222) {
                $c++
                if ($x -lt $mn) { $mn = $x }
                if ($x -gt $mx) { $mx = $x }
            }
        }
        if ($c -ge 120) {
            $ys += $y
            $rows[$y] = @($mn, $mx)
            if ($mn -lt $xmin) { $xmin = $mn }
            if ($mx -gt $xmax) { $xmax = $mx }
        }
    }
    if ($ys.Count -eq 0) { Write-Output ("{0}: none" -f $win.Name); continue }
    $yt = $ys[0]; $yb = $ys[-1]
    Write-Output ("{0}:  x {1}..{2} (w {3})   y {4}..{5} (h {6})" -f $win.Name, $xmin, $xmax, ($xmax - $xmin + 1), $yt, $yb, ($yb - $yt + 1))
    $mid = [int](($yt + $yb) / 2)
    Write-Output ("      top  y={0}: x {1}..{2}" -f $yt, $rows[$yt][0], $rows[$yt][1])
    Write-Output ("      mid  y={0}: x {1}..{2}" -f $mid, $rows[$mid][0], $rows[$mid][1])
    Write-Output ("      bot  y={0}: x {1}..{2}" -f $yb, $rows[$yb][0], $rows[$yb][1])
}