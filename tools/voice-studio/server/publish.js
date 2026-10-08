// Writing the two index files the app reads from the host.
//
// The clips and the worksheet PDFs are uploaded by hand to
//   http://mp-apdl.ir/grade-3/olom/audio/     and  …/karbarg/
// and in each folder sits an index.json saying what is there. The app fetches that file first
// and then knows what it may download. Nothing here uploads anything — it only prepares the
// folders so the whole thing can be dragged across in one go.

import fs from "node:fs";
import path from "node:path";

export const AUDIO_BASE = "http://mp-apdl.ir/grade-3/olom/audio/";
export const KARBARG_BASE = "http://mp-apdl.ir/grade-3/olom/karbarg/";

const AUDIO_EXT = [".ogg", ".mp3", ".wav"];

/**
 * index.json for the narration: every clip in the output folder, keyed the way the app asks
 * for it. Written into the folder itself, so uploading the folder uploads the index with it.
 */
export function writeAudioIndex({ outDir, baseUrl = AUDIO_BASE, speaker = "", appAssets = null }) {
  if (!fs.existsSync(outDir)) throw new Error(`پوشه‌ی خروجی نیست: ${outDir}`);

  const files = {};
  let bytes = 0;
  for (const name of fs.readdirSync(outDir).sort()) {
    const ext = path.extname(name).toLowerCase();
    if (!AUDIO_EXT.includes(ext)) continue;
    // _test-…, _diag-…, _sample-… : the studio's own listening clips, never lesson narration.
    // No lesson key starts with an underscore, so one rule covers all of them — and keeps a
    // voice sample from being uploaded to the host as though هوهو said it in a lesson.
    if (name.startsWith("_")) continue;
    const size = fs.statSync(path.join(outDir, name)).size;
    if (size < 512) continue;
    files[path.basename(name, ext)] = name;
    bytes += size;
  }

  const index = {
    baseUrl: baseUrl.endsWith("/") ? baseUrl : baseUrl + "/",
    generatedAt: new Date().toISOString(),
    voice: speaker,
    count: Object.keys(files).length,
    bytes,
    files,
  };
  const json = JSON.stringify(index, null, 2);
  fs.writeFileSync(path.join(outDir, "index.json"), json, "utf8");

  // the app carries a copy so the first launch, before any network, knows the shape of things
  if (appAssets && fs.existsSync(path.dirname(appAssets))) {
    fs.writeFileSync(appAssets, json, "utf8");
  }
  return index;
}

/**
 * A folder name for a worksheet. It becomes part of a URL the app opens, so it stays ASCII:
 * a Persian title has no safe spelling in a path, and a half-encoded one is worse than a plain
 * «karbarg-<n>». The real title lives in info.json and in the index, which is what the app shows.
 */
export function slugify(title) {
  const ascii = String(title).trim()
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase()
    .slice(0, 50);
  if (ascii) return ascii;

  let hash = 0;
  for (const ch of String(title)) hash = (hash * 31 + ch.codePointAt(0)) >>> 0;
  return "karbarg-" + hash.toString(36);
}

/**
 * One worksheet: its own folder holding the PDF and an info.json describing it. The same shape
 * is uploaded to the host, so the app can show the title and the note without opening the PDF.
 */
export function addWorksheet({ dir, title, note = "", chapter = -1, fileName, data }) {
  if (!title || !String(title).trim()) throw new Error("عنوان لازم است");
  if (!data || !data.length) throw new Error("فایل خالی است");

  const slug = slugify(title);
  const folder = path.join(dir, slug);
  fs.mkdirSync(folder, { recursive: true });

  const safeName = path.basename(String(fileName || "worksheet.pdf")).replace(/[^\w.\-]+/g, "_");
  const pdfName = safeName.toLowerCase().endsWith(".pdf") ? safeName : safeName + ".pdf";
  fs.writeFileSync(path.join(folder, pdfName), data);

  const info = {
    title: String(title).trim(),
    note: String(note || "").trim(),
    chapter: Number.isFinite(Number(chapter)) ? Number(chapter) : -1,
    file: pdfName,
    bytes: data.length,
    addedAt: new Date().toISOString(),
  };
  fs.writeFileSync(path.join(folder, "info.json"), JSON.stringify(info, null, 2), "utf8");
  return { slug, ...info };
}

export function listWorksheets(dir) {
  if (!fs.existsSync(dir)) return [];
  const out = [];
  for (const slug of fs.readdirSync(dir).sort()) {
    const folder = path.join(dir, slug);
    const infoPath = path.join(folder, "info.json");
    if (!fs.statSync(folder).isDirectory() || !fs.existsSync(infoPath)) continue;
    try {
      out.push({ slug, ...JSON.parse(fs.readFileSync(infoPath, "utf8")) });
    } catch {
      // a folder we cannot read is simply not listed
    }
  }
  return out;
}

export function removeWorksheet(dir, slug) {
  const folder = path.join(dir, path.basename(slug));
  if (!fs.existsSync(folder)) return false;
  fs.rmSync(folder, { recursive: true, force: true });
  return true;
}

/** index.json for the worksheets: what the app reads to build its download list. */
export function writeWorksheetIndex({ dir, baseUrl = KARBARG_BASE }) {
  fs.mkdirSync(dir, { recursive: true });
  const base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
  const sheets = listWorksheets(dir).map((sheet) => ({
    title: sheet.title,
    note: sheet.note,
    chapter: sheet.chapter,
    file: `${sheet.slug}/${sheet.file}`,
    bytes: sheet.bytes,
  }));
  const index = { baseUrl: base, generatedAt: new Date().toISOString(), count: sheets.length, sheets };
  fs.writeFileSync(path.join(dir, "index.json"), JSON.stringify(index, null, 2), "utf8");
  return index;
}
