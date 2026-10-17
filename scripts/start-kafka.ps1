#requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'lib/kafka-infrastructure.ps1')
$credentialFile = Join-Path $repositoryRoot '.env.kafka'
$saved = @{}
Push-Location $repositoryRoot
try {
    & docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Docker engine unavailable; no Kafka credentials or containers changed.' }
    if (-not (Test-Path -LiteralPath $credentialFile)) {
        $lines = [Collections.Generic.List[string]]::new()
        $lines.Add('# Local Kafka credentials; ignored by Git. Do not share or commit.')
        foreach ($principal in @('BROKER','ADMIN','ORDER','PAYMENT','WAREHOUSE','CORE')) {
            $secret = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLowerInvariant()
            $lines.Add("KAFKA_${principal}_PASSWORD=$secret")
        }
        $lines.Add('KAFKA_HOST_PORT=19092')
        $lines.Add('KAFKA_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk')
        # CreateNew refuses overwrite, including a concurrent creation by another terminal.
        $stream = [IO.File]::Open($credentialFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
        try { foreach ($line in $lines) { $writer.WriteLine($line) } } finally { $writer.Dispose() }
        Write-Output 'Created ignored .env.kafka with separate generated credentials; no values printed.'
    }
    # Make the explicitly selected private file authoritative for this local stack.
    foreach ($suffix in @('BROKER_PASSWORD','ADMIN_PASSWORD','ORDER_PASSWORD','PAYMENT_PASSWORD','WAREHOUSE_PASSWORD','CORE_PASSWORD','HOST_PORT','CLUSTER_ID')) {
        $key = 'KAFKA_' + $suffix
        $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        Remove-Item -LiteralPath "Env:$key" -ErrorAction SilentlyContinue
    }
    $arguments = @('compose', '--env-file', $credentialFile, '--file', (Join-Path $repositoryRoot 'compose.kafka.yaml'))
    & docker @arguments up -d --wait --wait-timeout 120 kafka
    if ($LASTEXITCODE -ne 0) { throw 'Kafka startup failed; preserve its volume and inspect local broker health/logs.' }
    & docker @arguments run --rm --no-deps kafka-init
    if ($LASTEXITCODE -ne 0) { throw 'Kafka provisioning failed; preserve the broker and its volume for diagnosis.' }
    Write-Output 'Kafka is running; topics and source/Core ACLs provisioned. Credentials remain in ignored .env.kafka.'
    Write-Output 'Host bootstrap: 127.0.0.1:19092 by default (KAFKA_HOST_PORT in .env.kafka); container bootstrap: kafka:9092.'
} finally {
    Restore-KafkaVerificationEnvironment -Saved $saved
    Pop-Location
}
