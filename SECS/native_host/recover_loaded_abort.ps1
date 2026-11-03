param(
    [Parameter(Mandatory=$true)][string]$CommandId,
    [Parameter(Mandatory=$true)][string]$CarrierId,
    [Parameter(Mandatory=$true)][string]$VehicleId,
    [Parameter(Mandatory=$true)][string]$SourceNode,
    [Parameter(Mandatory=$true)][string]$WaitNode,
    [string]$ConfigPath=(Join-Path $PSScriptRoot 'servostar-secs_READY_LOCAL_WITH_TOKEN.json'),
    [string]$BaseUrl='http://192.168.0.45:10870/interfaces/api/mes-secs',
    [switch]$Apply
)
# Default is a read-only review. Never retries a POST or edits the journal.
$ErrorActionPreference='Stop'
foreach ($taskIdentity in @($CommandId,$CarrierId,$VehicleId,$SourceNode,$WaitNode)) {
    if ($taskIdentity -notmatch '^[A-Za-z0-9_.:-]{1,64}$') { throw 'Invalid recovery identity.' }
}
$taskConfig=Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
if ($taskConfig.authorization -notmatch '^[0-9a-fA-F]{64}$') { throw 'Fleet authorization is missing from the local configuration.' }
$taskHeaders=@{Authorization=$taskConfig.authorization}
function Invoke-RecoveryRequest([string]$TaskRoute,$TaskBody) {
    try {
        Invoke-RestMethod -Method Post -Uri ($BaseUrl.TrimEnd('/')+'/'+$TaskRoute) -Headers $taskHeaders `
            -ContentType 'application/json' -Body ($TaskBody | ConvertTo-Json -Compress -Depth 6) -TimeoutSec 30
    } catch {
        $taskCode='REQUEST_OUTCOME_UNCONFIRMED_QUERY_STATUS'
        try {
            $taskErrorBody=$_.ErrorDetails.Message | ConvertFrom-Json
            if ($taskErrorBody.code -cmatch '^[A-Z0-9_]{1,96}$') { $taskCode=$taskErrorBody.code }
        } catch { }
        throw "$taskCode. Do not retry an apply request; inspect this COMMANDID with the status API."
    }
}
$taskStatus=Invoke-RecoveryRequest 'status' @{}
if ($taskStatus.version -notin @('1.2.1','1.2.2','1.2.3','1.3.0')) { throw 'This recovery helper requires plugin 1.2.1, 1.2.2, 1.2.3 or 1.3.0.' }
if (-not $taskStatus.enabled -or -not $taskStatus.state.healthy) { throw 'Native SECS/journal is not healthy.' }
if ($taskStatus.state.tsc -ne 'AUTO') { throw 'TSC must be AUTO.' }
if ($taskStatus.loadedAbortWaitNodes.$VehicleId -ne $WaitNode) { throw 'The running plugin has a different wait-node mapping.' }
$taskTarget=@($taskStatus.state.jobs | Where-Object commandId -CEQ $CommandId)
if ($taskTarget.Count -ne 1 -or $taskTarget[0].phase -ne 'ABORTED') { throw 'Exact ABORTED job was not found.' }
if ($taskTarget[0].recoveryArmed -or $taskTarget[0].recoveryState -or $taskTarget[0].recoveryMission) {
    $taskTarget[0] | Select-Object commandId,phase,vehicle,recoveryArmed,recoveryState,recoveryMission,recoveryNode,recoveryDetail | ConvertTo-Json
    throw 'Recovery already initialized. No reset or movement retry was performed.'
}
$taskReview=@{commandId=$CommandId;carrierId=$CarrierId;vehicleId=$VehicleId;sourceNode=$SourceNode;waitNode=$WaitNode;apply=$false}
$taskPreview=Invoke-RecoveryRequest 'recover-loaded-abort' $taskReview
if (-not $taskPreview.success -or $taskPreview.armed) { throw 'Read-only review result was not confirmed.' }
$taskPreview | ConvertTo-Json -Depth 5
if (-not $Apply) { Write-Output 'PREVIEW ONLY: no recovery was armed and no mission was submitted.'; return }
if (-not $taskStatus.communicating) { throw 'Reconnect Virtual MCS and complete S1F13 before applying.' }
$taskReview.apply=$true
$taskResult=Invoke-RecoveryRequest 'recover-loaded-abort' $taskReview
$taskResult | ConvertTo-Json -Depth 5
if (-not $taskResult.success -or -not $taskResult.armed) { throw 'Apply outcome is unconfirmed. Query the original COMMANDID; do not resend.' }
Write-Output 'ARMED: wait-move processing is requested. This is not arrival or transport completion. Observe CEID9004 MOVING/READY.'
