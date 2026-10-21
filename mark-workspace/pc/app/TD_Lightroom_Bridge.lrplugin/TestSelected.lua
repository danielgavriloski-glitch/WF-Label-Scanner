local LrApplication = import 'LrApplication'
local LrDialogs = import 'LrDialogs'
local c = LrApplication.activeCatalog()
local p = c:getTargetPhoto()
if not p then
    LrDialogs.message('TD Production AI', 'No photo selected in Lightroom Classic.', 'info')
else
    local name = p:getFormattedMetadata('fileName') or 'Selected photo'
    LrDialogs.message('TD Production AI Bridge', 'Connection OK. Selected: ' .. tostring(name), 'info')
end
