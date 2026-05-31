param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$User = "admin",
  [string]$Password = "admin",
  [int]$Iterations = 5
)

$ErrorActionPreference = "Stop"

function Invoke-JsonRequest {
  param(
    [string]$Method,
    [string]$Url,
    [object]$Body = $null,
    [hashtable]$Headers = @{}
  )

  if ($null -ne $Body) {
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -Body ($Body | ConvertTo-Json -Depth 10) -ContentType "application/json"
  }
  return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers
}

Write-Host "[1/7] Authenticating..."
$auth = Invoke-JsonRequest -Method POST -Url "$BaseUrl/api/authenticate" -Body @{ username = $User; password = $Password }
$token = $auth.id_token
if (-not $token) { throw "No JWT token returned" }
$headers = @{ Authorization = "Bearer $token" }

Write-Host "[2/7] Creating algorithm..."
$algorithm = Invoke-JsonRequest -Method POST -Url "$BaseUrl/api/algorithms" -Headers $headers -Body @{
  name = "quick-sort-smoke"
  category = "sorting"
  version = "v1"
  complexityDeclared = "O(n log n)"
}

Write-Host "[3/7] Creating dataset..."
$dataset = Invoke-JsonRequest -Method POST -Url "$BaseUrl/api/datasets" -Headers $headers -Body @{
  type = "random-int-array"
  sizeValue = 1000
  seed = 42
  checksum = "smoke-checksum"
  datasetVersion = "v1"
}

Write-Host "[4/7] Creating implementation..."
$implementation = Invoke-JsonRequest -Method POST -Url "$BaseUrl/api/implementations" -Headers $headers -Body @{
  algorithmId = $algorithm.id
  language = "PYTHON"
  sourceCode = "print('benchlab smoke')"
  compileConfig = ""
  runtimeConfig = ""
}

Write-Host "[5/7] Creating run..."
$run = Invoke-JsonRequest -Method POST -Url "$BaseUrl/api/runs" -Headers $headers -Body @{
  implementationId = $implementation.id
  datasetId = $dataset.id
  timeoutMs = 5000
  memoryMb = 128
  cpuLimit = 1.0
  iterations = $Iterations
}

$runId = $run.id
Write-Host "Run queued with id: $runId"

Write-Host "[6/7] Polling run status..."
$maxAttempts = 30
for ($i = 1; $i -le $maxAttempts; $i++) {
  Start-Sleep -Seconds 2
  $state = Invoke-JsonRequest -Method GET -Url "$BaseUrl/api/runs/$runId" -Headers $headers
  Write-Host "Attempt $i -> status=$($state.status)"
  if ($state.status -in @("SUCCEEDED", "FAILED", "TIMEOUT", "COMPILE_ERROR", "RUNTIME_ERROR")) {
    break
  }
}

Write-Host "[7/7] Getting benchmark comparison..."
$comparison = Invoke-JsonRequest -Method GET -Url "$BaseUrl/api/benchmarks/compare?algorithmId=$($algorithm.id)&datasetId=$($dataset.id)" -Headers $headers
$series = Invoke-JsonRequest -Method GET -Url "$BaseUrl/api/benchmarks/timeseries?algorithmId=$($algorithm.id)&language=PYTHON" -Headers $headers

Write-Host "Smoke test completed"
Write-Host ("Final run status: " + $state.status)
$comparison | ConvertTo-Json -Depth 10
$series | ConvertTo-Json -Depth 10
