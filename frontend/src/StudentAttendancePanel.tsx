import { FormEvent, useEffect, useRef, useState } from 'react';
import { AttendanceAttempt, AttendanceOutcome, sendAttendance } from './attendanceRequest';

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
  const sending = useRef(false);
  const pendingAttempt = useRef<AttendanceAttempt | null>(null);
  const [pin, setPin] = useState('');
  const [scanning, setScanning] = useState(false);
  const [pending, setPending] = useState(false);
  const [outcome, setOutcome] = useState<AttendanceOutcome | null>(null);

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
    void submit({ token: value, pin: null, requestId: crypto.randomUUID() });
  }, []);

  function tokenFrom(value: string) {
    const trimmed = value.trim();
    try { return new URL(trimmed).searchParams.get('attendanceToken') || trimmed; }
    catch { return trimmed; }
  }

  async function submit(attempt: AttendanceAttempt) {
    if (sending.current) return;
    sending.current = true;
    pendingAttempt.current = attempt;
    setPending(true);
    setOutcome(null);
    try {
      const response = await sendAttendance(apiUrl, token, attempt);
      setOutcome(response);
      if (response.kind === 'success' || response.kind === 'already-recorded' || response.kind === 'rejected') {
        pendingAttempt.current = null;
        setPending(false);
      }
      if (response.kind === 'success' || response.kind === 'already-recorded') {
        setPin('');
        stop();
      }
    } finally {
      sending.current = false;
    }
  }

  function submitToken(value: string) {
    const attendanceToken = tokenFrom(value);
    if (!attendanceToken || pendingAttempt.current || sending.current) return;
    void submit({ token: attendanceToken, pin: null, requestId: crypto.randomUUID() });
  }

  function submitPin() {
    if (!/^\d{6}$/.test(pin) || pendingAttempt.current || sending.current) return;
    void submit({ token: null, pin, requestId: crypto.randomUUID() });
  }

  function abandon() {
    pendingAttempt.current = null;
    setPending(false);
    setPin('');
    setOutcome(null);
  }

  async function start() {
    if (pending || sending.current) return;
    setOutcome(null);
    const BarcodeDetector = (window as Window & { BarcodeDetector?: DetectorConstructor }).BarcodeDetector;
    if (!BarcodeDetector) {
      setOutcome({ kind: 'rejected', message: 'Ovaj preglednik ne podržava skeniranje kamerom. Unesite PIN kod.' });
      return;
    }
    try {
      stream.current = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
      if (!video.current) { stop(); return; }
      video.current.srcObject = stream.current;
      await video.current.play();
      setScanning(true);
      const detector = new BarcodeDetector({ formats: ['qr_code'] });
      const scan = async () => {
        if (!video.current || !stream.current) return;
        try {
          const codes = await detector.detect(video.current);
          if (codes[0]?.rawValue) { stop(); submitToken(codes[0].rawValue); return; }
        } catch {}
        frame.current = requestAnimationFrame(scan);
      };
      frame.current = requestAnimationFrame(scan);
    } catch {
      stop();
      setOutcome({ kind: 'rejected', message: 'Kamera nije dostupna. Provjerite dozvolu preglednika ili unesite PIN kod.' });
    }
  }

  return <section className="attendance-panel">
    <h2>Evidencija prisustva</h2>
    <p>Skenirajte QR kod ili unesite šestocifreni PIN koji je prikazao zaposleni.</p>
    {!scanning && <button disabled={pending} onClick={() => void start()}>Pokreni kameru</button>}
    <video ref={video} className="qr-camera" muted playsInline hidden={!scanning} />
    {scanning && <button onClick={stop}>Zaustavi kameru</button>}
    <form onSubmit={(event: FormEvent) => { event.preventDefault(); submitPin(); }}>
      <label>PIN kod<input inputMode="numeric" autoComplete="one-time-code" value={pin}
        disabled={pending} onChange={event => setPin(event.target.value.replace(/\D/g, '').slice(0, 6))} maxLength={6} /></label>
      <button disabled={pending || !/^\d{6}$/.test(pin)} type="submit">Evidentiraj prisustvo</button>
    </form>
    {pending && !outcome && <p role="status">Provjera prisustva je u toku…</p>}
    {outcome?.kind === 'success' && <div className="success" role="status"><strong>Prisustvo je evidentirano.</strong><br />
      {outcome.result.courseCode} — {outcome.result.courseName}<br />
      {activities[outcome.result.activityType]} — grupa {outcome.result.groupNumber}<br />
      Vrijeme prijave: {new Date(outcome.result.recordedAt).toLocaleString('bs-BA')}</div>}
    {outcome?.kind === 'already-recorded' && <p className="success" role="status">{outcome.message}</p>}
    {outcome?.kind === 'rejected' && <p className="error" role="alert">{outcome.message}</p>}
    {outcome && (outcome.kind === 'network-error' || outcome.kind === 'timeout' || outcome.kind === 'unavailable') &&
    <div className="notice" role="alert">
      <p>{outcome.message} Nemojte skenirati novi kod prije provjere ovog pokušaja.</p>
      <button type="button" onClick={() => { if (pendingAttempt.current) void submit(pendingAttempt.current); }}>Pokušaj ponovo</button>
      <button type="button" onClick={abandon}>Odustani od pokušaja</button>
    </div>}
  </section>;
}
