import { XMLParser } from 'fast-xml-parser';
import { activeAt, directionAt, pollingMs, tripInfo } from './schedule.mjs';
export const ROUTES = ['461', '641', '4319'];
export const START = '남부터미널역(한일시멘트)';
export const END = '한국제약바이오협회';
export function parseArrival(message = '', now = Date.now()) {
  const text = String(message);
  if (/운행종료|출발대기|정보없|회차대기/.test(text)) return { message: text, dueAt: null, precision: 'none' };
  if (/곧 도착|곧도착|도착예정/.test(text)) return { message: text, dueAt: null, precision: 'soon' };
  const min = text.match(/(\d+)\s*분/), sec = text.match(/(\d+)\s*초/);
  return { message: text || '도착정보 없음', dueAt: min || sec ? now + ((min ? +min[1] * 60 : 0) + (sec ? +sec[1] : 0)) * 1000 : null, precision: sec ? 'second' : min ? 'minute' : 'none' };
}
export function pickLeg(stations, direction = 'home') {
  const from = direction === 'commute' ? END : '남부터미널';
  const to = direction === 'commute' ? '남부터미널' : END;
  const sorted = [...stations].sort((a, b) => Number(a.seq) - Number(b.seq));
  for (let i = 0; i < sorted.length; i++) {
    if (!String(sorted[i].stationNm).includes(from)) continue;
    // Destination must be downstream, nearby, before visiting the origin again or turning.
    for (let j = i + 1; j < Math.min(i + 11, sorted.length); j++) {
      if (String(sorted[j].stationNm).includes(from)) break;
      if (String(sorted[j].stationNm).includes(to)) {
        return { origin: sorted[i], destination: sorted[j], stops: sorted.slice(i, j + 1) };
      }
      if (sorted[j].transYn === 'Y') break;
    }
  }
  throw new Error(`${to} 방향 정류장을 확인하지 못했습니다. 노선 변경 여부를 확인하세요.`);
}
export function decodeKey(key) { try { return decodeURIComponent(key.trim()); } catch { return key.trim(); } }
export function readBusPayload(raw) {
  let root;
  try { root = JSON.parse(raw); } catch { root = new XMLParser({ parseTagValue: false }).parse(raw); }
  root = root.ServiceResult || root;
  const h = root.msgHeader;
  if (!h) throw new Error('버스 API 인증 또는 응답 형식 오류. 활용신청 승인과 서비스키를 확인하세요.');
  if (String(h.headerCd) === '4') return [];
  if (String(h.headerCd) !== '0') throw new Error(`버스 API 오류 (${h.headerCd}). 키·활용신청·일일 호출량을 확인하세요.`);
  const rows = root.msgBody?.itemList;
  return rows ? Array.isArray(rows) ? rows : [rows] : [];
}
export class Transit {
  constructor(fetcher = fetch) { this.fetcher = fetcher; this.legs = null; this.legsAt = 0; this.lastBus = null; this.lastSubway = null; this.busAt = 0; this.subwayAt = 0; this.key = ''; }
  reset() { this.legs = null; this.lastBus = null; this.lastSubway = null; this.busAt = 0; this.subwayAt = 0; this.busError = null; this.subwayError = null; }
  async text(url) {
    const r = await this.fetcher(url, { signal: AbortSignal.timeout(12000) });
    if (!r.ok) throw new Error(`교통정보 서버 연결 실패 (${r.status})`);
    return r.text();
  }
  async bus(service, method, params, key) {
    const url = new URL(`http://ws.bus.go.kr/api/rest/${service}/${method}`);
    url.search = new URLSearchParams({ serviceKey: decodeKey(key), resultType: 'json', ...params });
    return readBusPayload(await this.text(url));
  }
  async resolve(key, direction = 'home') {
    if (this.direction !== direction) { this.reset(); this.direction = direction; }
    if (this.legs && Date.now() - this.legsAt < 86400000) return this.legs;
    const legs = [];
    for (const number of ROUTES) {
      const routes = await this.bus('busRouteInfo', 'getBusRouteList', { strSrch: number }, key);
      const route = routes.find(r => String(r.busRouteNm) === number);
      if (!route) throw new Error(`${number} 노선을 찾지 못했습니다.`);
      const stations = await this.bus('busRouteInfo', 'getStaionByRoute', { busRouteId: route.busRouteId }, key);
      legs.push({ number, routeId: String(route.busRouteId), ...pickLeg(stations, direction) });
    }
    if (new Set(legs.map(l => String(l.origin.station))).size !== 1) throw new Error('세 노선의 승차 정류장이 일치하지 않습니다. 실시간 표시를 중단합니다.');
    this.legs = legs; this.legsAt = Date.now(); return legs;
  }
  async arrivals(key, direction = 'home') {
    const legs = await this.resolve(key, direction);
    const arsId = String(legs[0].origin.arsId || legs[0].origin.stationNo).padStart(5, '0');
    if (!/^\d{5}$/.test(arsId) || arsId === '00000') throw new Error('승차 정류장의 ARS 번호가 없습니다.');
    const rows = await this.bus('stationinfo', 'getStationByUid', { arsId }, key);
    const now = Date.now();
    return { fetchedAt: now, arsId, origin: legs[0].origin.stationNm, buses: legs.map(l => {
      const r = rows.find(r => String(r.busRouteId) === l.routeId && String(r.staOrd) === String(l.origin.seq));
      return { number: l.number, direction: tripInfo(direction).destination, stopCount: l.stops.length - 1, stops: l.stops.map(s => s.stationNm), first: parseArrival(r?.arrmsg1, now), second: parseArrival(r?.arrmsg2, now) };
    }) };
  }
  async subway(key, direction = 'home') {
    const stations = await Promise.all(['경복궁', '남부터미널'].map(async name => {
      if (/^https?:/i.test(key.trim())) throw new Error('지하철 입력칸에는 URL 대신 인증키 문자열만 입력하세요.');
      const raw = await this.text(`http://swopenapi.seoul.go.kr/api/subway/${encodeURIComponent(key.trim())}/json/realtimeStationArrival/0/20/${encodeURIComponent(name)}`);
      let json; try { json = JSON.parse(raw); } catch { throw new Error('지하철 API 응답 형식 오류'); }
      if (!json.realtimeArrivalList && json.errorMessage?.code !== 'INFO-200') throw new Error('지하철 API 키·호출량을 확인하세요.');
      const rows = (json.realtimeArrivalList || []).filter(r => String(r.subwayId) === '1003' && r.updnLine === tripInfo(direction).subwayLine).slice(0, 2);
      return { name, trains: rows.map(r => ({ destination: r.bstatnNm, message: r.arvlMsg2, receivedAt: r.recptnDt, seconds: Number(r.barvlDt) || null })) };
    }));
    return { fetchedAt: Date.now(), stations };
  }
  async snapshot(config, forceTest = false) {
    const active = forceTest || activeAt(config), direction = directionAt(config), trip = tripInfo(direction), interval = pollingMs(config);
    const meta = { trip, pollSeconds: interval / 1000, staleAfterMs: Math.max(90000, interval * 2 + 15000) };
    if (config.mode === 'demo') return { ...demoSnapshot(active, forceTest, direction), ...meta };
    if (!active) return { ...meta, mode: 'live', active: false, fetchedAt: null, buses: [], subway: null, error: null };
    if (!config.busKey) return { ...meta, mode: 'live', active: true, fetchedAt: null, buses: [], subway: null, error: '설정에서 공공데이터포털 버스 서비스키를 입력하세요.' };
    const identity = config.busKey + '|' + config.subwayKey + '|' + direction;
    if (this.key !== identity) { this.reset(); this.key = identity; this.direction = direction; }
    let error = null, subwayError = null;
    if (Date.now() - this.busAt >= interval) {
      this.busAt = Date.now();
      try { this.lastBus = await this.arrivals(config.busKey, direction); } catch (e) { error = e.message; this.busError = error; }
      if (!error) this.busError = null;
    } else error = this.busError;
    if (config.subwayKey && Date.now() - this.subwayAt >= Math.max(90000, interval * 2)) {
      this.subwayAt = Date.now();
      try { this.lastSubway = await this.subway(config.subwayKey, direction); this.subwayError = null; } catch (e) { this.subwayError = e.message; }
    }
    subwayError = config.subwayKey ? this.subwayError : '지하철 인증키를 입력하면 3호선 하행 도착정보를 볼 수 있습니다.';
    return { ...meta, mode: 'live', active, testOverride: forceTest, ...this.lastBus, buses: this.lastBus?.buses || [], subway: this.lastSubway, error, subwayError };
  }
}
const demoEpoch = Date.now();
export function demoSnapshot(active = true, testOverride = true, direction = 'home') {
  const now = Date.now(), cycle = Math.floor((now - demoEpoch) / 300000);
  const base = demoEpoch + cycle * 300000;
  const trip = tripInfo(direction);
  return { mode: 'demo', active, testOverride, fetchedAt: now, arsId: '확인 전', origin: trip.origin, trip, error: null,
    buses: active ? ROUTES.map((number, i) => ({ number, direction: trip.destination, stopCount: null, stops: [trip.origin, trip.destination], first: { dueAt: base + [234, 487, 652][i] * 1000, precision: 'second', message: '테스트용 가상 도착예측' }, second: { dueAt: base + [234, 487, 652][i] * 1000 + 360000, precision: 'second', message: '테스트용 가상 도착예측' } })) : [],
    subway: { fetchedAt: now, stations: trip.subwayStations.map(name => ({ name, trains: [{ destination: direction === 'commute' ? '구파발' : '오금', message: `테스트용 가상 열차 · ${trip.subwayLine}`, seconds: null }] })) } };
}
