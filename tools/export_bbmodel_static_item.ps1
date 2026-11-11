param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$ItemId,
    [switch]$Block
)

$ErrorActionPreference = 'Stop'
if ($ItemId -notmatch '^[a-z0-9_]+$') { throw 'ItemId must be lowercase letters, digits or underscores.' }
$bb = Get-Content -LiteralPath $Source -Raw -Encoding UTF8 | ConvertFrom-Json
$assets = Join-Path (Split-Path -Parent $PSScriptRoot) 'src\main\resources\assets\crazywaterworld'
$folder = if ($Block) { 'block' } else { 'item' }
$modelPath = Join-Path $assets "models\$folder\$ItemId.json"
$textureDir = Join-Path $assets "textures\$folder"
if (!(Test-Path -LiteralPath $modelPath)) { throw "Model does not exist: $modelPath" }
if (!$bb.elements -or !$bb.textures) { throw 'Model has no cubes or textures.' }
if (@($bb.groups | Where-Object { $_.rotation -and @($_.rotation | Where-Object { [double]$_ -ne 0 }).Count -gt 0 }).Count) {
    throw 'Rotated groups are not supported.'
}

function Rotate-Point($point, $origin, $angles) {
    $x = [double]$point[0] - [double]$origin[0]
    $y = [double]$point[1] - [double]$origin[1]
    $z = [double]$point[2] - [double]$origin[2]
    $rx = [double]$angles[0] * [Math]::PI / 180
    $ry = [double]$angles[1] * [Math]::PI / 180
    $rz = [double]$angles[2] * [Math]::PI / 180
    $nextY = $y * [Math]::Cos($rx) - $z * [Math]::Sin($rx)
    $z = $y * [Math]::Sin($rx) + $z * [Math]::Cos($rx)
    $y = $nextY
    $nextX = $x * [Math]::Cos($ry) + $z * [Math]::Sin($ry)
    $z = -$x * [Math]::Sin($ry) + $z * [Math]::Cos($ry)
    $x = $nextX
    $nextX = $x * [Math]::Cos($rz) - $y * [Math]::Sin($rz)
    $y = $x * [Math]::Sin($rz) + $y * [Math]::Cos($rz)
    return @(($nextX + [double]$origin[0]), ($y + [double]$origin[1]), ($z + [double]$origin[2]))
}

$textures = [ordered]@{}
$images = @()
for ($i = 0; $i -lt $bb.textures.Count; $i++) {
    $sourceImage = [string]$bb.textures[$i].source
    if (!$sourceImage.StartsWith('data:image/png;base64,')) { throw "Texture $i is not an embedded PNG." }
    $textures["tex$i"] = "crazywaterworld:$folder/${ItemId}_$i"
    $images += ,([Convert]::FromBase64String($sourceImage.Substring('data:image/png;base64,'.Length)))
}
$textures['particle'] = $textures['tex0']

