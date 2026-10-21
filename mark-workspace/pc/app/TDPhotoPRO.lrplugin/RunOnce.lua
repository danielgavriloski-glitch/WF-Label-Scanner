local LrTasks=import 'LrTasks'
local LrDialogs=import 'LrDialogs'
local Importer=require 'Importer'
LrTasks.startAsyncTask(function()
  local ok,err=pcall(Importer.run)
  LrDialogs.message('TD Photo AI PRO',ok and 'Queue checked. See result logs in TD AI Studio folder.' or tostring(err))
end)
