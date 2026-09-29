param(
    [string]$Path = 'd:\pro\RGCalendar\.tools\unfold.png',
    [string]$Out = 'd:\pro\RGCalendar\.tools\row.png',
    [int]$X = 0, [int]$Y = 940, [int]$W = 0, [int]$H = 560, [double]$Scale = 0.5
)
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Bitmap]::FromFile($Path)
if ($W -le 0) { $W = $src.Width }
$crop = New-Object System.Drawing.Rectangle $X, $Y, $W, $H
$dst = New-Object System.Drawing.Bitmap ([int]($W * $Scale)), ([int]($H * $Scale))
$g = [System.Drawing.Graphics]::FromImage($dst)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, $dst.Width, $dst.Height), $crop, [System.Drawing.GraphicsUnit]::Pixel)
$g.Dispose()
$dst.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$dst.Dispose(); $src.Dispose()
Write-Output ("saved {0}  {1}x{2}  (crop {3},{4} {5}x{6} scale {7})" -f $Out, [int]($W * $Scale), [int]($H * $Scale), $X, $Y, $W, $H, $Scale)