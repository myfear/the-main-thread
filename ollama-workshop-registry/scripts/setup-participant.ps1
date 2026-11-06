# Requires Windows PowerShell 5.1+ or PowerShell 7+, and a running local Ollama.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Registry,
    [string]$LockFile = (Join-Path (Split-Path $PSScriptRoot -Parent) 'models.lock.json'),
    [switch]$ReplaceExisting
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-LocalDigest([string]$Name) {
    $tags = Invoke-RestMethod -Uri "$script:OllamaUrl/api/tags" -TimeoutSec 30
    foreach ($model in $tags.models) {
        if ($model.name -ceq $Name) {
            $digest = [string]$model.digest
            if (-not $digest.StartsWith('sha256:')) { $digest = "sha256:$digest" }
            return $digest
        }
    }
    return ''
}
function Get-ManifestDigest([string]$Repository) {
    $parts = $Repository.Split(':')
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$script:RegistryUrl/v2/$($parts[0])/manifests/$($parts[1])" `
        -Headers @{ Accept = 'application/vnd.docker.distribution.manifest.v2+json' } -TimeoutSec 30
    $digest = [string]($response.Headers['Docker-Content-Digest'] | Select-Object -First 1)
    if ($digest -cnotmatch '^sha256:[a-f0-9]{64}$') { throw "No valid manifest digest returned for $Repository" }
    return $digest
}
function Invoke-Ollama([string[]]$Arguments) {
    & ollama @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Ollama failed (exit $LASTEXITCODE): $($Arguments -join ' ')" }
}

Get-Command ollama -ErrorAction Stop | Out-Null
$RegistryUrl = $Registry.TrimEnd('/')
if ($RegistryUrl -cnotmatch '^https?://[A-Za-z0-9][A-Za-z0-9.-]*(:[0-9]+)?$') {
    throw 'Registry must be http(s)://HOST[:PORT], without a path, credentials, or query.'
}
$registryUri = [Uri]$RegistryUrl
# Omit explicit default ports in the model name, especially on Windows.
$registryHost = $registryUri.Host
if (-not $registryUri.IsDefaultPort) { $registryHost += ":$($registryUri.Port)" }
$OllamaUrl = $env:OLLAMA_HOST
if (-not $OllamaUrl) { $OllamaUrl = 'http://127.0.0.1:11434' }
if ($OllamaUrl -notmatch '^https?://') { $OllamaUrl = "http://$OllamaUrl" }
$OllamaUrl = $OllamaUrl.TrimEnd('/')
if ($OllamaUrl -notmatch '^https?://(localhost|127\.0\.0\.1|\[::1\])(:[0-9]+)?$') {
    throw 'OLLAMA_HOST must select a local Ollama daemon.'
}
$lock = Get-Content -Raw -LiteralPath $LockFile | ConvertFrom-Json
if ($lock.schema_version -ne 1 -or @($lock.models).Count -eq 0) { throw 'Invalid or empty model lock file.' }
$aliases = @{}; $repositories = @{}
foreach ($model in $lock.models) {
    if ($model.repository -cnotmatch '^workshop/[a-z0-9][a-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*$' -or
        $model.alias -cnotmatch '^[a-z0-9][a-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*$' -or
        $model.digest -cnotmatch '^sha256:[a-f0-9]{64}$') { throw 'Invalid entry in model lock file.' }
    if ($aliases.ContainsKey($model.alias) -or $repositories.ContainsKey($model.repository)) {
        throw 'Duplicate entry in model lock file.'
    }
    $aliases[$model.alias] = $true; $repositories[$model.repository] = $true
}

$previousOllamaHost = $env:OLLAMA_HOST
try {
    $env:OLLAMA_HOST = $OllamaUrl
    Invoke-RestMethod -Uri "$OllamaUrl/api/tags" -TimeoutSec 10 | Out-Null
    Invoke-WebRequest -UseBasicParsing -Uri "$RegistryUrl/v2/" -TimeoutSec 15 | Out-Null
    foreach ($model in $lock.models) {
        if ((Get-ManifestDigest $model.repository) -cne $model.digest) {
            throw "Registry tag $($model.repository) has changed. Ask for the matching lock file."
        }
        $existing = Get-LocalDigest $model.alias
        if ($existing -and $existing -cne $model.digest -and -not $ReplaceExisting) {
            throw "Local alias $($model.alias) already contains a different model. Use -ReplaceExisting to replace that alias."
        }
    }
    foreach ($model in $lock.models) {
        if ((Get-LocalDigest $model.alias) -ceq $model.digest) {
            Write-Host "Already ready: $($model.alias)"
            continue
        }
        $remote = "$registryHost/$($model.repository)"
        $pullArguments = @('pull')
        if ($registryUri.Scheme -eq 'http') { $pullArguments += '--insecure' }
        $pullArguments += $remote
        Invoke-Ollama -Arguments $pullArguments
        if ((Get-LocalDigest $remote) -cne $model.digest) { throw "Digest mismatch for $remote; local alias was not changed." }
        Invoke-Ollama -Arguments @('cp', $remote, $model.alias)
        if ((Get-LocalDigest $model.alias) -cne $model.digest) { throw "Cannot verify alias $($model.alias)" }
        Write-Host "Ready: $($model.alias)"
    }
    Write-Host 'Workshop models are installed locally.'
} finally {
    $env:OLLAMA_HOST = $previousOllamaHost
}
