// Putting the finished files on the host.
//
// The clips and the worksheets are made here and have to end up in /public_html/grade-3/olom/…
// on the teacher's own server. This does that over FTP: it connects, makes the folder if it is
// not there, and sends the files — skipping the ones already on the server with the same size,
// so a second run only sends what is new. Four thousand clips is not something to upload twice.
//
// The password is only ever read from the studio's own config file on this machine and is never
// written to the log.

import fs from "node:fs";
import path from "node:path";
import { Client } from "basic-ftp";

const AUDIO_EXT = [".ogg", ".mp3", ".wav"];

/**
 * What an FTP failure usually means, in words the person can act on.
 *
 * «وصل نشد» on its own sends someone hunting through settings. The code at the bottom of the
 * error says which setting, so it is translated here.
 */
export function ftpHint(err, { host, port, secure }) {
  const code = err?.code || "";
  const text = `${code} ${err?.message || ""}`;
  if (code === "ENOTFOUND" || code === "EAI_AGAIN") {
    return `آدرسِ «${host}» پیدا نشد — نامِ سرور را چک کن (معمولاً ftp.<دامنه> یا خودِ دامنه).`;
  }
  if (code === "ECONNREFUSED") return `پورتِ ${port} روی ${host} بسته است — پورت را چک کن.`;
  if (code === "ETIMEDOUT" || code === "ERR_SOCKET_CONNECTION_TIMEOUT" || /timeout/i.test(text)) {
    return `تا ${host}:${port} جوابی نیامد — فایروال یا آنتی‌ویروسِ خودت، یا پورتِ اشتباه.`;
  }
  if (code === "ECONNRESET" || code === "EPIPE") {
    return secure ? "اتصال قطع شد — شاید هاست FTPS نمی‌خواهد؛ تیکِ FTPS را بردار."
                  : "اتصال قطع شد — شاید هاست FTPS می‌خواهد؛ تیکِ FTPS را بزن.";
  }
  if (/\b530\b/.test(text)) return "نامِ کاربری یا رمز را قبول نکرد (۵۳۰).";
  if (/\b550\b/.test(text)) return "به آن پوشه اجازه نداد (۵۵۰) — مسیر را چک کن.";
  if (/\b425\b|\b421\b|passive/i.test(text)) {
    return "کانالِ داده باز نشد (passive) — هاست باید حالتِ passive را اجازه بدهد.";
  }
  if (/self.signed|certificate/i.test(text)) return "گواهیِ TLS مشکل دارد — تیکِ FTPS را بردار.";
  return "";
}

/**
 * One client, with the conversation on the wire reported line by line.
 *
 * `onLine` gets every command and reply — this is what turns «هیچ لاگی نمی‌زند» into a visible
 * handshake. basic-ftp masks the PASS command itself; the password is scrubbed again here, so
 * it cannot reach the log by any route.
 *
 * `connect` races the handshake against a timeout of our own. Without it a blocked port leaves
 * the page waiting with nothing on screen, which is exactly how this looked broken.
 */
function clientFor({ host, port, user, password, secure }, { onLine = null, connectMs = 20000 } = {}) {
  const client = new Client(60000);
  const clean = (text) => {
    const line = String(text);
    return password ? line.split(String(password)).join("###") : line;
  };
  if (onLine) {
    client.ftp.verbose = true;
    client.ftp.log = (message) => onLine(clean(message).trim().slice(0, 300));
  } else {
    client.ftp.verbose = false;
  }

  const where = `${String(host || "").trim()}:${Number(port) || 21}`;
  const connect = async () => {
    let timer;
    const expired = new Promise((_, reject) => {
      timer = setTimeout(() => {
        const err = new Error(`تا ${connectMs / 1000} ثانیه از ${where} جوابی نیامد`);
        err.code = "ERR_SOCKET_CONNECTION_TIMEOUT";
        reject(err);
      }, connectMs);
    });
    try {
      return await Promise.race([
        client.access({
          host: String(host || "").trim(),
          port: Number(port) || 21,
          user: String(user || "").trim(),
          password: String(password || ""),
          secure: Boolean(secure),             // explicit FTPS when the host wants it
          secureOptions: { rejectUnauthorized: false },
        }),
        expired,
      ]);
    } finally {
      clearTimeout(timer);
    }
  };
  return { client, connect, where };
}

/**
 * Connect, look at the folder, and say what is there. Nothing is written.
 *
 * `onPhase(name, note)` marks each step — connecting, logged in, listing — so the page can
 * show how far it got instead of a spinner that means nothing.
 */
