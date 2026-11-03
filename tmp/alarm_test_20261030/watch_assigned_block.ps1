param([Parameter(Mandatory=$true)][string]$CommandId,[switch]$BlockBeforeSubmit)
$ErrorActionPreference='Stop'
$taskRoot='C:\work\KUKA\KUKA_Fleet'
$taskConfig=Get-Content -LiteralPath (Join-Path $taskRoot 'SECS\native_host\servostar-secs_READY_LOCAL_WITH_TOKEN.json') -Raw|ConvertFrom-Json
$taskHeaders=@{Authorization=$taskConfig.authorization}
$taskEvidence=Join-Path $PSScriptRoot ('assigned_'+$CommandId+'.jsonl')
function Record-Test($kind,$value){
    [pscustomobject]@{clientTime=(Get-Date).ToString('o');kind=$kind;value=$value}|ConvertTo-Json -Depth 18 -Compress|Add-Content -LiteralPath $taskEvidence -Encoding utf8
}
function Read-TestApi($path,$body='{}'){
    Invoke-RestMethod -Method Post -Uri ('http://192.168.0.45:10870/interfaces/api/'+$path) -Headers $taskHeaders -ContentType 'application/json' -Body $body -TimeoutSec 8
}
function Set-TestBlock($enabled){
    $taskAreas=Invoke-RestMethod -Method Get -Uri 'http://192.168.0.45:10870/interfaces/api/amr/queryAllForbiddenAreas' -Headers $taskHeaders -TimeoutSec 8
    $taskMatches=@($taskAreas.data|Where-Object {$_.forbiddenAreaId -eq 1 -and $_.forbiddenAreaCode -eq 'CODEX_ALARM_TEST_261030' -and $_.mapCode -eq 'lay01'})
    if($taskMatches.Count -ne 1){throw 'Exact test area not found'}
    $taskResponse=Read-TestApi 'amr/updateForbiddenAreaStatus' (@{forbiddenAreaId=1;status=([int]$enabled).ToString()}|ConvertTo-Json -Compress)
    Record-Test 'area-change' @{enabled=$enabled;response=$taskResponse}
    if($taskResponse.success -ne $true){throw 'Test area update failed; inspect status'}
    Write-Output ('TEST_AREA_ENABLED='+$enabled)
}
$taskDidEnable=$false
$taskReleased=$false
$taskStart=Get-Date
$taskEnabledAt=$null
try{
    Write-Output ('WATCHING_COMMAND='+$CommandId)
    if($BlockBeforeSubmit){
        $taskPre=Read-TestApi 'mes-secs/status'
        $taskPreRobots=Read-TestApi 'amr/robotQuery'
        if($taskPre.version -ne '1.2.3' -or !$taskPre.communicating -or @($taskPre.state.jobs|Where-Object {$_.phase -notin @('COMPLETED','CANCELED','ABORTED','FAILED','REJECTED')}).Count){throw 'Expected idle communicating 1.2.3 host'}
        if(@($taskPreRobots.data).Count -ne 3 -or @($taskPreRobots.data|Where-Object {$_.buildingCode -ne 'SIM01' -or $_.robotType -ne 'KMP1500I' -or $_.missionCode -or $_.containerCode}).Count){throw 'Expected three empty idle SIM01 robots'}
        $taskDidEnable=$true;Set-TestBlock $true
    }
    while(((Get-Date)-$taskStart).TotalSeconds -lt 160){
        $taskNative=Read-TestApi 'mes-secs/status'
        $taskJob=Read-TestApi 'amr/jobQuery' (@{jobCode=$CommandId;limit=5}|ConvertTo-Json -Compress)
        $taskRobots=Read-TestApi 'amr/robotQuery'
        Record-Test 'snapshot' @{native=$taskNative;job=$taskJob;robots=$taskRobots}
        $taskOwn=@($taskNative.state.jobs|Where-Object {$_.commandId -eq $CommandId})
        if($BlockBeforeSubmit -and $taskOwn.Count -eq 1 -and $null -eq $taskEnabledAt){$taskEnabledAt=Get-Date}
        if(!$taskDidEnable -and $taskOwn.Count -eq 1 -and $taskOwn[0].phase -eq 'ACTIVE' -and $taskOwn[0].vehicle -in @('1','2','3')){
            $taskOthers=@($taskNative.state.jobs|Where-Object {$_.commandId -ne $CommandId -and $_.phase -notin @('COMPLETED','CANCELED','ABORTED','FAILED','REJECTED')})
            if($taskOthers.Count){throw 'Other active native work; no block applied'}
            if(@($taskRobots.data|Where-Object {$_.buildingCode -ne 'SIM01' -or $_.robotType -ne 'KMP1500I'}).Count){throw 'Non-SIM01 robot detected'}
            $taskDidEnable=$true
            Set-TestBlock $true
            $taskEnabledAt=Get-Date
            Write-Output ('ASSIGNED_AMR='+$taskOwn[0].vehicle)
        }
        if($taskDidEnable -and !$taskReleased -and $null -ne $taskEnabledAt -and ((Get-Date)-$taskEnabledAt).TotalSeconds -ge 30){Set-TestBlock $false;$taskReleased=$true}
        if($taskOwn.Count -eq 1 -and $taskOwn[0].phase -eq 'COMPLETED'){
            Write-Output 'NATIVE_COMPLETED';break
        }
        Start-Sleep -Milliseconds 500
    }
}finally{
    if($taskDidEnable -and !$taskReleased){Set-TestBlock $false}
    Record-Test 'final-state' (Read-TestApi 'mes-secs/status')
}
