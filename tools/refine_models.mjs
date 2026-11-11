// Reproducible model review / Blockbench MCP refinement pipeline.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'src/main/resources/assets/crazywaterworld');
const entries = [
  ['clam', 'item', '河蚌'], ['clam_opener', 'block', '开蚌台'],
  ['eel_generator', 'item', '电鳗发电机'], ['sea_filter', 'item', '海水过滤器'],
  ['salvage_net', 'block', '打捞网'],
];
const readJson = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, ''));
// MCP's eval validator rejects comment markers even inside base64 string literals.
const literal = value => JSON.stringify(value).replaceAll('/', '\\u002f');
let session;
async function rpc(method, params) {
  const headers = {'Content-Type': 'application/json', Accept: 'application/json, text/event-stream'};
  if (session) headers['mcp-session-id'] = session;
  const response = await fetch('http://localhost:3000/bb-mcp', {
    method: 'POST', headers, body: JSON.stringify({jsonrpc: '2.0', id: 1, method, params}),
  });
  if (!response.ok) throw new Error(await response.text());
  session = response.headers.get('mcp-session-id') || session;
  const result = await response.json();
  if (result.error || result.result?.isError) throw new Error(JSON.stringify(result));
  return result.result;
}
async function tool(name, args = {}) {
  if (name === 'risky_eval') args = {code: `(async () => {${args.code}\n})()`};
  return rpc('tools/call', {name, arguments: args});
}
async function connect() {
  await rpc('initialize', {protocolVersion: '2024-11-05', capabilities: {}, clientInfo: {name: 'cww-model-refinement', version: '1.0'}});
}
function textResult(result) {
  const text = result.content?.find(c => c.type === 'text')?.text;
  try { return JSON.parse(text); } catch { return text; }
}
function bounds(model) {
  return [0, 1, 2].map(a => [Math.min(...model.elements.map(e => e.from[a])), Math.max(...model.elements.map(e => e.to[a]))]);
}
function rotated(point, rotation) {
  let [x, y, z] = point;
  // Minecraft's display quaternion is rotationXYZ (Z, then Y, then X on a vector).
  for (const a of [2, 1, 0]) {
    const angle = rotation[a] * Math.PI / 180, c = Math.cos(angle), s = Math.sin(angle);
    if (a === 2) [x, y] = [x * c - y * s, x * s + y * c];
    if (a === 1) [x, z] = [x * c + z * s, -x * s + z * c];
    if (a === 0) [y, z] = [y * c - z * s, y * s + z * c];
  }
  return [x, y, z];
}
function corners(model) {
  return model.elements.flatMap(e => [0, 1].flatMap(x => [0, 1].flatMap(y => [0, 1].map(z => {
    let p = [x ? e.to[0] : e.from[0], y ? e.to[1] : e.from[1], z ? e.to[2] : e.from[2]];
    if (e.rotation) {
      const r = [0, 0, 0]; r['xyz'.indexOf(e.rotation.axis)] = e.rotation.angle;
      p = rotated(p.map((v, a) => v - e.rotation.origin[a]), r).map((v, a) => v + e.rotation.origin[a]);
    }
    return p;
  }))));
}
function finalizeDisplay(id, folder, destination) {
  const modelPath = path.join(assets, 'models', folder, `${id}.json`);
  const model = readJson(modelPath);
  const points = corners(model);
  // Show the operator-facing side: the bench faces south, the machines face north.
  const rotation = [id === 'salvage_net' ? 35 : 25, id === 'clam_opener' ? 225 : 45, 0];
  const projected = points.map(p => rotated(p.map(v => v - 8), rotation));
  const extents = [0, 1, 2].map(a => [Math.min(...projected.map(p => p[a])), Math.max(...projected.map(p => p[a]))]);
  const round = n => Math.round(n * 10000) / 10000;
  const scale = Math.min(.9, 14 / Math.max(...extents.slice(0, 2).map(([lo, hi]) => hi - lo)));
  const span = Math.max(...bounds(model).map(([lo, hi]) => hi - lo));
  const hand = Math.min(.55, 9 / span), first = Math.min(.7, 11 / span), ground = Math.min(.5, 8 / span);
  const center = [0, 1, 2].map(a => (Math.min(...points.map(p => p[a])) + Math.max(...points.map(p => p[a]))) / 2 - 8);
  const display = {
    gui: {rotation, translation: extents.map(([lo, hi], a) => a < 2 ? round(-(lo + hi) / 2 * scale) : 0), scale: Array(3).fill(round(scale))},
    ground: {translation: [round(-center[0] * ground), round(2 - (Math.min(...points.map(p => p[1])) - 8) * ground), round(-center[2] * ground)], scale: Array(3).fill(round(ground))},
    fixed: {rotation: [0, 180, 0], translation: [0, round(-center[1] * .55), 0], scale: [.55, .55, .55]},
  };
  for (const [side, sign] of [['righthand', 1], ['lefthand', -1]]) {
    display[`thirdperson_${side}`] = {rotation: [65, 45 * sign, 0], translation: [0, 2, 1], scale: Array(3).fill(round(hand))};
    display[`firstperson_${side}`] = {rotation: [0, 45 * sign, 0], translation: [0, round(1 - center[1] * first), 0], scale: Array(3).fill(round(first))};
  }
  model.display = display;
  fs.writeFileSync(modelPath, JSON.stringify(model, null, 2) + '\n');
  const source = readJson(destination); source.display = display;
  fs.writeFileSync(destination, JSON.stringify(source, null, 2) + '\n');
  if (folder === 'block') fs.writeFileSync(path.join(assets, 'models/item', `${id}.json`), JSON.stringify({parent: `crazywaterworld:block/${id}`}, null, 2) + '\n');
  console.log(`${id}: GUI fit scale ${round(scale)}, centered within 14 x 14 model units`);
}
function exportSource(id, folder, destination) {
  const script = id === 'clam' ? 'export_bbmodel_item.ps1' : 'export_bbmodel_static_item.ps1';
  const args = ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', path.join(root, 'tools', script), '-Source', destination, '-ItemId', id];
  if (folder === 'block') args.push('-Block');
  console.log(execFileSync('powershell.exe', args, {encoding: 'utf8'}));
  finalizeDisplay(id, folder, destination);
}
if (process.argv[2] === 'inspect') {
  for (const [id, folder, chinese] of entries) {
    const model = readJson(path.join(assets, 'models', folder, `${id}.json`));
    const source = path.join(process.env.USERPROFILE, 'Desktop/模型', `${chinese}.bbmodel`);
    const bb = fs.existsSync(source) ? readJson(source) : null;
    console.log(JSON.stringify({id, cubes: model.elements.length, bounds: bounds(model), display: model.display,
      source: bb && {format: bb.meta, groups: bb.groups?.map(g => ({name: g.name, rotation: g.rotation})),
        cubes: bb.elements.map(e => ({name: e.name, from: e.from, to: e.to, rotation: e.rotation})),
        textures: bb.textures.map(t => ({name: t.name, width: t.width, height: t.height})), display: bb.display}}, null, 2));
  }
} else if (process.argv[2] === 'schema') {
  await connect();
  const list = await rpc('tools/list', {});
  console.log(JSON.stringify(list.tools.filter(t => ['risky_eval', 'export_model', 'create_project', 'create_texture', 'place_cube'].includes(t.name)), null, 2));
} else if (process.argv[2] === 'load') {
  await connect();
  const entry = entries.find(e => e[0] === process.argv[3]);
  const [id, folder, chinese] = entry;
  if (id === 'salvage_net') throw new Error('Use refine for the net');
  const bb = readJson(path.join(process.env.USERPROFILE, 'Desktop/模型', `${chinese}.bbmodel`));
  await tool('create_project', {name: `${id}_refined`, format: bb.meta.model_format});
  console.log(textResult(await tool('risky_eval', {code: `Codecs.project.parse(${literal(bb)}); Project.name = '${id}_refined'; Project.save_path = ''; return {name: Project.name, cubes: Cube.all.length};`})));
} else if (process.argv[2] === 'eval') {
  await connect();
  console.log(JSON.stringify(textResult(await tool('risky_eval', {code: process.argv[3]})), null, 2));
} else if (process.argv[2] === 'screenshot') {
  await connect();
  const result = await tool('capture_screenshot', {});
  const image = result.content.find(c => c.type === 'image');
  if (!image) throw new Error(JSON.stringify(result));
  fs.mkdirSync(path.join(root, 'build/model-previews'), {recursive: true});
  const output = path.join(root, 'build/model-previews', `${process.argv[3]}.png`);
  fs.writeFileSync(output, Buffer.from(image.data, 'base64'));
  console.log(output);
} else if (process.argv[2] === 'refine') {
  await connect();
  const selected = process.argv[3] ? entries.filter(e => e[0] === process.argv[3]) : entries;
  if (!selected.length) throw new Error('Unknown model');
  fs.mkdirSync(path.join(root, 'models/refined'), {recursive: true});
  fs.mkdirSync(path.join(root, 'build/model-previews'), {recursive: true});
  for (const [id, folder, chinese] of selected) {
    const destination = path.join(root, 'models/refined', `${id}.bbmodel`);
    if (id !== 'salvage_net') {
      const source = readJson(path.join(process.env.USERPROFILE, 'Desktop/模型', `${chinese}.bbmodel`));
      await tool('create_project', {name: `${id}_refined`, format: source.meta.model_format});
      await tool('risky_eval', {code: `Codecs.project.parse(${literal(source)}); Project.name = '${id}_refined'; Project.save_path = '';`});
    } else {
      const originalPath = path.join(root, 'models/refined/salvage_net.original.json');
      if (!fs.existsSync(originalPath)) fs.copyFileSync(path.join(assets, 'models/block/salvage_net.json'), originalPath);
      const source = readJson(originalPath);
      const meshData = 'data:image/png;base64,' + fs.readFileSync(path.join(assets, 'textures/block/salvage_net_mesh.png')).toString('base64');
      await tool('create_project', {name: `${id}_refined`, format: 'java_block'});
      await tool('risky_eval', {code: `
        Project.texture_width = Project.texture_height = 16;
        const tex = {};
        for (const [key, color] of Object.entries({frame:'#79654a', post:'#493d2e', float_red:'#b75d49', float_white:'#e9dfbb'})) {
          const c = document.createElement('canvas'); c.width = c.height = 16; const g = c.getContext('2d');
          g.fillStyle = color; g.fillRect(0, 0, 16, 16);
          for (let y=0;y<16;y++) for (let x=0;x<16;x++) {
            g.fillStyle = ((x*11+Math.floor(y/4)*7)%9<2) ? '#00000020' : '#ffffff0c'; g.fillRect(x,y,1,1);
          }
          tex[key] = new Texture({name:'salvage_' + key}).fromDataURL(c.toDataURL()).add(false);
        }
        tex.mesh = new Texture({name:'salvage_net_mesh'}).fromDataURL(${literal(meshData)}).add(false);
        const source = ${JSON.stringify(source)};
        source.elements.forEach((e,i)=>{
          const faces = Object.fromEntries(['north','east','south','west','up','down'].map(s=>[s,{texture:null}]));
          for (const [s,f] of Object.entries(e.faces)) faces[s]={texture:tex[f.texture.slice(1)].uuid, uv:f.uv || [0,0,16,16]};
          new Cube({name:i>=6 && i<=11 ? 'net_surface_'+i : 'net_frame_'+i, from:e.from,to:e.to,faces,autouv:0,box_uv:false}).init();
        }); Canvas.updateAll();`});
    }
    const code = fs.readFileSync(path.join(root, 'tools/blockbench_refinement.js'), 'utf8').replace(/^\s*\/\/.*$/gm, '');
    console.log(id, textResult(await tool('risky_eval', {code: `${code}\nreturn await refine('${id}');`})));
    await new Promise(resolve => setTimeout(resolve, 500));
    const exported = await tool('export_model', {codec_id: 'project', result_format: 'embedded', max_content_length: 2000000});
    const resource = exported.content.find(c => c.type === 'resource' && c.resource?.text);
    if (!resource) throw new Error('Missing complete project export: ' + JSON.stringify(exported).slice(0, 1000));
    fs.writeFileSync(destination, resource.resource.text);
    await tool('risky_eval', {code: `Project.save_path = ${JSON.stringify(destination)}; Project.saved = true; const v = Preview.selected; v.camera.position.set(32, 26, 36); v.controls.target.set(${id === 'sea_filter' || id === 'salvage_net' ? '8, 6, 8' : '0, 7, 0'}); v.controls.update(); Canvas.updateAll();`});
    const shot = await tool('capture_screenshot', {});
    const image = shot.content.find(c => c.type === 'image');
    if (image) fs.writeFileSync(path.join(root, 'build/model-previews', `${id}-refined.png`), Buffer.from(image.data, 'base64'));
    exportSource(id, folder, destination);
  }
} else if (process.argv[2] === 'export') {
  for (const [id, folder] of entries) exportSource(id, folder, path.join(root, 'models/refined', `${id}.bbmodel`));
} else if (process.argv[2] === 'preview') {
  await connect();
  for (const [id, folder] of entries) {
    const model = readJson(path.join(assets, 'models', folder, `${id}.json`));
    const images = Object.fromEntries(Object.entries(model.textures).filter(([key]) => key !== 'particle').map(([key, ref]) => [key,
      'data:image/png;base64,' + fs.readFileSync(path.join(assets, 'textures', ref.split(':')[1] + '.png')).toString('base64')]));
    await tool('create_project', {name: id + '_game_preview', format: 'java_block'});
    await tool('risky_eval', {code: `
      Project.texture_width = Project.texture_height = 16;
      const textures = {};
      for (const [key, data] of Object.entries(${literal(images)})) textures[key] = new Texture({name:key, uv_width:16, uv_height:16}).fromDataURL(data).add(false);
      const model = ${literal(model)};
      for (const e of model.elements) {
        const faces = Object.fromEntries(['north','east','south','west','up','down'].map(s=>[s,{texture:null}]));
        for (const [s,f] of Object.entries(e.faces)) faces[s] = {...f, texture:textures[f.texture.slice(1)].uuid};
        const rotation = [0,0,0]; if (e.rotation) rotation['xyz'.indexOf(e.rotation.axis)] = e.rotation.angle;
        new Cube({name:e.name || 'shell',from:e.from,to:e.to,origin:e.rotation?.origin || [8,8,8],rotation,faces,shade:e.shade !== false,autouv:0,box_uv:false}).init();
      }
      const v = Preview.selected; v.camera.position.set(${id === 'clam_opener' ? '40,30,40' : '34,28,-32'}); v.controls.target.set(8,${id === 'clam' ? 12 : id === 'salvage_net' ? 4 : 9},8); v.controls.update(); Canvas.updateAll();`});
    await new Promise(resolve => setTimeout(resolve, 500));
    const shot = await tool('capture_screenshot', {});
    const image = shot.content.find(c => c.type === 'image');
    if (!image) throw new Error('Missing preview');
    fs.writeFileSync(path.join(root, 'build/model-previews', `${id}-game.png`), Buffer.from(image.data, 'base64'));
    await tool('risky_eval', {code: `Project.saved = true; await Project.close();`});
    console.log('Rendered exported Java model: ' + id);
  }
} else if (process.argv[2] === 'verify') {
  for (const [id, folder] of [...entries, ['clam_body_visual', 'item'], ['clam_lid_visual', 'item']]) {
    const model = readJson(path.join(assets, 'models', folder, `${id}.json`));
    for (const [i, e] of model.elements.entries()) {
      for (let a = 0; a < 3; a++) if (!(e.from[a] >= -16 && e.to[a] <= 32 && e.from[a] <= e.to[a])) throw new Error(`${id}/${i}: invalid bounds`);
      if (e.rotation && (!['x', 'y', 'z'].includes(e.rotation.axis) || ![-45, -22.5, 0, 22.5, 45].includes(e.rotation.angle))) throw new Error(`${id}/${i}: unsupported rotation`);
      for (const f of Object.values(e.faces)) {
        const ref = model.textures[f.texture?.slice(1)];
        if (!ref || !ref.startsWith('crazywaterworld:')) throw new Error(`${id}/${i}: unresolved texture ${f.texture}`);
        const image = fs.readFileSync(path.join(assets, 'textures', ref.split(':')[1] + '.png'));
        if (image.readUInt32BE(0) !== 0x89504e47) throw new Error('Invalid PNG ' + ref);
        if (f.uv && !f.uv.every(Number.isFinite)) throw new Error('Invalid UV');
      }
    }
    if (model.display) {
      const d = model.display.gui;
      for (const p of corners(model)) {
        const v = rotated(p.map(n => n - 8), d.rotation);
        for (const a of [0, 1]) if (Math.abs(v[a] * d.scale[a] + d.translation[a]) > 7.01) throw new Error(id + ': GUI clipping');
      }
    }
    console.log(`PASS ${id}: ${model.elements.length} elements, textures, bounds, rotations${model.display ? ', GUI fit' : ''}`);
  }
  const clam = readJson(path.join(assets, 'models/item/clam.json'));
  const body = readJson(path.join(assets, 'models/item/clam_body_visual.json'));
  const lid = readJson(path.join(assets, 'models/item/clam_lid_visual.json'));
  const combined = [...body.elements, ...lid.elements.map(e => ({...e, from: e.from.map((n,a)=>n+[0,2,7][a]), to:e.to.map((n,a)=>n+[0,2,7][a])}))];
  if (JSON.stringify(clam.elements) !== JSON.stringify(combined)) throw new Error('Closed animation geometry differs from inventory clam');
  console.log('PASS clam: body + hinge-offset lid exactly match the closed item');
}
