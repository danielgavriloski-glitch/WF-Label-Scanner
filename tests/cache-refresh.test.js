'use strict';
const assert=require('node:assert/strict');
const test=require('node:test');
const Core=require('../app/src/main/assets/calculation-cache.js');

function fresh(){
  const state=Core.fresh(new Date('2026-10-01T12:00:00Z'));
  state.settings.trackStart='2026-10-01';
  return state;
}

test('Minute refresh recalculates the current week while reusing completed history',()=>{
  const state=fresh(),day=Core.day;
  let calculations=0;
  Core.day=function(...args){calculations++;return day(...args);};
  try{
    const first=Core.period(state,'2026-10-01','2026-11-26',new Date('2026-11-26T10:00:00Z'));
    assert.ok(calculations>7);
    calculations=0;
    const same=Core.period(state,'2026-10-01','2026-11-26',new Date('2026-11-26T10:00:30Z'));
    assert.equal(same.worked,first.worked);
    assert.equal(calculations,0);
    const next=Core.period(state,'2026-10-01','2026-11-26',new Date('2026-11-26T10:01:00Z'));
    assert.equal(next.worked,first.worked+1);
    assert.equal(calculations,7,'closed weeks must not be recalculated for a minute tick');
  }finally{Core.day=day;}
});

test('Cached closed ranges retain unresolved flags; edits invalidate known totals and review',()=>{
  let state=Core.putTrip(fresh(),{date:'2026-10-05',departure:'10:00',arrival:'',arrivalDate:'2026-10-05',note:''}).state;
  const now=new Date('2026-11-26T10:00:00Z');
  const first=Core.period(state,'2026-10-05','2026-10-05',now);
  assert.equal(first.worked,0);
  assert.equal(first.totalsComplete,false);
  assert.deepEqual(first.reviewDates,['2026-10-05']);
  const cached=Core.period(state,'2026-10-05','2026-10-05',new Date('2026-11-26T10:01:00Z'));
  assert.equal(cached.totalsComplete,false);
  assert.equal(cached.rows[0].worked,null);
  state.trips[0].arrival='14:00';
  Core.invalidateCache(state);
  const resolved=Core.period(state,'2026-10-05','2026-10-05',now);
  assert.equal(resolved.worked,360);
  assert.equal(resolved.totalsComplete,true);
  assert.deepEqual(resolved.reviewDates,[]);
  const edited=Core.putRange(state,'2026-10-05','2026-10-05',{
    type:'work',start:'09:00',end:'16:00',pause:0,pausePaid:true,overnight:false,note:''
  }).state;
  assert.equal(Core.period(edited,'2026-10-05','2026-10-05',now).worked,300,'new state identity must not share old cached totals');
});

test('An overnight shift on yesterday and the previous week keeps advancing until its end',()=>{
  const state=fresh();
  state.records['2026-11-01']={type:'work',start:'22:00',end:'06:00',pause:0,pausePaid:true,overnight:true,note:''};
  const two=new Date('2026-11-02T01:00:00Z'),three=new Date('2026-11-02T02:00:00Z');
  assert.equal(Core.period(state,'2026-11-01','2026-11-01',two).worked,240);
  assert.equal(Core.period(state,'2026-11-01','2026-11-01',three).worked,300);
  assert.equal(Core.week(state,'2026-10-26',three).rows[6].worked,300);
  assert.equal(Core.week(state,'2026-10-26',new Date('2026-11-02T03:00:00Z')).rows[6].worked,360);
  assert.equal(Core.period(state,'2026-11-01','2026-11-01',new Date('2026-11-02T07:00:00Z')).worked,480);
  assert.equal(Core.period(state,'2026-11-01','2026-11-01',two).worked,240,'clock rollback must restore the actual partial shift');
});

test('Midnight changes a forgotten open trip into review and clock rollback restores elapsed time',()=>{
  const state=Core.putTrip(fresh(),{date:'2026-11-02',departure:'10:00',arrival:'',arrivalDate:'2026-11-02',note:''}).state;
  const before=new Date('2026-11-02T22:59:00Z'),midnight=new Date('2026-11-02T23:00:00Z');
  const active=Core.period(state,'2026-11-02','2026-11-02',before);
  assert.equal(active.rows[0].worked,959);
  assert.equal(active.totalsComplete,true);
  const review=Core.period(state,'2026-11-02','2026-11-02',midnight);
  assert.equal(review.rows[0].worked,null);
  assert.equal(review.rows[0].trips[0].travel,null);
  assert.equal(review.totalsComplete,false);
  assert.equal(Core.period(state,'2026-11-02','2026-11-02',before).rows[0].worked,959);
});
