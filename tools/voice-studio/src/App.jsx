import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";

const fa = (n) => Number(n || 0).toLocaleString("fa-IR");
const api = async (path, options) => {
  const res = await fetch("/api" + path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const text = await res.text();
  const body = text ? JSON.parse(text) : {};
  if (!res.ok) throw new Error(body.error || `HTTP ${res.status}`);
  return body;
};

const GENDERS = [
  { id: "female", label: "صدای زن" },
  { id: "male", label: "صدای مرد" },
];

/** The fourteen voices, grouped, with their Persian names. Used in both tabs. */
function VoicePicker({ voices, speakers, value, onChange }) {
  if (!voices || !voices.length) {
    return (
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        {(speakers || []).map((sp) => <option key={sp} value={sp}>{sp}</option>)}
      </select>
    );
  }
  return (
    <select value={value} onChange={(e) => onChange(e.target.value)}>
      {GENDERS.map((g) => (
        <optgroup key={g.id} label={g.label}>
          {voices.filter((v) => v.gender === g.id).map((v) => (
            <option key={v.name} value={v.name}>{v.label} — {v.name}</option>
          ))}
        </optgroup>
      ))}
    </select>
  );
}

const GROUPS = [
  { id: "pages", label: "کتاب، صفحه به صفحه" },
  { id: "sections", label: "خلاصه‌ی بخش‌ها" },
  { id: "numbers", label: "واژه‌های عددی" },
  { id: "phrases", label: "تکه‌های سؤال" },
];

export default function App() {
  const [config, setConfig] = useState(null);
  const [form, setForm] = useState(null);
  const [token, setToken] = useState("");
  const [lines, setLines] = useState([]);
  const [status, setStatus] = useState({ running: false, done: 0, failed: 0, total: 0, current: [] });
  const [log, setLog] = useState([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [filter, setFilter] = useState({ group: "all", chapter: "all", state: "all", q: "" });
  const [showLog, setShowLog] = useState(true);
  const [published, setPublished] = useState(null);
  const [sheets, setSheets] = useState([]);
  const [sheetForm, setSheetForm] = useState({ title: "", note: "", chapter: -1, file: null });
  const [ftpPass, setFtpPass] = useState("");
  const [uploading, setUploading] = useState({ running: false, done: 0, total: 0, sent: 0, skipped: 0, failed: 0, what: "" });
  const [editing, setEditing] = useState(null);      // { key, spoken }
  const [testStamp, setTestStamp] = useState(0);
  const [tab, setTab] = useState("studio");
  const [diag, setDiag] = useState({ running: false, steps: [], outcome: null });
  const [sample, setSample] = useState({ running: false, mode: "", items: [] });
  const [probe, setProbe] = useState({
    endpoint: "short", speaker: "", speed: "", timestamps: false,
    text: "سَلام! مَن هوهو هَستَم، مُعَلِّمِ عُلومِ تو. بیا با هَم دُنیا را کَشف کُنیم.",
  });
  const logBox = useRef(null);

  const load = useCallback(async () => {
    try {
      const cfg = await api("/config");
      setConfig(cfg);
      setForm((old) => old ?? cfg);
      const snap = await api("/status");
      setStatus(snap);
      setLog(snap.log || []);
      const manifest = await api("/manifest");
      setLines(manifest.lines);
      const karbarg = await api("/karbarg");
      setSheets(karbarg.sheets || []);
      const probeState = await api("/diag");
      setDiag({ running: probeState.running, steps: probeState.steps, outcome: probeState.outcome });
      setSample(await api("/diag/sample"));
      setUploading(await api("/ftp/status"));
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // the server streams every log line and every step of the job
  useEffect(() => {
    const stream = new EventSource("/api/events");
    stream.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.type === "log") setLog((old) => [...old.slice(-400), data.entry]);
      if (data.type === "status") setStatus((old) => ({ ...old, ...data }));
      if (data.type === "upload") setUploading((old) => ({ ...old, ...data }));
      if (data.type === "diag") setDiag((old) => ({ ...old, ...data }));
      if (data.type === "sample") setSample((old) => ({ ...old, ...data }));
      // one clip finished: patch its row where it stands, so the list keeps up with the run
      if (data.type === "line") {
        setLines((old) => old.map((l) => l.key === data.key
          ? { ...l, done: data.done, file: data.file, size: data.size, stamp: data.stamp }
          : l));
      }
    };
    return () => stream.close();
  }, []);

  // when a job finishes, the files on disk have changed — reread them
  const wasRunning = useRef(false);
  useEffect(() => {
    if (wasRunning.current && !status.running) load();
    wasRunning.current = status.running;
  }, [status.running, load]);

  useEffect(() => {
    if (showLog && logBox.current) logBox.current.scrollTop = logBox.current.scrollHeight;
  }, [log, showLog]);

  const counts = useMemo(() => {
    const done = lines.filter((l) => l.done).length;
    return { total: lines.length, done, left: lines.length - done };
  }, [lines]);

  const chapters = useMemo(
    () => [...new Set(lines.map((l) => l.chapter).filter(Boolean))].sort((a, b) => a - b),
    [lines]
  );

  const shown = useMemo(() => lines.filter((l) => {
    if (filter.group !== "all" && l.group !== filter.group) return false;
    if (filter.chapter !== "all" && String(l.chapter) !== filter.chapter) return false;
    if (filter.state === "done" && !l.done) return false;
    if (filter.state === "todo" && l.done) return false;
    if (filter.q && !(l.key.includes(filter.q) || l.written.includes(filter.q))) return false;
    return true;
  }), [lines, filter]);

  const save = async (patch) => {
    setBusy(true);
    try {
      const next = await api("/config", { method: "POST", body: JSON.stringify(patch) });
      setConfig(next);
      setForm((old) => ({ ...old, ...next }));
      setError("");
      if (patch.manifestPath || patch.outDir) load();
    } catch (err) { setError(err.message); }
    setBusy(false);
  };

  /** Saves the FTP box as it stands (password included) and hands back the fresh config. */
  const saveFtp = async () => {
    const next = await api("/config", { method: "POST", body: JSON.stringify({
      ftp: { ...form.ftp, ...(ftpPass ? { password: ftpPass } : {}) },
    }) });
    setConfig(next);
    setForm((old) => ({ ...old, ...next }));
    setFtpPass("");
    return next;
  };

  /** Nothing on the FTP side runs before the box it reads from is saved. */
  const ftpDo = async (path, body = {}) => {
    setBusy(true);
    try {
      const next = await saveFtp();
      if (!next.ftp?.passwordSet) throw new Error("رمزِ FTP داده نشده");
      await api(path, { method: "POST", body: JSON.stringify(body) });
      setError("");
    } catch (err) { setError(err.message); }
    setBusy(false);
  };

  const sampleRun = async (what) => {
    try {
      await api("/diag/sample", { method: "POST", body: JSON.stringify({
        ...what,
        text: probe.text,
        endpoint: probe.endpoint,
        speaker: probe.speaker || config.speaker,
        speed: probe.speed === "" ? config.speed : probe.speed,
      }) });
      setError("");
    } catch (err) { setError(err.message); }
  };

  const generate = async (keys, limit, force = false) => {
    setBusy(true);
    try {
      await api("/generate", { method: "POST", body: JSON.stringify({ keys, limit, force }) });
      setError("");
    } catch (err) { setError(err.message); }
    setBusy(false);
  };

  /** Save a corrected line. It lands in tools/voice-overrides.json, which goes into git. */
  const saveText = async (key, spoken) => {
    setBusy(true);
    try {
      await api("/text", { method: "POST", body: JSON.stringify({ key, spoken }) });
      setEditing(null);
      await load();
    } catch (err) { setError(err.message); }
    setBusy(false);
  };

  const stop = () => api("/stop", { method: "POST" }).catch((e) => setError(e.message));

  const testVoice = async () => {
    setBusy(true);
    try {
      await api("/test", { method: "POST", body: JSON.stringify({}) });
      setTestStamp(Date.now());        // makes the player reload the new file
      load();
    } catch (err) { setError(err.message); }
    setBusy(false);
  };

  const removeFile = async (key) => {
    try {
      await api(`/audio/${key}`, { method: "DELETE" });
      load();
    } catch (err) { setError(err.message); }
  };

  if (!config || !form) {
    return <div className="studio"><div className="card">در حال باز شدن…</div></div>;
  }

  const todoKeys = (subset) => subset.filter((l) => !l.done).map((l) => l.key);
  const progress = status.total ? Math.round((status.done / status.total) * 100) : 0;

  return (
    <div className="studio">
      <div className="top">
        <span className="owl">🦉</span>
        <div>
          <h1>استودیوی صداگذاری هوهو</h1>
          <p>
            متن از <code>audio_manifest.txt</code> · صدای{" "}
            {(config.voices || []).find((v) => v.name === config.speaker)?.label || config.speaker}
            {" "}با سرعتِ {config.speed} · فایل‌ها در <code>{config.outDir}</code>
          </p>
        </div>
        <div className="grow" />
        <button className={tab === "studio" ? "chip sel" : "chip"} onClick={() => setTab("studio")}>
          استودیو
        </button>
        <button className={tab === "diag" ? "chip sel" : "chip"} onClick={() => setTab("diag")}>
          تستِ وب‌سرویس
        </button>
        <button className="ghost" onClick={load} disabled={busy}>تازه‌سازی</button>
        {status.running
          ? <button className="warn" onClick={stop}>ایستادن</button>
          : <button className="go" onClick={() => generate([], 0)} disabled={!config.tokenSet || !counts.left}>
              ساختِ همه‌ی باقی‌مانده‌ها ({fa(counts.left)})
            </button>}
      </div>

      {error && <div className="err">{error}</div>}
      {!config.tokenSet && <div className="err">توکنِ آواشو را پایین وارد کن تا ساخت شروع شود.</div>}

      {tab === "studio" && (<>
      <div className="stats">
        <div className="stat">
          <div className="label">کلِ جمله‌ها</div>
          <div className="value">{fa(counts.total)}</div>
          <div className="foot">طبق audio_manifest.txt</div>
        </div>
        <div className={"stat" + (counts.done ? " on" : "")}>
          <div className="label">ساخته‌شده</div>
          <div className="value">{fa(counts.done)}</div>
          <div className="bar"><i style={{ width: `${counts.total ? (counts.done / counts.total) * 100 : 0}%` }} /></div>
        </div>
        <div className="stat">
          <div className="label">باقی‌مانده</div>
          <div className="value">{fa(counts.left)}</div>
          <div className="foot">{status.running ? `در حال ساخت: ${status.current.join("، ") || "…"}` : "آماده"}</div>
        </div>
        <div className="stat">
          <div className="label">{status.running ? "پیشرفتِ این دسته" : "ناموفق در آخرین دسته"}</div>
          <div className="value">{status.running ? `${fa(status.done)}/${fa(status.total)}` : fa(status.failed)}</div>
          {status.running && <div className="bar"><i style={{ width: `${progress}%` }} /></div>}
        </div>
      </div>

      <div className="card">
        <h2>⚙️ تنظیمات <span className="hint">در voice-studio.config.json روی سیستمِ خودت ذخیره می‌شود</span></h2>
        <div className="grid">
          <div>
            <label>توکنِ آواشو (gateway-token)</label>
            <input type="password" placeholder={config.tokenHint || "توکن را بچسبان"}
                   value={token} onChange={(e) => setToken(e.target.value)} />
          </div>
          <div>
            <label>مسیرِ ذخیره‌ی فایل‌های صوتی</label>
            <input type="text" value={form.outDir}
                   onChange={(e) => setForm({ ...form, outDir: e.target.value })} />
          </div>
          <div>
            <label>مسیرِ audio_manifest.txt</label>
            <input type="text" value={form.manifestPath}
                   onChange={(e) => setForm({ ...form, manifestPath: e.target.value })} />
          </div>
          <div>
            <label>گوینده <span className="hint">
              {config.speaker === form.speaker ? "همین ذخیره شده" : "در حال ذخیره…"}
            </span></label>
            <VoicePicker voices={config.voices} speakers={config.speakers} value={form.speaker}
                         onChange={(speaker) => {
                           setForm({ ...form, speaker });
                           save({ speaker });        // picked is chosen; no second button
                         }} />
          </div>
          <div>
            <label>سرعت <span className="hint">
              {Number(config.speed) === Number(form.speed)
                ? "۱ سرعتِ خودِ سرویس · ۰٫۹ آرام‌ترِ کلاسی"
                : "ذخیره نشده — «ذخیره‌ی تنظیمات» را بزن"}
            </span></label>
            <input type="number" step="0.1" min="0.5" max="2" value={form.speed}
                   onChange={(e) => setForm({ ...form, speed: e.target.value })}
                   onBlur={(e) => save({ speed: e.target.value })} />
          </div>
          <div>
            <label>سرویسِ آواشو</label>
            <select value={form.endpoint} onChange={(e) => setForm({ ...form, endpoint: e.target.value })}>
              <option value="short">avasho — متنِ کوتاه (درست برای جمله‌های درس)</option>
              <option value="long">avasho-large — متنِ بلند</option>
            </select>
          </div>
          <div>
            <label>چند تا هم‌زمان</label>
            <input type="number" min="1" max="8" value={form.concurrency}
                   onChange={(e) => setForm({ ...form, concurrency: e.target.value })} />
          </div>
          <div>
            <label>فاصله‌ی بینِ درخواست‌ها (میلی‌ثانیه)</label>
            <input type="number" min="0" step="100" value={form.delayMs}
                   onChange={(e) => setForm({ ...form, delayMs: e.target.value })} />
          </div>
          <div>
            <label>تلاشِ مجدد در خطا</label>
            <input type="number" min="0" max="8" value={form.retries}
                   onChange={(e) => setForm({ ...form, retries: e.target.value })} />
          </div>
          <label className="check">
            <input type="checkbox" checked={form.useVowels}
                   onChange={(e) => setForm({ ...form, useVowels: e.target.checked })} />
            متنِ اعراب‌گذاری‌شده (ستون سوم)
          </label>
          <label className="check">
            <input type="checkbox" checked={form.convertOgg}
                   onChange={(e) => setForm({ ...form, convertOgg: e.target.checked })} />
            تبدیل به ogg با ffmpeg
          </label>
        </div>
        <div className="row" style={{ marginTop: 14 }}>
          <button className="go" disabled={busy}
                  onClick={() => { save({ ...form, token: token || undefined }); setToken(""); }}>
            ذخیره‌ی تنظیمات
          </button>
          <button className="ghost" disabled={busy || !config.tokenSet} onClick={testVoice}>
            تستِ صدا با یک جمله
          </button>
          {config.tokenSet &&
            <button className="ghost" onClick={() => save({ token: "" })}>پاک کردنِ توکن</button>}
        </div>
        {testStamp > 0 && (
          <div className="row" style={{ marginTop: 12 }}>
            <span className="tag done">نمونه‌ی تست</span>
            <audio controls src={`/api/audio/_test-${config.speaker}?t=${testStamp}`} />
          </div>
        )}
        <p className="note">
          توکن و رمزِ FTP در <code>{config.configFile}</code> ذخیره می‌شوند و با رفرش یا بالا
          آمدنِ دوباره‌ی سرور از دست نمی‌روند. کادرها از روی عادت خالی نشان داده می‌شوند —
          {config.tokenSet ? " ✓ توکن ذخیره شده است" : " توکن هنوز داده نشده"}
          {config.ftp?.passwordSet ? " · ✓ رمزِ FTP ذخیره شده است" : " · رمزِ FTP داده نشده"}.
          {config.savedAt ? " آخرین ذخیره: " + new Date(config.savedAt).toLocaleTimeString("fa-IR") : ""}
        </p>
        <p className="note">
          فایل‌ها با نامِ کلید ذخیره می‌شوند (مثلاً <code>p008_01.mp3</code>) در
          <code> {config.outDir}</code>.
        </p>
      </div>

      <div className="card">
        <h2>⏱ زمان‌بندی <span className="hint">دسته‌دسته می‌سازد و خودش ادامه می‌دهد</span></h2>
        <div className="grid">
          <div>
            <label>هر چند ثانیه یک دسته</label>
            <input type="number" min="30" step="30" value={form.schedule.everySeconds}
                   onChange={(e) => setForm({ ...form, schedule: { ...form.schedule, everySeconds: Number(e.target.value) } })} />
          </div>
          <div>
            <label>اندازه‌ی هر دسته</label>
            <input type="number" min="1" value={form.schedule.batchSize}
                   onChange={(e) => setForm({ ...form, schedule: { ...form.schedule, batchSize: Number(e.target.value) } })} />
          </div>
          <label className="check">
            <input type="checkbox" checked={config.schedule.enabled}
                   onChange={(e) => save({ schedule: { ...form.schedule, enabled: e.target.checked } })} />
            {config.schedule.enabled ? "روشن است" : "خاموش"}
          </label>
        </div>
        <p className="note">
          تا این پنجره باز است و سرور بالا، هر دوره یک دسته‌ی تازه از جمله‌هایی که فایل ندارند
          ساخته می‌شود. با سهمیه‌ی محدودِ سرویس، دسته‌ی کوچک و فاصله‌ی بلند بهتر جواب می‌دهد.
        </p>
      </div>

      <div className="card">
        <h2>⚡ ساختِ دسته‌ای</h2>
        <div className="row">
          {GROUPS.map((g) => {
            const subset = lines.filter((l) => l.group === g.id);
            const left = todoKeys(subset).length;
            return (
              <button key={g.id} className="chip" disabled={busy || status.running || !left}
                      onClick={() => generate(todoKeys(subset))}>
                {g.label} ({fa(left)})
              </button>
            );
          })}
        </div>
        <div className="row" style={{ marginTop: 10 }}>
          {chapters.map((c) => {
            const subset = lines.filter((l) => l.chapter === c);
            const left = todoKeys(subset).length;
            return (
              <button key={c} className="chip" disabled={busy || status.running || !left}
                      onClick={() => generate(todoKeys(subset))}>
                درس {fa(c)} ({fa(left)})
              </button>
            );
          })}
        </div>
        <div className="row" style={{ marginTop: 10 }}>
          {[10, 50, 200].map((n) => (
            <button key={n} className="ghost" disabled={busy || status.running || !counts.left}
                    onClick={() => generate([], n)}>
              فقط {fa(n)} تای بعدی
            </button>
          ))}
          <button disabled={busy || status.running || !shown.some((l) => !l.done)}
                  onClick={() => generate(todoKeys(shown))}>
            هرچه در فهرستِ پایین فیلتر شده ({fa(shown.filter((l) => !l.done).length)})
          </button>
        </div>
      </div>

      <div className="card">
        <h2>📤 انتشار روی هاست <span className="hint">فایل‌ها را خودت آپلود می‌کنی؛ اینجا فقط فهرست ساخته می‌شود</span></h2>
        <div className="grid">
          <div>
            <label>آدرسِ پوشه‌ی صداها روی هاست</label>
            <input type="text" value={form.audioBase || ""}
                   onChange={(e) => setForm({ ...form, audioBase: e.target.value })} />
          </div>
          <div>
            <label>آدرسِ پوشه‌ی کاربرگ‌ها روی هاست</label>
            <input type="text" value={form.karbargBase || ""}
                   onChange={(e) => setForm({ ...form, karbargBase: e.target.value })} />
          </div>
        </div>
        <div className="row" style={{ marginTop: 12 }}>
          <button className="go" disabled={busy} onClick={async () => {
            setBusy(true);
            try {
              const index = await api("/publish/audio", { method: "POST" });
              setPublished(index);
              setError("");
            } catch (err) { setError(err.message); }
            setBusy(false);
          }}>
            ساختِ فهرستِ صدا (index.json)
          </button>
          <button className="ghost" disabled={busy} onClick={() => save(form)}>ذخیره‌ی آدرس‌ها</button>
        </div>
        {published && (
          <p className="note">
            ✓ {fa(published.count)} کلیپ، {fa(Math.round(published.bytes / 1048576))} مگابایت.
            <code> index.json </code> کنارِ خودِ فایل‌ها ساخته شد و یک نسخه هم در
            <code> app/src/main/assets/voice-index.json </code> گذاشته شد.
          </p>
        )}

        <h2 style={{ marginTop: 20 }}>
          🚀 آپلود با FTP
          <span className="hint">
            {config.ftp?.passwordSet ? "رمز ذخیره شده است" : "رمز هنوز داده نشده"}
          </span>
        </h2>
        <div className="grid">
          <div>
            <label>آدرسِ سرورِ FTP</label>
            <input type="text" value={form.ftp?.host || ""}
                   onChange={(e) => setForm({ ...form, ftp: { ...form.ftp, host: e.target.value } })} />
          </div>
          <div>
            <label>پورت</label>
            <input type="number" value={form.ftp?.port ?? 21}
                   onChange={(e) => setForm({ ...form, ftp: { ...form.ftp, port: Number(e.target.value) } })} />
          </div>
          <div>
            <label>نام کاربری</label>
            <input type="text" value={form.ftp?.user || ""}
                   onChange={(e) => setForm({ ...form, ftp: { ...form.ftp, user: e.target.value } })} />
          </div>
          <div>
            <label>رمز</label>
            <input type="password" value={ftpPass}
                   placeholder={config.ftp?.passwordSet ? "ذخیره شده — برای تغییر بنویس" : "رمزِ FTP"}
                   onChange={(e) => setFtpPass(e.target.value)} />
          </div>
          <div>
            <label>پوشه‌ی صداها روی سرور</label>
            <input type="text" value={form.ftp?.audioDir || ""}
                   onChange={(e) => setForm({ ...form, ftp: { ...form.ftp, audioDir: e.target.value } })} />
          </div>
          <div>
            <label>پوشه‌ی کاربرگ‌ها روی سرور</label>
            <input type="text" value={form.ftp?.karbargDir || ""}
                   onChange={(e) => setForm({ ...form, ftp: { ...form.ftp, karbargDir: e.target.value } })} />
          </div>
          <label className="check">
            <input type="checkbox" checked={Boolean(form.ftp?.secure)}
                   onChange={(e) => setForm({ ...form, ftp: { ...form.ftp, secure: e.target.checked } })} />
            FTPS (explicit) — اگر هاست می‌خواهد
          </label>
        </div>

        <div className="row" style={{ marginTop: 12 }}>
          <button className="ghost" disabled={busy} onClick={async () => {
            setBusy(true);
            try { await saveFtp(); setError(""); } catch (err) { setError(err.message); }
            setBusy(false);
          }}>
            ذخیره‌ی تنظیماتِ FTP
          </button>
          <button className="ghost" disabled={busy}
                  onClick={() => ftpDo("/ftp/test", { which: "audio" })}>
            تستِ اتصال
          </button>
          <button className="go" disabled={busy || uploading.running}
                  onClick={() => ftpDo("/ftp/upload/audio")}>
            آپلودِ صداها (فقط تازه‌ها)
          </button>
          <button disabled={busy || uploading.running}
                  onClick={() => ftpDo("/ftp/upload/audio", { all: true })}>
            آپلودِ همه دوباره
          </button>
          <button className="chip" disabled={busy || uploading.running}
                  onClick={() => ftpDo("/ftp/upload/karbarg")}>
            آپلودِ کاربرگ‌ها
          </button>
          {uploading.running && (
            <button className="warn" onClick={() => api("/ftp/stop", { method: "POST" })}>
              توقفِ آپلود
            </button>
          )}
        </div>

        {uploading.link?.state && (
          <div style={{ marginTop: 12 }}>
            <div className="row" style={{ justifyContent: "space-between" }}>
              <strong style={{ fontSize: 13 }}>
                وضعیتِ اتصال:{" "}
                <span className={uploading.link.error ? "state bad"
                                 : uploading.link.state === "وصل است" ? "state ok" : "state warn"}>
                  {uploading.link.state}
                </span>
                {uploading.link.note ? <span className="key"> · {uploading.link.note}</span> : null}
              </strong>
              <span className="tag">
                {config.ftp?.host}:{fa(config.ftp?.port ?? 21)}
                {config.ftp?.secure ? " · FTPS" : ""}
              </span>
            </div>
            {uploading.link.error && <div className="err" style={{ marginTop: 8 }}>
              {uploading.link.error}
            </div>}
            {uploading.link.warning && <div className="err" style={{ marginTop: 8 }}>
              ⚠ {uploading.link.warning}
            </div>}
            {uploading.link.info && (
              <p className="note">
                پوشه‌ی ورود <code>{uploading.link.info.loginDir}</code> · مقصد{" "}
                <code>{uploading.link.info.remoteDir}</code>{" "}
                {uploading.link.info.remoteExists
                  ? `— ${fa(uploading.link.info.remoteCount)} فایل آنجاست`
                  : "— هنوز ساخته نشده؛ موقعِ آپلود ساخته می‌شود"}
                {uploading.link.info.names?.length
                  ? ` (${uploading.link.info.names.join("، ")}…)` : ""}
              </p>
            )}
            {uploading.link.lines?.length > 0 && (
              <details open={!uploading.link.info}>
                <summary className="note" style={{ cursor: "pointer" }}>
                  گفت‌وگو با سرور ({fa(uploading.link.lines.length)} خط)
                </summary>
                <pre style={{ direction: "ltr", textAlign: "left", whiteSpace: "pre-wrap",
                              fontSize: 11, background: "#faf7f2", padding: 9, borderRadius: 8,
                              maxHeight: 220, overflow: "auto" }}>
                  {uploading.link.lines.join("\n")}
                </pre>
              </details>
            )}
          </div>
        )}

        {(uploading.running || uploading.done > 0) && (
          <div style={{ marginTop: 12 }}>
            <div className="bar">
              <i style={{ width: `${uploading.total ? (uploading.done / uploading.total) * 100 : 0}%` }} />
            </div>
            <p className="note">
              {uploading.running ? `در حال آپلودِ ${uploading.what}: ` : "آخرین آپلود: "}
              {fa(uploading.done)} از {fa(uploading.total)} — {fa(uploading.sent)} فرستاده،
              {" "}{fa(uploading.skipped)} از قبل بود، {fa(uploading.failed)} ناموفق
            </p>
          </div>
        )}

        <p className="note">
          «فقط تازه‌ها» فایلی را که با همان نام و همان حجم روی سرور هست رد می‌کند، پس اجرای
          دوباره فقط چیزهای جدید را می‌فرستد. <code>index.json</code> همیشه آخر از همه و از نو
          ساخته می‌شود، تا هیچ‌وقت فهرستی از فایل‌هایی که هنوز نرفته‌اند روی سرور نباشد.
        </p>
        <p className="note">
          آپلودِ کاربرگ‌ها پوشه‌ی سرور را با پوشه‌ی اینجا یکی می‌کند — کاربرگی که اینجا پاک
          کرده باشی، آنجا هم پاک می‌شود.
        </p>
      </div>

      <div className="card">
        <h2>📄 کاربرگ‌های چاپی <span className="hint">{fa(sheets.length)} کاربرگ در {config.karbargDir}</span></h2>
        <div className="grid">
          <div>
            <label>عنوانِ کاربرگ</label>
            <input type="text" value={sheetForm.title}
                   onChange={(e) => setSheetForm({ ...sheetForm, title: e.target.value })} />
          </div>
          <div>
            <label>توضیح (مثلاً «۲ صفحه، جمع و تفریق»)</label>
            <input type="text" value={sheetForm.note}
                   onChange={(e) => setSheetForm({ ...sheetForm, note: e.target.value })} />
          </div>
          <div>
            <label>درس</label>
            <select value={sheetForm.chapter}
                    onChange={(e) => setSheetForm({ ...sheetForm, chapter: Number(e.target.value) })}>
              <option value={-1}>متفرقه (به درسی مربوط نیست)</option>
              {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14].map((n) => (
                <option key={n} value={n - 1}>درس {fa(n)}</option>
              ))}
            </select>
          </div>
          <div>
            <label>فایلِ PDF</label>
            <input type="file" accept="application/pdf"
                   onChange={(e) => setSheetForm({ ...sheetForm, file: e.target.files?.[0] || null })} />
          </div>
        </div>
        <div className="row" style={{ marginTop: 12 }}>
          <button className="go" disabled={busy || !sheetForm.title || !sheetForm.file}
                  onClick={async () => {
                    setBusy(true);
                    try {
                      const data = await new Promise((resolve, reject) => {
                        const reader = new FileReader();
                        reader.onload = () => resolve(reader.result);
                        reader.onerror = reject;
                        reader.readAsDataURL(sheetForm.file);
                      });
                      await api("/karbarg", { method: "POST", body: JSON.stringify({
                        title: sheetForm.title, note: sheetForm.note,
                        chapter: sheetForm.chapter, fileName: sheetForm.file.name, dataBase64: data,
                      }) });
                      setSheetForm({ title: "", note: "", chapter: -1, file: null });
                      load();
                      setError("");
                    } catch (err) { setError(err.message); }
                    setBusy(false);
                  }}>
            افزودنِ کاربرگ
          </button>
          <button className="ghost" disabled={busy} onClick={async () => {
            try { await api("/publish/karbarg", { method: "POST" }); setError(""); }
            catch (err) { setError(err.message); }
          }}>
            ساختِ فهرستِ کاربرگ (index.json)
          </button>
        </div>

        {sheets.length > 0 && (
          <div className="files" style={{ marginTop: 12 }}>
            {sheets.map((sheet) => (
              <div className="file" key={sheet.slug}>
                <span className="key">{sheet.slug}</span>
                <span className="say">
                  {sheet.title}
                  {sheet.note ? ` — ${sheet.note}` : ""}
                  {sheet.chapter >= 0 ? ` · درس ${fa(sheet.chapter + 1)}` : " · متفرقه"}
                </span>
                <span className="row">
                  <span className="tag done">{fa(Math.round(sheet.bytes / 1024))}KB</span>
                  <button className="tiny ghost" onClick={async () => {
                    await api(`/karbarg/${sheet.slug}`, { method: "DELETE" });
                    load();
                  }}>پاک</button>
                </span>
              </div>
            ))}
          </div>
        )}
        <p className="note">
          هر کاربرگ یک پوشه با نامِ خودش می‌گیرد که PDF و <code>info.json</code> در آن است.
          همین ساختار را در <code>{config.karbargBase}</code> آپلود کن؛ اپ فهرست را با
          عنوان و توضیح می‌خواند و لینکِ دانلود می‌سازد.
        </p>
      </div>

      </>)}

      {tab === "diag" && (
        <div className="card">
          <h2>🔎 تستِ وب‌سرویس <span className="hint">یک تماسِ کامل، با همه‌ی مرحله‌ها</span></h2>
          <div className="grid">
            <div>
              <label>نوعِ درخواست</label>
              <select value={probe.endpoint}
                      onChange={(e) => setProbe({ ...probe, endpoint: e.target.value })}>
                <option value="short">avasho — متنِ کوتاه</option>
                <option value="long">avasho-large — متنِ بلند</option>
              </select>
            </div>
            <div>
              <label>گوینده</label>
              <VoicePicker voices={config.voices} speakers={config.speakers}
                           value={probe.speaker || config.speaker}
                           onChange={(speaker) => setProbe({ ...probe, speaker })} />
            </div>
            <div>
              <label>سرعت</label>
              <input type="number" step="0.1" min="0.5" max="2"
                     value={probe.speed === "" ? config.speed : probe.speed}
                     onChange={(e) => setProbe({ ...probe, speed: e.target.value })} />
            </div>
            <label className="check">
              <input type="checkbox" checked={probe.timestamps}
                     onChange={(e) => setProbe({ ...probe, timestamps: e.target.checked })} />
              زمان‌بندیِ کلمه‌ها را هم بخواه
            </label>
          </div>
          <div style={{ marginTop: 12 }}>
            <label>متنِ تست</label>
            <textarea rows={3} style={{ width: "100%" }} value={probe.text}
                      onChange={(e) => setProbe({ ...probe, text: e.target.value })} />
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <button className="go" disabled={diag.running || !config.tokenSet}
                    onClick={async () => {
                      setDiag({ running: true, steps: [], outcome: null });
                      try {
                        await api("/diag", { method: "POST", body: JSON.stringify({
                          ...probe, speaker: probe.speaker || config.speaker,
                          speed: probe.speed === "" ? config.speed : probe.speed }) });
                        setError("");
                      } catch (err) { setError(err.message); setDiag({ running: false, steps: [], outcome: null }); }
                    }}>
              {diag.running ? "در حال تست…" : "شروعِ تست"}
            </button>
            {diag.running && (
              <button className="warn" onClick={() => api("/diag/stop", { method: "POST" })}>
                توقفِ تست
              </button>
            )}
            {!config.tokenSet && <span className="note">اول در تبِ استودیو توکن را بده.</span>}
          </div>

          {diag.steps.length > 0 && (
            <div className="files" style={{ marginTop: 14 }}>
              {diag.steps.map((st, i) => (
                <div key={i} style={{ padding: "10px 12px", borderBottom: "1px solid #f2ede5" }}>
                  <div className="row" style={{ justifyContent: "space-between" }}>
                    <strong style={{ fontSize: 13 }}>
                      {st.name} — <span className={st.status >= 200 && st.status < 300 ? "" : "tag"}>
                        {st.status === 0 ? "به سرویس نرسید" : `HTTP ${st.status}`}
                      </span>
                    </strong>
                    <span className="tag">{st.ms} میلی‌ثانیه{st.bytes ? ` · ${fa(st.bytes)} بایت` : ""}</span>
                  </div>
                  <div className="key" style={{ margin: "4px 0", wordBreak: "break-all" }}>
                    {st.method} {st.url}
                  </div>
                  <pre style={{ margin: 0, direction: "ltr", textAlign: "left", whiteSpace: "pre-wrap",
                                fontSize: 11, background: "#faf7f2", padding: 9, borderRadius: 8,
                                maxHeight: 180, overflow: "auto" }}>{st.body}</pre>
                </div>
              ))}
            </div>
          )}

          {diag.outcome && (
            <div style={{ marginTop: 14 }}>
              {diag.outcome.ok ? (
                <>
                  <p className="note" style={{ color: "#1f6f66", fontWeight: 700 }}>
                    ✓ وب‌سرویس کار می‌کند — {fa(Math.round(diag.outcome.bytes / 1024))} کیلوبایت
                    {diag.outcome.ext}، در {(diag.outcome.ms / 1000).toFixed(1)} ثانیه
                    {diag.outcome.words ? ` · ${fa(diag.outcome.words)} کلمه زمان‌بندی شد` : ""}
                  </p>
                  <audio controls src={`/api/audio/_diag-${probe.speaker || config.speaker}?t=${diag.outcome.ms}`} />
                </>
              ) : (
                <div className="err">✗ {diag.outcome.error}</div>
              )}
            </div>
          )}

          <p className="note">
            سه مرحله را می‌بینی: <code>request</code>، بعد اگر لازم بود <code>track</code>، و
            آخر <code>download</code>. اگر جایی ایستاد، همان کادرِ خام می‌گوید سرویس چه گفت —
            و همان را بفرست تا درستش کنم.
          </p>
        </div>
      )}

      {tab === "diag" && (
        <div className="card">
          <h2>🎙 نمونه‌ها <span className="hint">صدا و سرعت را با گوش انتخاب کن، نه با حدس</span></h2>
          <p className="note" style={{ marginTop: 0 }}>
            همان جمله‌ی بالا با هر صدا — یا با چند سرعتِ مختلف — ساخته می‌شود و همین‌جا پخش.
            هرکدام را پسندیدی، «همین» را بزن تا گوینده و سرعتِ درس‌ها بشود. نمونه‌ها نامشان با
            <code> _ </code> شروع می‌شود، پس هیچ‌وقت در <code>index.json</code> و روی هاست
            نمی‌روند.
          </p>
          <div className="row" style={{ flexWrap: "wrap" }}>
            <button className="go" disabled={sample.running || !config.tokenSet}
                    onClick={() => sampleRun({ mode: "voices" })}>
              نمونه‌ی همه‌ی گوینده‌ها ({fa((config.voices || []).length)})
            </button>
            <button disabled={sample.running || !config.tokenSet}
                    onClick={() => sampleRun({ mode: "voices", gender: "female" })}>
              فقط صدای زن
            </button>
            <button disabled={sample.running || !config.tokenSet}
                    onClick={() => sampleRun({ mode: "voices", gender: "male" })}>
              فقط صدای مرد
            </button>
            <button disabled={sample.running || !config.tokenSet}
                    onClick={() => sampleRun({ mode: "speeds" })}>
              نمونه‌ی سرعت‌ها (۰٫۸ تا ۱٫۱)
            </button>
            {sample.running && (
              <button className="warn" onClick={() => api("/diag/sample/stop", { method: "POST" })}>
                توقف
              </button>
            )}
          </div>

          {(sample.duplicates || []).length > 0 && (
            <div className="err" style={{ marginTop: 14 }}>
              <strong>سرویس نامِ گوینده را نگرفته است.</strong>{" "}
              {(sample.duplicates || []).map((set) => set.join("، ")).join(" · ")} —
              فایلِ این‌ها بایت‌به‌بایت یکی است، و دو صدای مختلف نمی‌توانند یک جمله را یکسان
              بخوانند. پس هرچه انتخاب کنی، سرویس همان صدای پیش‌فرضِ خودش را می‌دهد؛ ما نام را
              درست فرستاده‌ایم و زیرِ هر ردیف، در کادرِ پاسخ، دیده می‌شود. این را به
              پشتیبانیِ ایویرا بگو.
            </div>
          )}

          {sample.items.length > 0 && (
            <div className="files" style={{ marginTop: 14 }}>
              {sample.items.map((item) => {
                const voice = (config.voices || []).find((v) => v.name === item.speaker);
                const chosen = config.speaker === item.speaker
                  && Number(config.speed) === Number(item.speed);
                return (
                  <div key={item.key} className="file sample">
                    <span style={{ minWidth: 150 }}>
                      <strong>{voice ? voice.label : item.speaker}</strong>{" "}
                      <span className="tag">{voice ? voice.gender === "male" ? "مرد" : "زن" : ""}</span>{" "}
                      <span className="key">سرعت {item.speed}</span>
                    </span>
                    {item.state === "آماده" ? (
                      <>
                        <span>
                          <audio controls src={`/api/audio/${item.key}?t=${item.hash || 0}`} />
                          <div className="key" style={{ marginTop: 2 }}>
                            {fa(item.bytes)} بایت · اثرِ انگشت {item.hash}
                            {(sample.duplicates || []).some((set) =>
                              set.includes(item.speaker) || set.includes(`${item.speaker}@${item.speed}`))
                              && <span className="tag" style={{ marginInlineStart: 6 }}>تکراری</span>}
                          </div>
                        </span>
                        <button className="tiny" disabled={chosen}
                                onClick={async () => {
                                  const next = await api("/diag/sample/pick", { method: "POST",
                                    body: JSON.stringify({ speaker: item.speaker, speed: item.speed }) });
                                  setConfig(next);
                                  setForm((old) => ({ ...old, ...next }));
                                }}>
                          {chosen ? "✓ همین است" : "همین"}
                        </button>
                      </>
                    ) : (
                      <span className={item.error ? "err" : "note"} style={{ margin: 0 }}>
                        {item.error ? `${item.state} — ${item.error}` : item.state}
                      </span>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          {sample.items.some((i) => i.reply) && (
            <details style={{ marginTop: 12 }}>
              <summary className="note" style={{ cursor: "pointer" }}>
                پاسخِ خامِ سرویس برای هر نمونه — ببین نام را تحویل گرفته یا نه
              </summary>
              <pre style={{ direction: "ltr", textAlign: "left", whiteSpace: "pre-wrap",
                            fontSize: 11, background: "#faf7f2", padding: 9, borderRadius: 8,
                            maxHeight: 260, overflow: "auto" }}>
                {sample.items.filter((i) => i.reply)
                  .map((i) => `speaker=${i.speaker} speed=${i.speed}\n${i.reply}`).join("\n\n")}
              </pre>
            </details>
          )}
        </div>
      )}

      <div className="card">
        <div className="log-head">
          <h2 style={{ margin: 0 }}>لاگِ زنده</h2>
          <button className="tiny ghost" onClick={() => setShowLog(!showLog)}>
            {showLog ? "مخفی کن" : "نشان بده"}
          </button>
        </div>
        {showLog && (
          <div className="log" ref={logBox}>
            {log.length === 0 && <div className="t">— هنوز چیزی نیست —</div>}
            {log.map((entry, i) => (
              <div key={i}>
                <span className="t">[{entry.at.slice(11, 19)}]</span>{" "}
                <span className={entry.level}>{entry.message}</span>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card">
        <h2>🎧 جمله‌ها و پخش <span className="hint">{fa(shown.length)} از {fa(counts.total)}</span></h2>
        <div className="grid" style={{ marginBottom: 12 }}>
          <div>
            <label>جستجو در کلید یا متن</label>
            <input type="text" value={filter.q} onChange={(e) => setFilter({ ...filter, q: e.target.value })} />
          </div>
          <div>
            <label>گروه</label>
            <select value={filter.group} onChange={(e) => setFilter({ ...filter, group: e.target.value })}>
              <option value="all">همه</option>
              {GROUPS.map((g) => <option key={g.id} value={g.id}>{g.label}</option>)}
            </select>
          </div>
          <div>
            <label>درس</label>
            <select value={filter.chapter} onChange={(e) => setFilter({ ...filter, chapter: e.target.value })}>
              <option value="all">همه</option>
              {chapters.map((c) => <option key={c} value={String(c)}>درس {fa(c)}</option>)}
            </select>
          </div>
          <div>
            <label>وضعیت</label>
            <select value={filter.state} onChange={(e) => setFilter({ ...filter, state: e.target.value })}>
              <option value="all">همه</option>
              <option value="done">ساخته‌شده</option>
              <option value="todo">بی‌فایل</option>
            </select>
          </div>
        </div>

        <div className="files">
          {shown.length === 0 && <div className="empty">چیزی با این فیلتر پیدا نشد.</div>}
          {shown.slice(0, 400).map((l) => (
            <div className="file" key={l.key}>
              <span className="key">{l.key}{l.edited ? " ✎" : ""}</span>
              {editing?.key === l.key ? (
                <span style={{ gridColumn: "2 / 4" }}>
                  <textarea rows={3} value={editing.spoken} style={{ width: "100%" }}
                            onChange={(e) => setEditing({ ...editing, spoken: e.target.value })} />
                  <span className="row" style={{ marginTop: 6 }}>
                    <button className="tiny go" disabled={busy}
                            onClick={() => saveText(l.key, editing.spoken)}>ذخیره‌ی متن</button>
                    <button className="tiny go" disabled={busy || status.running}
                            onClick={() => saveText(l.key, editing.spoken).then(() => generate([l.key], 0, true))}>
                      ذخیره و بساز از نو
                    </button>
                    <button className="tiny ghost" onClick={() => setEditing(null)}>بی‌خیال</button>
                    {l.edited && (
                      <button className="tiny ghost" disabled={busy} onClick={async () => {
                        await api(`/text/${l.key}`, { method: "DELETE" });
                        setEditing(null);
                        load();
                      }}>برگردان به متنِ درس</button>
                    )}
                  </span>
                </span>
              ) : (
                <>
                  <span className="say" title={config.useVowels ? l.spoken : l.written}>
                    {config.useVowels ? l.spoken : l.written}
                  </span>
                  <span className="row">
                    {/* the stamp changes when the clip is remade, so «از نو» is not answered
                        out of the browser's cache with the old recording */}
                    {l.done && <audio controls preload="none"
                                      src={`/api/audio/${l.key}?v=${l.stamp || l.size}`} />}
                    {status.current.includes(l.key)
                      ? <span className="tag">در حال ساخت…</span>
                      : l.done
                        ? <span className="tag done">{Math.round(l.size / 1024)}KB</span>
                        : <span className="tag">بی‌فایل</span>}
                    <button className="tiny ghost"
                            onClick={() => setEditing({ key: l.key, spoken: l.spoken })}>متن</button>
                    <button className="tiny go" disabled={busy || status.running || !config.tokenSet}
                            onClick={() => generate([l.key], 0, l.done)}>
                      {l.done ? "از نو" : "بساز"}
                    </button>
                    {l.done && (
                      <button className="tiny ghost" onClick={() => removeFile(l.key)}>پاک</button>
                    )}
                  </span>
                </>
              )}
            </div>
          ))}
        </div>
        {shown.length > 400 && <p className="note">۴۰۰ ردیفِ اول نشان داده شد — با فیلتر محدودترش کن.</p>}
      </div>
    </div>
  );
}
