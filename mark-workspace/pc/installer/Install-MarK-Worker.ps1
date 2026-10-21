param([Parameter(Mandatory=$true)][string]$Request,[Parameter(Mandatory=$true)][string]$Status)
$ErrorActionPreference='Stop'
$info=Get-Content -LiteralPath $Request -Raw -Encoding UTF8 | ConvertFrom-Json
$source=[System.IO.Path]::GetFullPath([string]$info.source)
$root=[System.IO.Path]::GetFullPath([string]$info.target)
$log=Join-Path $root 'data\install_log.txt'
function Status([string]$phase,[int]$progress,[string]$message){
 $item=@{phase=$phase;progress=$progress;message=$message;target=$root;log=$log;time=(Get-Date -Format o)}|ConvertTo-Json -Compress
 $file=$Status+'.writing'
 [System.IO.File]::WriteAllText($file,$item,(New-Object System.Text.UTF8Encoding($false)))
 Move-Item -LiteralPath $file -Destination $Status -Force
 if(Test-Path (Split-Path $log -Parent)) {Add-Content -LiteralPath $log -Value ('['+(Get-Date -Format s)+'] '+$message) -Encoding UTF8}
}
function Run([string]$exe,[string[]]$args,[string]$what){
 & $exe @args 2>&1 | Out-File -LiteralPath $log -Append -Encoding UTF8
 if($LASTEXITCODE -ne 0){throw ('Грешка при '+$what+', exit code '+$LASTEXITCODE)}
}
function Download([string]$url,[string]$output,[int64]$minimum){
 if((Test-Path $output) -and ((Get-Item $output).Length -ge $minimum)){return}
 New-Item -ItemType Directory -Force (Split-Path $output -Parent)|Out-Null
 $part=$output+'.partial'
 $curl=Join-Path $env:SystemRoot 'System32\curl.exe'
 if(!(Test-Path $curl)){$curl='curl.exe'}
 $args=@('--location','--fail','--retry','4','--retry-delay','5','--connect-timeout','40','--continue-at','-','--output',$part,$url)
 Run $curl $args 'преземање'
 if(!(Test-Path $part) -or (Get-Item $part).Length -lt $minimum){throw 'Преземен е нецелосен или премал фајл: '+$url}
 Move-Item -LiteralPath $part -Destination $output -Force
}
try{
 if(![Environment]::Is64BitOperatingSystem){throw 'Потребен е Windows 64-bit.'}
 if($root -eq $source){$same=$true}else{$same=$false}
 New-Item -ItemType Directory -Force $root,(Join-Path $root 'data')|Out-Null
 Status 'running' 3 'Проверка на папката и зачувување на постојните MarK податоци...'
 if(!$same){
   foreach($folder in @('app','brand','installer')){
      $from=Join-Path $source $folder;$to=Join-Path $root $folder
      New-Item -ItemType Directory -Force $to|Out-Null
      Copy-Item -Path (Join-Path $from '*') -Destination $to -Recurse -Force
   }
   foreach($name in @('START_MarK.vbs','STOP_MarK.vbs','README_GUI_MK.txt','model_sources.json','third_party_notice.txt','VERSION.txt')){
      $from=Join-Path $source $name
      if(Test-Path $from){Copy-Item -LiteralPath $from -Destination (Join-Path $root $name) -Force}
   }
 }
 New-Item -ItemType Directory -Force (Join-Path $root 'runtime'),(Join-Path $root 'models'),(Join-Path $root 'cache')|Out-Null
 $pythonDir=Join-Path $root 'runtime\python';$py=Join-Path $pythonDir 'python.exe'
 Status 'running' 10 'Подготвување приватен Python runtime само внатре во MarK (без глобален PATH)...'
 if(!(Test-Path $py)){
   $pythonInst=Join-Path $root 'cache\python-3.12.10-amd64.exe'
   Download 'https://www.python.org/ftp/python/3.12.10/python-3.12.10-amd64.exe' $pythonInst 25000000
   $pArgs=@('/quiet','InstallAllUsers=0',('TargetDir="'+$pythonDir+'"'),'PrependPath=0','Include_launcher=0','AssociateFiles=0','Shortcuts=0','Include_pip=1','Include_tcltk=1','Include_test=0','Include_doc=0')
   $proc=Start-Process -FilePath $pythonInst -ArgumentList $pArgs -PassThru -Wait -WindowStyle Hidden
   if($proc.ExitCode -ne 0 -or !(Test-Path $py)){throw 'Неуспешно поставување на приватниот Python runtime. Код: '+$proc.ExitCode}
 }
 Status 'running' 25 'Се инсталираат модулите за TD Studio, Lightroom и Photo AI...'
 $requirements=Join-Path $root 'app\requirements.txt'
 Run $py @('-m','pip','install','--disable-pip-version-check','-r',$requirements) 'Python dependencies'
 try{Run $py @('-m','pip','install','--disable-pip-version-check','rawpy') 'RAW photo plugin'}catch{Add-Content $log 'OPTIONAL: RAW decoder could not install.'}
 Status 'running' 43 'Го пронаоѓам официјалниот Windows MarK inference runtime (со GitHub fallback)...'
 $binDir=Join-Path $root 'runtime\llama';$binary=Join-Path $binDir 'llama-server.exe'
 if(!(Test-Path $binary)){
   [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
   $releases=Invoke-RestMethod -Uri 'https://api.github.com/repos/ggml-org/llama.cpp/releases?per_page=20' -Headers @{'User-Agent'='MarK-windows-installer';'Accept'='application/vnd.github+json'} -TimeoutSec 60
   $gpu=$false
   try{$gpu=(@(Get-CimInstance Win32_VideoController|Where-Object{$_.Name -match 'NVIDIA|AMD|Radeon'}).Count -gt 0)}catch{}
   $wanted=if($gpu){@('vulkan','cpu')}else{@('cpu','vulkan')}
   $selected=$null
   foreach($kind in $wanted){
      foreach($release in $releases){
         $selected=@($release.assets)|Where-Object{
            $_.name -match ('(?i)-bin-win-'+$kind+'-x64\.zip$') -or
            ($kind -eq 'cpu' -and $_.name -match '(?i)-bin-win-(avx2|avx)-x64\.zip$')
         }|Select-Object -First 1
         if($selected){break}
      }
      if($selected){break}
   }
   if(!$selected){throw 'Најдени се изданија, но не и поддржан Windows x64 binary во последните 20 изданија. Проверете runtime log.'}
   Status 'running' 49 ('Преземање '+$selected.name)
   $zip=Join-Path $root ('cache\'+$selected.name)
   Download ([string]$selected.browser_download_url) $zip 1000000
   if($selected.digest -match '^sha256:([0-9a-fA-F]{64})$'){
      $hash=(Get-FileHash -Path $zip -Algorithm SHA256).Hash
      if($hash -ne $Matches[1]){throw 'Неуспешна SHA256 проверка на runtime архивата.'}
   }
   $stage=Join-Path $root 'cache\llama_extracted'
   if(Test-Path $stage){Remove-Item -LiteralPath $stage -Force -Recurse}
   Expand-Archive -LiteralPath $zip -DestinationPath $stage -Force
   $found=Get-ChildItem -LiteralPath $stage -Recurse -File -Filter 'llama-server.exe'|Select-Object -First 1
   if(!$found){throw 'Недостига llama-server.exe во преземениот официјален ZIP.'}
   New-Item -ItemType Directory -Force $binDir|Out-Null
   Copy-Item -Path (Join-Path $found.Directory.FullName '*') -Destination $binDir -Recurse -Force
   if(!(Test-Path $binary)){throw 'Не можев да го поставам llama-server.exe.'}
   Remove-Item -LiteralPath $stage -Recurse -Force
 }
 Status 'running' 62 'Се презема локалниот MarK Vision модел (приближно 2.5 GB)...'
 $url='https://huggingface.co/Qwen/Qwen3-VL-4B-Instruct-GGUF/resolve/main/'
 $model1=Join-Path $root 'models\Qwen3VL-4B-Instruct-Q4_K_M.gguf'
 $model2=Join-Path $root 'models\mmproj-Qwen3VL-4B-Instruct-Q8_0.gguf'
 Download ($url+'Qwen3VL-4B-Instruct-Q4_K_M.gguf') $model1 2300000000
 Status 'running' 83 'Се презема MarK Vision модулот за препознавање фотографии (454 MB)...'
 Download ($url+'mmproj-Qwen3VL-4B-Instruct-Q8_0.gguf') $model2 400000000
 foreach($file in @($model1,$model2)){
   $s=[System.IO.File]::OpenRead($file)
   try{
      $b=New-Object byte[] 4;[void]$s.Read($b,0,4)
      if([System.Text.Encoding]::ASCII.GetString($b) -ne 'GGUF'){throw 'Невалиден GGUF модел: '+$file}
   }finally{$s.Close()}
 }
 Status 'running' 94 'Подготвување MarK, Lightroom приклучоци и Desktop икони...'
 # Optional Lightroom plugin copies: never replace an existing plugin or alter old TD files.
 try {
   $modules=Join-Path $env:APPDATA 'Adobe\Lightroom\Modules'
   foreach($plugin in @(@{src='TDPhotoPRO.lrplugin';new='MarK_TDPhotoPRO.lrplugin'},@{src='TD_Lightroom_Bridge.lrplugin';new='MarK_TD_Lightroom_Bridge.lrplugin'})){
     $from=Join-Path (Join-Path $root 'app') $plugin.src
     $to=Join-Path $modules $plugin.new
     if((Test-Path $from) -and !(Test-Path $to)){
       New-Item -ItemType Directory -Force $modules | Out-Null
       Copy-Item -LiteralPath $from -Destination $to -Recurse
     }
   }
 }catch{Add-Content -LiteralPath $log -Value ('Optional Lightroom plugin warning: '+$_) -Encoding UTF8}
 foreach($folder in @('workspace\01_INPUT_VIDEO','workspace\02_MUSIC','workspace\06_OUTPUT')){
  New-Item -ItemType Directory -Force (Join-Path $root $folder)|Out-Null
 }
 try{
  $desktop=[Environment]::GetFolderPath('Desktop');$sh=New-Object -ComObject WScript.Shell
  $sc=$sh.CreateShortcut((Join-Path $desktop 'MarK - TD AI Studio PRO.lnk'))
  $sc.TargetPath=Join-Path $env:WINDIR 'System32\wscript.exe'
  $sc.Arguments='"'+(Join-Path $root 'START_MarK.vbs')+'"'
  $sc.WorkingDirectory=$root;$sc.Description='MarK TD AI Studio PRO'
  $ico=Join-Path $root 'brand\MarK.ico';if(Test-Path $ico){$sc.IconLocation=$ico};$sc.Save()
 }catch{Add-Content $log ('Shortcut warning: '+$_)}
 $manifest=@{product='MarK_TD_AI_Studio_PRO';version='0.3';installed=(Get-Date -Format o);path=$root} |ConvertTo-Json
 [System.IO.File]::WriteAllText((Join-Path $root 'installation.json'),$manifest,[System.Text.Encoding]::UTF8)
 Status 'done' 100 'MarK + TD AI Studio PRO е инсталиран! Кликни СТАРТУВАЈ MarK.'
 exit 0
}catch{
 $e=$_.Exception.Message
 try{Status 'error' 0 ('Грешка: '+$e)}catch{}
 try{Add-Content -LiteralPath $log -Value ('ERROR: '+$e) -Encoding UTF8}catch{}
 exit 1
}
