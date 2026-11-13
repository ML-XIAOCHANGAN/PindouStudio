param([string]$Path)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
$entry = $zip.Entries | Where-Object { $_.FullName -eq 'word/document.xml' }
if (-not $entry) { Write-Output 'no document.xml'; $zip.Dispose(); exit }
$sr = New-Object System.IO.StreamReader($entry.Open())
$xml = $sr.ReadToEnd()
$sr.Close(); $zip.Dispose()
$nl = [string][char]10
$xml = $xml -replace '</w:p>', $nl
$xml = $xml -replace '</w:tc>', ' | '
$xml = $xml -replace '</w:tr>', $nl
$xml = $xml -replace '<w:br[^>]*/>', $nl
$xml = $xml -replace '<w:tab[^>]*/>', '    '
$text = [System.Text.RegularExpressions.Regex]::Replace($xml, '<[^>]+>', '')
$text = $text -replace '&lt;', '<' -replace '&gt;', '>' -replace '&amp;', '&' -replace '&quot;', [string][char]34
$arr = $text -split $nl | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne '' -and $_ -ne '|' }
Write-Output ('LINES: ' + $arr.Count)
$arr | ForEach-Object { Write-Output $_ }