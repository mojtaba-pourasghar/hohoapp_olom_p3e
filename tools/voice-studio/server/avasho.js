// The آواشو (Sahab / پارت) text-to-speech service.
//
// It works in three steps, and the first reply is never the audio:
//
//   1. POST …/request            → a receipt: an id, and sometimes the finished file name
//   2. GET  …/track/{id}         → whether it is done yet  (long text)
//   3. GET  …/download/{id}      → the file itself
//
// A short line often comes back «success» on the first reply with the file name already in it,
// and then only step 3 is needed. A long one comes back «pending» and has to be tracked.
//
// One trap worth naming: the reply carries two statuses. The real one is data.status; the
// meta.status at the bottom is masked in the published examples and reads "fail" even on a
// successful call. Reading that one would make every request look broken.

const HOST = "https://partai.gw.isahab.ir/avasho/v2";

export const FAMILIES = {
  short: {
    request: `${HOST}/avasho/request`,
    track: `${HOST}/avasho/track`,
    download: `${HOST}/avasho/download`,
  },
  long: {
    request: `${HOST}/avasho-large/request`,
    track: `${HOST}/avasho-large/track`,
    download: `${HOST}/avasho-large/download`,
  },
};

/** Kept for anything still importing the old name. */
export const ENDPOINTS = { short: FAMILIES.short.request, long: FAMILIES.long.request };

/**
 * Every voice the service offers, with the Persian name shown in the studio.
 *
 * هوهو is a female teacher, so the lessons are recorded with one of the female voices — but
 * the male ones are here too, for a second character or for whoever wants to listen first.
 */
export const VOICES = [
  { name: "sara",      gender: "female", label: "سارا" },
  { name: "pune",      gender: "female", label: "پونه" },
  { name: "bahar",     gender: "female", label: "بهار" },
  { name: "shahrzad",  gender: "female", label: "شهرزاد" },
  { name: "sheyda",    gender: "female", label: "شیدا" },
  { name: "shirin",    gender: "female", label: "شیرین" },
  { name: "kiani",     gender: "male",   label: "کیانی" },
  { name: "nourai",    gender: "male",   label: "نورایی" },
  { name: "dara",      gender: "male",   label: "دارا" },
  { name: "parviz",    gender: "male",   label: "پرویز" },
  { name: "bahman",    gender: "male",   label: "بهمن" },
  { name: "farhad",    gender: "male",   label: "فرهاد" },
  { name: "shahriyar", gender: "male",   label: "شهریار" },
  { name: "ariya",     gender: "male",   label: "آریا" },
];

export const SPEAKERS = VOICES.map((v) => v.name);

const AUDIO_EXT = { "audio/mpeg": ".mp3", "audio/mp3": ".mp3", "audio/wav": ".wav",
                    "audio/x-wav": ".wav", "audio/ogg": ".ogg", "audio/wave": ".wav" };

export class RateLimited extends Error {}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Node hides the real reason behind a bare «fetch failed». The cause is where the useful part
 * is — a refused proxy, a name that does not resolve, a closed port — and that is what has to
 * reach the log, or every network problem looks identical.
 */
function why(err) {
  const parts = [err?.message || String(err)];
  for (let cause = err?.cause; cause; cause = cause.cause) {
    const line = cause.code ? `${cause.code} ${cause.message || ""}`.trim() : cause.message;
    if (line && !parts.includes(line)) parts.push(line);
  }
  return parts.join(" — ");
}

/** Sniff the container from the first bytes — steadier than trusting a content-type. */
function extFromBytes(buf) {
  if (!buf || buf.length < 4) return null;
  const head = buf.subarray(0, 4).toString("latin1");
  if (head === "OggS") return ".ogg";
  if (head === "RIFF") return ".wav";
  if (head.startsWith("ID3")) return ".mp3";
  if (buf[0] === 0xff && (buf[1] & 0xe0) === 0xe0) return ".mp3";
  return null;
}

function audioFromBody(bytes, contentType) {
  const sniffed = extFromBytes(bytes);
  const byHeader = AUDIO_EXT[(contentType || "").split(";")[0].trim()];
  return sniffed || byHeader ? { buffer: bytes, ext: sniffed || byHeader } : null;
}

/** Any link to an audio file, however deep in the reply. */
function findAudioUrl(value, depth = 0) {
  if (depth > 8) return null;
  if (typeof value === "string") {
    return /^https?:\/\//.test(value) && /\.(mp3|wav|ogg)(\?|$)/i.test(value) ? value : null;
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      const hit = findAudioUrl(item, depth + 1);
      if (hit) return hit;
    }
    return null;
  }
  if (value && typeof value === "object") {
    for (const item of Object.values(value)) {
      const hit = findAudioUrl(item, depth + 1);
      if (hit) return hit;
    }
  }
  return null;
}

