export type AttendanceResult = {
  courseCode: string;
  courseName: string;
  activityType: string;
  groupNumber: string;
  recordedAt: string;
};

export type AttendanceAttempt = {
  token: string | null;
  pin: string | null;
  requestId: string;
};

export type AttendanceOutcome =
  | { kind: 'success'; result: AttendanceResult }
  | { kind: 'already-recorded' | 'rejected' | 'unavailable' | 'network-error' | 'timeout'; message: string };

export async function sendAttendance(
  apiUrl: string, accessToken: string, attempt: AttendanceAttempt,
  timeoutMs = 8000, send: typeof fetch = fetch
): Promise<AttendanceOutcome> {
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);
  try {
    const response = await send(apiUrl + '/api/student/attendance', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + accessToken, 'Content-Type': 'application/json' },
      body: JSON.stringify(attempt),
      signal: controller.signal
    });
    const data = await response.json().catch(() => ({}));
    if (response.ok) return { kind: 'success', result: data as AttendanceResult };
    if (response.status === 409 && data.code === 'ATTENDANCE_ALREADY_RECORDED') {
      return { kind: 'already-recorded', message: 'Prisustvo je već evidentirano za ovu sesiju.' };
    }
    if (response.status >= 500) {
      return { kind: 'unavailable', message: data.detail || 'Server trenutno nije dostupan. Pokušajte ponovo.' };
    }
    return { kind: 'rejected', message: data.detail || (response.status === 401
      ? 'Prijava je istekla. Prijavite se ponovo.' : 'Prisustvo nije prihvaćeno.') };
  } catch {
    return timedOut
      ? { kind: 'timeout', message: 'Provjera traje predugo. Ishod nije poznat; pokušajte ponovo.' }
      : { kind: 'network-error', message: 'Veza sa serverom je prekinuta. Ishod nije poznat; pokušajte ponovo.' };
  } finally {
    clearTimeout(timer);
  }
}
