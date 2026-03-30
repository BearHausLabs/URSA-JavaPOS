$body = @'
[{"type": "TEXT", "data": "=== DIAGNOSTIC TEST ===\nPrinter is working!\n\n\n\n"}]
'@

try {
    $r = Invoke-WebRequest -Uri http://localhost:1994/v1/print -Method POST -ContentType "application/json" -Body $body -UseBasicParsing
    Write-Host "STATUS: $($r.StatusCode)"
    Write-Host $r.Content
} catch {
    Write-Host "ERROR: $($_.Exception.Response.StatusCode.value__)"
    Write-Host $_.Exception.Message
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    $reader.BaseStream.Position = 0
    Write-Host $reader.ReadToEnd()
}
