// Draws the mod's hand-tuned pixel-art item textures and the mod icon.
// Run from the project root: node tools/draw_item_textures.js [previewDir]
// With previewDir, also writes 16x enlarged copies for checking the art.
"use strict";
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const ROOT = path.join(__dirname, "..", "src", "main", "resources", "assets", "crazywaterworld");
const PREVIEW = process.argv[2];

// ---------- PNG encoding ----------
const CRC_TABLE = new Int32Array(256).map((_, n) => {
	let c = n;
	for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
	return c;
});
function crc32(buf) {
	let c = -1;
	for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
	return (c ^ -1) >>> 0;
}
function chunk(type, data) {
	const len = Buffer.alloc(4);
	len.writeUInt32BE(data.length);
	const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
	const crc = Buffer.alloc(4);
	crc.writeUInt32BE(crc32(body));
	return Buffer.concat([len, body, crc]);
}
function encodePng(img) {
	const { w, h, px } = img;
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) {
		raw[y * (w * 4 + 1)] = 0;
		for (let x = 0; x < w; x++) {
			const c = px[y * w + x];
			const o = y * (w * 4 + 1) + 1 + x * 4;
			raw[o] = (c >>> 24) & 255; raw[o + 1] = (c >>> 16) & 255; raw[o + 2] = (c >>> 8) & 255; raw[o + 3] = c & 255;
		}
	}
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
	ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
	return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
		chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]);
}

// ---------- tiny canvas ----------
const hex = (s, a = 255) => ((parseInt(s.slice(1), 16) << 8) | a) >>> 0;
function canvas(w, h = w) {
	const img = { w, h, px: new Uint32Array(w * h) };
	img.get = (x, y) => (x < 0 || y < 0 || x >= w || y >= h ? 0 : img.px[y * w + x]);
	img.set = (x, y, c) => { if (x >= 0 && y >= 0 && x < w && y < h) img.px[y * w + x] = c; };
	img.rect = (x, y, rw, rh, c) => { for (let j = y; j < y + rh; j++) for (let i = x; i < x + rw; i++) img.set(i, j, c); };
	img.solid = (x, y) => (img.get(x, y) & 255) > 0;
	/** Dark 1px outline around every opaque region (4-neighbour), like vanilla item sprites. */
	img.outline = (c, diagonal = false) => {
		const add = [];
		for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
			if (img.solid(x, y)) continue;
			const n = [[1, 0], [-1, 0], [0, 1], [0, -1]];
			if (diagonal) n.push([1, 1], [-1, -1], [1, -1], [-1, 1]);
			if (n.some(([dx, dy]) => img.solid(x + dx, y + dy))) add.push([x, y]);
		}
		for (const [x, y] of add) img.set(x, y, c);
	};
	return img;
}
/** Fills pixels whose centre lies inside the circle, picking a tone from the light direction. */
function shadedDisc(img, cx, cy, r, tones, light = [-0.6, -0.7]) {
	for (let y = 0; y < img.h; y++) for (let x = 0; x < img.w; x++) {
		const dx = x + 0.5 - cx, dy = y + 0.5 - cy;
		const d = Math.hypot(dx, dy);
		if (d > r) continue;
		const nz = Math.sqrt(Math.max(0, 1 - (d / r) ** 2));
		const lit = (dx / r) * light[0] + (dy / r) * light[1] + nz * 0.6;
		const idx = Math.max(0, Math.min(tones.length - 1, Math.floor((1 - (lit + 0.45) / 1.6) * tones.length)));
		img.set(x, y, tones[idx]);
	}
}

