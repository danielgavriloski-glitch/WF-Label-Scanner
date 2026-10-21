// TD Premiere Bridge v1.7.2. UXP plugin runs inside Adobe Premiere only.
// Restricts input to exact authenticated commands from localhost.
const { entrypoints } = require('uxp');
entrypoints.setup({panels:{tdPremiere172:{show(){},hide(){}}}});
const pp = require('premierepro');
const endpoint = 'http://127.0.0.1:8765';
const info = document.getElementById('log');
const field = document.getElementById('pair');
let token = localStorage.getItem('td_premiere_bridge172_key') || '';
let busy = false, lastId = '';
function reportText(s){info.textContent = new Date().toLocaleTimeString() + ' · ' + s;}
function localPath(s){
  if (typeof s !== 'string' || !/^[a-z]:\\/i.test(s)) throw new Error('Expected local Windows drive path');
  return s;
}
async function endpointFetch(path, opts={}){
  const response = await fetch(endpoint+path,{
    ...opts,
    headers:{'X-TD-Bridge-Key':token,'Content-Type':'application/json',...(opts.headers||{})}
  });
  if(!response.ok) throw new Error('Bridge HTTP '+response.status);
  return await response.json();
}
async function runTask(task){
  let created=false, imported=false, message='', error='';
  try{
    if(task.action!=='create_project') throw new Error('Unsupported command');
    const projectPath=localPath(task.project_path);
    const xmlPath=localPath(task.xml_path);
    reportText('Создавам Premiere проект: '+projectPath);
    const project=await pp.Project.createProject(projectPath);
    if(!project) throw new Error('Premiere refused project creation');
    created=!!(await project.save());
    if(!created) throw new Error('Premiere did not confirm project save');
    // XML import is a best-effort API call; fallback in Python uses File > Import.
    try{
      const root=await project.getRootItem();
      await project.importFiles([xmlPath],true,root,false);
      const seqs=await project.getSequences();
      imported=Array.isArray(seqs) && seqs.length>0;
      if(imported) await project.save();
    }catch(e){message='Project created, XML may need File > Import: '+String(e);}
    message = message || (imported ? 'Created project and imported AI timeline' : 'Project created; XML import not confirmed');
    reportText(message);
  }catch(e){
    error=String(e);reportText('Грешка: '+error);
  }
  try{
    await endpointFetch('/api/bridge/report',{method:'POST',body:JSON.stringify({id:task.id,created,imported,message,error})});
  }catch(e){reportText('Извештајот не стигна до TD AI Studio: '+String(e));}
}
async function poll(){
  if(!token||busy)return;
  busy=true;
  try{
    const res=await endpointFetch('/api/bridge/poll');
    const cmd=res.command;
    if(cmd&&cmd.id!==lastId){lastId=cmd.id;await runTask(cmd);}
    else if(!cmd && !info.textContent.includes('поврзан')) reportText('Поврзан со TD AI Studio. Чекам задача.');
  }catch(e){reportText('Нема врска: '+String(e));}
  finally{busy=false;}
}
document.getElementById('connect').onclick=async()=>{
  const v=field.value.trim();
  if(!/^[0-9a-f]{48}$/i.test(v)){reportText('Внеси го 48-цифрениот Premiere bridge клуч, не 6-цифрениот PIN.');return;}
  token=v;localStorage.setItem('td_premiere_bridge172_key',token);field.value='';
  lastId='';await poll();
};
document.getElementById('disconnect').onclick=()=>{
  token='';field.value='';localStorage.removeItem('td_premiere_bridge172_key');reportText('Исклучен');
};
if(token)reportText('Клучот е зачуван. Проверувам врска...');
setInterval(()=>{poll().catch(e=>reportText(String(e)));},2500);
setTimeout(()=>{poll().catch(()=>{});},750);
