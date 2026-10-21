# MarK TD AI Studio PRO single-folder installer (Windows 10/11 64-bit)
# Creates isolated environment, gets verified upstream model/runtime to APP folder. Does NOT change old TD files.
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$log=Join-Path $root 'data\install_log.txt'
New-Item -ItemType Directory -Force (Join-Path $root 'data') | Out-Null
Start-Transcript -Path $log -Append | Out-Null
try {
 Write-Host '======== MarK + TD Studio automated installation ========' -ForegroundColor Cyan
 if ([Environment]::Is64BitOperatingSystem -eq $false) { throw 'Windows 64-bit required.' }
 $python=$null
 $candidates=@()
 if (Get-Command py -ErrorAction SilentlyContinue){ $candidates+= 'py -3.13';$candidates+='py -3.12';$candidates+='py -3.11';$candidates+='py -3' }
 if (Get-Command python -ErrorAction SilentlyContinue){$candidates+='python'}
 foreach($candidate in $candidates){
   $parts=$candidate.Split(' ');$exe=$parts[0];$a=@();if($parts.Count -gt 1){$a=@($parts[1])}
   try{$ver=& $exe @a -c "import sys;print(sys.version_info.major,sys.version_info.minor)" 2>$null
     if($LASTEXITCODE -eq 0 -and $ver -match '^3 (11|12|13)'){$python=$candidate;break}
   }catch{}
 }
 if(-not $python){
     $winget=Get-Command winget.exe -ErrorAction SilentlyContinue
     if($winget){
       Write-Host 'Python missing. Attempting automatic installation of Python 3.13 via Windows Package Manager...'
       & winget.exe install --id Python.Python.3.13 --exact --silent --accept-package-agreements --accept-source-agreements
       $launcher=Join-Path $env:LOCALAPPDATA 'Programs\Python\Launcher\py.exe'
       $pythonexe=Join-Path $env:LOCALAPPDATA 'Programs\Python\Python313\python.exe'
       if(Test-Path $pythonexe){$python=$pythonexe}
       elseif(Test-Path $launcher){$python=$launcher+' -3.13'}
     }
   }
   if(-not $python){throw 'Automatic Python installation could not complete. Install Python 3.13 and rerun this installer; original TD is unchanged.'}
 $p=$python.Split(' ');$pyexe=$p[0];$pyargs=@();if($p.Count -gt 1){$pyargs=@($p[1])}
 $venv=Join-Path $root '.venv'
 $pythonVenv=Join-Path $venv 'Scripts\python.exe'
 if(!(Test-Path $pythonVenv)){Write-Host 'Creating isolated Python environment...'; & $pyexe @pyargs -m venv $venv; if($LASTEXITCODE -ne 0){throw 'venv failed'}}
 Write-Host 'Installing Python modules to app-local environment...'
 & $pythonVenv -m pip install --disable-pip-version-check -r (Join-Path $root 'app\requirements.txt')
 if($LASTEXITCODE -ne 0){throw 'Required Python modules failed to install'}
 Write-Host 'Installing optional RAW photo support...'
 & $pythonVenv -m pip install --disable-pip-version-check rawpy
 if($LASTEXITCODE -ne 0){Write-Warning 'Optional rawpy not installed. JPEG scans will continue to work.'}
 $bin=Join-Path $root 'runtime\llama\llama-server.exe'
 if(!(Test-Path $bin)){
   Write-Host 'Getting embedded llama.cpp runtime from its official GitHub release...'
   [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
   try {$release=Invoke-RestMethod -Uri 'https://api.github.com/repos/ggml-org/llama.cpp/releases/latest' -Headers @{ 'User-Agent'='MarK-local-installer' }}
   catch {throw 'GitHub official releases are unreachable. Internet required once: '+$_}
   $assets=@($release.assets)
   # Prefer Vulkan on NVIDIA GPUs; CPU fallback works without GPU support.
   $gpu=Get-Command nvidia-smi.exe -ErrorAction SilentlyContinue
   $asset=$null
   if($gpu){$asset=$assets|Where-Object{$_.name -match 'bin-win-vulkan-x64\.zip$'}|Select-Object -First 1}
   if(!$asset){$asset=$assets|Where-Object{$_.name -match 'bin-win-cpu-x64\.zip$'}|Select-Object -First 1}
   if(!$asset){throw 'Official Windows llama.cpp binary not found on GitHub latest release.'}
   $zip=Join-Path $root 'runtime\mark_llama_download.zip'
   Write-Host ('Downloading '+$asset.name)
   & curl.exe --location --fail --retry 3 --output $zip $asset.browser_download_url
   if($LASTEXITCODE -ne 0){throw 'Failed to download official inference runtime'}
   Expand-Archive -Path $zip -DestinationPath (Join-Path $root 'runtime\llama') -Force
   Remove-Item -LiteralPath $zip -Force
   if(!(Test-Path $bin)){
       $found=Get-ChildItem (Join-Path $root 'runtime\llama') -Filter 'llama-server.exe' -Recurse|Select-Object -First 1
       if(!$found){throw 'llama-server.exe not found in official runtime archive'}
       Copy-Item -Path (Join-Path $found.Directory.FullName '*') -Destination (Join-Path $root 'runtime\llama') -Recurse -Force
   }
 }
 $modelDir=Join-Path $root 'models';New-Item -ItemType Directory -Force $modelDir|Out-Null
 $repo='https://huggingface.co/Qwen/Qwen3-VL-4B-Instruct-GGUF/resolve/main/'
 $models=@(
   @{name='Qwen3VL-4B-Instruct-Q4_K_M.gguf';min=1800000000},
   @{name='mmproj-Qwen3VL-4B-Instruct-Q8_0.gguf';min=300000000}
 )
 foreach($item in $models){
   $dest=Join-Path $modelDir $item.name
   if ((Test-Path $dest) -and ((Get-Item $dest).Length -ge $item.min)){Write-Host ('Already present: '+$item.name);continue}
   $partial="$dest.partial"
   Write-Host ('Downloading model file (large one-time download): '+$item.name) -ForegroundColor Yellow
   & curl.exe --location --fail --retry 4 --continue-at - --output $partial ($repo+$item.name)
   if($LASTEXITCODE -ne 0){throw ('Could not download '+$item.name+'. Resume by running installer again.')}
   if((Get-Item $partial).Length -lt $item.min){throw ('Unexpectedly small model download: '+$item.name)}
   Move-Item -LiteralPath $partial -Destination $dest -Force
 }
 foreach($d in @('workspace\01_INPUT_VIDEO','workspace\02_MUSIC','workspace\06_OUTPUT')){New-Item -ItemType Directory -Force (Join-Path $root $d)|Out-Null}
 Write-Host 'Installing Lightroom plugin files side-by-side without changing original plugin...'
 $lightroomModules=Join-Path $env:APPDATA 'Adobe\Lightroom\Modules'
 foreach($entry in @(@{dir='TDPhotoPRO.lrplugin';target='MarK_TDPhotoPRO.lrplugin'},@{dir='TD_Lightroom_Bridge.lrplugin';target='MarK_TD_Lightroom_Bridge.lrplugin'})){
    $from=Join-Path (Join-Path $root 'app') $entry.dir
    $to=Join-Path $lightroomModules $entry.target
    if((Test-Path $from) -and !(Test-Path $to)){
       New-Item -ItemType Directory -Force $lightroomModules | Out-Null
       Copy-Item -LiteralPath $from -Destination $to -Recurse
       Write-Host ('Copied optional Lightroom plugin: '+$entry.target)
    }
 }
 Write-Host 'Premiere UXP source bundled; enabling it inside Adobe may require the Adobe UXP tooling.'
 Write-Host 'Creating MarK desktop shortcut...'
 try {
   $desktop=[Environment]::GetFolderPath('Desktop')
   $shell=New-Object -ComObject WScript.Shell
   $lnk=$shell.CreateShortcut((Join-Path $desktop 'MarK - TD AI Studio PRO.lnk'))
   $lnk.TargetPath=Join-Path $root 'START_MarK_TD.bat'
   $lnk.WorkingDirectory=$root
   $lnk.Description='MarK + TD AI Studio PRO – local app'
   $ico=Join-Path $root 'brand\MarK.ico'
   if(Test-Path $ico){$lnk.IconLocation=$ico}
   $lnk.Save()
 }catch{Write-Warning ('Shortcut not created: '+$_)}
 Write-Host 'Installation finished. To start run START_MarK_TD.bat' -ForegroundColor Green
 Write-Host 'Note: the original TD project and TD Booking APK are not modified.'
} catch {
 Write-Host ('ERROR: '+ $_.Exception.Message) -ForegroundColor Red
 exit 1
} finally {Stop-Transcript|Out-Null}
