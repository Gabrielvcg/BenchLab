param(
  [string]$OutDir = ".\\backups",
  [string]$Container = "benchlab-vps-postgresql-1"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force $OutDir | Out-Null

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$tmpPath = "/tmp/benchlab-$stamp.sql"
$localPath = Join-Path $OutDir "benchlab-$stamp.sql"

docker exec $Container sh -lc "pg_dump -U benchLab -d benchLab > $tmpPath"
docker cp "${Container}:$tmpPath" $localPath
docker exec $Container rm -f $tmpPath

Write-Host "Backup created at $localPath"
