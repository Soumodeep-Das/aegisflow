[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateRange(5, 120)][int]$TimeoutSeconds = 60
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) { throw 'Set JAVA_HOME to a Java 21 JDK before running this check.' }
$javaExecutable = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable)) { throw "Java executable missing: $javaExecutable" }
$version = (& $javaExecutable -version 2>&1 | Out-String)
if ($version -notmatch 'version "21\.') { throw 'This foundation check requires Java 21.' }

$applications = @(
    @{ Name = 'core'; Jar = 'control-plane/target/aegisflow-core-0.1.0-SNAPSHOT.jar' },
    @{ Name = 'order'; Jar = 'simulators/order-service/target/order-service-0.1.0-SNAPSHOT.jar' },
    @{ Name = 'payment'; Jar = 'simulators/payment-service/target/payment-service-0.1.0-SNAPSHOT.jar' },
    @{ Name = 'warehouse'; Jar = 'simulators/warehouse-service/target/warehouse-service-0.1.0-SNAPSHOT.jar' }
)
$logDirectory = Join-Path $repositoryRoot '.cache/startup'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$ownedProcesses = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
$results = [System.Collections.Generic.List[object]]::new()

try {
    foreach ($application in $applications) {
        $jar = Join-Path $repositoryRoot $application.Jar
        if (-not (Test-Path -LiteralPath $jar)) { throw "Build the runnable JAR first: $jar" }
        $outputLog = Join-Path $logDirectory ($application.Name + '.stdout.log')
        $errorLog = Join-Path $logDirectory ($application.Name + '.stderr.log')
        # Ephemeral ports and process-owned logs ensure a pre-existing service cannot satisfy this check.
        $process = Start-Process -FilePath $javaExecutable -WindowStyle Hidden -PassThru `
            -ArgumentList @('-jar', ('"' + $jar + '"'), '--server.port=0', '--spring.profiles.active=foundation') `
            -WorkingDirectory $repositoryRoot -RedirectStandardOutput $outputLog -RedirectStandardError $errorLog
        $ownedProcesses.Add($process)
        $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
        $port = $null
        while ([DateTimeOffset]::UtcNow -lt $deadline) {
            if ($process.HasExited) { throw "$($application.Name) exited before readiness; inspect $errorLog and $outputLog" }
            $log = Get-Content -Raw -LiteralPath $outputLog -ErrorAction SilentlyContinue
            if ($log -match 'Tomcat started on port (\d+)') { $port = [int]$Matches[1]; break }
            Start-Sleep -Milliseconds 250
        }
        if (-not $port) { throw "$($application.Name) did not bind within $TimeoutSeconds seconds" }
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$port/actuator/health" -TimeoutSec 5
        # A JSON response without a charset can be returned as bytes by PowerShell.
        $content = if ($response.Content -is [byte[]]) {
            [System.Text.Encoding]::UTF8.GetString($response.Content)
        } else { [string]$response.Content }
        $health = $content | ConvertFrom-Json
        if ($response.StatusCode -ne 200 -or $health.status -ne 'UP') {
            throw "$($application.Name) health check failed: HTTP $($response.StatusCode), status=$($health.status)"
        }
        if ($health.PSObject.Properties.Name -contains 'components' -or $health.PSObject.Properties.Name -contains 'details') {
            throw "$($application.Name) exposed internal health details"
        }
        $results.Add([pscustomobject]@{
            Application = $application.Name; ProcessId = $process.Id; Port = $port
            HttpStatus = $response.StatusCode; Health = $health.status; Profile = 'foundation'
        })
        Write-Output "$($application.Name): started executable JAR; HTTP $($response.StatusCode); health $($health.status); port $port"
    }
    $results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logDirectory 'results.json') -Encoding utf8
} finally {
    # Stop only the exact child processes created above, including when an assertion fails.
    foreach ($process in $ownedProcesses) {
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -ErrorAction Stop }
        $process.Dispose()
    }
}
