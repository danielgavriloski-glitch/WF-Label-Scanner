Add-Type -AssemblyName System.Windows.Forms
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$pidFile=Join-Path $root 'data\mark_server.pid'
if(!(Test-Path $pidFile)){exit 0}
try{
 $serverId=[int](Get-Content $pidFile -Raw)
 $p=Get-CimInstance Win32_Process -Filter ('ProcessId='+$serverId)
 if($p -and $p.CommandLine -and $p.CommandLine.Contains('server.py') -and $p.CommandLine.Contains($root)){
  Stop-Process -Id $serverId -Force
 }
}catch{}
Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
