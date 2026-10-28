# Starts PlotPack on this machine: infrastructure in Docker, the 4 services as local JVMs (small heaps),
# then the React dev server. Stop everything with:  .\run-local.ps1 -Stop
param([switch]$Stop, [switch]$NoBuild)

$root = $PSScriptRoot
$services = "user-service", "package-service", "notification-service", "api-gateway"
$logs = Join-Path $root "logs"
$javaHome = "C:\Program Files\Java\jdk-23"
$mvn = "D:\tools\apache-maven-3.9.9\bin\mvn.cmd"

function Stop-Services {
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
        Where-Object { $_.CommandLine -match "plotpack|user-service-1|package-service-1|notification-service-1|api-gateway-1" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

if ($Stop) {
    Stop-Services
    docker compose -f "$root\docker-compose.yml" stop mysql redis kafka
    "Stopped."
    return
}

Stop-Services
docker compose -f "$root\docker-compose.yml" up -d mysql redis kafka

if (-not $NoBuild) {
    $env:JAVA_HOME = $javaHome
    & $mvn -B -q "-Dmaven.repo.local=D:\tools\m2" -DskipTests -f "$root\pom.xml" package
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
}

New-Item -ItemType Directory -Force $logs | Out-Null
foreach ($s in $services) {
    $jar = "$root\$s\target\$s-1.0.0.jar"
    $args = "-Xms64m", "-Xmx256m", "-XX:ReservedCodeCacheSize=64m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-jar", "`"$jar`""
    Start-Process -FilePath "$javaHome\bin\java.exe" -ArgumentList $args -WindowStyle Hidden `
        -RedirectStandardOutput "$logs\$s.log" -RedirectStandardError "$logs\$s.err"
    "started $s"
}

"Waiting for the gateway on http://localhost:8090 ..."
for ($i = 0; $i -lt 60; $i++) {
    try { if ((Invoke-WebRequest -UseBasicParsing http://localhost:8090/actuator/health -TimeoutSec 2).StatusCode -eq 200) { break } } catch {}
    Start-Sleep 2
}
"Backend up. Starting the React UI on http://localhost:5173"
Set-Location "$root\frontend"
if (-not (Test-Path node_modules)) { npm install --no-audit --no-fund }
npm run dev
