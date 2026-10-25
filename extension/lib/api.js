/** Talks to the local app. Every change carries the app's header (the API refuses changes without it). */
const DEFAULT_BASE = 'http://localhost:8081'

export async function baseUrl() {
  try {
    const { base } = await chrome.storage.local.get('base')
    return base || DEFAULT_BASE
  } catch {
    return DEFAULT_BASE
  }
}

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

export async function api(path, { method = 'GET', body } = {}) {
  const base = await baseUrl()
  let response
  try {
    response = await fetch(`${base}/api${path}`, {
      method,
      headers: {
        ...(method === 'GET' ? {} : { 'X-Requested-With': 'job-search-assistant' }),
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, `Can't reach the app at ${base}. Is it running? (docker compose up -d)`)
  }
  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new ApiError(response.status, data?.detail ?? `The app answered ${response.status}.`)
  return data
}

/** The resume as base64, for the form filler to attach. */
export async function resumeFile() {
  const base = await baseUrl()
  const response = await fetch(`${base}/api/profile/resume`)
  if (!response.ok) return null
  const blob = await response.blob()
  const disposition = response.headers.get('Content-Disposition') || ''
  const fileName = disposition.match(/filename="?([^";]+)"?/)?.[1] || 'resume.pdf'
  const bytes = new Uint8Array(await blob.arrayBuffer())
  let binary = ''
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000))
  return { base64: btoa(binary), fileName, type: blob.type || 'application/pdf' }
}
