param(
    [string]$Source = "C:\Users\evand\Downloads\Ícone GC com Velocímetro Azul Cobalto.png"
)

Add-Type -AssemblyName System.Drawing

$royal = [System.Drawing.Color]::FromArgb(255, 1, 49, 205)
$white = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
$lime = [System.Drawing.Color]::FromArgb(255, 185, 252, 1)
$transparent = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
$root = Split-Path -Parent $PSScriptRoot

function Get-PaletteColor([System.Drawing.Color]$pixel, [bool]$transparentBlue) {
    $brightness = ($pixel.R + $pixel.G + $pixel.B) / 3
    if ($brightness -lt 18) { return $transparent }
    if ($pixel.G -gt ($pixel.R * 1.3) -and $pixel.G -gt ($pixel.B * 1.2)) { return $lime }
    if ($brightness -gt 178 -and [Math]::Abs($pixel.R - $pixel.G) -lt 55) { return $white }
    if ($transparentBlue) { return $transparent }
    return $royal
}

function New-PaletteMaster([System.Drawing.Bitmap]$source, [bool]$transparentBlue) {
    # Bounds of the rounded-square artwork in the supplied 1254 px reference.
    $crop = [System.Drawing.Rectangle]::new(164, 139, 927, 951)
    $master = [System.Drawing.Bitmap]::new(1024, 1024, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($master)
    $graphics.Clear($transparent)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(0, 0, 1024, 1024), $crop, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.Dispose()
    $rect = [System.Drawing.Rectangle]::new(0, 0, 1024, 1024)
    $data = $master.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadWrite, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $bytes = [byte[]]::new([Math]::Abs($data.Stride) * 1024)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)
    for ($i = 0; $i -lt $bytes.Length; $i += 4) {
        $b = [int]$bytes[$i]; $g = [int]$bytes[$i + 1]; $r = [int]$bytes[$i + 2]
        $brightness = ($r + $g + $b) / 3
        if ($brightness -lt 18) { $bytes[$i + 3] = 0; continue }
        if ($g -gt ($r * 1.3) -and $g -gt ($b * 1.2)) {
            $bytes[$i] = 1; $bytes[$i + 1] = 252; $bytes[$i + 2] = 185; $bytes[$i + 3] = 255
        } elseif ($brightness -gt 178 -and [Math]::Abs($r - $g) -lt 55) {
            $bytes[$i] = 255; $bytes[$i + 1] = 255; $bytes[$i + 2] = 255; $bytes[$i + 3] = 255
        } elseif ($transparentBlue) {
            $bytes[$i + 3] = 0
        } else {
            $bytes[$i] = 205; $bytes[$i + 1] = 49; $bytes[$i + 2] = 1; $bytes[$i + 3] = 255
        }
    }
    [System.Runtime.InteropServices.Marshal]::Copy($bytes, 0, $data.Scan0, $bytes.Length)
    $master.UnlockBits($data)
    return $master
}

function Save-Scaled([System.Drawing.Bitmap]$source, [int]$size, [string]$path, [bool]$circle, [double]$contentScale = 1.0) {
    $output = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($output)
    $graphics.Clear($transparent)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    if ($circle) {
        $clip = [System.Drawing.Drawing2D.GraphicsPath]::new()
        $clip.AddEllipse(0, 0, $size, $size)
        $graphics.SetClip($clip)
        $clip.Dispose()
    }
    $contentSize = [int]($size * $contentScale)
    $offset = [int](($size - $contentSize) / 2)
    $graphics.DrawImage($source, $offset, $offset, $contentSize, $contentSize)
    $graphics.Dispose()
    $png = [System.IO.Path]::ChangeExtension($path, '.png')
    $output.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
    $output.Dispose()
    & ffmpeg -loglevel error -y -i $png -c:v libwebp -lossless 1 $path
    Remove-Item -LiteralPath $png
}

$sourceBitmap = [System.Drawing.Bitmap]::new($Source)
$master = New-PaletteMaster $sourceBitmap $false
$foregroundMaster = New-PaletteMaster $sourceBitmap $true
$master.Save((Join-Path $root 'design\girocerto-icon-royal.png'), [System.Drawing.Imaging.ImageFormat]::Png)

$legacy = @{ ldpi = 36; mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }
foreach ($entry in $legacy.GetEnumerator()) {
    $dir = Join-Path $root "app\src\main\res\mipmap-$($entry.Key)"
    Save-Scaled $master $entry.Value (Join-Path $dir 'ic_launcher.webp') $false
    Save-Scaled $master $entry.Value (Join-Path $dir 'ic_launcher_round.webp') $true
}

$foreground = @{ mdpi = 108; hdpi = 162; xhdpi = 216; xxhdpi = 324; xxxhdpi = 432 }
foreach ($entry in $foreground.GetEnumerator()) {
    $dir = Join-Path $root "app\src\main\res\mipmap-$($entry.Key)"
    Save-Scaled $foregroundMaster $entry.Value (Join-Path $dir 'ic_launcher_foreground.webp') $false 0.72
}

$foregroundMaster.Dispose()
$master.Dispose()
$sourceBitmap.Dispose()
