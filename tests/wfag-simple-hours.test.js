const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/core.js');
require('../app/src/main/assets/v12-core.js');
require('../app/src/main/assets/simple-hours.js');
const now=new Date('2026-11-19T18:00:00+01:00');
function base(){const s=C.fresh(now);s.settings.trackStart='2026-11-01';return s;}
function trip(date,departure,arrival,arrivalDate=date){return {id:'trip_1',date,departure,arrival,arrivalDate,note:'',workStart:'08:00'};}
function check(name,fn){fn();console.log('PASS',name);}
check('automatic past weekday is 8h',()=>{assert.equal(C.day(base(),'2026-11-17',now).worked,480);});
check('manual shorter shift wins over saved session and trip',()=>{
 const s=base();s.records['2026-11-18']={type:'work',start:'08:00',end:'13:30',overnight:false,pause:0,pausePaid:true,note:''};
 s.trips=[trip('2026-11-18','10:00','17:00')];
 s.workdaySessions={'2026-11-18':{date:'2026-11-18',startAt:1,endAt:null,status:'working',travel:[]}};
 assert.equal(C.day(s,'2026-11-18',now).worked,330);
 assert.equal(C.period(s,'2026-11-18','2026-11-18',now).worked,330);
});
check('finished trip extends automatic day',()=>{const s=base();s.trips=[trip('2026-11-18','10:00','19:00')];assert.equal(C.day(s,'2026-11-18',now).worked,660);});
check('unfinished yesterday trip cannot create 30h',()=>{const s=base();s.trips=[trip('2026-11-18','10:00','')];const d=C.day(s,'2026-11-18',now);assert.equal(d.worked,480);assert.equal(d.travel,0);assert.equal(d.pendingTripReview,true);});
check('today ongoing trip counts until now',()=>{const s=base();s.trips=[trip('2026-11-19','10:00','')];assert.equal(C.day(s,'2026-11-19',now).worked,600);});
check('manual change survives JSON backup reload',()=>{const s=base();s.records['2026-11-18']={type:'work',start:'08:00',end:'12:00',overnight:false,pause:0,pausePaid:true,note:''};const restored=C.validate(JSON.parse(JSON.stringify(s)));assert.equal(C.day(restored,'2026-11-18',now).worked,240);});