/** A long base64 blob in the reply is almost certainly the audio. */
function findBase64(value, depth = 0) {
  if (depth > 6) return null;
  if (typeof value === "string") {
    return value.length > 2000 && /^[A-Za-z0-9+/=\s]+$/.test(value) ? value : null;
  }
  if (value && typeof value === "object") {
    for (const item of Object.values(value)) {
      const hit = findBase64(item, depth + 1);
      if (hit) return hit;
    }
  }
  return null;
}

/** What the service is telling us, out of either reply shape. */
function readReply(payload) {
  const outer = payload?.data ?? {};
  const inner = outer?.data ?? {};
  const result = inner?.aiResponse?.result ?? {};
  const seconds = Number(inner?.estimationTime);
  return {
    status: String(outer?.status ?? "").toLowerCase(),
    message: String(outer?.message ?? ""),
    id: inner?.id ? String(inner.id) : null,
    filename: result?.filename ? String(result.filename) : null,
    timestamps: Array.isArray(result?.timestamps) ? result.timestamps : null,
    waitMs: Math.max(700, (Number.isFinite(seconds) ? seconds : 2) * 1000),
  };
}

/**
 * One GET, timed and reported.
 *
 * `onHttp` gets the method, the address, the status, how long it took and the first part of the
 * body — which is what the diagnostics tab shows, and what makes «it does not work» into
 * something readable.
 */
async function getJson(url, token, { onHttp, signal, name = "GET" } = {}) {
  const started = Date.now();
  let response;
  try {
    response = await fetch(url, {
      method: "GET",
      signal,
      headers: { "gateway-token": token, accept: "application/json" },
    });
  } catch (err) {
    const note = why(err);
    if (onHttp) onHttp({ name, method: "GET", url, status: 0, ms: Date.now() - started, body: note });
    throw new Error(note);
  }
  const bytes = Buffer.from(await response.arrayBuffer());
  const type = response.headers.get("content-type") || "";
  if (onHttp) {
    onHttp({
      name, method: "GET", url, status: response.status, ms: Date.now() - started, type,
      bytes: bytes.length,
      body: type.includes("json") || bytes.length < 2000
        ? bytes.toString("utf8").slice(0, 1200) : `«${bytes.length} بایتِ دودویی»`,
    });
  }
  if (response.status === 429) throw new RateLimited("سهمیه پر شد (۴۲۹)");
  return { status: response.status, bytes, type };
}

/**
 * Collects the finished file.
 *
 * The documentation gives «download/{id}» without saying whether {id} is the request id or the
 * returned file name, so both are tried — there are only two handles and both are in hand. The
 * one that works is remembered for the rest of the run and named in the log.
 */
let learnedHandle = null;          // "id" or "filename"

export function handleInUse() {
  return learnedHandle;
}

async function download({ token, family, id, filename, onNote, onHttp, signal }) {
  const handles = [];
  const add = (kind, value) => {
    if (value) handles.push({ kind, value });
  };
  if (learnedHandle === "filename") {
    add("filename", filename);
    add("id", id);
  } else {
    add("id", id);
    add("filename", filename);
  }
  if (filename) add("filename-stem", filename.replace(/\.[^.]+$/, ""));

  let lastNote = "";
  for (const handle of handles) {
    const url = `${family.download}/${encodeURIComponent(handle.value)}`;
    let reply;
    try {
      reply = await getJson(url, token, { onHttp, signal, name: `download (${handle.kind})` });
    } catch (err) {
      if (err instanceof RateLimited) throw err;
      lastNote = err.message;
      continue;
    }
    if (reply.status === 404 || reply.status === 400) {
      lastNote = `download ${reply.status} با ${handle.kind}`;
      continue;
    }
    if (reply.status !== 200) {
      lastNote = `download ${reply.status}: ${reply.bytes.toString("utf8").slice(0, 160)}`;
      continue;
    }

    const direct = audioFromBody(reply.bytes, reply.type);
    if (direct) {
      if (learnedHandle !== handle.kind) {
        learnedHandle = handle.kind;
        if (onNote) onNote(`فایل با ${handle.kind} دانلود شد`);
      }
      return direct;
    }

    // not the bytes themselves — then a link or a base64 blob inside JSON
    try {
      const payload = JSON.parse(reply.bytes.toString("utf8"));
      const link = findAudioUrl(payload);
      if (link) {
        const file = await fetch(link);
        if (!file.ok) throw new Error(`دانلودِ فایل نشد: HTTP ${file.status}`);
        const data = Buffer.from(await file.arrayBuffer());
        learnedHandle = handle.kind;
        if (onNote) onNote(`فایل از لینکِ داخلِ پاسخ گرفته شد`);
        return { buffer: data, ext: extFromBytes(data) || ".mp3" };
      }
      const b64 = findBase64(payload);
      if (b64) {
        const data = Buffer.from(b64.replace(/\s/g, ""), "base64");
        learnedHandle = handle.kind;
        return { buffer: data, ext: extFromBytes(data) || ".mp3" };
      }
      lastNote = `پاسخِ download شناخته نشد: ${JSON.stringify(payload).slice(0, 200)}`;
    } catch (err) {
      lastNote = err.message;
    }
  }
  throw new Error(lastNote || "فایل دانلود نشد");
}

