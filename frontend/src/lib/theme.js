import { useEffect, useState } from 'react'

const KEY = 'theme'

function read() {
  try {
    return localStorage.getItem(KEY) ?? 'system'
  } catch {
    return 'system'
  }
}

/** 'system', 'light' or 'dark'. Pinning a theme sets data-theme on <html>; the CSS tokens do the rest. */
export function useTheme() {
  const [theme, setTheme] = useState(read)
  useEffect(() => {
    const root = document.documentElement
    if (theme === 'system') root.removeAttribute('data-theme')
    else root.setAttribute('data-theme', theme)
    try {
      localStorage.setItem(KEY, theme)
    } catch {
      // Storage can be blocked; the theme still applies for this visit
    }
  }, [theme])
  return [theme, setTheme]
}
