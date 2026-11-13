param([string]$Base)
Add-Type -AssemblyName System.Drawing
$cs = Join-Path $Base 'tools\BrandMark.cs'
Add-Type -TypeDefinition ([System.IO.File]::ReadAllText($cs, [System.Text.Encoding]::UTF8)) -ReferencedAssemblies System.Drawing
$icon = Join-Path $Base 'app\src\main\res\drawable-nodpi\ic_launcher_foreground.png'
[BrandMark]::Run($icon, 432)
Write-Output ('icon ' + (Get-Item $icon).Length)