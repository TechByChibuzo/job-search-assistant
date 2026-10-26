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

  const US_STATES = {
    AL: 'alabama', AK: 'alaska', AZ: 'arizona', AR: 'arkansas', CA: 'california', CO: 'colorado', CT: 'connecticut',
    DE: 'delaware', FL: 'florida', GA: 'georgia', HI: 'hawaii', ID: 'idaho', IL: 'illinois', IN: 'indiana', IA: 'iowa',
    KS: 'kansas', KY: 'kentucky', LA: 'louisiana', ME: 'maine', MD: 'maryland', MA: 'massachusetts', MI: 'michigan',
    MN: 'minnesota', MS: 'mississippi', MO: 'missouri', MT: 'montana', NE: 'nebraska', NV: 'nevada', NH: 'new hampshire',
    NJ: 'new jersey', NM: 'new mexico', NY: 'new york', NC: 'north carolina', ND: 'north dakota', OH: 'ohio',
    OK: 'oklahoma', OR: 'oregon', PA: 'pennsylvania', RI: 'rhode island', SC: 'south carolina', SD: 'south dakota',
    TN: 'tennessee', TX: 'texas', UT: 'utah', VT: 'vermont', VA: 'virginia', WA: 'washington', WV: 'west virginia',
    WI: 'wisconsin', WY: 'wyoming', DC: 'district of columbia',
  }

  function norm(s) {
    return String(s ?? '').toLowerCase().replace(/[^a-z0-9&]+/g, ' ').trim()
  }

  /** "TX" or "Texas" → "texas"; anything else as it is. */
  function stateName(state) {
    const s = String(state ?? '').trim()
    return US_STATES[s.toUpperCase()] ?? norm(s)
  }

  function isCombobox(el) {
    return el.getAttribute('role') === 'combobox' || el.getAttribute('aria-autocomplete') === 'list'
  }

  /** The visible box around a type-to-search input, which itself can be a couple of pixels wide until typed in. */
  function comboboxControl(el) {
    return el.closest('[class*="control"], [class*="select"]:not(input)') ?? el.parentElement
  }

  /** Whether the list already has a choice (made by the owner, or by the site): never replaced. */
  function hasChoice(el) {
    const control = comboboxControl(el)
    return Boolean(control?.querySelector('[class*="single-value"], [class*="multi-value"]')) || Boolean(el.value)
  }

  /**
   * What to search for in a pick-list, and how to recognise the right suggestion, from the profile. Only plain
   * facts are picked: country, city, school and degree. The nth school or degree list on a form gets the nth of
   * the profile's (most recent first).
   */
  function pickFor(key, p, seen) {
    const nth = (k) => (seen[k] = (seen[k] ?? -1) + 1)
    switch (key) {
      case 'country': {
        // An empty country with a US state means the United States
        const inUs = Object.hasOwn(US_STATES, String(p.state ?? '').trim().toUpperCase()) || Object.values(US_STATES).includes(norm(p.state))
        const country = p.country || (inUs ? 'United States' : '')
        // Phone country lists read "United States +1"; others say "United States of America" or "USA"
        const names = new Set([norm(country)])
        if (['united states', 'united states of america', 'usa', 'us'].includes(norm(country))) {
          ;['united states', 'united states of america', 'usa', 'us'].forEach((n) => names.add(n))
        }
        return country && { query: country, score: (t) => (names.has(t.replace(/\s*\d+$/, '')) ? 2 : 0) }
      }
      case 'city':
      case 'location': {
        if (!p.city) return null
        const state = stateName(p.state)
        return { query: p.city, score: (t) => (t.startsWith(norm(p.city)) && (!state || t.includes(state)) ? 2 : 0) }
      }
      case 'school': {
        const school = [p.school, p.previousSchool][nth('school')]
        if (!school) return null
        // Lists name campuses ("Texas A&M University - College Station"): the profile's school must name the
        // same words to be chosen; a bare "Texas A&M University" matches several campuses and is left alone
        const words = new Set(norm(school).split(' '))
        const name = school.replace(/\s*[-–(,].*$/, '').trim()
        const base = norm(name)
        return {
          query: name,
          ambiguous: 'Several campuses match: choose yours, or add the campus to the school in your Profile',
          score: (t) => {
            const option = t.split(' ')
            if (option.length === words.size && option.every((w) => words.has(w))) return 2
            return t.startsWith(`${base} `) || t === base ? 1 : 0
          },
        }
      }
      case 'degree': {
        const degree = [p.degree, p.previousDegree][nth('degree')]
        const level = /master|m\.?s\b|mba/i.test(degree ?? '')
          ? 'master'
          : /bachelor|b\.?s\b|b\.?a\b/i.test(degree ?? '')
            ? 'bachelor'
            : /ph\.?d|doctor/i.test(degree ?? '')
              ? 'doctor'
              : /associate/i.test(degree ?? '')
                ? 'associate'
                : null
        // The plain "Master's Degree" beats a named one ("Master of Business Administration")
        return level && { query: level, score: (t) => (t === `${level} s degree` || t === `${level} degree` ? 2 : t.startsWith(level) ? 1 : 0) }
      }
      default:
        return null
    }
  }

  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

  function setRaw(el, text) {
    const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value')?.set
    if (setter) setter.call(el, text)
    else el.value = text
  }

  /**
   * Types into a search field one key at a time, with the events real typing fires, without leaving it:
   * Greenhouse's lists only open their suggestions for keystrokes, not for a value set in one go.
   */
  async function typeKeys(el, text) {
    let typed = ''
    for (const ch of text) {
      el.dispatchEvent(new KeyboardEvent('keydown', { key: ch, bubbles: true, cancelable: true }))
      typed += ch
      setRaw(el, typed)
      el.dispatchEvent(new InputEvent('input', { inputType: 'insertText', data: ch, bubbles: true }))
      el.dispatchEvent(new KeyboardEvent('keyup', { key: ch, bubbles: true }))
      await sleep(25)
    }
  }

  /** Empties the search and closes the list, which clearing itself can open, so it closes after a moment. */
  async function clearSearch(el) {
    setRaw(el, '')
    el.dispatchEvent(new InputEvent('input', { inputType: 'deleteContentBackward', bubbles: true }))
    await sleep(60)
    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    el.dispatchEvent(new FocusEvent('focusout', { bubbles: true }))
    el.blur()
  }

  /**
   * The field's own suggestions: from the list it points to, or else the menu drawn inside its own container.
   * Never another list on the page (a phone number's country codes).
   */
  function optionsOf(doc, el) {
    const id = el.getAttribute('aria-controls')
    const listbox = id && doc.getElementById(id)
    if (listbox) return [...listbox.querySelectorAll('[role="option"]')]
    const container = comboboxControl(el)?.parentElement
    return container ? [...container.querySelectorAll('[role="option"], [class*="__option"]')] : []
  }

  /**
   * Searches a pick-list and chooses the one best suggestion, waiting for suggestions that load from the network.
   * Chooses nothing, and clears the search, when no suggestion matches or two match equally well.
   * @returns true when chosen, or why not ('no-match' or the ambiguity message)
   */
  async function pick(doc, el, { query, score, ambiguous }, waitMs) {
    el.focus()
    el.dispatchEvent(new FocusEvent('focusin', { bubbles: true }))
    await typeKeys(el, query)
    let ranked = []
    for (let waited = 0; waited < waitMs; waited += 150) {
      await sleep(150)
      ranked = optionsOf(doc, el)
        .map((o) => ({ o, s: score(norm(o.textContent)) }))
        .filter(({ s }) => s > 0)
        .sort((a, b) => b.s - a.s)
      if (ranked[0]?.s === 2) break
    }
    const best = ranked[0]
    if (!best || (ranked[1] && ranked[1].s === best.s)) {
      await clearSearch(el)
      return best ? ambiguous || 'Several suggestions match: choose yours' : 'no-match'
    }
    best.o.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    best.o.click()
    await sleep(150)
    return true
  }

  const declined = (t) => /decline|don t wish|do not wish|prefer not|choose not|not to answer/.test(t)

  /**
   * Equal-opportunity questions the owner chose to answer in their profile: how to recognise each question, and
   * each saved answer among a form's options. Unset answers, and every other sensitive question (work
   * authorization, sponsorship, gender identity, disability, race), stay with the owner.
   */
  const EEO = [
    {
      key: 'eeoGender',
      question: /\bgender\b|\bsex\b/i,
      except: /identity|transgender|pronoun|orientation/i,
      answers: {
        Male: { queries: ['Male', 'Man'], match: (t) => t === 'male' || t === 'man' },
        Female: { queries: ['Female', 'Woman'], match: (t) => t === 'female' || t === 'woman' },
        'Non-binary': { queries: ['Non'], match: (t) => /^non ?binary/.test(t) },
        Decline: { queries: ['Decline', 'wish', 'Prefer'], match: declined },
      },
    },
    {
      key: 'eeoHispanicLatino',
      question: /hispanic|latin[oax]/i,
      answers: {
        Yes: { queries: ['Yes'], match: (t) => t === 'yes' || t.startsWith('yes ') },
        No: { queries: ['No'], match: (t) => t === 'no' || t.startsWith('no ') },
        Decline: { queries: ['Decline', 'wish', 'Prefer'], match: declined },
      },
    },
    {
      key: 'eeoVeteran',
      question: /veteran/i,
      answers: {
        'Not a protected veteran': { queries: ['not', 'No'], match: (t) => /\bnot a (protected )?veteran\b/.test(t) || t === 'no' },
        'Protected veteran': {
          queries: ['identify', 'protected', 'Yes'],
          match: (t) => /identify as (one or more|a protected)|^i am a protected veteran/.test(t) || t === 'yes',
        },
        Decline: { queries: ['wish', 'Decline', 'Prefer'], match: declined },
      },
    },
  ]

  /** The owner's saved answer for an equal-opportunity question, as { queries, score }, or null. */
  function eeoFor(questionText, p) {
    const q = EEO.find((e) => e.question.test(questionText) && !(e.except && e.except.test(questionText)))
    const answer = q && p[q.key] && q.answers[p[q.key]]
    return answer ? { queries: answer.queries, score: (t) => (answer.match(t) ? 2 : 0) } : null
  }

  /** Chooses the matching option of a plain dropdown. */
  function chooseOption(el, { score }) {
    const option = [...el.options].find((o) => score(norm(o.textContent)) > 0)
    if (!option) return false
    el.value = option.value
    el.dispatchEvent(new Event('input', { bubbles: true }))
    el.dispatchEvent(new Event('change', { bubbles: true }))
    return true
  }

  /** Clicks the matching radio button of the group a radio belongs to. */
  function chooseRadio(doc, el, { score }) {
    const group = el.name ? [...doc.querySelectorAll(`input[type="radio"][name="${CSS.escape(el.name)}"]`)] : [el]
    const radio = group.find((r) => score(norm(labelText(doc, r) || r.value)) > 0)
    if (!radio) return false
    radio.click()
    return true
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
  /** @param options.waitMs how long to wait for a pick-list's suggestions to load */
  async function fill(doc, profile, resume, answers = {}, { waitMs = 5000 } = {}) {
    const filled = []
    const answered = []
    const needsYou = []
    const toChoose = []
    const seenQuestions = new Set()
    const handled = new Set()
    const picked = {}

    for (const el of doc.querySelectorAll('input, textarea, select')) {
      if (!(isCombobox(el) ? visible(comboboxControl(el)) && !el.disabled : visible(el))) continue
      const type = (el.getAttribute('type') || '').toLowerCase()
      const description = describe(doc, el)
      const label = labelText(doc, el) || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.name || ''

      const choice = type === 'checkbox' || type === 'radio'
      // A sensitive question can hide behind innocent options ("She/her"), so check the question too
      const question = choice ? questionOf(doc, el) : label
      if (SENSITIVE.test(description) || (choice && SENSITIVE.test(question))) {
        if (handled.has(question)) continue
        // An equal-opportunity answer the owner saved is chosen; anything else is left for them
        const eeo = !seenQuestions.has(question) && eeoFor(choice ? question : label || description, profile)
        if (eeo) {
          let done = false
          if (choice && type === 'radio') done = chooseRadio(doc, el, eeo)
          else if (el.tagName === 'SELECT') done = chooseOption(el, eeo)
          else if (isCombobox(el)) {
            done = hasChoice(el)
            for (const query of eeo.queries) {
              if (done) break
              done = (await pick(doc, el, { query, score: eeo.score }, waitMs)) === true
            }
          }
          if (done) {
            handled.add(question)
            mark(isCombobox(el) ? (comboboxControl(el)?.parentElement ?? el) : el, '#18794e')
            filled.push(question || label)
            continue
          }
        }
        if (!seenQuestions.has(question)) needsYou.push(question || 'A question')
        seenQuestions.add(question)
        mark(el, '#d97706')
        continue
      }
      // A search-and-pick list only takes a choice from its own suggestions: for a plain fact (country, city,
      // school, degree) the matching suggestion is chosen; anything else, or no match, is left for the owner
      if (isCombobox(el)) {
        if (hasChoice(el)) continue
        const want = pickFor(keyFor(el, description), profile, picked)
        const outcome = want ? await pick(doc, el, want, waitMs) : 'no-match'
        if (outcome === true) {
          // The list's own styling covers an outline on the field, so the box around it carries the mark
          mark(comboboxControl(el)?.parentElement ?? el, '#18794e')
          filled.push(label)
        } else if (label && !toChoose.includes(label) && !toChoose.includes(`${label}: ${outcome}`)) {
          toChoose.push(outcome === 'no-match' ? label : `${label}: ${outcome}`)
        }
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

  /**
   * Types one answer the owner approved into the long-answer box whose question it is, outlined in blue.
   * Never into a box that already has text, a sensitive question, or a box the owner can't see.
   */
  function insertAnswer(doc, question, text) {
    for (const el of doc.querySelectorAll('textarea')) {
      const label = labelText(doc, el) || el.getAttribute('aria-label') || el.getAttribute('placeholder') || ''
      if (label !== question || !visible(el) || SENSITIVE.test(describe(doc, el))) continue
      if (el.value) return 'not-empty'
      setValue(el, text)
      mark(el, '#2a78d6')
      return 'inserted'
    }
    return 'not-found'
  }

  /** Addresses of the frames on the page, to spot a hiring system's form embedded from another site. */
  function frames(doc) {
    return [...doc.querySelectorAll('iframe')].map((f) => f.src).filter((src) => /^https:/i.test(src))
  }

  globalThis.JSA_FILL = { fill, questions, insertAnswer, frames, keyFor, describe, SENSITIVE }
})()
