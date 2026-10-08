// استودیوی صداگذاری هوهو — the local half.
//
// A browser cannot write to a folder you name, and it cannot call the آواشو gateway without
// being stopped by CORS. So the React page talks to this small Express server, which keeps the
// token, reads the manifest, records one line at a time, and writes the files where you said.
// Nothing is uploaded or pushed anywhere: the only things it writes are audio files in your
// output folder and its own config beside this file.

import express from "express";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import crypto from "node:crypto";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { readManifest, existingKeys, GROUP_LABELS } from "./manifest.js";
import { speak, SPEAKERS, VOICES, RateLimited } from "./avasho.js";
import {
  AUDIO_BASE, KARBARG_BASE, writeAudioIndex, addWorksheet, listWorksheets,
  removeWorksheet, writeWorksheetIndex,
} from "./publish.js";
import { probe, uploadFiles, uploadTree, audioFilesIn, ftpHint } from "./ftp.js";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const CONFIG_FILE = path.join(ROOT, "voice-studio.config.json");
const PORT = Number(process.env.PORT || 5174);

// ── config ───────────────────────────────────────────────────────────────────
const DEFAULTS = {
  token: "",
  manifestPath: path.resolve(ROOT, "../../app/src/main/res/raw/audio_manifest.txt"),
  // inside the repo, so the finished clips can be committed and uploaded from one place
  outDir: path.resolve(ROOT, "../voice-out"),
  karbargDir: path.resolve(ROOT, "../karbarg-out"),
  audioBase: AUDIO_BASE,
  karbargBase: KARBARG_BASE,
  speaker: "pune",         // پونه — the voice the lessons are recorded with
  // 1 is the service's own pace, which for a third-grader runs ahead of the animation on the
  // stage. A shade under it lets the words breathe and the sentence stay in one piece, which
  // is what «روان» means here. The test tab's «نمونه‌ی سرعت‌ها» button is for settling this
  // by ear rather than by argument.
  speed: 0.9,
  // Every line in the manifest is a sentence or two (the longest is 539 characters), so the
  // short family is the right one. avasho-large is for pages of text and answers «pending»
  // even for a line, which is what made the generation section look broken.
  endpoint: "short",
  useVowels: true,          // send the third column, the one with the vowels
  concurrency: 2,
  delayMs: 400,
  retries: 3,
  convertOgg: false,        // needs ffmpeg on PATH
  oggBitrate: "24k",
  schedule: { enabled: false, everySeconds: 300, batchSize: 25 },

  // where the finished files are sent. The paths are the real ones on the server, under
  // /public_html — the same folders the app's URLs point at.
  ftp: {
    host: "ftp.mp-apdl.ir",
    port: 21,
    user: "",
    password: "",
    secure: false,
    audioDir: "/public_html/grade-3/olom/audio",
    karbargDir: "/public_html/grade-3/olom/karbarg",
  },
};

/**
 * Two settings decide whether the service answers at all, so neither is taken on trust.
 *
 * A stored «long» is rewritten to «short»: the long family is for pages of text, and the
 * studio only ever sends single sentences. A session that had once picked long kept the
 * generation section on it for good, and that was the whole of «the test tab works but
 * generating does not» — the test tab normalises its endpoint, the job did not.
 */
function normalizeEndpoint(value) {
  return value === "long" ? "long" : "short";
}

function normalizeSpeaker(value) {
  const name = String(value || "").trim().toLowerCase();
  return SPEAKERS.includes(name) ? name : DEFAULTS.speaker;
}

/** A pace the service will accept: half speed to double, one decimal. */
function speedOf(value, fallback = 1) {
  const n = Number(value);
  if (!Number.isFinite(n) || n <= 0) return Number(fallback) || 1;
  return Math.round(Math.min(2, Math.max(0.5, n)) * 10) / 10;
}

/** One sentence هوهو really says, so a sample is judged on the real thing. */
const SAMPLE_TEXT = "سَلام! مَن هوهو هَستَم، مُعَلِّمِ عُلومِ تو. بیا با هَم دُنیا را کَشف کُنیم.";

/** _sample-pune-090 — the speed in the name, so two paces never overwrite each other. */
const sampleKey = (speaker, speed) =>
  `_sample-${speaker}-${String(Math.round(speed * 100)).padStart(3, "0")}`;

let config = { ...DEFAULTS };
let migrated = null;
try {
  if (fs.existsSync(CONFIG_FILE)) {
    const saved = JSON.parse(fs.readFileSync(CONFIG_FILE, "utf8"));
    config = {
      ...DEFAULTS, ...saved,
      schedule: { ...DEFAULTS.schedule, ...(saved.schedule || {}) },
      ftp: { ...DEFAULTS.ftp, ...(saved.ftp || {}) },
    };
    if (normalizeEndpoint(config.endpoint) === "long") {
      migrated = `نقطه‌ی سرویس از avasho-large به متنِ کوتاه برگشت (جمله‌های درس کوتاه‌اند)`;
      config.endpoint = "short";
    }
    if (normalizeSpeaker(config.speaker) !== config.speaker) {
      migrated = `${migrated ? migrated + "؛ " : ""}صدای «${config.speaker}» شناخته نشد — `
        + `${DEFAULTS.speaker}`;
      config.speaker = normalizeSpeaker(config.speaker);
    }
  }
} catch (err) {
  console.error("config خوانده نشد:", err.message);
}
config.endpoint = normalizeEndpoint(config.endpoint);
config.speaker = normalizeSpeaker(config.speaker);

let savedAt = fs.existsSync(CONFIG_FILE) ? fs.statSync(CONFIG_FILE).mtimeMs : 0;

/**
 * Writes the settings — token and FTP password included — beside this file. Written to a
 * temporary name first and then moved, so a crash half-way cannot leave a truncated config
 * that would look on the next launch as though the token had vanished.
 */
