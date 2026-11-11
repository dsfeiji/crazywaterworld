// Executed inside Blockbench by refine_models.mjs, on a fresh copy of each source.
// `modelId` is supplied by the MCP runner. All geometry/paint changes share one Undo entry.
async function refine(modelId) {
  const originals = [...Cube.all];
  const textures = [...Texture.all];
  Undo.initEdit({elements: originals, textures, bitmap: true, outliner: true});
  const sides = ['north', 'east', 'south', 'west', 'up', 'down'];
  function add(name, from, to, texture, uv, group) {
    const faces = Object.fromEntries(sides.map(s => [s, {texture: texture.uuid, uv: [...uv]}]));
    const cube = new Cube({name, from, to, autouv: 0, box_uv: false, faces});
    cube.addTo(group || Group.all[0] || 'root').init();
    return cube;
  }
  function paint(texture, callback) {
    texture.edit(canvas => callback(canvas.getContext('2d'), canvas.width, canvas.height), {no_undo: true});
  }
  const group = name => Group.all.find(g => g.name === name);
  if (modelId === 'clam') {
    // The upper half is the exterior shell atlas; preserve meat, pearl and treasure UV islands.
    paint(textures[0], (ctx, w, h) => {
      const tones = ['#56625a', '#657165', '#748074', '#869184', '#a6ad95'];
      for (let y = 0; y < h / 2; y++) for (let x = 0; x < w; x++) {
        const growth = (y + Math.floor(Math.abs(x - w / 2) / 5)) % 8;
        ctx.fillStyle = tones[growth === 0 ? 0 : growth === 1 ? 4 : growth < 5 ? 2 : 1];
        ctx.fillRect(x, y, 1, 1);
        if ((x * 7 + y * 13) % 53 === 0) {
          ctx.fillStyle = '#527e75'; ctx.fillRect(x, y, 1, 1);
        }
      }
    });
    // Stepped ribs follow the existing shell dome, remain attached to the animated lid.
    for (let i = -2; i <= 2; i++) {
      const x = i * 2.15;
      add(`shell_growth_rib_front_${i + 2}`, [x - .18, 4.48, -7], [x + .18, 4.72, -5], textures[0], [4, 1, 5, 8], group('top_shell'));
      add(`shell_growth_rib_mid_${i + 2}`, [x * .8 - .16, 6.48, -5.4], [x * .8 + .16, 6.72, -3.5], textures[0], [4, 1, 5, 8], group('top_shell'));
      add(`shell_growth_rib_crown_${i + 2}`, [x * .65 - .14, 8.48, -4.8], [x * .65 + .14, 8.7, -2.6], textures[0], [4, 1, 5, 8], group('top_shell'));
    }
  } else {
    // A small shared pixel-art material sheet for copper fittings, enamel, rope and gauges.
    const size = Project.texture_width || 64;
    const canvas = document.createElement('canvas'); canvas.width = canvas.height = size;
    const ctx = canvas.getContext('2d');
    const colors = ['#b78250', '#244753', '#72cbb9', '#e5d9ae', '#576975', '#322b28', '#d88c60', '#e7f4ea'];
    const tile = size / 4;
    for (let i = 0; i < colors.length; i++) {
      const x = (i % 4) * tile, y = Math.floor(i / 4) * tile;
      ctx.fillStyle = colors[i]; ctx.fillRect(x, y, tile, tile);
      ctx.fillStyle = '#ffffff25'; ctx.fillRect(x, y, tile, 1);
      ctx.fillStyle = '#00000030'; ctx.fillRect(x, y + tile - 1, tile, 1);
      for (let k = 2; k < tile - 1; k += 4) { ctx.fillStyle = '#ffffff0c'; ctx.fillRect(x + 1, y + k, tile - 2, 1); }
    }
    const palette = new Texture({name: 'tidal_workshop_fittings'}).fromDataURL(canvas.toDataURL()).add(false);
    const uv = i => [(i % 4) * tile + 1, Math.floor(i / 4) * tile + 1, (i % 4 + 1) * tile - 1, (Math.floor(i / 4) + 1) * tile - 1];
    const detail = (name, from, to, material = 0, parent) => add(name, from, to, palette, uv(material), parent);
    function recolor(texture, factor, tint) {
      paint(texture, (c, w, h) => {
        const image = c.getImageData(0, 0, w, h), d = image.data;
        for (let i = 0; i < d.length; i += 4) {
          if (!d[i + 3]) continue;
          for (let k = 0; k < 3; k++) d[i + k] = Math.min(255, Math.max(0, d[i + k] * factor + tint[k]));
        }
        c.putImageData(image, 0, 0);
      });
    }
    if (modelId === 'clam_opener') {
      // Retain the atlas layout and wood grain, lifting dark details for in-game lighting.
      recolor(textures[0], 1.1, [3, 6, 5]);
      for (const c of originals) {
        if (/lever_(shaft|grip|end_cap)/.test(c.name)) c.rotation = [-45, 0, 0];
      }
      detail('workshop_front_badge_frame', [-3, 6, 8.03], [3, 7.35, 8.23], 0, group('bench_base'));
      detail('workshop_front_badge_enamel', [-2.65, 6.2, 8.24], [2.65, 7.15, 8.32], 1, group('bench_base'));
      for (const x of [-2, 0, 2]) detail('badge_shell_mark_' + x, [x - .12, 6.4, 8.33], [x + .12, 6.95, 8.39], 2, group('bench_base'));
      for (const x of [-10.25, 10.25]) {
        detail('copper_leg_band_' + x, [x - 1.33, 5.65, 4.4], [x + 1.33, 6.2, 7.1], 0, group('bench_base'));
        detail('leg_rivet_' + x, [x - .22, 5.72, 7.11], [x + .22, 6.13, 7.3], 7, group('bench_base'));
      }
    }
    if (modelId === 'eel_generator') {
      for (const t of textures) {
        if (/iron/.test(t.name)) recolor(t, .85, [5, 18, 23]);
        if (/copper/.test(t.name)) recolor(t, 1.08, [8, 3, 0]);
      }
      // Replace multi-axis AABB approximations with intentional Java-compatible poses.
      for (const c of originals) {
        if (/^eel_/.test(c.name)) c.rotation = [0, c.rotation[1] < 0 ? -22.5 : 22.5, 0];
        else if (/^arc_/.test(c.name)) c.rotation = [0, /side/.test(c.name) ? 0 : /yellow/.test(c.name) ? -22.5 : 22.5, 0];
        else if (/^coil_|^electrode_/.test(c.name)) c.rotation = [0, 0, 0];
        else if (/^top_wire/.test(c.name)) c.rotation = [0, 0, c.rotation[2] < 0 ? -22.5 : 22.5];
        if (/^arc_/.test(c.name)) c.shade = false;
      }
      detail('instrument_bezel', [-4.3, 1, -8.22], [-1, 2.9, -8.04], 0, group('machine_frame'));
      detail('instrument_dark_face', [-4, 1.25, -8.31], [-1.3, 2.65, -8.23], 1, group('machine_frame'));
      for (let i = 0; i < 3; i++) detail('charge_indicator_' + i, [-3.75 + i * .8, 1.5, -8.4], [-3.25 + i * .8, 2.35, -8.32], i === 2 ? 3 : 2, group('machine_frame'));
      for (const x of [-6.35, 6.35]) for (const z of [-6.35, 6.35]) {
        detail(`collar_bolt_${x}_${z}`, [x - .25, 13.7, z - .25], [x + .25, 13.95, z + .25], 0, group('machine_frame'));
      }
    }
    if (modelId === 'sea_filter') {
      for (const t of textures) {
        if (/old_iron|dark_iron/.test(t.name)) recolor(t, .9, [0, 13, 18]);
        if (/filter_blue/.test(t.name)) recolor(t, .9, [0, 13, 7]);
      }
      for (const x of [2, 5]) {
        detail('cartridge_bottom_band_' + x, [x - .08, 2.1, 1.92], [x + 3.08, 2.6, 5.08], 0, group('pre_filters'));
        detail('cartridge_top_band_' + x, [x - .08, 8.25, 1.92], [x + 3.08, 8.75, 5.08], 0, group('pre_filters'));
        detail('cartridge_enamel_label_' + x, [x + .6, 4.2, 1.82], [x + 2.4, 6.8, 1.94], x === 2 ? 1 : 2, group('pre_filters'));
      }
      // Two readable pressure dials, with tiny needles over ivory faces.
      for (const x of [4.25, 6.35]) {
        detail('pressure_bezel_' + x, [x, 9, .7], [x + 1.5, 10.6, 1.05], 0, group('plumbing_gauges'));
        detail('pressure_dial_' + x, [x + .2, 9.2, .58], [x + 1.3, 10.4, .72], 3, group('plumbing_gauges'));
        const needle = detail('pressure_needle_' + x, [x + .68, 9.4, .5], [x + .82, 10.2, .6], 1, group('plumbing_gauges'));
        needle.origin = [x + .75, 9.8, .55]; needle.rotation = [0, 0, -22.5];
      }
      detail('freshwater_delivery_pipe', [14.8, 6.9, 3.4], [15.6, 9.1, 4.2], 2, group('plumbing_gauges'));
      detail('freshwater_valve_handle', [14.1, 9.1, 3.3], [16, 9.5, 4.3], 0, group('plumbing_gauges'));
      for (let i = 0; i < 4; i++) detail('motor_cooling_rib_' + i, [9.1, 6.9, 6.6 + i * 1.3], [13.9, 7.3, 7 + i * 1.3], 4, group('hp_pump_motor'));
      detail('membrane_retaining_band_left', [3, 10.9, 2.9], [3.5, 14.1, 7.1], 0, group('ro_membrane'));
      detail('membrane_retaining_band_right', [12.5, 10.9, 2.9], [13, 14.1, 7.1], 0, group('ro_membrane'));
    }
    if (modelId === 'salvage_net') {
      // Replace the floating second floor with a single suspended basket.
      const mesh = Texture.all.find(t => t.name === 'salvage_net_mesh');
      for (const c of originals) if (/^net_surface/.test(c.name)) c.remove();
      const floor = add('net_basket_floor', [2, 1, 2], [14, 1, 14], mesh, [2, 2, 14, 14]);
      for (const s of ['north', 'east', 'south', 'west']) floor.faces[s].texture = null;
      for (const [name, from, to, visible] of [
        ['north', [2, 1, 2], [14, 3, 2], ['north', 'south']],
        ['south', [2, 1, 14], [14, 3, 14], ['north', 'south']],
        ['west', [2, 1, 2], [2, 3, 14], ['east', 'west']],
        ['east', [14, 1, 2], [14, 3, 14], ['east', 'west']],
      ]) {
        const c = add('net_basket_' + name, from, to, mesh, [2, 12, 14, 16]);
        for (const s of sides) if (!visible.includes(s)) c.faces[s].texture = null;
      }
      for (const x of [.3, 14.3]) for (const z of [.3, 14.3]) {
        detail(`frame_copper_cap_${x}_${z}`, [x, 3.05, z], [x + 1.4, 3.45, z + 1.4], 0);
        detail(`rope_lashing_x_${x}_${z}`, [x - .2, 2.4, z + .4], [x + 1.6, 3.65, z + .7], 3);
        detail(`rope_lashing_z_${x}_${z}`, [x + .4, 2.4, z - .2], [x + .7, 3.7, z + 1.6], 3);
      }
      detail('front_nameplate', [5.2, .65, -.14], [10.8, 2.45, .05], 1);
      detail('front_nameplate_trim', [5.4, 2.15, -.22], [10.6, 2.4, -.15], 0);
      for (const [x, z] of [[15.6, 1.6], [.2, 14.2]]) {
        detail('float_rope_knot_' + x, [x - .35, 9, z - .35], [x + .35, 9.6, z + .35], 3);
      }
    }
  }
  Canvas.updateAll();
  Undo.finishEdit('Refine ' + modelId + ' materials and silhouette', {elements: [...Cube.all], textures: [...Texture.all], bitmap: true, outliner: true});
  return {name: Project.name, cubes: Cube.all.length, textures: Texture.all.length};
}
