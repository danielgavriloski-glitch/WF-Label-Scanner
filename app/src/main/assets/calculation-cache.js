/* Completed weeks are immutable until a saved edit/import invalidates this cache.
   Live results refresh once per minute; nothing derived is written to user data. */
(function(root,factory){
  if(typeof module==='object'&&module.exports)module.exports=factory(require('./v12-core.js'));
  else factory(root.Core);
})(typeof globalThis!=='undefined'?globalThis:this,function(Core){
  'use strict';
  if(!Core||Core.__calculationCache)return Core;
  Core.__calculationCache=true;
  const cache=new WeakMap(),week=Core.week,period=Core.period;
  function bucket(state,now){
    const day=Core.today(now),minute=Math.floor(now.getTime()/60000);
    let b=cache.get(state);
    if(!b||minute<b.minute){b={day,minute,weeks:new Map(),periods:new Map(),live:new Map()};cache.set(state,b);}
    if(b.minute!==minute){b.live.clear();b.minute=minute;}
    b.day=day;return b;
  }
  function remember(map,key,fn){
    if(map.has(key))return map.get(key);
    const value=fn();if(map.size>1500)map.clear();map.set(key,value);return value;
  }
  Core.invalidateCache=function(state){if(state&&typeof state==='object')cache.delete(state);};
  Core.week=function(state,monday,now=new Date()){
    const b=bucket(state,now),closed=Core.plus(monday,6)<Core.plus(b.day,-1);
    return remember(closed?b.weeks:b.live,'week:'+monday,()=>week(state,monday,now));
  };
  Core.period=function(state,from,to,now=new Date()){
    const b=bucket(state,now),closed=to<Core.plus(b.day,-1);
    return remember(closed?b.periods:b.live,'period:'+from+':'+to,()=>period(state,from,to,now));
  };
  return Core;
});