// ---------- items ----------
function electricEel() {
	const img = canvas(16);
	const K = hex("#14202e"), D = hex("#1f4560"), B = hex("#2e6d8e"), L = hex("#56a7c7"),
		P = hex("#b6e1e8"), Y = hex("#ffe34a"), W = hex("#f4f7f2");
	// Cubic Bezier spine: a horizontal S from the thin tail (left) to the blunt head (right).
	const p = [[0.8, 9.5], [5.0, 2.0], [8.5, 15.5], [13.6, 6.5]];
	const at = (t) => {
		const u = 1 - t;
		return [0, 1].map((k) => u ** 3 * p[0][k] + 3 * u * u * t * p[1][k] + 3 * u * t * t * p[2][k] + t ** 3 * p[3][k]);
	};
	const samples = [];
	for (let i = 0; i <= 200; i++) samples.push([i / 200, ...at(i / 200)]);
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		let best = null;
		for (const [t, sx, sy] of samples) {
			const d = Math.hypot(x + 0.5 - sx, y + 0.5 - sy);
			if (!best || d < best.d) best = { d, t, sx, sy };
		}
		const r = 0.7 + 1.3 * Math.min(1, best.t * 1.3);
		if (best.d > r) continue;
		// Above the spine is the dark back, below it the pale belly.
		const side = (y + 0.5 - best.sy) - (x + 0.5 - best.sx) * 0.3;
		img.set(x, y, side < -0.6 ? D : side < 0.2 ? B : side < 0.9 ? L : P);
	}
	img.outline(K);
	// Electric spots along the spine and a spark off the head.
	for (const t of [0.22, 0.42, 0.62, 0.8]) {
		const [sx, sy] = at(t);
		img.set(Math.floor(sx), Math.floor(sy), Y);
	}
	const [hx, hy] = at(1);
	img.set(Math.floor(hx), Math.floor(hy) - 1, W); // eye
	img.set(Math.floor(hx) + 1, Math.floor(hy) - 1, K);
	// Zig-zag spark crackling off the snout.
	for (const [dx, dy] of [[2, -4], [1, -3], [2, -2], [3, 1], [2, 2], [3, 3]]) img.set(Math.floor(hx) + dx - 1, Math.floor(hy) + dy - 1, Y);
	return img;
}

function wasteFilter() {
	const img = canvas(16);
	const K = hex("#1d1a16"), CAP = hex("#6f7478"), CAPL = hex("#a3a9ad"), CAPD = hex("#4a4f53"),
		F1 = hex("#b9a77c"), F2 = hex("#93805a"), F3 = hex("#6d5d40"), G = hex("#4f5b2c"), M = hex("#5d6b3a", 200);
	// Pleated paper cartridge between two metal caps.
	for (let y = 4; y <= 11; y++) for (let x = 4; x <= 11; x++) img.set(x, y, (x % 2 === 0) ? F1 : F2);
	for (let x = 4; x <= 11; x++) { img.set(x, 11, F3); }
	img.rect(3, 2, 10, 2, CAP); img.rect(4, 2, 8, 1, CAPL); img.rect(3, 3, 10, 1, CAPD);
	img.rect(3, 12, 10, 2, CAP); img.rect(4, 12, 8, 1, CAPL); img.rect(3, 13, 10, 1, CAPD);
	img.rect(7, 1, 2, 1, CAPD); // outlet nozzle
	// Grime: sludge stains creeping up from the bottom.
	for (const [x, y] of [[5, 10], [6, 9], [6, 10], [9, 10], [10, 9], [10, 10], [8, 7], [5, 6], [11, 8], [7, 10]]) img.set(x, y, G);
	img.outline(K);
	img.set(9, 15, M); img.set(9, 14, M); img.set(5, 15, M); // drips
	return img;
}

