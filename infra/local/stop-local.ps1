# Stops the MongoDB and Keycloak processes started by start-local.ps1. Data is kept.
#   powershell -ExecutionPolicy Bypass -File infra\local\stop-local.ps1
foreach ($port in 27017, 8080) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conn) {
        $proc = Get-Process -Id $conn.OwningProcess
        Write-Host "Stopping $($proc.ProcessName) (PID $($proc.Id)) on port $port"
        Stop-Process -Id $proc.Id -Force -Confirm:$false
    } else {
        Write-Host "Nothing running on port $port"
    }
}
