import test from 'node:test';
import assert from 'node:assert/strict';
import { parseArrival, pickLeg, readBusPayload, Transit } from '../lib/transit.mjs';
import { activeAt } from '../lib/schedule.mjs';
test('Seoul schedule includes 18:00 and stops precisely at midnight', () => {
  assert.equal(activeAt({},new Date('2026-10-08T08:59:59Z')), false);
  assert.equal(activeAt({},new Date('2026-10-08T09:00:00Z')), true);
  assert.equal(activeAt({},new Date('2026-10-08T14:59:59Z')), true);
  assert.equal(activeAt({},new Date('2026-10-08T15:00:00Z')), false);
});
test('arrival messages preserve precision and non-countdown statuses', () => {
  assert.equal(parseArrival('3분 25초후[2번째 전]', 1000).dueAt, 206000);
  assert.equal(parseArrival('45초후', 1000).dueAt, 46000);
  assert.equal(parseArrival('3분후', 1000).precision, 'minute');
  for (const m of ['운행종료', '출발대기', '정보없음', '회차대기']) assert.equal(parseArrival(m, 1000).dueAt, null);
  assert.equal(parseArrival('곧 도착', 1000).precision, 'soon');
});
test('route validation rejects reverse direction and selects the downstream origin', () => {
  const rows = [
    { seq: 1, stationNm: '한국제약바이오협회' }, { seq: 2, stationNm: '남부터미널역(한일시멘트)', station: 'wrong' },
    { seq: 3, stationNm: '다른 정류장' }, { seq: 4, stationNm: '남부터미널역(한일시멘트)', station: 'correct' },
    { seq: 5, stationNm: '중간 정류장' }, { seq: 6, stationNm: '한국제약바이오협회' }
  ];
  const leg = pickLeg([...rows].reverse());
  assert.equal(leg.origin.station, 'correct'); assert.equal(leg.stops.length, 3);
  assert.throws(() => pickLeg(rows.slice(0, 3)));
  assert.throws(() => pickLeg([{ seq: 1, stationNm: '남부터미널' }, { seq: 2, stationNm: '회차', transYn: 'Y' }, { seq: 3, stationNm: '한국제약바이오협회' }]));
});
test('API handles JSON, XML, empty responses and gateway auth errors', () => {
  assert.equal(readBusPayload('{"msgHeader":{"headerCd":"0"},"msgBody":{"itemList":{"rtNm":"461"}}}')[0].rtNm, '461');
  assert.equal(readBusPayload('<ServiceResult><msgHeader><headerCd>0</headerCd></msgHeader><msgBody><itemList><rtNm>461</rtNm></itemList></msgBody></ServiceResult>')[0].rtNm, '461');
  assert.deepEqual(readBusPayload('{"msgHeader":{"headerCd":"4"}}'), []);
  assert.throws(() => readBusPayload('<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>'));
  assert.throws(() => readBusPayload('{"msgHeader":{"headerCd":"7","headerMsg":"secret-key"}}'), e => !e.message.includes('secret-key'));
});
test('live pipeline resolves three routes, rejects wrong stop order, caches and never substitutes demo on failure', async () => {
  const calls = [];
  const fakeFetch = async url => {
    calls.push(String(url)); url = new URL(url);
    let rows;
    if (url.pathname.endsWith('getBusRouteList')) { const n = url.searchParams.get('strSrch'); rows = [{ busRouteNm: n, busRouteId: `route${n}` }]; }
    else if (url.pathname.endsWith('getStaionByRoute')) rows = [
      { seq: '30', station: 'origin', stationNm: '남부터미널역(한일시멘트)', arsId: '22153' },
      { seq: '31', station: 'destination', stationNm: '한국제약바이오협회' }
    ];
    else rows = ['461', '641', '4319'].flatMap(n => [
      { busRouteId: `route${n}`, staOrd: '99', arrmsg1: '1초후' },
      { busRouteId: `route${n}`, staOrd: '30', arrmsg1: '3분 25초후', arrmsg2: '10분후' }
    ]);
    return { ok: true, text: async () => JSON.stringify({ msgHeader: { headerCd: '0' }, msgBody: { itemList: rows } }) };
  };
  const transit = new Transit(fakeFetch), config = { mode: 'live', busKey: 'a%2Bb%3D', subwayKey: '' };
  const snapshot = await transit.snapshot(config, true);
  assert.equal(snapshot.buses.length, 3); assert.equal(snapshot.buses[0].first.message, '3분 25초후');
  assert.equal(snapshot.arsId, '22153'); assert.equal(calls.length, 7);
  assert.equal(new URL(calls[0]).searchParams.get('serviceKey'), 'a+b=');
  await transit.snapshot(config, true); assert.equal(calls.length, 7);
  const failed = new Transit(async () => { throw new Error('연결 실패'); });
  const error = await failed.snapshot(config, true); assert.equal(error.mode, 'live'); assert.deepEqual(error.buses, []); assert.equal(error.error, '연결 실패');
});
test('live mode with no key has no synthetic arrival data', async () => {
  const s = await new Transit().snapshot({ mode: 'live', busKey: '' }, true);
  assert.deepEqual(s.buses, []); assert.match(s.error, /서비스키/);
});
