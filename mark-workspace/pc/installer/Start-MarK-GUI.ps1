param()
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Windows.Forms
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$exe=Join-Path $root 'runtime\python\pythonw.exe'
if(!(Test-Path $exe)){
 [System.Windows.Forms.MessageBox]::Show('Прво стартувај Setup_MarK.vbs за инсталација.','MarK','OK','Warning') | Out-Null
 exit 1
}
$config=Join-Path $root 'app\td_local_config.json'
if(!(Test-Path $config)){
 $pin=(Get-Random -Minimum 100000 -Maximum 999999).ToString()
 $conf=@{pin=$pin;allow_remote_control=$false}|ConvertTo-Json
 [System.IO.File]::WriteAllText($config,$conf,(New-Object System.Text.UTF8Encoding($false)))
 [System.Windows.Forms.MessageBox]::Show(('Твојот MarK PIN за најавување е: '+$pin+"`nЗапиши го на сигурно место."),'MarK - прв старт','OK','Information') | Out-Null
}
$portOpen=$false
try{$r=Invoke-WebRequest -Uri 'http://127.0.0.1:8765/_td_ai_health' -UseBasicParsing -TimeoutSec 2;$portOpen=$true}catch{}
if($portOpen){
 $r=$null
 try{
  $r=Invoke-WebRequest -Uri 'http://127.0.0.1:8765/api/mark/status' -MaximumRedirection 0 -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
 }catch{
  # MarK's route is gated; a 401 means the correct app is running.
  if($_.Exception.Response -and ([int]$_.Exception.Response.StatusCode -eq 401)){$r=$true}
 }
 if(!$r){[System.Windows.Forms.MessageBox]::Show('Port 8765 го користи друга TD апликација. Затвори ја старата TD верзија, па стартувај MarK.','MarK','OK','Warning') | Out-Null;exit 1}
}else{
 $env:TD_AI_ROOT=Join-Path $root 'workspace'
 $env:MARK_HOME=$root
 $env:TD_AI_DESKTOP_MODE='1'
 $p=Start-Process -FilePath $exe -ArgumentList ('"'+(Join-Path $root 'app\server.py')+'"') -WorkingDirectory (Join-Path $root 'app') -WindowStyle Hidden -PassThru
 New-Item -ItemType Directory -Force (Join-Path $root 'data')|Out-Null
 [System.IO.File]::WriteAllText((Join-Path $root 'data\mark_server.pid'),[string]$p.Id)
 $ready=$false
 for($i=0;$i -lt 60;$i++){
   Start-Sleep -Milliseconds 1000
   try{$r=Invoke-WebRequest -Uri 'http://127.0.0.1:8765/_td_ai_health' -UseBasicParsing -TimeoutSec 1;if($r.StatusCode -eq 200){$ready=$true;break}}catch{}
   if($p.HasExited){break}
 }
 if(!$ready){[System.Windows.Forms.MessageBox]::Show('MarK не се отвори. Провери Windows Event Viewer или installer log.','MarK','OK','Error') | Out-Null;exit 1}
}
$url='http://127.0.0.1:8765/'
$edges=@((Join-Path $env:ProgramFiles '(x86)\Microsoft\Edge\Application\msedge.exe'),(Join-Path $env:ProgramFiles 'Microsoft\Edge\Application\msedge.exe'))
$chrome=@((Join-Path $env:ProgramFiles 'Google\Chrome\Application\chrome.exe'),(Join-Path ${env:ProgramFiles(x86)} 'Google\Chrome\Application\chrome.exe'))
$browser=@($edges+$chrome|Where-Object{Test-Path $_}|Select-Object -First 1)
if($browser.Count -gt 0){Start-Process -FilePath $browser[0] -ArgumentList ('--app='+$url),('--user-data-dir="'+(Join-Path $root 'data\browser_profile')+'"')}
else{Start-Process $url}
