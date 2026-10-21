# Grants the oh-auth-gtwy-service client permission to issue tokens on behalf of users
# (used by /api/v1/auth/otp/verify after the gateway has checked the code). Keycloak must run
# with --features=token-exchange,admin-fine-grained-authz. Safe to run repeatedly.
param(
    [string]$KeycloakUrl = 'http://localhost:8080',
    [string]$Realm = 'onehostel',
    [string]$AdminUser = $env:KC_BOOTSTRAP_ADMIN_USERNAME,
    [string]$AdminPassword = $env:KC_BOOTSTRAP_ADMIN_PASSWORD,
    [string]$ServiceClientId = 'oh-auth-gtwy-service'
)

$ErrorActionPreference = 'Stop'
if (-not $AdminUser -or -not $AdminPassword) {
    throw 'Keycloak admin credentials missing: set KC_BOOTSTRAP_ADMIN_USERNAME/PASSWORD (start-local.ps1 loads them from .env) or pass -AdminUser/-AdminPassword.'
}

$token = (Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
    -ContentType 'application/x-www-form-urlencoded' `
    -Body @{ grant_type = 'password'; client_id = 'admin-cli'; username = $AdminUser; password = $AdminPassword }).access_token
$h = @{ Authorization = "Bearer $token" }
$api = "$KeycloakUrl/admin/realms/$Realm"

function Get-ClientUuid([string]$ClientId) {
    (Invoke-RestMethod -Uri "$api/clients?clientId=$ClientId" -Headers $h)[0].id
}

$serviceUuid = Get-ClientUuid $ServiceClientId
$rmUuid = Get-ClientUuid 'realm-management'
$authz = "$api/clients/$rmUuid/authz/resource-server"

# Turning user permissions on creates realm-management's authorization settings, so do it first
$usersPerms = Invoke-RestMethod -Method Put -Uri "$api/users-management-permissions" -Headers $h -ContentType 'application/json' -Body '{"enabled":true}'

# One client policy: "the request comes from oh-auth-gtwy-service"
$policyName = "$ServiceClientId-policy"
$policy = Invoke-RestMethod -Uri "$authz/policy?name=$policyName&exact=true" -Headers $h | Select-Object -First 1
if (-not $policy) {
    $policy = Invoke-RestMethod -Method Post -Uri "$authz/policy/client" -Headers $h -ContentType 'application/json' `
        -Body (@{ name = $policyName; clients = @($serviceUuid); logic = 'POSITIVE'; decisionStrategy = 'UNANIMOUS' } | ConvertTo-Json)
}

# Users -> impersonate: lets the service client mint tokens for a user (requested_subject)
$permId = $usersPerms.scopePermissions.impersonate
$perm = Invoke-RestMethod -Uri "$authz/permission/scope/$permId" -Headers $h
$perm | Add-Member -NotePropertyName policies -NotePropertyValue @($policy.id) -Force
$perm.decisionStrategy = 'AFFIRMATIVE'
Invoke-RestMethod -Method Put -Uri "$authz/permission/scope/$permId" -Headers $h -ContentType 'application/json' `
    -Body ($perm | ConvertTo-Json -Depth 5) | Out-Null

Write-Host "Token exchange configured: $ServiceClientId may issue tokens for users"
