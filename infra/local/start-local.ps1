# Starts MongoDB (27017) and Keycloak (8080) for oh-auth-gtwy without Docker or admin rights.
# Binaries live outside the repo in $InfraDir; they are downloaded and unpacked on first run.
#   powershell -ExecutionPolicy Bypass -File infra\local\start-local.ps1
# Add -ResetKeycloak to wipe Keycloak's data and re-import keycloak\onehostel-realm.json
# (needed after editing the realm file; Keycloak only imports a realm that doesn't exist yet).
param(
    [string]$InfraDir = "$env:USERPROFILE\onehostel-infra",
    [switch]$ResetKeycloak
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$here = $PSScriptRoot

# Secrets come from the git-ignored .env in the repo root (copy .env.example to .env and fill it in).
# Values already set in the environment win over the file.
$envFile = Join-Path (Resolve-Path "$here\..\..") '.env'
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$' -and -not [Environment]::GetEnvironmentVariable($Matches[1])) {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2])
        }
    }
}
$missing = 'KC_BOOTSTRAP_ADMIN_USERNAME', 'KC_BOOTSTRAP_ADMIN_PASSWORD', 'KEYCLOAK_SERVICE_CLIENT_SECRET', 'SUPERADMIN_PASSWORD' |
    Where-Object { -not [Environment]::GetEnvironmentVariable($_) }
if ($missing) {
    throw "Missing $($missing -join ', '). Copy .env.example to .env in the repo root and fill it in."
}

$KeycloakVersion = '26.0.7'
$MongoVersion = '7.0.14'
$MongoshVersion = '2.3.2'

$downloads = @{
    "keycloak-$KeycloakVersion.zip"            = "https://github.com/keycloak/keycloak/releases/download/$KeycloakVersion/keycloak-$KeycloakVersion.zip"
    "mongodb-windows-x86_64-$MongoVersion.zip" = "https://fastdl.mongodb.org/windows/mongodb-windows-x86_64-$MongoVersion.zip"
    "mongosh-$MongoshVersion-win32-x64.zip"    = "https://downloads.mongodb.com/compass/mongosh-$MongoshVersion-win32-x64.zip"
}

function Test-Port([int]$Port) {
    [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Get-Tool([string]$Zip, [string]$Pattern) {
    $zipPath = Join-Path "$InfraDir\downloads" $Zip
    if (-not (Test-Path $zipPath)) {
        Write-Host "Downloading $Zip ..."
        New-Item -ItemType Directory -Force "$InfraDir\downloads" | Out-Null
        Invoke-WebRequest -Uri $downloads[$Zip] -OutFile $zipPath -UseBasicParsing
    }
    $dir = Get-ChildItem $InfraDir -Directory | Where-Object Name -like $Pattern | Select-Object -First 1
    if (-not $dir) {
        Write-Host "Unpacking $Zip ..."
        Expand-Archive -Path $zipPath -DestinationPath $InfraDir
        $dir = Get-ChildItem $InfraDir -Directory | Where-Object Name -like $Pattern | Select-Object -First 1
    }
    $dir.FullName
}

function Wait-Until([scriptblock]$Check, [string]$What, [int]$TimeoutSec) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while (-not (& $Check)) {
        if ((Get-Date) -gt $deadline) { throw "$What did not become ready within $TimeoutSec s" }
        Start-Sleep -Seconds 2
    }
}

New-Item -ItemType Directory -Force "$InfraDir\logs", "$InfraDir\mongo-data" | Out-Null

# --- MongoDB ---
$mongoHome = Get-Tool "mongodb-windows-x86_64-$MongoVersion.zip" "mongodb-win32-x86_64-windows-$MongoVersion*"
$mongoshHome = Get-Tool "mongosh-$MongoshVersion-win32-x64.zip" "mongosh-$MongoshVersion-win32-x64*"

if (Test-Port 27017) {
    Write-Host "MongoDB already running on 27017"
} else {
    Write-Host "Starting MongoDB on 27017 ..."
    Start-Process -FilePath "$mongoHome\bin\mongod.exe" -WindowStyle Hidden -ArgumentList @(
        '--dbpath', "`"$InfraDir\mongo-data`"", '--port', '27017', '--bind_ip', '127.0.0.1',
        '--logpath', "`"$InfraDir\logs\mongod.log`"")
    Wait-Until { Test-Port 27017 } 'MongoDB' 60
}

Write-Host "Seeding super admin ..."
& "$mongoshHome\bin\mongosh.exe" --quiet 'mongodb://127.0.0.1:27017' "$here\mongo\seed-users.js"
if ($LASTEXITCODE -ne 0) { throw 'Seeding MongoDB failed' }

# --- Keycloak ---
$kcHome = Get-Tool "keycloak-$KeycloakVersion.zip" "keycloak-$KeycloakVersion"
New-Item -ItemType Directory -Force "$kcHome\data\import" | Out-Null
Copy-Item "$here\keycloak\onehostel-realm.json" "$kcHome\data\import\" -Force

if ($ResetKeycloak) {
    $kc = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($kc) {
        Write-Host 'Stopping Keycloak for reset ...'
        Stop-Process -Id $kc.OwningProcess -Force
        Wait-Until { -not (Test-Port 8080) } 'Keycloak shutdown' 30
    }
    Remove-Item "$kcHome\data\h2" -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host 'Keycloak data wiped; the realm will be re-imported'
}

if (Test-Port 8080) {
    Write-Host "Something is already listening on 8080 (assuming Keycloak)"
} else {
    # Keycloak reads KC_BOOTSTRAP_ADMIN_* and fills ${...} placeholders in the realm file from this environment
    Write-Host "Starting Keycloak on 8080 (first start takes about a minute) ..."
    Start-Process -FilePath "$kcHome\bin\kc.bat" -WindowStyle Hidden `
        -ArgumentList 'start-dev', '--http-port=8080', '--import-realm', '"--features=token-exchange,admin-fine-grained-authz"' `
        -RedirectStandardOutput "$InfraDir\logs\keycloak.log" `
        -RedirectStandardError "$InfraDir\logs\keycloak-error.log"
}
Wait-Until {
    try { (Invoke-WebRequest 'http://localhost:8080/realms/onehostel/.well-known/openid-configuration' -UseBasicParsing -TimeoutSec 5).StatusCode -eq 200 } catch { $false }
} 'Keycloak realm onehostel' 240

& "$here\keycloak\configure-token-exchange.ps1"

Write-Host ''
Write-Host 'Ready:'
Write-Host '  MongoDB   mongodb://localhost:27017/onehostel_db'
Write-Host "  Keycloak  http://localhost:8080  (admin console user: $env:KC_BOOTSTRAP_ADMIN_USERNAME, password in .env)"
Write-Host '  Login     super.admin (password = SUPERADMIN_PASSWORD in .env; only seeded account)'
Write-Host "  Logs      $InfraDir\logs"