/** Asks «is it ready?» until it is. */
async function track({ token, family, id, waitMs, tries = 40, onNote, onHttp, signal }) {
  await sleep(waitMs);
  for (let attempt = 1; attempt <= tries; attempt++) {
    let reply;
    try {
      reply = await getJson(`${family.track}/${encodeURIComponent(id)}`, token,
                            { onHttp, signal, name: `track #${attempt}` });
    } catch (err) {
      if (err instanceof RateLimited) throw err;
      reply = null;
    }

    if (reply && reply.status === 200) {
      try {
        const info = readReply(JSON.parse(reply.bytes.toString("utf8")));
        if (info.status === "success" || info.filename) return info;
        if (info.status && !["pending", "processing", "inprogress", "queued"].includes(info.status)) {
          throw new Error(`سرویس خطا داد (${info.status}): ${info.message}`);
        }
      } catch (err) {
        if (!(err instanceof SyntaxError)) throw err;
      }
    } else if (attempt === 1 && onNote) {
      onNote(reply ? `track ${reply.status} — به‌جایش مستقیم download امتحان می‌شود`
                   : "track در دسترس نبود — مستقیم download");
    }

    // no tracking for this family (or it is not answering): the file may simply be ready
    if (!reply || reply.status === 404 || reply.status === 405) {
      return { status: "unknown", id, filename: null, timestamps: null };
    }

    if (onNote && attempt % 10 === 0) onNote(`هنوز آماده نیست — تلاشِ ${attempt}`);
    await sleep(Math.min(5000, 900 + attempt * 250));
  }
  throw new Error("نتیجه در زمانِ معقول آماده نشد");
}

/**
 * Records one line and hands back { buffer, ext, raw, timestamps }.
 *
 * `raw` is the receipt, kept so the studio can print the shape once. `onNote` is for the log
 * while the service is thinking. Throws RateLimited on 429 so the queue can back off.
 */
export async function speak({ token, text, speaker, speed = 1, endpoint = "short",
                              timestamps = false, timeout = 180000, onNote = null,
                              onHttp = null, signal = null }) {
  const family = FAMILIES[endpoint] || FAMILIES.short;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  if (signal) signal.addEventListener("abort", () => controller.abort(), { once: true });
  const pass = { onNote, onHttp, signal: controller.signal };

  const started = Date.now();
  let response;
  try {
    response = await fetch(family.request, {
      method: "POST",
      signal: controller.signal,
      headers: {
        "gateway-token": token,
        accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ text, speaker, speed, timestamp: Boolean(timestamps) }),
    });
  } catch (err) {
    const note = `${family.request} — ${why(err)}`;
    if (onHttp) onHttp({ name: "request", method: "POST", url: family.request, status: 0,
                         ms: Date.now() - started, body: note });
    throw new Error(note);
  } finally {
    clearTimeout(timer);
  }

  const bytes = Buffer.from(await response.arrayBuffer());
  if (onHttp) {
    onHttp({
      name: "request", method: "POST", url: family.request, status: response.status,
      ms: Date.now() - started, bytes: bytes.length,
      type: response.headers.get("content-type") || "",
      body: bytes.toString("utf8").slice(0, 1600),
    });
  }
  if (response.status === 429) {
    throw new RateLimited(`سهمیه پر شد (۴۲۹): ${bytes.toString("utf8").slice(0, 200)}`);
  }
  if (response.status !== 200 && response.status !== 201) {
    throw new Error(`HTTP ${response.status} — ${bytes.toString("utf8").slice(0, 300)}`);
  }

  // just in case a future version answers with the file outright
  const immediate = audioFromBody(bytes, response.headers.get("content-type"));
  if (immediate) return { ...immediate, raw: null, timestamps: null };

  let payload;
  try {
    payload = JSON.parse(bytes.toString("utf8"));
  } catch {
    throw new Error(`پاسخِ ناشناخته: ${bytes.toString("utf8").slice(0, 300)}`);
  }

  let info = readReply(payload);
  if (!info.id && !info.filename) {
    throw new Error(`در پاسخ نه شناسه بود نه نامِ فایل: ${JSON.stringify(payload).slice(0, 300)}`);
  }

  // «pending» means it is still being made; «success» means only the download is left
  if (info.status && info.status !== "success" && !info.filename) {
    if (onNote) onNote(info.message || "در حال پردازش…");
    const tracked = await track({ token, family, id: info.id, waitMs: info.waitMs, ...pass });
    info = { ...info, ...tracked, id: info.id };
  }

  const file = await download({ token, family, id: info.id, filename: info.filename, ...pass });
  return { ...file, raw: payload, timestamps: info.timestamps };
}
