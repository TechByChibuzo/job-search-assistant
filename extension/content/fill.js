/*
 * Fills the application form on the page from the owner's profile, once, when they click "Fill". It types
 * into empty text fields it recognises, puts saved answers into long-answer boxes whose question matches
 * one in the owner's answer bank, and attaches the resume. It never answers work authorization,
 * sponsorship, EEO, consent or similar questions: those are outlined for the owner. It never submits.
 * Injected on demand (activeTab); defines globalThis.JSA_FILL for the popup to call.
 */
;(function () {
  /** Questions only the owner may answer. Matched against a field's label, name and hints. */
  const SENSITIVE =
    /authori[sz]|sponsor|visa|citizen|immigration|gender|\bsex\b|race|racial|ethnic|hispanic|latin[oax]|veteran|disab|pronoun|orientation|transgender|criminal|convict|background check|consent|acknowledg|certify|attest|signature|salary|compensation|pay expectation/i

  /** Which profile value a field wants, from its description. Order matters: first match wins. */
  const RULES = [
    ['linkedinUrl', /linked\s*-?in/i],
    ['githubUrl', /git\s*hub/i],
    ['websiteUrl', /website|portfolio|personal (site|url|page)|\bblog\b/i],
    ['firstName', /first[\s_-]*name|given[\s_-]*name|\bfname\b|preferred first/i],
    ['lastName', /last[\s_-]*name|family[\s_-]*name|surname|\blname\b/i],
    ['email', /e-?mail/i],
    ['phone', /phone|mobile|\btel\b|telephone/i],
    ['currentCompany', /current (company|employer)|^company$|\bemployer\b|organi[sz]ation\b|\borg\b/i],
    ['currentTitle', /current (title|role|position)|job title|^title$/i],
    ['school', /school|university|college|institution/i],
    ['degree', /\bdegree\b/i],
    ['graduationYear', /graduat/i],
    // "What city and state do you live in?" wants both, so it comes before city alone
    ['location', /\bcity\b,?\s*(and|&|\/|or)?\s*\bstate\b/i],
    ['city', /\bcity\b|\btown\b/i],
    ['state', /\bstate\b(?!ment)|province/i],
    ['country', /\bcountry\b/i],
    ['location', /location|where (are you|do you) (based|live|located)|current address/i],
    ['fullName', /full[\s_-]*name|legal name|your name|^name$|^name\b|candidate name/i],
  ]

  const AUTOCOMPLETE = {
    'given-name': 'firstName',
    'family-name': 'lastName',
    name: 'fullName',
    email: 'email',
    tel: 'phone',
    organization: 'currentCompany',
    'organization-title': 'currentTitle',
    'address-level2': 'city',
    'address-level1': 'state',
    'country-name': 'country',
    url: 'websiteUrl',
  }

  const TEXT_TYPES = new Set(['', 'text', 'email', 'tel', 'url', 'search'])

  const CONTROLS = 'input, select, textarea'

  /**
   * A label's own words, without the field inside it: Lever wraps the input, its dropdown and hints inside
   * the <label>, so the plain text would read "Current location ✱ No location found…".
   */
  function ownText(label) {
    const copy = label.cloneNode(true)
    for (const child of [...copy.children]) {
      if (child.matches(CONTROLS) || child.querySelector(CONTROLS)) child.remove()
    }
    return copy.textContent
  }

  function clean(text) {
    return text.replace(/[✱*]/g, '').replace(/\s+/g, ' ').trim()
  }

  const TITLE = 'legend, .application-label, [class*="label"], [class*="question-title"], h3, h4'

  /**
   * For a checkbox or radio, the question it belongs to ("Pronouns"), not the option ("She/her"): the
   * nearest ancestor with a title element beside the options, rather than around them.
   */
  function questionOf(doc, el) {
    let node = el.parentElement
    for (let depth = 0; node && depth < 8; depth++, node = node.parentElement) {
      const title = [...node.children].find((c) => c.matches(TITLE) && !c.contains(el) && !c.querySelector(CONTROLS))
      if (title && clean(title.textContent)) return clean(title.textContent)
    }
    return clean(labelText(doc, el))
  }

  function labelText(doc, el) {
    const parts = []
    if (el.id) {
      const forLabel = doc.querySelector(`label[for="${CSS.escape(el.id)}"]`)
      if (forLabel) parts.push(ownText(forLabel))
    }
    const wrapping = el.closest('label')
    if (wrapping) parts.push(ownText(wrapping))
    const labelledBy = el.getAttribute('aria-labelledby')
    if (labelledBy) {
      labelledBy.split(/\s+/).forEach((id) => {
        const ref = doc.getElementById(id)
        if (ref) parts.push(ref.textContent)
      })
    }
    // Ashby, Workday and others put the question in a nearby element rather than a <label>
    // Start from the parent: the input itself may carry the same attributes as its container
    const field = el.parentElement?.closest('[data-automation-id], .application-question, .field, fieldset, [class*="question"]')
    if (!parts.length && field) {
      const heading = field.querySelector('label, legend, [class*="label"], [data-automation-id*="label"]')
      if (heading) parts.push(heading.textContent)
    }
    // A field can point at the same label twice (a <label for> and aria-labelledby); say it once
    return clean([...new Set(parts.map(clean))].join(' '))
  }

  /** "addressSection_city" → "address Section city", so machine names match by word too. */
  function words(s) {
    return s.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/[_\-[\].]+/g, ' ')
  }

  function describe(doc, el) {
    const machine = [el.getAttribute('name'), el.id, el.getAttribute('data-automation-id')].filter(Boolean).map(words)
    return [labelText(doc, el), el.getAttribute('aria-label'), el.getAttribute('placeholder'), ...machine]
      .filter(Boolean)
      .join(' | ')
  }

  function keyFor(el, description) {
    const auto = (el.getAttribute('autocomplete') || '').trim().toLowerCase()
    if (AUTOCOMPLETE[auto]) return AUTOCOMPLETE[auto]
    for (const [key, pattern] of RULES) {
      if (pattern.test(description)) return key
    }
    return null
  }

  function valueFor(key, p) {
    switch (key) {
      case 'fullName':
        return [p.firstName, p.lastName].filter(Boolean).join(' ')
      case 'location':
        return [p.city, p.state].filter(Boolean).join(', ')
      default:
        return p[key] || ''
    }
  }

  /** Sets a value so React and other frameworks notice: the native setter, then the events a user's typing fires. */
  function setValue(el, value) {
    const proto = Object.getPrototypeOf(el)
    const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set
    if (setter) setter.call(el, value)
    else el.value = value
    for (const type of ['input', 'change', 'blur']) el.dispatchEvent(new Event(type, { bubbles: true }))
  }

  /**
   * Whether the owner can actually see a field. A hostile page could hide fields (transparent, tiny,
   * off-screen, or inside a hidden box) to collect details the owner never sees being typed, so only
   * fields with a real size, inside the page, with nothing transparent or hidden around them are filled.
   */
  function visible(el) {
    if (el.disabled || el.readOnly || el.type === 'hidden') return false
    if (typeof el.checkVisibility === 'function' && !el.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true })) {
      return false
    }
    const view = el.ownerDocument.defaultView
    for (let node = el; node && node.nodeType === 1; node = node.parentElement) {
      const style = view.getComputedStyle(node)
      if (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity) === 0) return false
    }
    const rect = el.getBoundingClientRect()
    const page = el.ownerDocument.documentElement
    const pageWidth = Math.max(page.scrollWidth, view.innerWidth)
    const pageHeight = Math.max(page.scrollHeight, view.innerHeight)
    const onPage = rect.right + view.scrollX > 0 && rect.bottom + view.scrollY > 0 && rect.left + view.scrollX < pageWidth && rect.top + view.scrollY < pageHeight
    return rect.width >= 4 && rect.height >= 4 && onPage
  }

  function mark(el, colour) {
    el.style.outline = `2px solid ${colour}`
    el.style.outlineOffset = '2px'
  }

  function attachResume(doc, resume) {
    const inputs = [...doc.querySelectorAll('input[type="file"]')]
    if (!inputs.length) return 'no-upload-field'
    const described = inputs.map((el) => ({ el, d: describe(doc, el) }))
    const target =
      described.find(({ d }) => /resume|\bcv\b|curriculum/i.test(d) && !/cover/i.test(d)) ||
      (described.length === 1 && !/cover/i.test(described[0].d) ? described[0] : null)
    if (!target) return 'not-found'
    if (!resume) return 'not-attached'
    if (typeof DataTransfer === 'undefined') return 'unsupported'
    if (target.el.files && target.el.files.length) return 'already-attached'
    const bytes = Uint8Array.from(atob(resume.base64), (c) => c.charCodeAt(0))
    const transfer = new DataTransfer()
    transfer.items.add(new File([bytes], resume.fileName, { type: resume.type }))
    target.el.files = transfer.files
    target.el.dispatchEvent(new Event('input', { bubbles: true }))
    target.el.dispatchEvent(new Event('change', { bubbles: true }))
    mark(target.el, '#18794e')
    return 'attached'
  }

  /** The long-answer boxes the owner can see, empty and not sensitive: the questions to look up in the answer bank. */
  function questions(doc) {
    const found = []
    for (const el of doc.querySelectorAll('textarea')) {
      if (!visible(el) || el.value) continue
      const label = labelText(doc, el) || el.getAttribute('aria-label') || el.getAttribute('placeholder') || ''
      if (label && !SENSITIVE.test(describe(doc, el)) && !found.includes(label)) found.push(label)
    }
    return found
  }

  /**
   * @param profile the owner's profile fields
   * @param resume  { base64, fileName, type }, or null to leave the upload to the owner
   * @param answers saved answers by the question they match on this page ({ question: { answerId, answer } })
   * @returns what was filled, which saved answers were used, what was left for the owner, and whether the
   *          resume was attached
   */
  function fill(doc, profile, resume, answers = {}) {
    const filled = []
    const answered = []
    const needsYou = []
    const toChoose = []
    const seenQuestions = new Set()

    for (const el of doc.querySelectorAll('input, textarea, select')) {
      if (!visible(el)) continue
      const type = (el.getAttribute('type') || '').toLowerCase()
      const description = describe(doc, el)
      const label = labelText(doc, el) || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.name || ''

      const choice = type === 'checkbox' || type === 'radio'
      // A sensitive question can hide behind innocent options ("She/her"), so check the question too
      const question = choice ? questionOf(doc, el) : label
      if (SENSITIVE.test(description) || (choice && SENSITIVE.test(question))) {
        if (!seenQuestions.has(question)) needsYou.push(question || 'A question')
        seenQuestions.add(question)
        mark(el, '#d97706')
        continue
      }
      // A search-and-pick list (country, city, school) only takes a choice from its own list: typing into it
      // selects nothing, so it's left for the owner and named
      if (el.getAttribute('role') === 'combobox' || el.getAttribute('aria-autocomplete') === 'list') {
        if (!el.value && label && !toChoose.includes(label)) toChoose.push(label)
        continue
      }
      // A long answer is filled only from the answer bank, outlined in blue for the owner to read
      if (el.tagName === 'TEXTAREA') {
        const saved = Object.hasOwn(answers, label) ? answers[label] : null
        if (saved && !el.value) {
          setValue(el, saved.answer)
          mark(el, '#2a78d6')
          answered.push({ question: label, answerId: saved.answerId })
        }
        continue
      }
      // Only plain text fields are filled; choices and checkboxes stay with the owner
      if (el.tagName !== 'INPUT' || !TEXT_TYPES.has(type) || el.value) continue
      const key = keyFor(el, description)
      const value = key && valueFor(key, profile)
      if (!value) continue
      setValue(el, value)
      mark(el, '#18794e')
      filled.push(label || key)
    }

    return { filled, answered, needsYou, toChoose, resume: attachResume(doc, resume), pageTitle: doc.title }
  }

  /** Addresses of the frames on the page, to spot a hiring system's form embedded from another site. */
  function frames(doc) {
    return [...doc.querySelectorAll('iframe')].map((f) => f.src).filter((src) => /^https:/i.test(src))
  }

  globalThis.JSA_FILL = { fill, questions, frames, keyFor, describe, SENSITIVE }
})()