function pearl(size, tones, rim) {
	const img = canvas(16);
	shadedDisc(img, 8, 8.5, size, tones);
	img.outline(rim);
	return img;
}
function riverPearl() {
	const img = pearl(4.6, [hex("#ffffff"), hex("#f6efe6"), hex("#e9dccf"), hex("#d3bfb2"), hex("#b59e95")], hex("#5e4c4a"));
	img.set(6, 6, hex("#ffffff")); img.set(7, 6, hex("#ffffff")); img.set(6, 7, hex("#ffffff"));
	img.set(10, 11, hex("#f2c9cf")); img.set(11, 10, hex("#f2c9cf")); // pink nacre glint
	return img;
}
function ancientPearl() {
	const img = canvas(16);
	const K = hex("#1b1530"), GO = hex("#e8b54a"), GD = hex("#a8742a"), GL = hex("#fff0a6");
	// Gold claw setting under the pearl.
	img.rect(4, 12, 8, 2, GD); img.rect(5, 12, 6, 1, GO);
	for (const [x, y] of [[3, 9], [3, 10], [3, 11], [12, 9], [12, 10], [12, 11], [7, 14], [8, 14]]) img.set(x, y, GO);
	shadedDisc(img, 8, 7.5, 4.6, [hex("#e9fffb"), hex("#a5f0e6"), hex("#5fcfd0"), hex("#3a8fb3"), hex("#4a4b9c")]);
	img.outline(K);
	// Inner glow, a rune ring hint, sparkles around.
	img.set(6, 5, hex("#ffffff")); img.set(7, 5, hex("#ffffff")); img.set(6, 6, hex("#ffffff"));
	img.set(9, 9, hex("#d9a8ff")); img.set(10, 8, hex("#d9a8ff"));
	for (const [x, y] of [[1, 2], [14, 1], [15, 6], [0, 7]]) img.set(x, y, GL);
	img.set(13, 12, GL); img.set(2, 13, GL);
	return img;
}

function magnifier({ frame, frameLight, frameDark, handle, handleLight, gem }) {
	const img = canvas(16);
	const K = hex("#1a1c22");
	// Handle first so the ring overlaps it.
	for (let i = 0; i < 6; i++) {
		img.set(9 + i, 10 + i, handle); img.set(10 + i, 10 + i, handle); img.set(9 + i, 11 + i, handleLight);
	}
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const d = Math.hypot(x + 0.5 - 6.5, y + 0.5 - 6.5);
		if (d <= 5.2 && d > 3.6) {
			const lit = (x + y) < 12;
			img.set(x, y, lit ? frameLight : d > 4.6 ? frameDark : frame);
		} else if (d <= 3.6) {
			img.set(x, y, (x + y) < 10 ? hex("#d8f4ff", 230) : (x + y) < 14 ? hex("#9fd6ee", 210) : hex("#6fb0d6", 210));
		}
	}
	img.outline(K);
	img.set(5, 4, hex("#ffffff")); img.set(4, 5, hex("#ffffff")); // lens glint
	if (gem) { img.set(6, 1, gem); img.set(6, 0, K); img.set(5, 1, K); img.set(7, 1, K); }
	return img;
}

function netTexture() {
	// Knotted rope mesh, tileable on a 4px grid; empty cells stay transparent for the cutout layer.
	const img = canvas(16);
	const R1 = hex("#c9a46a"), R2 = hex("#a7834d"), KNOT = hex("#6e5332");
	for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
		const vx = x % 4 === 1, hy = y % 4 === 1;
		if (vx && hy) img.set(x, y, KNOT);
		else if (vx) img.set(x, y, y % 2 ? R1 : R2);
		else if (hy) img.set(x, y, x % 2 ? R2 : R1);
	}
	return img;
}

