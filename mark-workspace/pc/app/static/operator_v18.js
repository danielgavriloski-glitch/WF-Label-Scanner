(() => {
  const $ = id => document.getElementById(id);
  const box = $('tdop18');
  if (!box) return;
  const stateNode = $('tdop18-status');
  const logNode = $('tdop18-log');
  const startBtn = $('tdop18-start');
  async function api18(path, body) {
    const opts = {credentials:'same-origin', cache:'no-store'};
    if (body !== undefined) {
      opts.method = 'POST';
      opts.headers = {'Content-Type':'application/json', 'X-TD-Client':'TD-v1.4'};
      opts.body=JSON.stringify(body);
    }
    const response=await fetch('/api/operator18/'+path,opts);
    let data={};
    try {data=await response.json();} catch {throw new Error('Unexpected server reply (try restarting TD AI Studio).');}
    if(!response.ok) throw new Error(data.error || ('HTTP '+response.status));
    return data;
  }
  async function update(){
    try {
      const d=await api18('status');
      const labels={idle:'IDLE',running:'RUNNING',stopping:'STOPPING',stopped:'STOPPED',finished:'SUCCESS',needs_review:'NEEDS REVIEW',failed:'FAILED'};
      stateNode.textContent=(labels[d.phase]||d.phase)+': '+d.message;
      logNode.textContent=(d.history||[]).join('\n') || 'Waiting for a safe test...';
      startBtn.disabled=d.phase==='running'||d.phase==='stopping';
    } catch(e){stateNode.textContent='Cannot read operator status: '+e.message;}
  }
  startBtn.onclick=async () => {
    stateNode.textContent='Starting...';
    try {await api18('start',{});await update();}
    catch(e){stateNode.textContent='Cannot start: '+e.message;}
  };
  $('tdop18-stop').onclick=async()=>{
    try {await api18('stop',{});await update();}
    catch(e){stateNode.textContent='Stop failed: '+e.message;}
  };
  update();setInterval(update,1500);
})();
