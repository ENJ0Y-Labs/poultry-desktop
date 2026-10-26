param(
    [string]$BaseUrl = "http://127.0.0.1:18942/api/v1/auth",
    [string]$Email = "auth-benchmark@example.invalid",
    [string]$Password = "Disposable-Benchmark-Password-2026!",
    [string]$FullName = "Disposable Auth Benchmark"
)

$ErrorActionPreference = "Stop"
$headers = @{ "X-Requested-With" = "XMLHttpRequest" }
$results = [System.Collections.Generic.List[object]]::new()
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logDirectory = Join-Path (Get-Location) "logs"
New-Item -ItemType Directory -Force $logDirectory | Out-Null
$outputPath = Join-Path $logDirectory "auth-benchmark-$stamp.csv"

function Invoke-TimedAuthRequest {
    param(
        [string]$Label,
        [string]$Uri,
        [string]$Body
    )

    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $statusCode = 0
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Post -Headers $headers -ContentType "application/json" -Body $Body
        $statusCode = [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        } else {
            throw
        }
    } finally {
        $watch.Stop()
    }

    $row = [pscustomobject]@{
        Timestamp = (Get-Date).ToString("o")
        Label = $Label
        StatusCode = $statusCode
        ElapsedMs = [Math]::Round($watch.Elapsed.TotalMilliseconds, 2)
    }
    $results.Add($row)
    Write-Host ("{0,-22} HTTP {1}  {2,8:N2} ms" -f $Label, $statusCode, $row.ElapsedMs)
    return $row
}

Write-Host "Auth timing benchmark"
Write-Host "Target: $BaseUrl"
Write-Host "Safety: use only a fresh disposable database. This script creates an owner account."
Write-Host ""

$setupBody = @{
    email = $Email
    password = $Password
    fullName = $FullName
} | ConvertTo-Json -Compress

$firstSetup = Invoke-TimedAuthRequest -Label "setup-first-request" -Uri "$BaseUrl/setup" -Body $setupBody
if ($firstSetup.StatusCode -lt 200 -or $firstSetup.StatusCode -ge 300) {
    throw "Initial setup did not succeed (HTTP $($firstSetup.StatusCode)). Use a fresh disposable database; do not reset a real farm database."
}

for ($i = 1; $i -le 3; $i++) {
    Invoke-TimedAuthRequest -Label "setup-duplicate-$i" -Uri "$BaseUrl/setup" -Body $setupBody | Out-Null
}

$loginBody = @{
    email = $Email
    password = $Password
} | ConvertTo-Json -Compress

for ($i = 1; $i -le 5; $i++) {
    Invoke-TimedAuthRequest -Label "login-$i" -Uri "$BaseUrl/login" -Body $loginBody | Out-Null
}

$results | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $outputPath
Write-Host ""
Write-Host "Saved request timings to $outputPath"
Write-Host "Correlate these rows with backend.log entries named auth_setup_timing and auth_login_timing."
Write-Host "The CSV records HTTP request duration; backend logs break out hashing and SQLite stages."
