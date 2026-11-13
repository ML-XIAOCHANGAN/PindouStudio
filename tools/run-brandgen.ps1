param([string]$Base)
Add-Type -AssemblyName System.Drawing
$cs = Join-Path $Base 'tools\BrandGen.cs'
Add-Type -TypeDefinition ([System.IO.File]::ReadAllText($cs, [System.Text.Encoding]::UTF8)) -ReferencedAssemblies System.Drawing
$logo = Join-Path $Base 'app\src\main\res\drawable-nodpi\brand_logo.png'
$icon = Join-Path $Base 'app\src\main\res\drawable-nodpi\ic_launcher_foreground.png'
[BrandGen]::Run($logo, $icon)
Write-Output ("ok " + (Get-Item $logo).Length + " " + (Get-Item $icon).Length)
