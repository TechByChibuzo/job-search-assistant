/**
 * The link if it is an absolute http(s) URL, otherwise null. Every external href goes through this, so a
 * bad value from a feed or an old record can never become a javascript: link. The server checks too.
 */
export function safeHref(url) {
  return typeof url === 'string' && /^https?:\/\//i.test(url.trim()) ? url.trim() : null
}
