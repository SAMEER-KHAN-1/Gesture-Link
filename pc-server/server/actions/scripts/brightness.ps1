# Reads or sets the built-in display's brightness (0-100) through WMI.
# Only works on displays that expose WMI brightness control - laptop panels, not
# most external monitors - and says so on stderr (exit 1) when it isn't available.

param(
    [Parameter(Mandatory = $true)][ValidateSet("Get", "Set")][string]$Action,
    [int]$Level = 0
)

$ErrorActionPreference = "Stop"

try {
    if ($Action -eq "Get") {
        $monitor = Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightness | Select-Object -First 1
        if (-not $monitor) { throw "no controllable display" }
        [string]$monitor.CurrentBrightness
    }
    else {
        $methods = Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightnessMethods | Select-Object -First 1
        if (-not $methods) { throw "no controllable display" }
        Invoke-CimMethod -InputObject $methods -MethodName WmiSetBrightness -Arguments @{ Timeout = 1; Brightness = $Level } | Out-Null
    }
}
catch {
    [Console]::Error.WriteLine("Brightness control isn't supported on this PC's display (external monitors usually don't expose it).")
    exit 1
}
