-- Lightroom Classic SDK. Import new photos by reference; no duplicate copies.
-- A pre-existing catalog cannot automatically be relinked after external MOVE.
local LrApplication = import 'LrApplication'
local LrFileUtils = import 'LrFileUtils'
local LrJson = import 'LrJson'
local LrTasks = import 'LrTasks'
local LrPathUtils = import 'LrPathUtils'
local config = require 'Config'
local M = {}
local busy = false

local function applyJob(path)
  local text,err = LrFileUtils.readFile(path)
  if not text then error(err or ('Unable to read '..path)) end
  local data = LrJson.decode(text)
  if not data or type(data.files) ~= 'table' or #data.files == 0 then error('Invalid TD import manifest') end
  local catalog=LrApplication.activeCatalog()
  local imported=0
  local errors={}
  for startIdx=1,#data.files,25 do
    local lastIdx=math.min(startIdx+24,#data.files)
    local ok,exception=pcall(function()
      catalog:withWriteAccessDo('TD Photo PRO - Import and label',function()
        for i=startIdx,lastIdx do
          local item=data.files[i]
          if type(item.path)=='string' and LrFileUtils.exists(item.path)=='file' then
            local photo=catalog:findPhotoByPath(item.path)
            if not photo then photo=catalog:addPhoto(item.path) end
            if photo then
              local color=(item.color or 'Blue'):lower()
              if not ({red=true,yellow=true,green=true,blue=true,purple=true})[color] then color='blue' end
              photo:setRawMetadata('colorNameForLabel',color)
              imported=imported+1
            end
          else
            errors[#errors+1]='Missing media: '..tostring(item.path)
          end
        end
      end,{timeout=60})
    end)
    if not ok then
      errors[#errors+1]=tostring(exception)
      break
    end
    LrTasks.yield()
  end
  local resultPath=path..'.result.txt'
  LrFileUtils.writeFile(resultPath,'Imported/labeled: '..imported..' / '..#data.files..'\nErrors: '..table.concat(errors,'\n'))
  if #errors==0 then
    local ok,reason=LrFileUtils.move(path,path..'.done')
    if not ok then error(reason or 'Could not mark queue item complete') end
  else
    -- Preserve queue for manual inspection; do NOT blindly retry failed batches.
    LrFileUtils.move(path,path..'.needs_review')
  end
end

function M.run()
  if busy then return end
  busy=true
  local ok,err=pcall(function()
    local queue=config.queue
    if not queue or LrFileUtils.exists(queue)~='directory' then return end
    for path in LrFileUtils.files(queue) do
      if path:lower():match('%.json$') then
        local success,reason=pcall(applyJob,path)
        if not success then
          LrFileUtils.writeFile(path..'.error.txt',tostring(reason))
          LrFileUtils.move(path,path..'.needs_review')
        end
      end
    end
  end)
  busy=false
  if not ok then error(err) end
end
return M
