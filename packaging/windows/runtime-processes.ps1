# Used only by status/stop to prevent acting on a PID reused by an unrelated program.
param([Parameter(Mandatory)]$Manifest)
foreach ($entry in $Manifest.processes) {
    $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
    if (!$process) { continue }
    $command = Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.pid)"
    if ($process.StartTime.ToUniversalTime().Ticks -ne $entry.startedAtTicks -or
        !$entry.marker -or !$command.CommandLine.Contains($entry.marker)) {
        throw "PID $($entry.pid) 的启动时间或命令不匹配，未对该进程执行操作。"
    }
    $process
}
