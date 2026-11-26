<#
.SYNOPSIS
  Levanta RutaExpress completo en el Docker de esta maquina y abre el frontend en el navegador.

.DESCRIPTION
  Usa el compose.yaml de la raiz: frontend-v2, los 8 microservicios, Oracle, Redis, Mailpit,
  RabbitMQ (2 nodos), Kafka (3 brokers), ZooKeeper (3 nodos) y Kafka UI.
  Funciona con Docker Desktop o con Docker instalado dentro de WSL (por ejemplo, en Ubuntu).
  El login sigue usando Microsoft Entra ID, asi que se necesita internet.

  Uso:      .\deploy-local.ps1
  Si Windows bloquea el script:
            powershell -ExecutionPolicy Bypass -File .\deploy-local.ps1
#>

Set-Location -LiteralPath $PSScriptRoot

# true cuando Docker vive dentro de WSL en vez de Docker Desktop.
$script:UseWsl = $false

function Invoke-Docker {
    if ($script:UseWsl) { wsl.exe --cd $PSScriptRoot -e docker @args } else { docker @args }
}

function Test-DockerEngine {
    Invoke-Docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
    return ($LASTEXITCODE -eq 0)
}

function Wait-DockerEngine([int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while (-not (Test-DockerEngine)) {
        if ((Get-Date) -gt $deadline) { return $false }
        Start-Sleep -Seconds 5
    }
    return $true
}

function Stop-WithError([string]$message) {
    Write-Host $message -ForegroundColor Red
    exit 1
}

# 1) Encontrar Docker: primero el de Windows, si no, el de WSL.
$dockerDesktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
$windowsDocker = [bool](Get-Command docker -ErrorAction SilentlyContinue)
$wslDocker = $false
if (Get-Command wsl.exe -ErrorAction SilentlyContinue) {
    wsl.exe -e sh -c 'command -v docker' 2>$null | Out-Null
    $wslDocker = ($LASTEXITCODE -eq 0)
}

if ($windowsDocker -and (Test-DockerEngine)) {
    Write-Host 'Usando Docker de Windows.' -ForegroundColor Cyan
} elseif ($wslDocker) {
    $script:UseWsl = $true
    Write-Host 'Usando Docker dentro de WSL.' -ForegroundColor Cyan
    # WSL apaga la distro cuando no queda ninguna sesion abierta, y con ella los contenedores.
    # Este proceso oculto la mantiene encendida hasta que se ejecute "wsl --shutdown".
    $keepAlive = Get-CimInstance Win32_Process -Filter "Name = 'wsl.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*sleep infinity*' }
    if (-not $keepAlive) {
        Start-Process -FilePath 'wsl.exe' -ArgumentList '-e', 'sleep', 'infinity' -WindowStyle Hidden
    }
    if (-not (Wait-DockerEngine 90)) {
        Stop-WithError 'Docker no responde dentro de WSL. Prueba con: wsl -u root -e systemctl start docker'
    }
} elseif ($windowsDocker -and (Test-Path -LiteralPath $dockerDesktop)) {
    Write-Host 'Iniciando Docker Desktop...' -ForegroundColor Cyan
    Start-Process -FilePath $dockerDesktop
    if (-not (Wait-DockerEngine 180)) {
        Stop-WithError 'Docker Desktop no respondio en 3 minutos. Espera a que termine de iniciar y vuelve a intentarlo.'
    }
} else {
    Stop-WithError 'No se encontro Docker ni en Windows ni en WSL. Instala Docker Desktop o Docker dentro de WSL.'
}

# 2) Requisitos del motor: contenedores Linux y Compose v2.
$osType = Invoke-Docker info --format '{{.OSType}}' 2>$null
if ($osType -ne 'linux') {
    Stop-WithError 'Docker debe usar contenedores Linux (menu de Docker Desktop: Switch to Linux containers).'
}

Invoke-Docker compose version 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Stop-WithError 'Se necesita Docker Compose v2 (comando "docker compose").'
}

# 3) Aviso de memoria: la suma de los limites del compose ronda los 14 GiB.
$memBytes = 0.0
if ([double]::TryParse((Invoke-Docker info --format '{{.MemTotal}}' 2>$null), [ref]$memBytes) -and $memBytes -gt 0) {
    $memGiB = [math]::Round($memBytes / 1GB, 1)
    if ($memGiB -lt 15) {
        $hint = 'Ajustalo en Docker Desktop > Settings > Resources.'
        if ($script:UseWsl) { $hint = 'En WSL se ajusta en la seccion [wsl2] de %UserProfile%\.wslconfig (por ejemplo memory=12GB en un equipo de 16 GB) y luego wsl --shutdown.' }
        Write-Host "Aviso: Docker tiene $memGiB GiB de RAM y se recomiendan 16 GiB. $hint" -ForegroundColor Yellow
    }
}

# 4) Puerto del frontend: el mismo que usa compose.yaml (FRONTEND_PORT en .env, o 4200).
$port = '4200'
if (Test-Path -LiteralPath '.env') {
    $match = Select-String -LiteralPath '.env' -Pattern '^\s*FRONTEND_PORT\s*=\s*(\d+)\s*$' | Select-Object -First 1
    if ($match) { $port = $match.Matches[0].Groups[1].Value }
}
# Debe ser localhost: es la redirect URI registrada en Entra ID.
$url = "http://localhost:$port"

# 5) Construir y levantar; --wait espera a que todos los healthchecks pasen.
$prefix = ''
if ($script:UseWsl) { $prefix = 'wsl ' }
Write-Host 'Construyendo y levantando RutaExpress. La primera vez tarda varios minutos...' -ForegroundColor Cyan
Invoke-Docker compose up -d --build --wait --wait-timeout 600
if ($LASTEXITCODE -ne 0) {
    Write-Host ''
    Write-Host 'Algunos servicios no quedaron sanos. Estado actual:' -ForegroundColor Red
    # -a incluye los contenedores creados, detenidos o caidos, que son los que importan aqui.
    Invoke-Docker compose ps -a
    Stop-WithError "Revisa los logs con: ${prefix}docker compose logs <servicio>"
}

Invoke-Docker compose ps
Write-Host ''
Write-Host "RutaExpress listo en $url" -ForegroundColor Green
Write-Host '  Kafka UI:             http://localhost:8090'
Write-Host '  RabbitMQ nodo 1 y 2:  http://localhost:15672  y  http://localhost:15673'
Write-Host '  Mailpit (correos):    http://localhost:8025'
Write-Host "  Comandos de Docker:   ${prefix}docker compose ps   (desde esta carpeta)"
Write-Host "  Detener sin borrar datos: ${prefix}docker compose down"
if ($script:UseWsl) { Write-Host '  Liberar la memoria de WSL al terminar: wsl --shutdown' }

Start-Process $url
