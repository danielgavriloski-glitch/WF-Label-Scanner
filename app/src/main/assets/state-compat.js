(function(root,factory){
  if(typeof module==='object'&&module.exports)module.exports=factory();
  else root.StateCompat=factory();
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  const clone=x=>JSON.parse(JSON.stringify(x));
  const own=(o,k)=>Object.prototype.hasOwnProperty.call(o,k);
  const attendance=new Set(['appId','schemaVersion','createdAt','settings','budgets','records','trips','audit','lastBackupAt']);
  function preserve(current,incoming){
    if(!incoming||typeof incoming!=='object'||Array.isArray(incoming))throw Error('Невалиден бекап.');
    const next=clone(incoming);
    if(!current)return next;
    // Old attendance-only backups never contained the newer optional modules.
    // A module present in the backup is deliberately restored, not merged by date.
    for(const key of Object.keys(current)){
      if(!attendance.has(key)&&!own(next,key))next[key]=clone(current[key]);
    }
    if(next.vehicleLog&&current.vehicleLog){
      for(const key of Object.keys(current.vehicleLog)){
        if(!own(next.vehicleLog,key))next.vehicleLog[key]=clone(current.vehicleLog[key]);
      }
    }
    return next;
  }
  return {preserve};
});
