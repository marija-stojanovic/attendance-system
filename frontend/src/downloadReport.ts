export async function downloadReport(apiUrl: string, token: string, path: string, filename: string): Promise<void> {
  let response: Response;
  try {
    response = await fetch(apiUrl + path, {
      cache: 'no-store',
      headers: { Authorization: 'Bearer ' + token }
    });
  } catch {
    throw new Error('Preuzimanje nije uspjelo. Provjerite vezu i pokušajte ponovo.');
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || problem.message || 'Fajl nije dostupan. Pokušajte ponovo.');
  }
  let file: Blob;
  try {
    file = await response.blob();
  } catch {
    throw new Error('Preuzimanje je prekinuto. Pokušajte ponovo.');
  }
  if (file.size === 0) throw new Error('Fajl je prazan. Pokušajte ponovo.');
  const url = URL.createObjectURL(file);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
