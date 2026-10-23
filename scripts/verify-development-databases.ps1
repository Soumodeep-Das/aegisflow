#requires -Version 7.0
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot/lib/development-credentials.ps1"
$private=Join-Path $root '.env.development'
$evidence=Join-Path $root ('.cache/p05b-db/'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$result=[ordered]@{Passed=$false;Passes=@();Scope='persistent database authentication/roles/restart only'}
$probe=@'
set -eu
export PGCONNECT_TIMEOUT=5
export PGPASSWORD="$POSTGRES_PASSWORD"
identity=$(psql -X -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atq -v ON_ERROR_STOP=1 -c 'SELECT system_identifier FROM pg_control_system()')
test -n "$identity"
for role in migration runtime; do
  if [ "$role" = migration ]; then user="$MIGRATION_DB_USER"; password="$MIGRATION_DB_PASSWORD"; expected=t; else user="$RUNTIME_DB_USER"; password="$RUNTIME_DB_PASSWORD"; expected=f; fi
  export PGPASSWORD="$password"
  actual=$(psql -X -h 127.0.0.1 -U "$user" -d "$POSTGRES_DB" -Atq -v ON_ERROR_STOP=1 -c "SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication FROM pg_roles WHERE rolname=current_user")
  test "$actual" = t
  actual=$(psql -X -h 127.0.0.1 -U "$user" -d "$POSTGRES_DB" -Atq -v ON_ERROR_STOP=1 -c "SELECT has_schema_privilege(current_user,'aegisflow','USAGE')")
  test "$actual" = t
  actual=$(psql -X -h 127.0.0.1 -U "$user" -d "$POSTGRES_DB" -Atq -v ON_ERROR_STOP=1 -c "SELECT has_schema_privilege(current_user,'aegisflow','CREATE')")
  test "$actual" = "$expected"
  # A deliberate wrong password must fail for authentication, not a network outage.
  export PGPASSWORD=deliberately-invalid-verification-password
  error=$(psql -X -h 127.0.0.1 -U "$user" -d "$POSTGRES_DB" -Atq -c 'SELECT 1' 2>&1) && exit 1
  case "$error" in *'password authentication failed'*) ;; *) exit 1;; esac
done
printf '%s\n' "$identity"
'@
Push-Location $root
try {
    & "$PSScriptRoot/dev-init.ps1" -CheckOnly
    $hash=(Get-FileHash $private).Hash
    Invoke-WithDevelopmentEnvironment {
        $compose=@('compose','--project-name','aegisflow-foundation','--env-file',$private,'--file',"$root/compose.yaml",'--file',"$root/compose.kafka.yaml",'--profile','postgres')
        $identities=@{}
        foreach($pass in 1..2) {
            # Reuses the real bootstrap on each pass; no separate verification database.
            & "$PSScriptRoot/dev-infrastructure-up.ps1"
            foreach($owner in @('core','order','payment','warehouse')) {
                $output=@(& docker @compose exec -T "$owner-db" sh -c ($probe.Replace("`r","")) 2>&1)
                if($LASTEXITCODE -ne 0 -or $output.Count -ne 1 -or "$($output[0])" -notmatch '^\d+$') { throw "Database authentication/role checks failed for $owner; values withheld." }
                if($pass -eq 1) { $identities[$owner]="$($output[0])" }
                elseif($identities[$owner] -ne "$($output[0])") { throw 'Database storage identity changed across restart.' }
                $result.Passes+=@{Pass=$pass;Owner=$owner;Authentication='Passed';LeastPrivilege='Passed';WrongPasswords='Rejected';StorageIdentity=$identities[$owner]}
                Write-Output "PASS: $owner pass $pass authenticated bootstrap/migration/runtime, schema privileges and wrong-password rejection."
            }
            if($pass -eq 1) {
                Write-Output 'Stopping only four project databases, retaining all containers/volumes; Kafka remains running.'
                & docker @compose stop --timeout 30 core-db order-db payment-db warehouse-db
                if($LASTEXITCODE -ne 0) { throw 'Database stop failed; preserve infrastructure.' }
            }
        }
    }
    if((Get-FileHash $private).Hash -ne $hash) { throw 'Private file changed across verification.' }
    $result.Passed=$true
    Write-Output 'PASS: persistent database authentication/least privilege, repeated bootstrap and retained-storage restart. Kafka auth/ACL/restart and cross-service network acceptance still pending; B is incomplete.'
} finally {
    $result | ConvertTo-Json -Depth 6 | Set-Content "$evidence/results.json"
    Write-Output "Evidence: $evidence (no credentials). No storage deleted."
    Pop-Location
}