export async function probe(settings, remoteDir, { onLine = null, onPhase = () => {} } = {}) {
  const { client, connect, where } = clientFor(settings, { onLine });
  try {
    onPhase("connect", where);
    await connect();
    onPhase("login", String(settings.user || "").trim());
    const root = await client.pwd();
    onPhase("pwd", root);

    // what the login folder holds. On a chrooted host pwd is just «/» and tells you nothing,
    // but the names here say whether you are above public_html or already inside it.
    let loginNames = [];
    try {
      loginNames = (await client.list()).map((f) => (f.isDirectory ? `${f.name}/` : f.name));
      onPhase("home", loginNames.slice(0, 10).join("، ") || "(خالی)");
    } catch {
      // not fatal: the folder listing is a hint, not a requirement
    }

    let files = [];
    let exists = true;
    try {
      files = await client.list(remoteDir);
      onPhase("list", `${files.filter((f) => f.isFile).length} فایل`);
    } catch (err) {
      exists = false;
      onPhase("list", `پوشه نیست — ${err.message}`);
    }
    return {
      ok: true,
      loginDir: root,
      remoteDir,
      remoteExists: exists,
      remoteCount: files.filter((f) => f.isFile).length,
      names: files.filter((f) => f.isFile).slice(0, 8).map((f) => f.name),
      loginNames,
    };
  } finally {
    client.close();
  }
}

/**
 * Sends a list of local files into one remote folder.
 *
 * `onStep` is called with (name, state) as it goes — "sent", "skipped" or "failed" — so the
 * studio's log reads like the upload is happening, which with four thousand files it is.
 */
export async function uploadFiles({ settings, remoteDir, files, skipExisting = true, onStep,
                                   onLine = null, onPhase = () => {},
                                   shouldStop = () => false }) {
  const { client, connect, where } = clientFor(settings, { onLine });
  const result = { sent: 0, skipped: 0, failed: 0, bytes: 0, errors: [], stopped: false };
  try {
    onPhase("connect", where);
    await connect();
    onPhase("login", String(settings.user || "").trim());
    await client.ensureDir(remoteDir);       // creates the whole path and moves into it
    onPhase("dir", await client.pwd());

    let onServer = new Map();
    if (skipExisting) {
      try {
        for (const entry of await client.list()) {
          if (entry.isFile) onServer.set(entry.name, entry.size);
        }
      } catch {
        // an unreadable folder just means nothing can be skipped
      }
    }

    for (const local of files) {
      if (shouldStop()) {
        result.stopped = true;
        break;
      }
      const name = path.basename(local);
      let size = 0;
      try {
        size = fs.statSync(local).size;
      } catch {
        result.failed += 1;
        result.errors.push(`${name}: فایل نیست`);
        if (onStep) onStep(name, "failed");
        continue;
      }

      if (skipExisting && onServer.get(name) === size) {
        result.skipped += 1;
        if (onStep) onStep(name, "skipped");
        continue;
      }

      try {
        await client.uploadFrom(local, name);
        result.sent += 1;
        result.bytes += size;
        if (onStep) onStep(name, "sent");
      } catch (err) {
        result.failed += 1;
        result.errors.push(`${name}: ${err.message}`);
        if (onStep) onStep(name, "failed");
      }
    }
    return result;
  } finally {
    client.close();
  }
}

/** Mirrors a whole folder tree — used for the worksheets, which live in one folder each. */
export async function uploadTree({ settings, remoteDir, localDir, onStep,
                                  onLine = null, onPhase = () => {} }) {
  const { client, connect, where } = clientFor(settings, { onLine });
  try {
    onPhase("connect", where);
    await connect();
    onPhase("login", String(settings.user || "").trim());
    if (onStep) client.trackProgress((info) => onStep(info.name, "sent"));
    await client.ensureDir(remoteDir);
    onPhase("dir", await client.pwd());
    await client.clearWorkingDir();          // the index must not keep sheets that were removed
    await client.uploadFromDir(localDir);
    client.trackProgress();
    return { ok: true };
  } finally {
    client.close();
  }
}

/** Every clip in the output folder, plus its index — what actually goes to the audio folder. */
export function audioFilesIn(dir) {
  if (!fs.existsSync(dir)) return [];
  const out = [];
  for (const name of fs.readdirSync(dir).sort()) {
    const ext = path.extname(name).toLowerCase();
    if (name === "index.json") continue;              // sent last, once the clips are all there
    if (!AUDIO_EXT.includes(ext)) continue;
    if (name.startsWith("_")) continue;               // the studio's own listening clips
    out.push(path.join(dir, name));
  }
  return out;
}
