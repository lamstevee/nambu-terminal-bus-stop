export const DEFAULT_SCHEDULE = { activation: 'commute', direction: 'auto', commuteStart: '06:00', commuteEnd: '10:00', homeStart: '18:00', homeEnd: '24:00' };
export const minuteOf = time => { const [h, m] = time.split(':').map(Number); return h * 60 + m; };
export function seoulMinute(now = new Date()) {
 const p = new Intl.DateTimeFormat('en', { timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(now);
 return +p.find(p => p.type === 'hour').value * 60 + +p.find(p => p.type === 'minute').value;
}
export function normalizeSchedule(config = {}) { return { ...DEFAULT_SCHEDULE, ...config }; }
export function validateSchedule(config) {
 const c = normalizeSchedule(config);
 if (!['commute', 'always'].includes(c.activation) || !['auto', 'commute', 'home'].includes(c.direction)) throw new Error('활성화 방식과 방향을 확인하세요.');
 for (const key of ['commuteStart', 'commuteEnd', 'homeStart', 'homeEnd']) if (!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(c[key]) && !(key.endsWith('End') && c[key] === '24:00')) throw new Error('시간은 HH:mm 형식으로 입력하세요.');
 if (!(minuteOf(c.commuteStart) < minuteOf(c.commuteEnd) && minuteOf(c.commuteEnd) <= minuteOf(c.homeStart) && minuteOf(c.homeStart) < minuteOf(c.homeEnd))) throw new Error('출근 시작 < 출근 종료 ≤ 퇴근 시작 < 퇴근 종료 순서로 설정하세요.');
 return c;
}
export function activeAt(config = {}, now = new Date()) {
 const c = normalizeSchedule(config), m = seoulMinute(now);
 return c.activation === 'always' || [[c.commuteStart,c.commuteEnd],[c.homeStart,c.homeEnd]].some(([s,e]) => m >= minuteOf(s) && m < minuteOf(e));
}
export function directionAt(config = {}, now = new Date()) {
 const c = normalizeSchedule(config), m = seoulMinute(now);
 if (c.direction !== 'auto') return c.direction;
 if (m >= minuteOf(c.commuteStart) && m < minuteOf(c.commuteEnd)) return 'commute';
 if (m >= minuteOf(c.homeStart) && m < minuteOf(c.homeEnd)) return 'home';
 return m < 720 ? 'commute' : 'home';
}
export function pollingMs(config = {}) {
 const c=normalizeSchedule(config), minutes=c.activation==='always' ? 1440 : minuteOf(c.commuteEnd)-minuteOf(c.commuteStart)+minuteOf(c.homeEnd)-minuteOf(c.homeStart);
 return Math.max(60000,Math.ceil(minutes*60000/900/10000)*10000);
}
export function tripInfo(direction) { return direction === 'commute' ? { direction, label: '출근', origin: '한국제약바이오협회', destination: '남부터미널', subwayLine: '상행', subwayDirection: '경복궁 · 구파발/대화 방면', subwayStations: ['남부터미널','경복궁'], headwayMinutes: 6 } : { direction: 'home', label: '퇴근', origin: '남부터미널역(한일시멘트)', destination: '한국제약바이오협회', subwayLine: '하행', subwayDirection: '오금 방면', subwayStations: ['경복궁','남부터미널'] }; }
