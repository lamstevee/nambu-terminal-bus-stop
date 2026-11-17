const $ = id => document.getElementById(id), native = !!window.Android;
let snapshot = null, config = null, loading = false, nativeRequests = new Map(), requestId = 0;
window.nativeReply = (id, result) => { const r = nativeRequests.get(id); if (r) { clearTimeout(r.timeout); nativeRequests.delete(id); result.error && result.nativeFailure ? r.reject(new Error(result.error)) : r.resolve(result); } };
async function call(action, data = {}) {
  if (native) return new Promise((resolve, reject) => { const id = ++requestId; const timeout = setTimeout(() => { nativeRequests.delete(id); reject(new Error('응답 시간이 초과되었습니다. 다시 시도하세요.')); }, 90000); nativeRequests.set(id, { resolve, reject, timeout }); window.Android.request(id, action, JSON.stringify(data)); });
  const url = action === 'snapshot' ? `/api/snapshot?test=${$('testOverride').checked ? 1 : 0}` : '/api/config';
  const response = await fetch(url, action === 'save' ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) } : {});
  if (!response.ok) { let result;try {result=await response.json()}catch{};throw new Error(result?.error || '정보를 가져오지 못했습니다. PC 서버 상태를 확인하세요.'); }
  return response.json();
}
const timeString = ms => new Date(ms).toLocaleTimeString('ko-KR', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: 'Asia/Seoul' });
function remaining(arrival) {
  if (!arrival?.dueAt) return arrival?.precision === 'soon' ? '곧 도착' : (arrival?.message || '도착정보 없음');
  const seconds = Math.ceil((arrival.dueAt - Date.now()) / 1000);
  if (seconds <= 0) return '도착예측 경과';
  return `${arrival.precision === 'minute' ? '약 ' : ''}${Math.floor(seconds / 60)}분 ${String(seconds % 60).padStart(2, '0')}초`;
}
function el(tag, className, text) { const node = document.createElement(tag); if (className) node.className = className; if (text !== undefined) node.textContent = text; return node; }
function stale() { return snapshot?.mode === 'live' && (!snapshot.fetchedAt || Date.now() - snapshot.fetchedAt > (snapshot.staleAfterMs || 135000)); }
function trip() { return snapshot?.trip || {direction:'home',label:'퇴근',origin:'남부터미널역(한일시멘트)',destination:'한국제약바이오협회',subwayLine:'하행',subwayDirection:'오금 방면',subwayStations:['경복궁','남부터미널']}; }
function scheduleText() { return config?.activation==='always' ? '24시간 활성화' : `출근 ${config?.commuteStart || '06:00'}–${config?.commuteEnd || '10:00'} · 퇴근 ${config?.homeStart || '18:00'}–${config?.homeEnd || '24:00'}`; }
function renderTrip() {
 const t=trip(),commute=t.direction==='commute';
 $('heading').textContent=commute ? '이제 출근길로.' : '이제 집으로.';
 $('introCopy').textContent=commute ? '한국제약바이오협회에서 탈 버스, 여기서 확인하세요.' : '남부터미널에서 탈 버스, 여기서 확인하세요.';
 $('busFrom').textContent=t.origin; $('busTo').textContent=t.destination+' 방향 →';
 $('railFrom').textContent=commute ? '남부터미널' : '경복궁'; $('railTo').textContent=commute ? '경복궁' : '남부터미널';
 const journey=$('journey'),rail=journey.querySelector('.rail'),bus=journey.querySelector('.bus-path');
 journey.classList.toggle('commute',commute); journey.append(...(commute ? [bus,rail] : [rail,bus]));
 $('headwayHint').hidden=!commute; $('notificationTitle').textContent=t.label+' · '+t.origin+' → '+t.destination;
 $('subwayHeading').textContent='3호선 '+t.subwayLine; $('subwayDirection').textContent=t.subwayDirection;
 $('subwayJourney').textContent=commute ? '남부터미널에서 경복궁까지' : '경복궁에서 남부터미널까지';
 $('testLabel').textContent=commute ? '지금 제약바이오협회에서 테스트' : '지금 남부터미널에서 테스트';
 $('scheduleTitle').textContent=config?.activation==='always' ? '하루 종일 켜집니다.' : '출퇴근시간에만 켜집니다.';
 $('scheduleCopy').textContent=scheduleText()+' · 한국 시간';
 $('notificationFoot').textContent=`도착예측 카운트다운 · ${snapshot?.pollSeconds || 60}초마다 갱신`;
 for(const b of document.querySelectorAll('[data-direction]')) b.setAttribute('aria-pressed',String(b.dataset.direction===(config?.direction || 'auto')));
}
function displayArrival(a) { return stale() ? '갱신 지연' : remaining(a); }
function renderBuses() {
  const buses = snapshot?.buses || [], valid = buses.filter(b => b.first?.dueAt > Date.now());
  const best = valid.sort((a, b) => a.first.dueAt - b.first.dueAt)[0]?.number;
  $('buses').replaceChildren(); $('notificationRows').replaceChildren();
  for (const number of ['461', '641', '4319']) {
    const b = buses.find(b => b.number === number);
    const card = el('article', `bus-card ${number === '4319' ? 'green' : ''} ${number === best ? 'best' : ''}`);
    const top = el('div', 'bus-top'), route = el('div'); route.append(el('span', 'bus-number', number), document.createTextNode(' '), el('span', 'bus-type', number === '4319' ? '지선' : '간선'));
    top.append(route); if (number === best && !stale()) top.append(el('span', 'next-tag', '가장 먼저 도착'));
    card.append(top, el('div', 'bus-direction', trip().destination+' 방향'));
    const value = !snapshot?.active ? '조회시간 대기' : b ? displayArrival(b.first) : snapshot?.error ? '연결 확인' : '정보 없음';
    const arrival = el('div', 'arrival'); const digits = value.match(/^(약 )?(\d+)분 (\d+)초$/);
    if (digits) { if (digits[1]) arrival.append(el('span', 'approx', '≈')); arrival.append(el('span', '', digits[2]), el('small', '', '분'), el('span', '', digits[3]), el('small', '', '초')); } else { arrival.classList.add('message'); arrival.textContent = value; }
    card.append(arrival); const bottom = el('div', 'bus-bottom'); bottom.append(el('span', '', '다음 버스'), el('strong', '', b && snapshot.active ? displayArrival(b.second) : '—')); card.append(bottom); $('buses').append(card);
    const row = el('div', 'notification-row'); row.append(el('b', '', number), el('span', '', value)); $('notificationRows').append(row);
  }
  if (snapshot?.fetchedAt) $('freshness').textContent = `${snapshot.mode === 'demo' ? '가상 데이터' : '서울 버스정보'} · ${timeString(snapshot.fetchedAt)} 갱신${stale() ? ' · 갱신 지연' : ` · ${snapshot.pollSeconds || 60}초마다 조회`}`;
  else $('freshness').textContent=snapshot?.active ? '도착정보를 확인하세요.' : '설정한 활성화 시간까지 대기합니다.';
}
function render() {
  renderTrip();
  const demo = snapshot?.mode === 'demo'; $('modeLabel').textContent = demo ? '시뮬레이션' : '실시간'; $('statusDot').classList.toggle('live', !demo && !snapshot?.error);
  $('notificationMode').textContent = demo ? '시뮬레이션' : '실시간';
  $('notice').classList.toggle('error', !!snapshot?.error);
  $('notice').textContent = snapshot?.error || (!snapshot?.active ? '지금은 조회 시간이 아닙니다. '+scheduleText() : demo ? `시뮬레이션 · 가상 도착시간입니다. ${trip().origin}에 있는 것으로 가정합니다.` : `서울 공공데이터 조회 · ${snapshot?.origin || '정류장 확인 중'}${snapshot?.arsId ? ` (${snapshot.arsId})` : ''}${snapshot?.testOverride ? ' · 시간 제한 해제 테스트' : ''}`);
  $('subwayRows').replaceChildren();
  for (const name of trip().subwayStations) {
    const s = snapshot?.subway?.stations?.find(s => s.name === name), row = el('div', 'subway-row'), text = el('div');
    row.append(el('span', 'station-dot')); text.append(el('strong', '', name));
    const old = snapshot?.subway?.fetchedAt && Date.now() - snapshot.subway.fetchedAt > (snapshot?.pollSeconds || 60)*4000+15000;
    text.append(el('p', '', old ? '열차정보 갱신 지연' : s?.trains?.length ? s.trains.map(t => `${t.destination || '하행'} · ${t.message || '도착정보 없음'}`).join(' / ') : '도착정보 없음'));
    row.append(text); $('subwayRows').append(row);
  }
  $('subwayNote').textContent = snapshot?.subwayError || (demo ? '가상 열차정보입니다. 위치 진입 알림은 설정에서 별도로 켭니다.' : `3호선 ${trip().subwayLine}만 표시 · 위치 진입 알림은 별도 설정`);
  renderBuses();
}
async function refresh() {
  if (loading) return; loading = true; $('refresh').disabled = true;
  try { snapshot = await call('snapshot', { test: $('testOverride').checked }); render(); }
  catch (e) { $('notice').classList.add('error'); $('notice').textContent = e.message; $('freshness').textContent = '연결 실패 · 마지막 데이터가 오래되면 갱신 지연으로 표시합니다.'; }
  finally { loading = false; $('refresh').disabled = false; }
}
function showConfig(c) {
  config = c; $('mode').value = c.mode; $('busKey').value = ''; $('subwayKey').value = '';
  $('proximityEnabled').checked=!!c.proximityEnabled; $('locationControls').hidden=!native;
  $('locationStatus').textContent=native ? `정확한 위치 ${c.preciseLocationGranted ? '허용' : '미허용'} · 백그라운드 위치 ${c.backgroundLocationGranted ? '허용' : '미허용'} · ${c.proximityStatus || '꺼짐'}` : 'PC는 실제 GPS 감지 없이 진입 팝업 모양을 테스트합니다.';
  for(const k of ['activation','direction','commuteStart','commuteEnd','homeStart','homeEnd']) $(k).value=c[k] || ({activation:'commute',direction:'auto',commuteStart:'06:00',commuteEnd:'10:00',homeStart:'18:00',homeEnd:'24:00'})[k];
  $('busKey').placeholder = c.busKeySet ? '저장된 키 있음 · 변경할 때 입력' : '공공데이터포털 서비스키';
  $('subwayKey').placeholder = c.subwayKeySet ? '저장된 키 있음 · 변경할 때 입력' : '서울 열린데이터광장 인증키';
  if (native) { $('androidSettings').hidden = false; $('platformLabel').textContent = '안드로이드'; $('pcNote').textContent = '설정한 출퇴근시간 또는 24시간 동안 알림을 유지합니다. 테스트 알림은 15분 뒤 종료됩니다.'; $('permissionStatus').textContent = `알림 ${c.notificationsGranted ? '허용' : '미허용'} · 자동 시작 ${c.exactAlarmGranted ? '허용' : '미허용'}`; }
  $('notify').textContent = native ? c.enabled ? '지금 알림 보기' : '알림 켜기' : 'PC 알림 테스트'; $('stopNotify').hidden = !native || !c.enabled;
  $('notifyStatus').textContent=c.enabled ? scheduleText()+' 활성화' : scheduleText(); renderTrip();
}
$('refresh').onclick = refresh;
$('testOverride').onchange = refresh;
$('settingsOpen').onclick = async () => { try { showConfig(await call('config')); $('settingsMessage').textContent = ''; $('settings').showModal(); } catch(e) { $('notice').textContent = e.message; } };
$('settingsClose').onclick = () => $('settings').close();
$('settingsForm').onsubmit = async e => { e.preventDefault(); try { const data={mode:$('mode').value,busKey:$('busKey').value,subwayKey:$('subwayKey').value,proximityEnabled:$('proximityEnabled').checked};for(const k of ['activation','direction','commuteStart','commuteEnd','homeStart','homeEnd'])data[k]=$(k).value;showConfig(await call('save',data)); $('settings').close(); snapshot = null; await refresh(); } catch(e) { $('settingsMessage').textContent = e.message; } };
for(const b of document.querySelectorAll('[data-direction]')) b.onclick=async()=>{if(loading)return;try{showConfig(await call('save',{direction:b.dataset.direction}));snapshot=null;await refresh()}catch(e){$('notice').textContent=e.message}};
$('clearKeys').onclick = async () => { try { showConfig(await call('save', { clearKeys: true })); $('settingsMessage').textContent = '저장된 API 키를 삭제했습니다.'; } catch(e) { $('settingsMessage').textContent = e.message; } };
$('notify').onclick = async () => {
  try {
    if (native) { showConfig(await call('start', { test: $('testOverride').checked })); $('notifyStatus').textContent = $('testOverride').checked ? '15분 테스트 알림 시작' : scheduleText()+' 활성화'; }
    else if ('Notification' in window) { const p = await Notification.requestPermission(); if (p === 'granted') { new Notification(`퇴근길 · ${snapshot?.mode === 'demo' ? '시뮬레이션' : '도착정보'}`, { body: (snapshot?.buses || []).map(b => `${b.number}: ${displayArrival(b.first)}`).join('\n') || '도착정보 없음', tag: 'homebound-test' }); $('notifyStatus').textContent = 'PC 일회성 테스트 알림 전송'; } else $('notifyStatus').textContent = '브라우저 알림 권한을 허용하세요.'; }
    else $('notifyStatus').textContent = '이 브라우저는 알림을 지원하지 않습니다.';
  } catch(e) { $('notifyStatus').textContent = e.message; }
};
$('stopNotify').onclick = async () => { try { showConfig(await call('stop')); $('notifyStatus').textContent = '자동 알림 중지'; } catch(e) { $('notifyStatus').textContent = e.message; } };
$('alarmPermission').onclick = () => call('alarm').catch(e => { $('settingsMessage').textContent = e.message; });
$('batterySettings').onclick = () => call('battery').catch(e => { $('settingsMessage').textContent = e.message; });
$('locationPermission').onclick=()=>call('location').catch(e=>{$('settingsMessage').textContent=e.message});
$('popupSettings').onclick=()=>call('popupSettings').catch(e=>{$('settingsMessage').textContent=e.message});
$('proximityToastClose').onclick=()=>{$('proximityToast').hidden=true};
$('proximityTest').onclick=async()=>{
 try {
  const group=$('proximityPlace').value;
  if(native){await call('proximityTest',{group});$('proximityTestStatus').textContent='테스트 알림 요청 완료 · 뜨지 않으면 알림 권한과 팝업 알림 설정을 확인하세요.';return;}
  const name=group==='nambu' ? '남부터미널역·정류장' : group==='bio' ? '한국제약바이오협회 정류장' : '경복궁역';
  const commute=trip().direction==='commute';
  const body=group==='nambu' ? commute ? '3호선 상행 · 경복궁 방면을 확인하세요.' : '461 / 641 / 4319 · 한국제약바이오협회 방면을 확인하세요.' : group==='bio' ? commute ? '461 / 641 / 4319 · 남부터미널 방면을 확인하세요.' : '한국제약바이오협회 하차 지점 근처입니다.' : commute ? '경복궁역 도착 지점 근처입니다.' : '3호선 하행 · 남부터미널, 오금 방면을 확인하세요.';
  $('proximityToastTitle').textContent='[테스트] '+name+' 300m 근처';$('proximityToastBody').textContent=body+' 실제 GPS 감지가 아닌 PC 미리보기입니다.';$('proximityToast').hidden=false;
  $('proximityTestStatus').textContent='PC 테스트 팝업 표시됨 · 실제 위치 감지와 별개';
 }catch(e){$('proximityTestStatus').textContent=e.message;}
};
async function init() { try { showConfig(await call('config')); $('testOverride').checked = !native; await refresh(); } catch(e) { $('notice').textContent = e.message; } }
window.refreshNativeConfig = async () => { try { showConfig(await call('config')); } catch {} };
init(); setInterval(refresh, 30000); setInterval(renderBuses, 1000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
