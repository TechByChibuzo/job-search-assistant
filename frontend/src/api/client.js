/** An API error carrying the server's problem-detail message, ready to show to the owner. */
export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

/**
 * Every request that changes data carries this header. Other sites can't add it without the API's
 * permission, which it never gives, so they can't trigger changes through your browser (CSRF).
 */
const APP_HEADER = { 'X-Requested-With': 'job-search-assistant' }

export async function api(path, { method = 'GET', body } = {}) {
  const headers = {
    ...(method === 'GET' ? {} : APP_HEADER),
    ...(body ? { 'Content-Type': 'application/json' } : {}),
  }
  const response = await fetch(`/api${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })
  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(response.status, data?.detail ?? `The server answered ${response.status}.`)
  }
  return data
}

/** Builds a query string, leaving out empty values and repeating arrays (workplace=REMOTE&workplace=HYBRID). */
export function toQuery(params) {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '' || value === false) continue
    for (const v of Array.isArray(value) ? value : [value]) search.append(key, String(v))
  }
  const s = search.toString()
  return s ? `?${s}` : ''
}
