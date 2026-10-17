# Infrastructure-only helpers. Never address the ordinary project during verification.
function Assert-KafkaVerificationProject {
    param([Parameter(Mandatory)][string]$Project)
    if ($Project -cnotmatch '^aegisflow-kafka-verify-[0-9a-f]{32}$') {
        throw 'Expected a uniquely named Kafka verification project.'
    }
}

function Get-KafkaComposeArguments {
    param([Parameter(Mandatory)][string]$RepositoryRoot, [Parameter(Mandatory)][string]$Project)
    Assert-KafkaVerificationProject $Project
    if (-not [IO.Path]::IsPathFullyQualified($RepositoryRoot)) { throw 'Repository root must be absolute.' }
    @('compose', '--project-name', $Project, '--env-file', (Join-Path $RepositoryRoot '.env.example'),
        '--file', (Join-Path $RepositoryRoot 'compose.kafka.yaml'))
}

function Assert-KafkaVerificationConfiguration {
    param([Parameter(Mandatory)]$Config, [Parameter(Mandatory)][string]$Project, [Parameter(Mandatory)][int]$HostPort)
    Assert-KafkaVerificationProject $Project
    if ($HostPort -lt 1 -or $HostPort -gt 65535) { throw 'Invalid Kafka host port.' }
    if ($Config.name -cne $Project -or (@($Config.services.PSObject.Properties.Name | Sort-Object) -join ',') -cne 'kafka,kafka-init') {
        throw 'Kafka verification must contain exactly its broker and provisioning service.'
    }
    if ((@($Config.volumes.PSObject.Properties.Name) -join ',') -cne 'kafka-data' -or
        $Config.volumes.'kafka-data'.name -cne "${Project}_kafka-data" -or $Config.volumes.'kafka-data'.external) {
        throw 'Refusing shared or unrelated Kafka storage.'
    }
    if ((@($Config.networks.PSObject.Properties.Name) -join ',') -cne 'kafka-events' -or
        $Config.networks.'kafka-events'.name -cne "${Project}_kafka-events" -or $Config.networks.'kafka-events'.external) {
        throw 'Refusing shared or unrelated Kafka networks.'
    }
    $ports = @($Config.services.kafka.ports)
    if ($ports.Count -ne 1 -or $ports[0].host_ip -cne '127.0.0.1' -or
        [int]$ports[0].published -ne $HostPort -or [int]$ports[0].target -ne 19092) {
        throw 'Kafka must publish only its one IPv4 loopback host listener.'
    }
    foreach ($service in @($Config.services.kafka, $Config.services.'kafka-init')) {
        if ($service.image -cne 'apache/kafka:4.2.2@sha256:1213eb3943d551e5ed1fca7a4e109001cee35770b66a02a0c37a8964efe09b69' -or
            (@($service.networks.PSObject.Properties.Name) -join ',') -cne 'kafka-events' -or $service.privileged) {
            throw 'Kafka image pin or service network boundary differs from the verified configuration.'
        }
    }
}

function Restore-KafkaVerificationEnvironment {
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Saved)
    foreach ($name in $Saved.Keys) {
        if ($name -cnotmatch '^KAFKA_(ADMIN_PASSWORD|BROKER_PASSWORD|ORDER_PASSWORD|PAYMENT_PASSWORD|WAREHOUSE_PASSWORD|CORE_PASSWORD|HOST_PORT|CLUSTER_ID)$') {
            throw 'Refusing to restore unrelated environment settings.'
        }
    }
    foreach ($name in $Saved.Keys) {
        if ($null -eq $Saved[$name]) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($name, [string]$Saved[$name], 'Process') }
    }
}

function New-KafkaSmokeEnvironment {
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Source,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Settings)
    $result = @{}
    foreach ($key in $Source.Keys) {
        if ([string]$key -notmatch '^(KAFKA_|SPRING_|PG|JAVA_TOOL_OPTIONS$|JDK_JAVA_OPTIONS$|_JAVA_OPTIONS$|CLASSPATH$)' -and
            [string]$key -notmatch '(^|_)(DB|DATABASE|JDBC)(_|$)') {
            $result[[string]$key] = [string]$Source[$key]
        }
    }
    foreach ($key in $Settings.Keys) {
        if ([string]$key -cnotmatch '^KAFKA_(ADMIN_PASSWORD|ORDER_PASSWORD|PAYMENT_PASSWORD|WAREHOUSE_PASSWORD|CORE_PASSWORD|BOOTSTRAP_SERVERS|SMOKE_RUN_ID|SMOKE_EXPECTED_HOST|SMOKE_EXPECTED_PORT)$') {
            throw 'Unexpected Kafka smoke environment field.'
        }
        $result[[string]$key] = [string]$Settings[$key]
    }
    $result
}
