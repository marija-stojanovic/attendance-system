import { FormEvent, useEffect, useRef, useState } from 'react';

type Result = { courseCode: string; courseName: string; activityType: string; groupNumber: string; recordedAt: string };
type DetectedCode = { rawValue: string };
type Detector = { detect(source: HTMLVideoElement): Promise<DetectedCode[]> };
type DetectorConstructor = new (options: { formats: string[] }) => Detector;

const activities: Record<string, string> = {
  LECTURE: 'Predavanje', AUDITORY_EXERCISE: 'Auditorne vježbe', LABORATORY_EXERCISE: 'Laboratorijske vježbe'
};

export function StudentAttendancePanel({ apiUrl, token }: { apiUrl: string; token: string }) {
  const video = useRef<HTMLVideoElement>(null);
  const stream = useRef<MediaStream | null>(null);
  const frame = useRef(0);
  const [qrToken, setQrToken] = useState('');
  const [pin, setPin] = useState('');
  const [scanning, setScanning] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<Result | null>(null);

  function stop() {
    cancelAnimationFrame(frame.current);
    stream.current?.getTracks().forEach(track => track.stop());
    stream.current = null;
    setScanning(false);
  }

  useEffect(() => stop, []);

  useEffect(() => {
    const value = new URLSearchParams(window.location.search).get('attendanceToken');
    if (!value) return;
    const url = new URL(window.location.href);
    url.searchParams.delete('attendanceToken');
    window.history.replaceState({}, '', url.pathname + url.search + url.hash);
    setQrToken(value);
    void submit(value);
  }, []);

  function tokenFrom(value: string) {
    const trimmed = value.trim();
    try { return new URL(trimmed).searchParams.get('attendanceToken') || trimmed; }
    catch { return trimmed; }
  }

  async function submit(value: string) {
    const attendanceToken = tokenFrom(value);
    if (!attendanceToken || saving) return;
    setSaving(true); setError(''); setResult(null);
    try {
      const response = await fetch(apiUrl + '/api/student/attendance', {
        method: 'POST', headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: attendanceToken, pin: null })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.detail || 'Prisustvo nije moguće evidentirati.');
      setResult(data); setQrToken(''); stop();
    } catch (e) { setError(e instanceof Error ? e.message : 'Prisustvo nije moguće evidentirati.'); }
    finally { setSaving(false); }
  }

  async function submitPin() {
    if (!/^\d{6}$/.test(pin) || saving) return;
    setSaving(true); setError(''); setResult(null);
    try {
      const response = await fetch(apiUrl + '/api/student/attendance', {
        method: 'POST', headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: null, pin })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.detail || 'Prisustvo nije moguće evidentirati.');
      setResult(data); setPin(''); stop();
    } catch (e) { setError(e instanceof Error ? e.message : 'Prisustvo nije moguće evidentirati.'); }
    finally { setSaving(false); }
  }

  async function start() {
    setError(''); setResult(null);
    const BarcodeDetector = (window as Window & { BarcodeDetector?: DetectorConstructor }).BarcodeDetector;
    if (!BarcodeDetector) { setError('Ovaj preglednik ne podržava skeniranje kamerom. Unesite QR token ručno.'); return; }
    try {
      stream.current = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
      if (!video.current) return;
      video.current.srcObject = stream.current;
      await video.current.play();
      setScanning(true);
      const detector = new BarcodeDetector({ formats: ['qr_code'] });
      const scan = async () => {
        if (!video.current || !stream.current) return;
        try {
          const codes = await detector.detect(video.current);
          if (codes[0]?.rawValue) { await submit(codes[0].rawValue); return; }
        } catch {}
        frame.current = requestAnimationFrame(scan);
      };
      frame.current = requestAnimationFrame(scan);
    } catch { stop(); setError('Kamera nije dostupna. Provjerite dozvolu preglednika ili unesite token ručno.'); }
  }

  return <section className="attendance-panel">
    <h2>Evidencija prisustva</h2>
    <p>Skenirajte QR kod ili unesite šestocifreni PIN koji je prikazao zaposleni.</p>
    {!scanning && <button disabled={saving} onClick={() => void start()}>Pokreni kameru</button>}
    <video ref={video} className="qr-camera" muted playsInline hidden={!scanning} />
    {scanning && <button onClick={stop}>Zaustavi kameru</button>}
    <form onSubmit={(event: FormEvent) => { event.preventDefault(); void submitPin(); }}>
      <label>PIN kod<input inputMode="numeric" autoComplete="one-time-code" value={pin}
        onChange={event => setPin(event.target.value.replace(/\D/g, '').slice(0, 6))} maxLength={6} /></label>
      <button disabled={saving || !/^\d{6}$/.test(pin)} type="submit">{saving ? 'Provjera…' : 'Evidentiraj prisustvo'}</button>
    </form>
    {error && <p className="error" role="alert">{error}</p>}
    {result && <div className="success"><strong>Prisustvo je evidentirano.</strong><br />
      {result.courseCode} — {result.courseName}<br />{activities[result.activityType]} — grupa {result.groupNumber}<br />
      Vrijeme prijave: {new Date(result.recordedAt).toLocaleString('bs-BA')}</div>}
  </section>;
}
