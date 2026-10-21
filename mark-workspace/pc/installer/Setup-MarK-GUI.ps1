param()
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()
$source=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$form=New-Object System.Windows.Forms.Form
$form.Text='MarK Engine + TD AI Studio PRO | Setup v0.3'
$form.StartPosition='CenterScreen';$form.Size=New-Object System.Drawing.Size(755,605)
$form.MinimumSize=$form.Size
$form.MaximumSize=$form.Size
$form.BackColor=[System.Drawing.Color]::FromArgb(16,23,36)
$form.ForeColor=[System.Drawing.Color]::White
$form.Font=New-Object System.Drawing.Font('Segoe UI',10)
$form.Icon= if(Test-Path (Join-Path $source 'brand\MarK.ico')) {New-Object System.Drawing.Icon((Join-Path $source 'brand\MarK.ico'))}else{$null}
function Label([string]$text,[int]$x,[int]$y,[int]$w,[int]$h,[int]$size=10){
 $l=New-Object System.Windows.Forms.Label;$l.Text=$text;$l.Location=New-Object System.Drawing.Point($x,$y);$l.Size=New-Object System.Drawing.Size($w,$h)
 $l.ForeColor=[System.Drawing.Color]::White;$l.Font=New-Object System.Drawing.Font('Segoe UI',$size);$form.Controls.Add($l);return $l
}
$accent=[System.Drawing.Color]::FromArgb(28,206,190)
$gold=[System.Drawing.Color]::FromArgb(224,187,104)
$logo=New-Object System.Windows.Forms.PictureBox
$logo.Location=New-Object System.Drawing.Point(26,20);$logo.Size=New-Object System.Drawing.Size(128,79)
$logo.SizeMode='Zoom'
$p=Join-Path $source 'brand\MarK_Logo_Transparent_2K.png'
if(Test-Path $p){$logo.Image=[System.Drawing.Image]::FromFile($p)}
$form.Controls.Add($logo)
$title=Label 'MarK ENGINE' 177 27 480 44 22;$title.ForeColor=$gold
$sub=Label 'TD AI Studio PRO  •  Една апликација, една инсталација' 177 73 510 26 11
$line=New-Object System.Windows.Forms.Panel;$line.Location=New-Object System.Drawing.Point(24,120);$line.Size=New-Object System.Drawing.Size(685,2);$line.BackColor=$accent;$form.Controls.Add($line)
$desc=Label 'Избери диск и папка за новиот MarK. Старото TD Studio останува недопрено.' 25 135 690 44 10
$driveLabel=Label 'Диск (HDD / SSD)' 25 190 350 28
$drives=New-Object System.Windows.Forms.ComboBox;$drives.DropDownStyle='DropDownList'
$drives.Location=New-Object System.Drawing.Point(26,217);$drives.Size=New-Object System.Drawing.Size(325,28)
foreach($d in [System.IO.DriveInfo]::GetDrives()){
 if($d.IsReady -and ($d.DriveType -eq [System.IO.DriveType]::Fixed)){
  [void]$drives.Items.Add(('{0}    ({1:N1} GB слободни)' -f $d.Name,($d.AvailableFreeSpace/1GB)))
 }
}
$form.Controls.Add($drives)
$pathLabel=Label 'Папка за инсталација' 25 262 400 26
$pathBox=New-Object System.Windows.Forms.TextBox
$pathBox.Location=New-Object System.Drawing.Point(26,292);$pathBox.Size=New-Object System.Drawing.Size(554,28)
$pathBox.Font=New-Object System.Drawing.Font('Segoe UI',10)
$form.Controls.Add($pathBox)
$browse=New-Object System.Windows.Forms.Button
$browse.Text='Пребарај...';$browse.Location=New-Object System.Drawing.Point(590,291);$browse.Size=New-Object System.Drawing.Size(119,30)
$form.Controls.Add($browse)
function DiskPath(){
 if($drives.SelectedIndex -lt 0){return}
 $root=$drives.SelectedItem.Substring(0,3)
 $pathBox.Text=Join-Path $root 'MarK_TD_AI_Studio_PRO'
}
$drives.add_SelectedIndexChanged({DiskPath})
$hint=Label 'Во избраната папка ќе се инсталираат апликацијата, локалниот AI runtime, моделите и податоците.' 26 335 683 44 9
$hint.ForeColor=[System.Drawing.Color]::FromArgb(176,199,211)
$stepLabel=Label 'Подготвено за инсталација' 26 389 680 30 11
$stepLabel.ForeColor=$gold
$bar=New-Object System.Windows.Forms.ProgressBar
$bar.Location=New-Object System.Drawing.Point(26,422);$bar.Size=New-Object System.Drawing.Size(682,23)
$bar.Style='Continuous';$bar.Minimum=0;$bar.Maximum=100;$bar.Value=0
$form.Controls.Add($bar)
$note=Label 'Потребен е интернет при првото инсталирање (~3 GB+). Потоа AI работи локално, без Ollama.' 26 456 670 40 9
$note.ForeColor=[System.Drawing.Color]::FromArgb(180,200,210)
$install=New-Object System.Windows.Forms.Button;$install.Text='ИНСТАЛИРАЈ MarK'
$install.Location=New-Object System.Drawing.Point(26,515);$install.Size=New-Object System.Drawing.Size(300,39)
$install.BackColor=$accent;$install.ForeColor=[System.Drawing.Color]::FromArgb(5,18,22);$install.FlatStyle='Flat'
$form.Controls.Add($install)
$launch=New-Object System.Windows.Forms.Button;$launch.Text='СТАРТУВАЈ MarK'
$launch.Location=New-Object System.Drawing.Point(343,515);$launch.Size=New-Object System.Drawing.Size(210,39)
$launch.BackColor=$gold;$launch.ForeColor=[System.Drawing.Color]::Black;$launch.Enabled=$false;$launch.FlatStyle='Flat'
$form.Controls.Add($launch)
$close=New-Object System.Windows.Forms.Button;$close.Text='ЗАТВОРИ';$close.Location=New-Object System.Drawing.Point(564,515)
$close.Size=New-Object System.Drawing.Size(145,39);$form.Controls.Add($close)
if($drives.Items.Count -gt 0){
 $wanted=0
 for($i=0;$i -lt $drives.Items.Count;$i++){if($drives.Items[$i].ToString().StartsWith('H:\')){$wanted=$i;break}}
 $drives.SelectedIndex=$wanted
}else{$pathBox.Text=Join-Path $env:LOCALAPPDATA 'MarK_TD_AI_Studio_PRO'}
$browse.Add_Click({
 $fd=New-Object System.Windows.Forms.FolderBrowserDialog
 $fd.Description='Избери локација за MarK'
 if(Test-Path $pathBox.Text){$fd.SelectedPath=$pathBox.Text}
 if($fd.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK){
   $pathBox.Text=Join-Path $fd.SelectedPath 'MarK_TD_AI_Studio_PRO'
 }
})
$script:proc=$null;$script:statusPath='';$script:installedDir='';$script:done=$false
function Poll(){
 if($script:statusPath -and (Test-Path $script:statusPath)){
  try{
    $status=Get-Content -LiteralPath $script:statusPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $stepLabel.Text=[string]$status.message
    if($status.progress -ge 0 -and $status.progress -le 100){$bar.Value=[int]$status.progress}
    if($status.phase -eq 'done'){
      $script:done=$true;$script:installedDir=$status.target;$launch.Enabled=$true;$install.Enabled=$true
      $stepLabel.ForeColor=$accent
      $bar.Value=100
    }
    elseif($status.phase -eq 'error'){
      $install.Enabled=$true;$stepLabel.ForeColor=[System.Drawing.Color]::Salmon
      $note.Text='Лог: '+$status.log+'    (Поправи и пробај пак; постојните податоци не се бришат.)'
    }
  }catch{}
 }
 if($script:proc -and $script:proc.HasExited -and !$script:done -and (!$script:statusPath -or !(Test-Path $script:statusPath))){
   $install.Enabled=$true;$stepLabel.Text='Инсталацијата заврши неочекувано. Провери го Windows логот.'
 }
}
$timer=New-Object System.Windows.Forms.Timer;$timer.Interval=1000;$timer.Add_Tick({Poll})
$install.Add_Click({
 try{
   $target=[System.IO.Path]::GetFullPath($pathBox.Text.Trim())
   $sr=[System.IO.Path]::GetFullPath($source)
   if($target.TrimEnd('\') -eq $sr.TrimEnd('\')){
      # Same folder is a valid in-place upgrade of the extracted MarK package.
   }elseif($target.StartsWith($sr.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){
      throw 'Избери папка што не е внатре во распакуваниот инсталер.'
   }
   if(Test-Path $target){
     $items=@(Get-ChildItem -LiteralPath $target -Force)
     $isMark=((Test-Path (Join-Path $target 'app\mark_core')) -and (Test-Path (Join-Path $target 'app\server.py')))
     if($items.Count -gt 0 -and !$isMark){throw 'Оваа папка веќе има други фајлови. Избери празна папка или постоечка MarK инсталација.'}
   }
   $drive=[System.IO.Path]::GetPathRoot($target)
   $d=New-Object System.IO.DriveInfo($drive)
   if($d.AvailableFreeSpace -lt 7GB){throw 'За целосен AI модел препорачуваме најмалку 7 GB слободни. Избери друг диск.'}
   $id=[Guid]::NewGuid().ToString('N')
   $requestPath=Join-Path $env:TEMP ('mark_install_'+$id+'.json')
   $script:statusPath=Join-Path $env:TEMP ('mark_status_'+$id+'.json')
   $request=@{source=$source;target=$target} | ConvertTo-Json -Compress
   [System.IO.File]::WriteAllText($requestPath,$request,[System.Text.Encoding]::UTF8)
   $exe=Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
   $worker=Join-Path $PSScriptRoot 'Install-MarK-Worker.ps1'
   $args='-NoProfile -ExecutionPolicy Bypass -STA -File "'+$worker+'" -Request "'+$requestPath+'" -Status "'+$script:statusPath+'"'
   $script:proc=Start-Process -FilePath $exe -ArgumentList $args -WindowStyle Hidden -PassThru
   $install.Enabled=$false;$launch.Enabled=$false;$script:done=$false;$stepLabel.Text='Почнува инсталацијата...'
   $timer.Start()
 }catch{[System.Windows.Forms.MessageBox]::Show($_.Exception.Message,'MarK Setup','OK','Warning') | Out-Null}
})
$launch.Add_Click({
 $vbs=Join-Path $script:installedDir 'START_MarK.vbs'
 if(Test-Path $vbs){Start-Process -FilePath 'wscript.exe' -ArgumentList ('"'+$vbs+'"')}
 else{[System.Windows.Forms.MessageBox]::Show('Не е најден MarK стартерот.','MarK Setup')|Out-Null}
})
$close.Add_Click({$form.Close()})
$form.add_FormClosed({$timer.Stop();if($logo.Image){$logo.Image.Dispose()}})
[void]$form.ShowDialog()
