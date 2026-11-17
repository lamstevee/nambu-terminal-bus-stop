import test from 'node:test';import assert from 'node:assert/strict';
import {activeAt,directionAt,validateSchedule,pollingMs} from '../lib/schedule.mjs';
import {pickLeg,Transit} from '../lib/transit.mjs';
const at=t=>new Date('2026-10-08T'+t+'+09:00');
test('morning, evening and midnight boundaries and 24h mode',()=>{
 for(const [t,active] of [['05:59:59',false],['06:00:00',true],['09:59:59',true],['10:00:00',false],['17:59:59',false],['18:00:00',true],['23:59:59',true],['00:00:00',false]]) assert.equal(activeAt({},at(t)),active,t);
 for(const t of ['00:00:00','12:00:00','23:59:59'])assert.equal(activeAt({activation:'always'},at(t)),true);
 assert.equal(directionAt({},at('08:00:00')),'commute');assert.equal(directionAt({},at('19:00:00')),'home');
 assert.equal(directionAt({direction:'commute'},at('19:00:00')),'commute');
 assert.equal(directionAt({direction:'home'},at('08:00:00')),'home');
});
test('custom hours, automatic direction outside windows, validation and polling quota',()=>{
 const c={commuteStart:'07:00',commuteEnd:'09:00',homeStart:'17:00',homeEnd:'22:00'};
 assert.equal(activeAt(c,at('06:59:59')),false);assert.equal(activeAt(c,at('07:00:00')),true);assert.equal(activeAt(c,at('09:00:00')),false);assert.equal(activeAt(c,at('17:00:00')),true);assert.equal(activeAt(c,at('22:00:00')),false);
 assert.equal(directionAt({...c,activation:'always'},at('11:00:00')),'commute');assert.equal(directionAt({...c,activation:'always'},at('12:00:00')),'home');
 for(const invalid of [{commuteStart:'10:00'}, {homeEnd:'25:00'}, {homeStart:'09:00'}, {direction:'other'}, {activation:'other'}])assert.throws(()=>validateSchedule(invalid));
 assert.equal(pollingMs({}),60000);assert.equal(pollingMs({activation:'always'}),100000);assert.ok(86400000/pollingMs({activation:'always'})<1000);
 assert.ok(86400000/pollingMs({commuteStart:'00:00',commuteEnd:'12:00',homeStart:'12:00',homeEnd:'24:00'})<1000);
});
test('reverse trip selects pharmaceutical association 22248 rather than 22228',()=>{
 const leg=pickLeg([{seq:1,stationNm:'한국제약바이오협회',arsId:'22228'},{seq:2,stationNm:'방배역'},{seq:20,stationNm:'한국제약바이오협회',arsId:'22248'},{seq:21,stationNm:'서울고등학교'},{seq:22,stationNm:'서초중학교'},{seq:23,stationNm:'남부터미널',arsId:'22157'}],'commute');
 assert.equal(leg.origin.arsId,'22248');assert.equal(leg.destination.arsId,'22157');assert.equal(leg.stops.length,4);
});
test('subway switches between 3호선 상행 and 하행 only',async()=>{
 const transit=new Transit(async()=>({ok:true,text:async()=>JSON.stringify({realtimeArrivalList:[{subwayId:'1003',updnLine:'상행',bstatnNm:'대화',arvlMsg2:'상행 열차'},{subwayId:'1003',updnLine:'하행',bstatnNm:'오금',arvlMsg2:'하행 열차'},{subwayId:'1004',updnLine:'상행',bstatnNm:'다른 호선'}]})}));
 assert.equal((await transit.subway('test','commute')).stations[0].trains[0].destination,'대화');
 assert.equal((await transit.subway('test','home')).stations[0].trains[0].destination,'오금');
 await assert.rejects(()=>transit.subway('https://example.com','home'),/URL/);
});
