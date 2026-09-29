param([string]$Path = 'd:\pro\RGCalendar\.tools\unfold.png', [int]$Step = 48)

Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile($Path)
$w = $bmp.Width; $h = $bmp.Height
Write-Output ("IMAGE {0} x {1}  step {2}" -f $w, $h, $Step)

$rect = New-Object System.Drawing.Rectangle 0,0,$w,$h
$d = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stride = $d.Stride
$buf = New-Object byte[] ($stride * $h)
[System.Runtime.InteropServices.Marshal]::Copy($d.Scan0, $buf, 0, $buf.Length)
$bmp.UnlockBits($d); $bmp.Dispose()

$hdr = '     '
for ($x = 0; $x -lt $w; $x += $Step) { $hdr += ('{0}' -f ([int]($x / $Step) % 10)) }
Write-Output $hdr
for ($y = 0; $y -lt $h; $y += $Step) {
    $line = ('{0,4} ' -f $y)
    for ($x = 0; $x -lt $w; $x += $Step) {
        $i = $y * $stride + $x * 4
        $b = $buf[$i]; $g = $buf[$i+1]; $r = $buf[$i+2]
        $ch = '.'
        if ($b -gt 210 -and ($b - $g) -gt 60 -and $r -gt 90 -and $r -lt 215) { $ch = 'P' }
        elseif (($r -gt 215) -and ([Math]::Abs($r - $g) -le 8) -and ([Math]::Abs($g - $b) -le 8)) { $ch = 'G' }
        $line += $ch
    }
    Write-Output $line
}