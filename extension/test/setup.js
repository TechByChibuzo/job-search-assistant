import { readFileSync } from 'node:fs'
import { join } from 'node:path'

// jsdom lacks CSS.escape; Chrome has it
globalThis.CSS ??= {}
globalThis.CSS.escape ??= (s) => String(s).replace(/["\\]/g, '\\$&')

/** Loads a content script the way Chrome injects it: as a classic script in the page's global scope. */
export function loadContentScript(name) {
  // Tests run from the extension folder; import.meta.url isn't a file URL under jsdom
  const code = readFileSync(join(process.cwd(), 'content', name), 'utf8')
  new Function(code)()
}

// jsdom does no layout: give every element a plausible box, unless a test sets data-rect="x,y,w,h"
Element.prototype.getBoundingClientRect = function () {
  const [x, y, w, h] = (this.getAttribute('data-rect') || '10,10,200,24').split(',').map(Number)
  return { x, y, left: x, top: y, width: w, height: h, right: x + w, bottom: y + h }
}
