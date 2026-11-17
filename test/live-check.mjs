import { readFile } from 'node:fs/promises';
import { Transit } from '../lib/transit.mjs';
const config = JSON.parse(await readFile('.local/settings.json', 'utf8'));
const t = new Transit();
try {
 const s = await t.subway(config.subwayKey);
 console.log(JSON.stringify({ subwayOk: true, stations: s.stations }));
} catch (e) { console.log(JSON.stringify({ subwayOk: false, error: e.message })); }
for(const direction of ['commute','home']) {
 const s=await t.snapshot({...config,direction},true);
 console.log(JSON.stringify({direction,origin:s.origin,arsId:s.arsId,error:s.error,subwayError:s.subwayError,buses:s.buses.map(b=>({number:b.number,stops:b.stops,message:b.first.message})),subway:s.subway?.stations}));
}
