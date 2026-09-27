# Turns the WiFi or Bluetooth radio on/off using the WinRT Radio API.
# There's no clean netsh/CLI equivalent that covers both radios reliably, so we
# reach into WinRT the same way Windows' own Action Center toggles do.

param(
    [Parameter(Mandatory = $true)][ValidateSet("WiFi", "Bluetooth")][string]$Kind,
    [Parameter(Mandatory = $true)][ValidateSet("On", "Off")][string]$State
)

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
[Windows.Devices.Radios.RadioState, Windows.System.Devices, ContentType = WindowsRuntime] | Out-Null

$access = Await ([Windows.Devices.Radios.Radio]::RequestAccessAsync()) ([Windows.Devices.Radios.RadioAccessStatus])
if ($access -ne [Windows.Devices.Radios.RadioAccessStatus]::Allowed) {
    Write-Error "Radio access not allowed ($access)"
    exit 1
}

$radios = Await ([Windows.Devices.Radios.Radio]::GetRadiosAsync()) ([System.Collections.Generic.IReadOnlyList[Windows.Devices.Radios.Radio]])
$radio = $radios | Where-Object { $_.Kind.ToString() -eq $Kind }

if (-not $radio) {
    Write-Error "No $Kind radio found on this PC"
    exit 1
}

$desiredState = if ($State -eq "On") { [Windows.Devices.Radios.RadioState]::On } else { [Windows.Devices.Radios.RadioState]::Off }
$result = Await ($radio.SetStateAsync($desiredState)) ([Windows.Devices.Radios.RadioAccessStatus])

if ($result -ne [Windows.Devices.Radios.RadioAccessStatus]::Allowed) {
    Write-Error "Failed to set $Kind state to $State ($result)"
    exit 1
}

Write-Output "$Kind set to $State"
