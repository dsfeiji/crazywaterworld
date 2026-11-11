param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$ItemId
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $project 'src\main\resources\assets\crazywaterworld'
$modelPath = Join-Path $assets "models\item\$ItemId.json"
$texturePath = Join-Path $assets "textures\item\$ItemId.png"

if ($ItemId -notmatch '^[a-z0-9_]+$') { throw 'ItemId must contain only lowercase letters, numbers, and underscores.' }
if (!(Test-Path -LiteralPath $Source)) { throw "Model not found: $Source" }
if (!(Test-Path -LiteralPath (Split-Path $modelPath))) { throw 'Expected models/item directory is missing.' }
New-Item -ItemType Directory -Force -Path (Split-Path $texturePath) | Out-Null

$bb = Get-Content -LiteralPath $Source -Raw -Encoding UTF8 | ConvertFrom-Json
$prefix = 'data:image/png;base64,'
if ($bb.textures.Count -ne 1 -or !([string]$bb.textures[0].source).StartsWith($prefix)) {
    throw 'Expected exactly one embedded PNG texture.'
}
if (!$bb.resolution.width -or !$bb.resolution.height) { throw 'Missing texture resolution.' }

foreach ($group in $bb.groups) {
    if (@($group.rotation | Where-Object { [double]$_ -ne 0 }).Count -gt 0) {
        throw "Group $($group.name) is rotated; flatten it in Blockbench before exporting."
    }
}

$faces = @('north', 'east', 'south', 'west', 'up', 'down')
$elements = @(
    foreach ($cube in $bb.elements) {
        if ($cube.type -ne 'cube') { throw "Unsupported element: $($cube.name) ($($cube.type))" }
        if ($cube.rotation) { throw "Cube $($cube.name) is rotated; flatten it in Blockbench first." }

        $modelFaces = [ordered]@{}
        foreach ($side in $faces) {
            $face = $cube.faces.$side
            if ($null -eq $face -or $null -eq $face.texture -or [int]$face.texture -lt 0) { continue }
            if ([int]$face.texture -ne 0) { throw "Unexpected texture index in $($cube.name)" }
            $uv = @(
                ([double]$face.uv[0] * 16 / [double]$bb.resolution.width),
                ([double]$face.uv[1] * 16 / [double]$bb.resolution.height),
                ([double]$face.uv[2] * 16 / [double]$bb.resolution.width),
                ([double]$face.uv[3] * 16 / [double]$bb.resolution.height)
            )
            $modelFaces[$side] = [ordered]@{ uv = $uv; texture = '#clam' }
        }
        $from = @($cube.from | ForEach-Object { [double]$_ + 8 })
        $to = @($cube.to | ForEach-Object { [double]$_ + 8 })
        for ($axis = 0; $axis -lt 3; $axis++) {
            if ($from[$axis] -lt -16 -or $to[$axis] -gt 32 -or $from[$axis] -gt $to[$axis]) {
                throw "Cube $($cube.name) is outside Minecraft's model limits."
            }
        }
        [ordered]@{ from = $from; to = $to; faces = $modelFaces }
    }
)

$model = [ordered]@{
    credit = 'Converted from the supplied Blockbench model; static pose only.'
    textures = [ordered]@{
        clam = "crazywaterworld:item/$ItemId"
        particle = "crazywaterworld:item/$ItemId"
    }
    elements = $elements
    display = [ordered]@{
        gui = [ordered]@{ rotation = @(30, 225, 0); translation = @(0, -1, 0); scale = @(0.8, 0.8, 0.8) }
        ground = [ordered]@{ translation = @(0, 2, 0); scale = @(0.65, 0.65, 0.65) }
        fixed = [ordered]@{ rotation = @(0, 180, 0); scale = @(0.9, 0.9, 0.9) }
        thirdperson_righthand = [ordered]@{ rotation = @(65, 45, 0); translation = @(0, 3, 1); scale = @(0.55, 0.55, 0.55) }
        thirdperson_lefthand = [ordered]@{ rotation = @(65, -45, 0); translation = @(0, 3, 1); scale = @(0.55, 0.55, 0.55) }
        firstperson_righthand = [ordered]@{ rotation = @(0, 45, 0); translation = @(0, 2, 0); scale = @(0.8, 0.8, 0.8) }
        firstperson_lefthand = [ordered]@{ rotation = @(0, -45, 0); translation = @(0, 2, 0); scale = @(0.8, 0.8, 0.8) }
    }
}

if ($bb.display) {
    foreach ($property in $bb.display.PSObject.Properties) { $model.display[$property.Name] = $property.Value }
}

function Get-CubeIds($group) {
    foreach ($child in $group.children) {
        if ($child -is [string]) { $child }
        else { Get-CubeIds $child }
    }
}

$root = $bb.outliner[0]
$bottomId = ($bb.groups | Where-Object { $_.name -eq 'bottom_shell' } | Select-Object -First 1).uuid
$hinge = $bb.groups | Where-Object { $_.name -eq 'hinge' } | Select-Object -First 1
$bottom = $root.children | Where-Object { $_.uuid -eq $bottomId } | Select-Object -First 1
$lid = $root.children | Where-Object { $_.uuid -eq $hinge.uuid } | Select-Object -First 1
if (!$bottom -or !$lid) { throw 'Expected bottom_shell and hinge groups for animated clam.' }

$byId = @{}
for ($i = 0; $i -lt $bb.elements.Count; $i++) { $byId[$bb.elements[$i].uuid] = $elements[$i] }
$bodyElements = @(Get-CubeIds $bottom | ForEach-Object { $byId[$_] })
$lidElements = @(
    Get-CubeIds $lid | ForEach-Object {
        $sourceElement = $byId[$_]
        $from = @(for ($axis = 0; $axis -lt 3; $axis++) { [double]$sourceElement.from[$axis] - [double]$hinge.origin[$axis] })
        $to = @(for ($axis = 0; $axis -lt 3; $axis++) { [double]$sourceElement.to[$axis] - [double]$hinge.origin[$axis] })
        [ordered]@{ from = $from; to = $to; faces = $sourceElement.faces }
    }
)
if ($bodyElements.Count -eq 0 -or $lidElements.Count -eq 0) { throw 'Clam body or lid has no cubes.' }

$lidClosed = @(Get-CubeIds $lid | ForEach-Object { $byId[$_] })
$model.elements = @($bodyElements) + @($lidClosed)
$visualBody = [ordered]@{ textures = $model.textures; elements = $bodyElements }
$visualLid = [ordered]@{ textures = $model.textures; elements = $lidElements }

$image = [Convert]::FromBase64String(([string]$bb.textures[0].source).Substring($prefix.Length))
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllBytes($texturePath, $image)
[System.IO.File]::WriteAllText($modelPath, ($model | ConvertTo-Json -Depth 30), $utf8)
[System.IO.File]::WriteAllText((Join-Path $assets "models\item\${ItemId}_body_visual.json"), ($visualBody | ConvertTo-Json -Depth 30), $utf8)
[System.IO.File]::WriteAllText((Join-Path $assets "models\item\${ItemId}_lid_visual.json"), ($visualLid | ConvertTo-Json -Depth 30), $utf8)
Write-Output "Exported $($model.elements.Count) cubes ($($bodyElements.Count) body, $($lidElements.Count) lid) and $($image.Length)-byte PNG for $ItemId."
if ($bb.animations.Count -gt 0) { Write-Warning 'Blockbench animations are not supported by vanilla item JSON and were not exported.' }
