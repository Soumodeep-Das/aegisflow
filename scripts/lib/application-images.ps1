function Get-ApplicationImagePlan {
    @(
        @{Owner='core'; Module='control-plane'; Artifact='aegisflow-core'; Main='com.aegisflow.core.AegisFlowCoreApplication'},
        @{Owner='order'; Module='simulators/order-service'; Artifact='order-service'; Main='com.aegisflow.simulator.order.OrderSimulatorApplication'},
        @{Owner='payment'; Module='simulators/payment-service'; Artifact='payment-service'; Main='com.aegisflow.simulator.payment.PaymentSimulatorApplication'},
        @{Owner='warehouse'; Module='simulators/warehouse-service'; Artifact='warehouse-service'; Main='com.aegisflow.simulator.warehouse.WarehouseSimulatorApplication'}
    )
}
function Assert-ApplicationArchive {
    param([string]$Jar,[string]$Main)
    $zip=[IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        $entry=$zip.GetEntry('META-INF/MANIFEST.MF')
        if($null -eq $entry) { throw 'Executable manifest missing.' }
        $reader=[IO.StreamReader]::new($entry.Open())
        try { $manifest=($reader.ReadToEnd() -replace "\r?\n ",'') } finally { $reader.Dispose() }
        if($manifest -notmatch ('(?m)^Start-Class: '+[regex]::Escape($Main)+'\r?$') -or
            $manifest -notmatch 'Main-Class: org.springframework.boot.loader.launch.JarLauncher') { throw 'Wrong executable application selected.' }
        if($null -eq $zip.GetEntry('BOOT-INF/classes/'+$Main.Replace('.','/')+'.class')) { throw 'Application class missing.' }
        foreach($item in $zip.Entries) {
            if($item.FullName -match '(?i)(^|/)(\.env[^/]*|\.git|\.cache)(/|$)|application-local\.ya?ml$') { throw 'Private file found in executable archive.' }
        }
    } finally { $zip.Dispose() }
}
function Get-ApplicationLoopbackPort {
    param([string[]]$Bindings)
    $lines=@($Bindings | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if($lines.Count -ne 1 -or $lines[0] -notmatch '^127\.0\.0\.1:([0-9]{1,5})$') { throw 'Expected exactly one loopback binding.' }
    $port=[int]$Matches[1]
    if($port -lt 1 -or $port -gt 65535) { throw 'Invalid loopback port.' }
    return $port
}
