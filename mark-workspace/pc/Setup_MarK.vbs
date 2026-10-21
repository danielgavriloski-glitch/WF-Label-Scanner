Option Explicit
Dim sh, fso, home, cmd
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
home = fso.GetParentFolderName(WScript.ScriptFullName)
cmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File """ & home & "\installer\Setup-MarK-GUI.ps1" & """"
sh.Run cmd, 0, False
