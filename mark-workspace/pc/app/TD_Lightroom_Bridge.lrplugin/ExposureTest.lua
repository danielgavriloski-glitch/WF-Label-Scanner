-- Non-destructive Lightroom Develop adjustment, only ONE selected image and with confirmation.
local LrApplication = import 'LrApplication'
local LrDialogs = import 'LrDialogs'
local LrTasks = import 'LrTasks'
local catalog = LrApplication.activeCatalog()
local photo = catalog:getTargetPhoto()
if not photo then
    LrDialogs.message('TD AI', 'Select one photo first.', 'info')
    return
end
local confirmation = LrDialogs.confirm('TD AI Develop Test',
    'Add +0.20 EV to the selected photo? This creates a new Lightroom Develop history step and does not change the original file.')
if confirmation ~= 'ok' then return end
LrTasks.startAsyncTask(function()
    local success, err = pcall(function()
        local settings = photo:getDevelopSettings() or {}
        local exposure = tonumber(settings.Exposure2012) or 0
        local newValue = math.max(-5, math.min(5, exposure + 0.20))
        catalog:withWriteAccessDo('TD AI Test +0.20 EV', function()
            photo:applyDevelopSettings({ Exposure2012 = newValue }, 'TD AI Test +0.20 EV')
        end)
    end)
    if success then
        LrDialogs.message('TD AI', 'Applied +0.20 EV to selected photo. Check Develop History.', 'info')
    else
        LrDialogs.message('TD AI', 'Could not apply adjustment: ' .. tostring(err), 'critical')
    end
end)
