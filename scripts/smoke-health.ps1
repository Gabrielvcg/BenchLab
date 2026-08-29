param(
  [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

$health = Invoke-RestMethod -Method GET -Uri "$BaseUrl/management/health"
if ($health.status -ne "UP") {
  throw "Health check failed. Status: $($health.status)"
}

Write-Host "API health is UP"