$sides = @('north', 'east', 'south', 'west', 'up', 'down')
$elements = @(
    foreach ($cube in $bb.elements) {
        if ($cube.type -ne 'cube') { throw "Unsupported element: $($cube.name)" }
        if ($cube.export -eq $false) { continue }
        $faces = [ordered]@{}
        foreach ($side in $sides) {
            $face = $cube.faces.$side
            if ($null -eq $face -or $null -eq $face.texture -or [int]$face.texture -lt 0) { continue }
            $index = [int]$face.texture
            if ($index -ge $bb.textures.Count) { throw "Unknown texture $index in $($cube.name)." }
            $uv = @($face.uv | ForEach-Object { [double]$_ })
            if ($uv.Count -ne 4) { throw "Missing UV on $($cube.name)/$side." }
            $entry = [ordered]@{ uv = @(
                ($uv[0] * 16 / [double]$bb.resolution.width),
                ($uv[1] * 16 / [double]$bb.resolution.height),
                ($uv[2] * 16 / [double]$bb.resolution.width),
                ($uv[3] * 16 / [double]$bb.resolution.height)
            ); texture = "#tex$index" }
            if ($face.rotation) { $entry['rotation'] = [int]$face.rotation }
            $faces[$side] = $entry
        }
        $shift = if ($bb.meta.model_format -eq 'free') { @(8, 0, 8) } else { @(0, 0, 0) }
        $from = @(0..2 | ForEach-Object { [Math]::Round(([double]$cube.from[$_] + $shift[$_]), 4) })
        $to = @(0..2 | ForEach-Object { [Math]::Round(([double]$cube.to[$_] + $shift[$_]), 4) })
        for ($axis = 0; $axis -lt 3; $axis++) {
            if ($from[$axis] -gt $to[$axis]) {
                $from[$axis], $to[$axis] = $to[$axis], $from[$axis]
            }
        }
        $element = [ordered]@{ name = $cube.name; from = $from; to = $to; faces = $faces }
        if ($cube.shade -eq $false) { $element['shade'] = $false }
        $angles = @(0, 0, 0)
        if ($cube.rotation) { $angles = @($cube.rotation | ForEach-Object { [double]$_ }) }
        $active = @(0..2 | Where-Object { [Math]::Abs($angles[$_]) -gt 0.001 })
        if ($active.Count -eq 1 -and [Math]::Abs($angles[$active[0]]) -le 45) {
            $axis = $active[0]
            $allowed = @(-45, -22.5, 0, 22.5, 45)
            $angle = $allowed | Sort-Object { [Math]::Abs($_ - $angles[$axis]) } | Select-Object -First 1
            if ($angle -ne 0) {
                $origin = @(0..2 | ForEach-Object { [double]$cube.origin[$_] + $shift[$_] })
                $element['rotation'] = [ordered]@{ origin = $origin; axis = @('x', 'y', 'z')[$axis]; angle = $angle }
            }
        } elseif ($active.Count -gt 0) {
            # Vanilla model JSON supports only one axis and a few angles; use the rotated bounding box.
            $origin = @(0..2 | ForEach-Object { [double]$cube.origin[$_] + $shift[$_] })
            $corners = @(foreach ($x in @($from[0], $to[0])) {
                foreach ($y in @($from[1], $to[1])) {
                    foreach ($z in @($from[2], $to[2])) { ,(Rotate-Point @($x, $y, $z) $origin $angles) }
                }
            })
            $element['from'] = @(0..2 | ForEach-Object { $axis = $_; [Math]::Round([double](($corners | ForEach-Object { $_[$axis] } | Measure-Object -Minimum).Minimum), 4) })
            $element['to'] = @(0..2 | ForEach-Object { $axis = $_; [Math]::Round([double](($corners | ForEach-Object { $_[$axis] } | Measure-Object -Maximum).Maximum), 4) })
        }
        for ($axis = 0; $axis -lt 3; $axis++) {
            if ($element.from[$axis] -lt -16 -or $element.to[$axis] -gt 32 -or $element.from[$axis] -gt $element.to[$axis]) {
                throw "Invalid Minecraft element bounds on $($cube.name): from=$($element.from -join ',') to=$($element.to -join ',')."
            }
        }
        $element
    }
)

$model = [ordered]@{
    credit = 'Static conversion of the supplied Blockbench model.'
    textures = $textures
    elements = $elements
    display = [ordered]@{
        gui = [ordered]@{ rotation = @(30, 225, 0); translation = @(0, -1, 0); scale = @(0.65, 0.65, 0.65) }
        ground = [ordered]@{ translation = @(0, 2, 0); scale = @(0.5, 0.5, 0.5) }
        fixed = [ordered]@{ rotation = @(0, 180, 0); scale = @(0.75, 0.75, 0.75) }
        thirdperson_righthand = [ordered]@{ rotation = @(65, 45, 0); translation = @(0, 3, 1); scale = @(0.45, 0.45, 0.45) }
        thirdperson_lefthand = [ordered]@{ rotation = @(65, -45, 0); translation = @(0, 3, 1); scale = @(0.45, 0.45, 0.45) }
        firstperson_righthand = [ordered]@{ rotation = @(0, 45, 0); translation = @(0, 2, 0); scale = @(0.6, 0.6, 0.6) }
        firstperson_lefthand = [ordered]@{ rotation = @(0, -45, 0); translation = @(0, 2, 0); scale = @(0.6, 0.6, 0.6) }
    }
}

# Keep the display transforms authored with the refined source instead of resetting every export.
if ($bb.display) {
    foreach ($property in $bb.display.PSObject.Properties) { $model.display[$property.Name] = $property.Value }
}

if (!(Test-Path -LiteralPath $textureDir)) { New-Item -ItemType Directory -Path $textureDir | Out-Null }
$utf8 = New-Object System.Text.UTF8Encoding($false)
for ($i = 0; $i -lt $images.Count; $i++) {
    [System.IO.File]::WriteAllBytes((Join-Path $textureDir "${ItemId}_$i.png"), $images[$i])
}
[System.IO.File]::WriteAllText($modelPath, ($model | ConvertTo-Json -Depth 30), $utf8)
Write-Output "Exported $($elements.Count) cubes and $($images.Count) textures for $folder/$ItemId."
$bounds = @(0..2 | ForEach-Object {
    $axis = $_
    $low = ($elements | ForEach-Object { $_.from[$axis] } | Measure-Object -Minimum).Minimum
    $high = ($elements | ForEach-Object { $_.to[$axis] } | Measure-Object -Maximum).Maximum
    "$low..$high"
})
Write-Output "Model bounds (x/y/z): $($bounds -join ' / ')"
if ($bb.animations -and @($bb.animations).Count -gt 0) {
    Write-Warning "Blockbench animations are not supported by vanilla JSON models: $(@($bb.animations | ForEach-Object { $_.name }) -join ', ')"
}
