# Reports the current on/off state of the WiFi and Bluetooth radios, using the
# same WinRT Radio API toggle_radio.ps1 uses to set them.

Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($WinRtTask, $ResultType) {
    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
    $netTask = $asTask.Invoke($null, @($WinRtTask))
    $netTask.Wait(-1) | Out-Null
    return $netTask.Result
}

[Windows.Devices.Radios.Radio, Windows.System.Devices, ContentType = WindowsRuntime] | Out-Null
[Windows.Devices.Radios.RadioAccessStatus, Windows.System.Devices, ContentType = WindowsRuntime] | Out-Null

$access = Await ([Windows.Devices.Radios.Radio]::RequestAccessAsync()) ([Windows.Devices.Radios.RadioAccessStatus])
if ($access -ne [Windows.Devices.Radios.RadioAccessStatus]::Allowed) {
    Write-Error "Radio access not allowed ($access)"
    exit 1
}

$radios = Await ([Windows.Devices.Radios.Radio]::GetRadiosAsync()) ([System.Collections.Generic.IReadOnlyList[Windows.Devices.Radios.Radio]])

$status = @{}
foreach ($kind in @("WiFi", "Bluetooth")) {
    $radio = $radios | Where-Object { $_.Kind.ToString() -eq $kind }
    $status[$kind] = if ($radio) { $radio.State.ToString() } else { $null }
}

$status | ConvertTo-Json -Compress