function saveConfig() {
  const temp = CONFIG_FILE + ".tmp";
  fs.writeFileSync(temp, JSON.stringify(config, null, 2), "utf8");
  fs.renameSync(temp, CONFIG_FILE);
  savedAt = Date.now();
}

// ── the live log, shared with the page over SSE ───────────────────────────────
const listeners = new Set();
const log = [];

function say(level, message) {
  const entry = { at: new Date().toISOString(), level, message };
  log.push(entry);
  if (log.length > 600) log.splice(0, log.length - 600);
  const frame = `data: ${JSON.stringify({ type: "log", entry })}\n\n`;
  for (const res of listeners) res.write(frame);
  console.log(`[${entry.at.slice(11, 19)}] ${level}: ${message}`);
}

function push(type, payload) {
  const frame = `data: ${JSON.stringify({ type, ...payload })}\n\n`;
  for (const res of listeners) res.write(frame);
}

// ── the job ──────────────────────────────────────────────────────────────────
const job = {
  running: false,
  stopping: false,
  total: 0,
  done: 0,
  failed: 0,
  current: [],
  startedAt: null,
  lastError: null,
  shapeShown: false,
  traceShown: false,
  controller: null,
};

function snapshot() {
  return {
    running: job.running,
    stopping: job.stopping,
    total: job.total,
    done: job.done,
    failed: job.failed,
    current: job.current,
    startedAt: job.startedAt,
    lastError: job.lastError,
  };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function toOgg(src, dst, bitrate) {
  return new Promise((resolve) => {
    const ff = spawn("ffmpeg", ["-y", "-loglevel", "error", "-i", src,
      "-c:a", "libvorbis", "-b:a", bitrate, "-ar", "24000", "-ac", "1", dst]);
    ff.on("error", () => resolve(false));
    ff.on("close", (code) => resolve(code === 0));
  });
}

/** Record one line and write it into the output folder. */
async function recordOne(line, { force = false } = {}) {
  if (force) {
    for (const ext of [".ogg", ".mp3", ".wav"]) {
      const old = path.join(config.outDir, line.key + ext);
      if (fs.existsSync(old)) fs.unlinkSync(old);
    }
  }
  const text = config.useVowels ? line.spoken : line.written;
  let attempt = 0;

  for (;;) {
    attempt += 1;

    // Every exchange is kept, exactly as the test tab keeps them. On the first line they go
    // into the log so a run can be read while it is young, and after that only a failure
    // prints them — otherwise four thousand clips would bury the log.
    const trace = [];
    const step = (entry) => {
      trace.push(entry);
      if (!job.traceShown) {
        say("info", `${line.key} · ${entry.name} ${entry.method} ${entry.status} `
          + `(${entry.ms} میلی‌ثانیه) ${String(entry.body || "").slice(0, 300)}`);
      }
    };

    try {
      const { buffer, ext, raw } = await speak({
        token: config.token, text, speaker: config.speaker,
        speed: Number(config.speed) || 1, endpoint: config.endpoint,
        timestamps: false,
        signal: job.controller ? job.controller.signal : null,
        onHttp: step,
        onNote: (note) => say("info", `${line.key}: ${note}`),
      });

      job.traceShown = true;
      if (raw && !job.shapeShown) {
        job.shapeShown = true;
        say("info", `شکلِ پاسخِ سرویس: ${JSON.stringify(raw).slice(0, 400)}`);
      }

      fs.mkdirSync(config.outDir, { recursive: true });
      const target = path.join(config.outDir, line.key + ext);
      fs.writeFileSync(target, buffer);

      let saved = path.basename(target);
      let size = buffer.length;
      if (config.convertOgg && ext !== ".ogg") {
        const ogg = path.join(config.outDir, line.key + ".ogg");
        if (await toOgg(target, ogg, config.oggBitrate)) {
          fs.unlinkSync(target);
          saved = path.basename(ogg);
          size = fs.statSync(ogg).size;
        } else {
          say("warn", "ffmpeg اجرا نشد؛ فایل با فرمتِ خودِ سرویس ماند");
        }
      }
      return { ok: true, saved, size };
    } catch (err) {
      const limited = err instanceof RateLimited;
      if (job.traceShown && trace.length) {
        for (const entry of trace) {
          say("warn", `${line.key} · ${entry.name} ${entry.method} ${entry.status} `
            + `(${entry.ms} میلی‌ثانیه) ${String(entry.body || "").slice(0, 300)}`);
        }
      }
      job.traceShown = true;
      if (job.stopping) return { ok: false, error: "ایستاد" };
      if (attempt > Number(config.retries)) {
        return { ok: false, error: err.message };
      }
      const wait = limited ? 20000 * attempt : 1500 * attempt;
      say("warn", `${line.key}: ${err.message} — ${Math.round(wait / 1000)} ثانیه صبر و تلاشِ ${attempt + 1}`);
      await sleep(wait);
    }
  }
}

/**
 * Starts a batch and comes back straight away.
 *
 * The page used to wait on the whole run: one request held open for four thousand clips, with
 * the buttons disabled and the list below untouched until the very end. Now the checks happen
 * here, the answer goes back, and the run reports itself over the stream.
 */
function startJob(keys, { force = false } = {}) {
  if (job.running) return { error: "یک کار در حال اجراست" };
  if (!config.token) return { error: "توکن خالی است" };
  if (!keys.length) return { error: "چیزی برای ساختن نیست" };
  try {
    readManifest(config.manifestPath);
  } catch (err) {
    return { error: `منیفست خوانده نشد: ${err.message}` };
  }
  runJob(keys, { force }).catch((err) => {
    job.running = false;
    job.controller = null;
    job.lastError = err.message;
    say("error", `کار ایستاد: ${err.message}`);
    push("status", snapshot());
  });
  return { started: true };
}

async function runJob(keys, { force = false } = {}) {
  const lines = readManifest(config.manifestPath);
  const byKey = new Map(lines.map((l) => [l.key, l]));
  const todo = keys.map((k) => byKey.get(k)).filter(Boolean);

  Object.assign(job, { running: true, stopping: false, total: todo.length, done: 0,
                       failed: 0, current: [], startedAt: Date.now(), lastError: null,
                       traceShown: false, controller: new AbortController() });
  say("info", `شروع: ${todo.length} کلیپ — صدای ${config.speaker}، `
    + `${config.endpoint === "long" ? "avasho-large" : "avasho (متنِ کوتاه)"} → ${config.outDir}`);
  push("status", snapshot());

  let cursor = 0;
  const workers = Array.from({ length: Math.max(1, Number(config.concurrency)) }, async () => {
    while (cursor < todo.length && !job.stopping) {
      const line = todo[cursor++];
      job.current = [...job.current.filter((k) => k !== line.key), line.key];
      push("status", snapshot());

      const result = await recordOne(line, { force });
      job.current = job.current.filter((k) => k !== line.key);
      if (result.ok) {
        job.done += 1;
        say("ok", `${line.key} ← ${result.saved} (${Math.round(result.size / 1024)} کیلوبایت)`);
      } else {
        job.failed += 1;
        job.lastError = result.error;
        say("error", `${line.key}: ${result.error}`);
      }
      // the list at the bottom of the page follows along: one line, one frame, as it happens.
      // Waiting for the whole batch to end before rereading the folder made a run look frozen.
      push("line", { key: line.key, done: result.ok, stamp: Date.now(),
                     file: result.ok ? result.saved : null, size: result.ok ? result.size : 0 });
      push("status", snapshot());
      if (Number(config.delayMs) > 0) await sleep(Number(config.delayMs));
    }
  });

  await Promise.all(workers);
  job.running = false;
  job.current = [];
  job.controller = null;
  say("info", job.stopping
    ? `ایستاد — ${job.done} ساخته، ${job.failed} ناموفق`
    : `تمام — ${job.done} ساخته، ${job.failed} ناموفق`);
  job.stopping = false;
  push("status", snapshot());
}

// ── the scheduler: wake up now and then and take the next batch ───────────────
let timer = null;

function applySchedule() {
  if (timer) clearInterval(timer);
  timer = null;
  if (!config.schedule.enabled) {
    say("info", "زمان‌بندی خاموش شد");
    return;
  }
  const every = Math.max(30, Number(config.schedule.everySeconds) || 300);
  say("info", `زمان‌بندی روشن: هر ${every} ثانیه، ${config.schedule.batchSize} کلیپ`);
  timer = setInterval(() => {
    if (job.running) return;
    const missing = missingKeys();
    if (!missing.length) {
      say("info", "زمان‌بندی: چیزی باقی نمانده");
      return;
    }
    const batch = missing.slice(0, Math.max(1, Number(config.schedule.batchSize) || 25));
    say("info", `زمان‌بندی: دسته‌ی تازه، ${batch.length} کلیپ`);
    startJob(batch);
  }, every * 1000);
}

function missingKeys() {
  const lines = readManifest(config.manifestPath);
  const have = existingKeys(config.outDir);
  return lines.filter((l) => !have.has(l.key)).map((l) => l.key);
}

// ── HTTP ─────────────────────────────────────────────────────────────────────
const app = express();
app.use(express.json({ limit: "64mb" }));   // a worksheet PDF arrives as base64

const publicConfig = () => ({
  ...config,
  ftp: { ...config.ftp, password: undefined, passwordSet: Boolean(config.ftp.password) },
  token: undefined,
  tokenSet: Boolean(config.token),
  tokenHint: config.token ? `${config.token.slice(0, 6)}…${config.token.slice(-4)}` : "",
  configFile: CONFIG_FILE,
  savedAt,
  speakers: SPEAKERS,
  voices: VOICES,
  groupLabels: GROUP_LABELS,
  ffmpeg: true,
});

app.get("/api/config", (_req, res) => res.json(publicConfig()));

app.post("/api/config", (req, res) => {
  const body = req.body || {};
  for (const key of ["manifestPath", "outDir", "karbargDir", "audioBase", "karbargBase"]) {
    if (typeof body[key] === "string" && body[key].trim()) config[key] = body[key].trim();
  }
  if (typeof body.speaker === "string" && body.speaker.trim()) {
    config.speaker = normalizeSpeaker(body.speaker);
  }
  if (typeof body.endpoint === "string" && body.endpoint.trim()) {
    config.endpoint = normalizeEndpoint(body.endpoint.trim());
  }
  for (const key of ["speed", "concurrency", "delayMs", "retries"]) {
    if (body[key] !== undefined && body[key] !== "") config[key] = Number(body[key]);
  }
  for (const key of ["useVowels", "convertOgg"]) {
    if (typeof body[key] === "boolean") config[key] = body[key];
  }
  if (typeof body.oggBitrate === "string" && body.oggBitrate.trim()) {
    config.oggBitrate = body.oggBitrate.trim();
  }
  if (typeof body.token === "string" && body.token.trim()) {
    config.token = body.token.trim();
    say("info", "توکن ذخیره شد");
  }
  if (body.token === "") {
    config.token = "";
    say("info", "توکن پاک شد");
  }
  if (body.schedule) {
    config.schedule = { ...config.schedule, ...body.schedule };
    applySchedule();
  }
  if (body.ftp) {
    const next = { ...config.ftp, ...body.ftp };
    if (body.ftp.password === undefined) next.password = config.ftp.password;  // keep the old one
    if (body.ftp.password === "") next.password = "";                          // unless cleared
    config.ftp = next;
    say("info", "تنظیماتِ FTP ذخیره شد" + (next.password ? "" : " (رمز خالی است)"));
  }
  saveConfig();
  res.json(publicConfig());
});

app.get("/api/manifest", (_req, res) => {
  try {
    const lines = readManifest(config.manifestPath);
    const have = existingKeys(config.outDir);
    const overrides = readOverrides();
    res.json({
      outDir: config.outDir,
      lines: lines.map((l) => {
        const file = have.get(l.key);
        return {
          ...l,
          done: Boolean(file),
          file: file?.name ?? null,
          size: file?.size ?? 0,
          edited: Object.prototype.hasOwnProperty.call(overrides, l.key),
        };
      }),
    });
  } catch (err) {
    res.status(400).json({ error: `منیفست خوانده نشد: ${err.message}` });
  }
});

app.get("/api/status", (_req, res) => res.json({ ...snapshot(), log: log.slice(-120) }));

// ── the two index.json files the host serves ─────────────────────────────────
app.post("/api/publish/audio", (_req, res) => {
  try {
    const index = writeAudioIndex({
      outDir: config.outDir,
      baseUrl: config.audioBase,
      speaker: config.speaker,
      appAssets: path.resolve(ROOT, "../../app/src/main/assets/voice-index.json"),
    });
    say("ok", `فهرستِ صدا ساخته شد: ${index.count} کلیپ، ${Math.round(index.bytes / 1048576)} مگابایت`);
    res.json(index);
  } catch (err) {
    say("error", `فهرستِ صدا ساخته نشد: ${err.message}`);
    res.status(400).json({ error: err.message });
  }
});




// ── «does the service work at all?» ──────────────────────────────────────────
// One call, with every exchange written down: the address, the status, how long it took and
// the start of the body. When something does not work this is what turns «it failed» into a
// sentence someone can act on.
const diag = { running: false, controller: null, steps: [], startedAt: 0, outcome: null };

app.get("/api/diag", (_req, res) => res.json({
  running: diag.running, steps: diag.steps, startedAt: diag.startedAt, outcome: diag.outcome,
  speakers: SPEAKERS, voices: VOICES,
}));

app.post("/api/diag", async (req, res) => {
  if (!config.token) return res.status(400).json({ error: "توکن خالی است" });
  if (diag.running) return res.status(409).json({ error: "یک تست در حال اجراست" });

  // the same two guards the job uses, so the two sections can never drift apart again
  const speaker = normalizeSpeaker(req.body?.speaker || config.speaker);
  const endpoint = normalizeEndpoint(req.body?.endpoint);
  const text = String(req.body?.text || SAMPLE_TEXT);
  const speed = speedOf(req.body?.speed, config.speed);
  const timestamps = req.body?.timestamps === true;

  diag.controller = new AbortController();
  Object.assign(diag, { running: true, steps: [], startedAt: Date.now(), outcome: null });
  push("diag", { running: true, steps: [] });
  say("info", `تستِ وب‌سرویس — ${endpoint === "long" ? "avasho-large" : "avasho"}، `
    + `صدای ${speaker}، سرعتِ ${speed}`);
  res.json({ started: true });

  const step = (entry) => {
    diag.steps.push({ at: Date.now() - diag.startedAt, ...entry });
    push("diag", { running: true, steps: diag.steps });
  };

  try {
    const { buffer, ext, timestamps: marks } = await speak({
      token: config.token, text, speaker, speed,
      endpoint, timestamps,
      signal: diag.controller.signal,
      onHttp: step,
      onNote: (note) => say("info", `تست: ${note}`),
    });

    fs.mkdirSync(config.outDir, { recursive: true });
    const name = `_diag-${speaker}${ext}`;
    fs.writeFileSync(path.join(config.outDir, name), buffer);
    diag.outcome = {
      ok: true, file: name, bytes: buffer.length, ext,
      words: marks ? marks.length : 0,
      ms: Date.now() - diag.startedAt,
    };
    say("ok", `تست موفق — ${name}، ${Math.round(buffer.length / 1024)} کیلوبایت، `
      + `${((Date.now() - diag.startedAt) / 1000).toFixed(1)} ثانیه`);
  } catch (err) {
    diag.outcome = { ok: false, error: err.message, ms: Date.now() - diag.startedAt };
    say("error", `تست ناموفق: ${err.message}`);
  } finally {
    diag.running = false;
    diag.controller = null;
    push("diag", { running: false, steps: diag.steps, outcome: diag.outcome });
  }
});

app.post("/api/diag/stop", (_req, res) => {
  if (diag.controller) {
    diag.controller.abort();
    say("warn", "تست متوقف شد");
  }
  res.json({ ok: true });
});

// ── نمونه‌ها: choosing a voice and a pace by ear ──────────────────────────────
//
// Fourteen voices and a handful of speeds are not a thing to argue about; they are a thing to
// listen to. This records the same sentence across whichever set is asked for and leaves the
// clips in the output folder under «_sample-…», where the page plays them side by side. The
// underscore keeps them out of index.json, so a sample can never reach the host as a lesson.
const sampler = { running: false, controller: null, mode: "", text: "", items: [],
                  duplicates: [] };

function samplerState() {
  return { running: sampler.running, mode: sampler.mode, text: sampler.text,
           items: sampler.items, duplicates: sampler.duplicates };
}

/**
 * Names whose clip is byte-for-byte another's.
 *
 * Two different voices reading the same sentence cannot produce the same bytes, so a match is
 * the service handing back one recording for several names — the one answer to «I chose a man
 * and got a woman» that does not depend on anyone's ear.
 */
function identicalSets(items) {
  const byHash = new Map();
  for (const item of items) {
    if (!item.hash) continue;
    const label = item.speed === items[0]?.speed
      ? item.speaker : `${item.speaker}@${item.speed}`;
    byHash.set(item.hash, [...(byHash.get(item.hash) || []), label]);
  }
  return [...byHash.values()].filter((set) => set.length > 1);
}

app.get("/api/diag/sample", (_req, res) => res.json(samplerState()));

app.post("/api/diag/sample", async (req, res) => {
  if (!config.token) return res.status(400).json({ error: "توکن خالی است" });
  if (sampler.running) return res.status(409).json({ error: "یک نمونه‌گیری در حال اجراست" });

  const mode = req.body?.mode === "speeds" ? "speeds" : "voices";
  const text = String(req.body?.text || SAMPLE_TEXT);
  const endpoint = normalizeEndpoint(req.body?.endpoint);
  const speed = speedOf(req.body?.speed, config.speed);
  const speaker = normalizeSpeaker(req.body?.speaker || config.speaker);

  let pairs;
  if (mode === "speeds") {
    const speeds = Array.isArray(req.body?.speeds) && req.body.speeds.length
      ? req.body.speeds.map((v) => speedOf(v, 1))
      : [0.8, 0.9, 1, 1.1];
    pairs = [...new Set(speeds)].map((sp) => ({ speaker, speed: sp }));
  } else {
    const gender = ["male", "female"].includes(req.body?.gender) ? req.body.gender : null;
    pairs = VOICES.filter((v) => !gender || v.gender === gender)
                  .map((v) => ({ speaker: v.name, speed }));
  }

  sampler.controller = new AbortController();
  Object.assign(sampler, { running: true, mode, text, duplicates: [],
    items: pairs.map((pair) => ({
      ...pair, key: sampleKey(pair.speaker, pair.speed), state: "در نوبت",
    })) });
  push("sample", samplerState());
  say("info", mode === "speeds"
    ? `نمونه‌ی سرعت‌ها — ${speaker}، ${pairs.map((p) => p.speed).join("، ")}`
    : `نمونه‌ی گوینده‌ها — ${pairs.length} صدا، سرعتِ ${speed}`);
  res.json({ started: true, items: sampler.items.length });

  fs.mkdirSync(config.outDir, { recursive: true });
  for (const item of sampler.items) {
    if (sampler.controller?.signal.aborted) {
      item.state = "ایستاد";
      continue;
    }
    item.state = "در حال ساخت";
    push("sample", samplerState());
    try {
      const { buffer, ext } = await speak({
        token: config.token, text, speaker: item.speaker, speed: item.speed,
        endpoint, timestamps: false, signal: sampler.controller.signal,
        // what went out and what came back, kept per row: «it made a female voice» is then a
        // thing anyone can check instead of a thing to take on trust
        onHttp: (entry) => {
          if (entry.name === "request") item.reply = String(entry.body || "").slice(0, 400);
        },
      });
      // one name per voice-and-speed, whatever the container turns out to be
      for (const old of [".ogg", ".mp3", ".wav"]) {
        const stale = path.join(config.outDir, item.key + old);
        if (old !== ext && fs.existsSync(stale)) fs.unlinkSync(stale);
      }
      fs.writeFileSync(path.join(config.outDir, item.key + ext), buffer);
      item.state = "آماده";
      item.bytes = buffer.length;
      // the fingerprint of the audio itself. Two names that come back with the same one got
      // the same recording, and that means the service ignored the name it was given.
      item.hash = crypto.createHash("sha1").update(buffer).digest("hex").slice(0, 10);
      say("ok", `نمونه: ${item.speaker} با سرعتِ ${item.speed} — `
        + `${Math.round(buffer.length / 1024)} کیلوبایت · ${item.hash}`);
    } catch (err) {
      item.state = "نشد";
      item.error = err.message;
      say("error", `نمونه‌ی ${item.speaker}: ${err.message}`);
    }
    push("sample", samplerState());
    if (Number(config.delayMs) > 0) await sleep(Number(config.delayMs));
  }

  sampler.running = false;
  sampler.controller = null;
  const ready = sampler.items.filter((i) => i.state === "آماده").length;
  sampler.duplicates = identicalSets(sampler.items);
  for (const set of sampler.duplicates) {
    say("warn", `سرویس برای ${set.join("، ")} فایلِ یکسان داد — نامِ گوینده را نگرفته است`);
  }
  say("info", `نمونه‌ها تمام — ${ready} از ${sampler.items.length} آماده`
    + (sampler.duplicates.length ? `، ${sampler.duplicates.length} دسته تکراری` : ""));
  push("sample", samplerState());
});

app.post("/api/diag/sample/stop", (_req, res) => {
  if (sampler.controller) {
    sampler.controller.abort();
    say("warn", "نمونه‌گیری متوقف شد");
  }
  res.json({ ok: true });
});

/** Takes the voice and the pace that were just listened to and makes them the settings. */
app.post("/api/diag/sample/pick", (req, res) => {
  const speaker = normalizeSpeaker(req.body?.speaker);
  const speed = speedOf(req.body?.speed, config.speed);
  config.speaker = speaker;
  config.speed = speed;
  saveConfig();
  const voice = VOICES.find((v) => v.name === speaker);
  say("ok", `گوینده‌ی درس‌ها: ${voice ? voice.label : speaker} (${speaker})، سرعتِ ${speed}`);
  res.json(publicConfig());
});

// ── fixing the words of one line ─────────────────────────────────────────────
// The manifest is generated from the lesson sources, so an edit typed here would be lost the
// next time it is rebuilt. It is therefore kept in tools/voice-overrides.json — which goes into
// git — and the manifest line is patched straight away so the change takes effect now.
const OVERRIDES = path.resolve(ROOT, "../voice-overrides.json");

function readOverrides() {
  try {
    return JSON.parse(fs.readFileSync(OVERRIDES, "utf8"));
  } catch {
    return {};
  }
}

function patchManifestLine(key, spoken) {
  const text = fs.readFileSync(config.manifestPath, "utf8");
  const lines = text.split(/\r?\n/);
  let hit = false;
  for (let i = 0; i < lines.length; i++) {
    if (!lines[i].startsWith(key + " |")) continue;
    const cols = lines[i].split("|").map((c) => c.trim());
    lines[i] = `${cols[0]} | ${cols[1]} | ${spoken}`;
    hit = true;
    break;
  }
  if (!hit) return false;
  fs.writeFileSync(config.manifestPath, lines.join("\n"), "utf8");
  return true;
}

app.get("/api/overrides", (_req, res) => res.json({ file: OVERRIDES, overrides: readOverrides() }));

app.post("/api/text", (req, res) => {
  const key = String(req.body?.key || "").trim();
  const spoken = String(req.body?.spoken || "").trim();
  if (!key || !spoken) return res.status(400).json({ error: "کلید و متن لازم است" });
  if (spoken.includes("|")) return res.status(400).json({ error: "متن نباید | داشته باشد" });

  try {
    const overrides = readOverrides();
    overrides[key] = spoken;
    fs.writeFileSync(OVERRIDES, JSON.stringify(overrides, null, 2), "utf8");
    const patched = patchManifestLine(key, spoken);
    say("ok", `متنِ ${key} عوض شد${patched ? "" : " (در منیفست پیدا نشد)"} — در voice-overrides.json ماند`);
    res.json({ ok: true, patched, file: OVERRIDES });
  } catch (err) {
    say("error", `متن ذخیره نشد: ${err.message}`);
    res.status(400).json({ error: err.message });
  }
});

app.delete("/api/text/:key", (req, res) => {
  const overrides = readOverrides();
  delete overrides[req.params.key];
  fs.writeFileSync(OVERRIDES, JSON.stringify(overrides, null, 2), "utf8");
  say("warn", `اصلاحِ متنِ ${req.params.key} برداشته شد — با make_manifest.py به متنِ درس برمی‌گردد`);
  res.json({ ok: true });
});

// ── sending the finished files to the host ───────────────────────────────────
const upload = { running: false, stopping: false, what: "", done: 0, total: 0,
                 sent: 0, skipped: 0, failed: 0 };

// Where the connection got to, and the handshake itself. The page shows both: an FTP that
// says nothing is indistinguishable from an FTP that is broken.
const link = { state: "", phase: "", note: "", lines: [], at: 0, info: null, error: "",
               warning: "" };

function uploadSnapshot() {
  return { ...upload, link };
}

/** Records a phase, both in the log and in the panel. */
function ftpPhase(name, note = "") {
  const words = {
    connect: "در حالِ اتصال به", login: "وارد شد با کاربرِ", pwd: "پوشه‌ی ورود:",
    dir: "پوشه‌ی مقصد:", list: "فهرستِ پوشه:",
  };
  link.phase = name;
  link.note = note;
  link.state = name === "connect" ? "در حالِ اتصال" : "وصل است";
  say("info", `FTP · ${words[name] || name} ${note}`);
  push("upload", uploadSnapshot());
}

/** One line of the conversation on the wire. The password never reaches here. */
function ftpLine(text) {
  link.lines = [...link.lines.slice(-60), text];
  push("upload", uploadSnapshot());
}

/**
 * The cPanel trap, in both shapes it takes.
 *
 * Some hosts log you in above public_html, some drop you straight inside it. A path that
 * starts with /public_html is right for the first and wrong for the second — and when it is
 * wrong nothing fails: the files go to a folder that really exists, just not the one the web
 * server serves, and the app never finds them. So the login folder is read and compared.
 */
function pathTrap(info) {
  const head = String(info.remoteDir || "").split("/").filter(Boolean)[0];
  if (!head) return "";
  const landed = String(info.loginDir || "").replace(/\/+$/, "");
  const names = info.loginNames || [];

  if (landed.endsWith(`/${head}`)) {
    return `پوشه‌ی ورودِ تو خودش «${head}» است و مسیرِ مقصد هم با «${head}» شروع می‌شود — `
      + `فایل‌ها در ${landed}/${head}/… می‌افتند. «${head}/» را از ابتدای مسیر بردار.`;
  }
  if (names.length && !names.some((n) => n.replace(/\/$/, "") === head)) {
    return `در پوشه‌ی ورود چیزی به نامِ «${head}» نیست (اینجا هست: ${names.slice(0, 6).join("، ")})`
      + ` — یعنی یا همین حالا داخلِ آن هستی یا نامش چیزِ دیگری است. این مسیر یک پوشه‌ی تازه`
      + ` می‌سازد و اپ فایل‌ها را پیدا نمی‌کند.`;
  }
  return "";
}

function ftpFailed(err) {
  const hint = ftpHint(err, config.ftp);
  link.state = "وصل نشد";
  link.error = hint ? `${err.message} — ${hint}` : err.message;
  say("error", `FTP · ${link.error}`);
  push("upload", uploadSnapshot());
  return link.error;
}

function startLink(what) {
  Object.assign(link, { state: "در حالِ اتصال", phase: "", note: "", lines: [],
                        at: Date.now(), info: null, error: "", warning: "" });
  const { host, port, user, secure, audioDir, karbargDir } = config.ftp;
  say("info", `FTP · ${what} — ${host}:${port || 21}، کاربر ${user}`
    + `${secure ? "، FTPS" : ""} → ${what.includes("کاربرگ") ? karbargDir : audioDir}`);
  push("upload", uploadSnapshot());
}

/**
 * The settings have to be there before anything is attempted — and the refusal has to be
 * visible. It used to answer the request with an error and write nothing in the log, so from
 * the page it looked as though the button did nothing at all.
 */
function requireFtp(res) {
  const { host, user, password } = config.ftp;
  const missing = [!host && "آدرسِ سرور", !user && "نام کاربری", !password && "رمز"].filter(Boolean);
  if (missing.length) {
    const message = `تنظیماتِ FTP کامل نیست: ${missing.join(" و ")} داده نشده`
      + " — پُرش کن و «ذخیره‌ی تنظیماتِ FTP» را بزن";
    link.state = "تنظیمات ناقص";
    link.error = message;
    say("error", `FTP · ${message}`);
    push("upload", uploadSnapshot());
    res.status(400).json({ error: message });
    return false;
  }
  return true;
}

app.post("/api/ftp/test", async (req, res) => {
  if (!requireFtp(res)) return;
  const which = req.body?.which === "karbarg" ? "karbargDir" : "audioDir";
  const what = which === "karbargDir" ? "تستِ اتصال (کاربرگ‌ها)" : "تستِ اتصال (صداها)";

  // the answer goes back now; the handshake arrives on the stream. A connect can take twenty
  // seconds, and twenty seconds of a page saying nothing is what «کار نمی‌کند» looked like.
  startLink(what);
  res.json({ started: true });

  try {
    const info = await probe(config.ftp, config.ftp[which],
                             { onLine: ftpLine, onPhase: ftpPhase });
    link.state = "وصل است";
    link.info = info;

    link.warning = pathTrap(info);
    if (link.warning) say("warn", `FTP · ${link.warning}`);
    push("upload", uploadSnapshot());
    say("ok", `FTP وصل شد · پوشه‌ی ورود ${info.loginDir} · ${info.remoteDir}: `
      + (info.remoteExists ? `${info.remoteCount} فایل` : "هنوز ساخته نشده (موقع آپلود ساخته می‌شود)"));
  } catch (err) {
    ftpFailed(err);
  }
});

app.post("/api/ftp/upload/audio", async (req, res) => {
  if (!requireFtp(res)) return;
  if (upload.running) return res.status(409).json({ error: "یک آپلود در حال اجراست" });

  const skipExisting = req.body?.all !== true;      // «همه را دوباره بفرست» خاموشش می‌کند
  const files = audioFilesIn(config.outDir);
  if (!files.length) return res.status(400).json({ error: "در پوشه‌ی خروجی کلیپی نیست" });

  // the index must describe what is actually there, so it is rebuilt and sent last
  const index = writeAudioIndex({
    outDir: config.outDir, baseUrl: config.audioBase, speaker: config.speaker,
    appAssets: path.resolve(ROOT, "../../app/src/main/assets/voice-index.json"),
  });

  Object.assign(upload, { running: true, stopping: false, what: "صداها",
                          done: 0, total: files.length + 1, sent: 0, skipped: 0, failed: 0 });
  startLink("آپلودِ صداها");
  res.json({ started: true, total: files.length });
  say("info", `${files.length} فایل`
    + (skipExisting ? " (آنچه روی سرور هست رد می‌شود)" : " (همه دوباره)"));

  try {
    const outcome = await uploadFiles({
      settings: config.ftp, remoteDir: config.ftp.audioDir, files, skipExisting,
      onLine: ftpLine, onPhase: ftpPhase,
      shouldStop: () => upload.stopping,
      onStep: (name, state) => {
        upload.done += 1;
        if (state === "sent") upload.sent += 1;
        else if (state === "skipped") upload.skipped += 1;
        else upload.failed += 1;
        if (state !== "skipped" || upload.done % 50 === 0) {
          say(state === "failed" ? "error" : "ok",
              `${upload.done}/${upload.total} ${name} — ${state === "sent" ? "فرستاده شد"
                : state === "skipped" ? "از قبل بود" : "نرفت"}`);
        }
        push("upload", uploadSnapshot());
      },
    });

    if (!outcome.stopped) {
      await uploadFiles({
        settings: config.ftp, remoteDir: config.ftp.audioDir,
        files: [path.join(config.outDir, "index.json")], skipExisting: false,
        onStep: () => { upload.done += 1; upload.sent += 1; push("upload", uploadSnapshot()); },
      });
    }

    say("info", (outcome.stopped ? "آپلود متوقف شد — " : "آپلود تمام شد — ")
      + `${outcome.sent} فرستاده، ${outcome.skipped} از قبل بود، ${outcome.failed} ناموفق.`
      + (outcome.stopped ? " فهرست فرستاده نشد؛ دوباره که زدی از همان‌جا ادامه می‌دهد."
                         : ` فهرست (${index.count} کلیپ) هم رفت.`));
    for (const line of outcome.errors.slice(0, 10)) say("error", line);
  } catch (err) {
    ftpFailed(err);                          // the reason, and what to change, in one line
  } finally {
    upload.running = false;
    push("upload", uploadSnapshot());
  }
});

app.post("/api/ftp/upload/karbarg", async (req, res) => {
  if (!requireFtp(res)) return;
  if (upload.running) return res.status(409).json({ error: "یک آپلود در حال اجراست" });

  try {
    writeWorksheetIndex({ dir: config.karbargDir, baseUrl: config.karbargBase });
  } catch (err) {
    return res.status(400).json({ error: err.message });
  }

  Object.assign(upload, { running: true, stopping: false, what: "کاربرگ‌ها", done: 0, total: 0,
                          sent: 0, skipped: 0, failed: 0 });
  startLink("آپلودِ کاربرگ‌ها");
  res.json({ started: true });
  say("info", "پوشه‌ی سرور با اینجا یکی می‌شود");

  try {
    await uploadTree({
      settings: config.ftp, remoteDir: config.ftp.karbargDir, localDir: config.karbargDir,
      onLine: ftpLine, onPhase: ftpPhase,
      onStep: (name) => {
        upload.sent += 1;
        upload.done += 1;
        push("upload", uploadSnapshot());
      },
    });
    say("ok", "کاربرگ‌ها آپلود شدند");
  } catch (err) {
    ftpFailed(err);
  } finally {
    upload.running = false;
    push("upload", uploadSnapshot());
  }
});

app.get("/api/ftp/status", (_req, res) => res.json(uploadSnapshot()));

app.post("/api/ftp/stop", (_req, res) => {
  if (upload.running) {
    upload.stopping = true;
    link.state = "در حالِ ایستادن";
    say("warn", "درخواستِ توقفِ آپلود — فایلِ در جریان تمام می‌شود و بعد می‌ایستد");
    push("upload", uploadSnapshot());
  }
  res.json(uploadSnapshot());
});

app.get("/api/karbarg", (_req, res) => {
  try {
    res.json({ dir: config.karbargDir, baseUrl: config.karbargBase,
               sheets: listWorksheets(config.karbargDir) });
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post("/api/karbarg", (req, res) => {
  try {
    const { title, note, chapter, fileName, dataBase64 } = req.body || {};
    const data = Buffer.from(String(dataBase64 || "").split(",").pop(), "base64");
    const sheet = addWorksheet({ dir: config.karbargDir, title, note, chapter, fileName, data });
    writeWorksheetIndex({ dir: config.karbargDir, baseUrl: config.karbargBase });
    say("ok", `کاربرگ اضافه شد: ${sheet.title} → ${sheet.slug}/${sheet.file}`);
    res.json(sheet);
  } catch (err) {
    say("error", `کاربرگ اضافه نشد: ${err.message}`);
    res.status(400).json({ error: err.message });
  }
});

app.delete("/api/karbarg/:slug", (req, res) => {
  const gone = removeWorksheet(config.karbargDir, req.params.slug);
  if (gone) {
    writeWorksheetIndex({ dir: config.karbargDir, baseUrl: config.karbargBase });
    say("warn", `کاربرگ پاک شد: ${req.params.slug}`);
  }
  res.json({ ok: gone });
});

app.post("/api/publish/karbarg", (_req, res) => {
  try {
    const index = writeWorksheetIndex({ dir: config.karbargDir, baseUrl: config.karbargBase });
    say("ok", `فهرستِ کاربرگ ساخته شد: ${index.count} کاربرگ`);
    res.json(index);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.post("/api/generate", async (req, res) => {
  const keys = Array.isArray(req.body?.keys) ? req.body.keys : [];
  const picked = keys.length ? keys : missingKeys();
  const limit = Number(req.body?.limit);
  const batch = Number.isFinite(limit) && limit > 0 ? picked.slice(0, limit) : picked;
  const outcome = startJob(batch, { force: req.body?.force === true });
  if (outcome.error) return res.status(409).json(outcome);
  res.json({ ok: true, started: batch.length });
});

app.post("/api/stop", (_req, res) => {
  if (job.running) {
    job.stopping = true;
    // the test tab can be stopped mid-request; so can this one now
    if (job.controller) job.controller.abort();
    say("warn", "درخواستِ ایستادن");
  }
  res.json(snapshot());
});

app.post("/api/test", async (req, res) => {
  if (!config.token) return res.status(400).json({ error: "توکن خالی است" });
  const text = String(req.body?.text || "سلام! من هوهو هستم، مُعَلِّمِ عُلومِ تو.");
  try {
    const { buffer, ext, raw } = await speak({
      token: config.token, text, speaker: config.speaker,
      speed: Number(config.speed) || 1, endpoint: config.endpoint,
      onNote: (note) => say("info", `تست: ${note}`),
    });
    fs.mkdirSync(config.outDir, { recursive: true });
    const name = `_test-${config.speaker}${ext}`;
    fs.writeFileSync(path.join(config.outDir, name), buffer);
    say("ok", `تستِ صدا ساخته شد: ${name} (${Math.round(buffer.length / 1024)} کیلوبایت)`);
    res.json({ ok: true, file: name, bytes: buffer.length, shape: raw ? JSON.stringify(raw).slice(0, 400) : null });
  } catch (err) {
    say("error", `تستِ صدا نشد: ${err.message}`);
    res.status(502).json({ error: err.message });
  }
});

// play a produced file back in the browser
app.get("/api/audio/:key", (req, res) => {
  const have = existingKeys(config.outDir);
  const file = have.get(req.params.key);
  if (!file) return res.status(404).end();
  res.sendFile(path.join(config.outDir, file.name));
});

app.delete("/api/audio/:key", (req, res) => {
  const have = existingKeys(config.outDir);
  const file = have.get(req.params.key);
  if (!file) return res.status(404).end();
  fs.unlinkSync(path.join(config.outDir, file.name));
  say("warn", `پاک شد: ${file.name}`);
  res.json({ ok: true });
});

app.get("/api/events", (req, res) => {
  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
  });
  res.write(`data: ${JSON.stringify({ type: "status", ...snapshot() })}\n\n`);
  listeners.add(res);
  req.on("close", () => listeners.delete(res));
});

const dist = path.join(ROOT, "dist");
if (fs.existsSync(dist)) {
  app.use(express.static(dist));
  app.get("*", (_req, res) => res.sendFile(path.join(dist, "index.html")));
}

app.listen(PORT, "127.0.0.1", () => {
  console.log(`\n  استودیوی صداگذاری هوهو → http://127.0.0.1:${PORT}\n`);
  console.log(`  منیفست : ${config.manifestPath}`);
  console.log(`  خروجی  : ${config.outDir}`);
  console.log(`  صدا    : ${config.speaker}`);
  console.log(`  سرویس  : ${config.endpoint === "long" ? "avasho-large" : "avasho (متنِ کوتاه)"}\n`);
  if (migrated) {
    say("warn", migrated);
    saveConfig();
  }
  if (config.schedule.enabled) applySchedule();
});