function icon() {
	// 32x32 art scaled 4x: an open clam with a glowing pearl over a coin on deep water.
	const s = canvas(32);
	const WATER = [hex("#2b8aa3"), hex("#1f6d86"), hex("#175468"), hex("#0f3b4b")];
	for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
		const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16);
		if (d <= 15.5) s.set(x, y, WATER[Math.min(3, Math.floor((y + (x % 3 === 0 ? 1 : 0)) / 8))]);
	}
	for (const [x, y, w] of [[6, 7, 5], [19, 5, 6], [9, 25, 4], [21, 24, 6]]) s.rect(x, y, w, 1, hex("#5fc3d3"));
	// Lower shell.
	for (let y = 17; y <= 25; y++) for (let x = 5; x <= 26; x++) {
		const nx = (x + 0.5 - 16) / 11, ny = (y + 0.5 - 17) / 8.5;
		if (nx * nx + ny * ny <= 1) s.set(x, y, (x % 3 === 0) ? hex("#b88a5a") : (y > 22 ? hex("#8a6340") : hex("#d4a877")));
	}
	// Upper shell lid, raised.
	for (let y = 4; y <= 14; y++) for (let x = 6; x <= 25; x++) {
		const nx = (x + 0.5 - 16) / 10, ny = (y + 0.5 - 14) / 10;
		if (ny <= 0 && nx * nx + ny * ny <= 1) s.set(x, y, (x % 3 === 1) ? hex("#c99a69") : (y < 8 ? hex("#f0cf9e") : hex("#ddb383")));
	}
	s.rect(7, 15, 18, 2, hex("#5b3b26"));
	shadedDisc(s, 16, 16.5, 4.2, [hex("#ffffff"), hex("#eafffb"), hex("#a8ece4"), hex("#6fc8cf")]);
	s.outline(hex("#0a1e27"));
	s.set(14, 14, hex("#ffffff")); s.set(15, 14, hex("#ffffff"));
	// Gold coin bottom-right.
	shadedDisc(s, 25, 26, 4.3, [hex("#fff3b0"), hex("#ffd75e"), hex("#e6a93a"), hex("#b9781f")]);
	s.rect(25, 24, 1, 5, hex("#9a5f16"));
	const big = canvas(128);
	for (let y = 0; y < 128; y++) for (let x = 0; x < 128; x++) big.set(x, y, s.get(x >> 2, y >> 2));
	return big;
}

// ---------- output ----------
const GOLD = { frame: hex("#e7b143"), frameLight: hex("#ffe27a"), frameDark: hex("#a8701f"), handle: hex("#2a2230"), handleLight: hex("#4b3e57"), gem: hex("#ff4f7b") };
const outputs = {
	"textures/item/electric_eel.png": electricEel(),
	"textures/item/waste_filter.png": wasteFilter(),
	"textures/item/river_pearl.png": riverPearl(),
	"textures/item/ancient_pearl.png": ancientPearl(),
	"textures/item/basic_appraisal.png": magnifier({ frame: hex("#b8723f"), frameLight: hex("#e09a5e"), frameDark: hex("#7c4826"), handle: hex("#6b4a2b"), handleLight: hex("#8d6840") }),
	"textures/item/advanced_appraisal.png": magnifier({ frame: hex("#aeb7bf"), frameLight: hex("#e6edf2"), frameDark: hex("#6c7680"), handle: hex("#33404d"), handleLight: hex("#55667a"), gem: hex("#57d6ff") }),
	"textures/item/expert_appraisal.png": magnifier(GOLD),
	"textures/block/salvage_net_mesh.png": netTexture(),
	"icon.png": icon(),
};
for (const [rel, img] of Object.entries(outputs)) {
	const file = path.join(ROOT, rel);
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, encodePng(img));
	console.log("wrote", rel);
	if (PREVIEW) {
		const scale = img.w <= 16 ? 16 : img.w <= 32 ? 8 : 2;
		const big = canvas(img.w * scale, img.h * scale);
		for (let y = 0; y < big.h; y++) for (let x = 0; x < big.w; x++) {
			const c = img.get(Math.floor(x / scale), Math.floor(y / scale));
			// Checkerboard behind transparency so the silhouette is visible.
			const bg = ((x >> 3) + (y >> 3)) % 2 ? hex("#3a3f44") : hex("#4a5056");
			const a = (c & 255) / 255;
			const mix = (sh) => Math.round(((c >>> sh) & 255) * a + ((bg >>> sh) & 255) * (1 - a));
			big.set(x, y, ((mix(24) << 24) | (mix(16) << 16) | (mix(8) << 8) | 255) >>> 0);
		}
		fs.mkdirSync(PREVIEW, { recursive: true });
		fs.writeFileSync(path.join(PREVIEW, rel.replace(/\//g, "_")), encodePng(big));
	}
}
