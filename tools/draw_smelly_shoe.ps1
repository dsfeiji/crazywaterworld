$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$image = New-Object System.Drawing.Bitmap 32, 32
$graphics = [System.Drawing.Graphics]::FromImage($image)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None

function Fill-Shape([string]$hex, [int[]]$coordinates) {
    $points = @(
        for ($i = 0; $i -lt $coordinates.Length; $i += 2) {
            New-Object System.Drawing.Point ($coordinates[$i], $coordinates[$i + 1])
        }
    )
    $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($hex))
    try { $graphics.FillPolygon($brush, [System.Drawing.Point[]]$points) }
    finally { $brush.Dispose() }
}

function Paint([string]$hex, [int]$x, [int]$y, [int]$width, [int]$height) {
    $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($hex))
    try { $graphics.FillRectangle($brush, $x, $y, $width, $height) }
    finally { $brush.Dispose() }
}

# Crooked odor trails behind the worn upper.
Paint '#6d8f47' 18 4 2 2
Paint '#6d8f47' 17 6 2 2
Paint '#6d8f47' 19 8 2 2
Paint '#a1ae61' 20 3 2 2
Paint '#869c50' 24 7 2 2
Paint '#869c50' 22 9 2 2
Paint '#869c50' 23 11 2 2
Paint '#6d8f47' 27 13 2 2
Paint '#6d8f47' 25 15 2 2

# Silhouette faces right: short heel, collapsed upper and a broad toe.
Fill-Shape '#242923' @(5, 8, 13, 8, 16, 11, 16, 17, 19, 19, 25, 20, 29, 23, 30, 27, 27, 30, 8, 30, 3, 27, 3, 22, 5, 19)
Fill-Shape '#664d35' @(7, 10, 12, 10, 14, 13, 14, 19, 18, 21, 24, 22, 28, 24, 28, 26, 25, 27, 9, 27, 5, 25, 6, 19)
Fill-Shape '#916b45' @(8, 11, 12, 11, 13, 14, 12, 19, 16, 21, 11, 23, 7, 21)
Fill-Shape '#7b5d3e' @(17, 21, 24, 22, 27, 24, 26, 25, 18, 25, 13, 23)

# Dark, frayed opening; mismatched laces and a repaired toe.
Paint '#33382d' 7 9 5 2
Paint '#28352d' 8 10 3 2
Paint '#aa8659' 5 14 2 5
Paint '#a78455' 7 12 1 3
Paint '#bb9966' 10 17 4 1
Paint '#bb9966' 9 19 5 1
Paint '#bb9966' 9 21 4 1
Paint '#3a352d' 12 17 1 1
Paint '#3a352d' 11 19 1 1
Paint '#3a352d' 10 21 1 1
Fill-Shape '#444536' @(22, 23, 25, 23, 27, 25, 26, 26, 22, 26)
Paint '#b09462' 23 23 2 1
Paint '#b09462' 22 25 2 1
Paint '#c4a072' 19 22 2 1
Paint '#424032' 7 22 2 2
Paint '#a68a5d' 6 24 2 1

# Muddy sole and a chipped heel keep the shape readable at 16px scale.
Paint '#343a30' 5 26 23 2
Paint '#718054' 11 27 7 1
Paint '#96774e' 20 27 4 1
Paint '#272d26' 9 28 4 1
Paint '#272d26' 22 28 4 1
Paint '#6b7750' 4 23 1 2

$path = Join-Path (Split-Path -Parent $PSScriptRoot) 'src\main\resources\assets\crazywaterworld\textures\item\smelly_shoe.png'
try { $image.Save($path, [System.Drawing.Imaging.ImageFormat]::Png) }
finally { $graphics.Dispose(); $image.Dispose() }
Write-Output "Drawn 32x32 shoe texture: $path"
