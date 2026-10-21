local LrTasks=import 'LrTasks'
local Importer=require 'Importer'
LrTasks.startAsyncTask(function()
  while true do
    pcall(Importer.run)
    LrTasks.sleep(10)
  end
end)
