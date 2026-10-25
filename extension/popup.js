import { api, baseUrl, resumeFile } from './lib/api.js'
import { classify, knownSystem } from './lib/pages.js'

const view = document.getElementById('view')

const KINDS = [
  ['ENGINEER', 'Engineer on the team'],
  ['RECRUITER', 'Recruiter'],
  ['HIRING_MANAGER', 'Hiring manager'],
  ['ALUMNI', 'Alumni'],
  ['OTHER', 'Other'],
]

/** Builds DOM from a template; values are always set as text, never parsed as HTML. */
function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag)
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') el.className = v
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v)
    else if (v !== undefined && v !== null && v !== false) el.setAttribute(k, v === true ? '' : v)
  }
  for (const c of children.flat()) if (c != null) el.append(c instanceof Node ? c : document.createTextNode(String(c)))
  return el
}

function show(...nodes) {
  view.replaceChildren(...nodes)
}

function note(kind, ...content) {
  return h('div', { class: `note ${kind}` }, ...content)
}

function field(label, input) {
  return h('label', {}, label, input)
}

/** Runs one of the content scripts in the current tab and calls it. The page is read once, now. */
async function inPage(tabId, file, fn, args = []) {
  await chrome.scripting.executeScript({ target: { tabId }, files: [file] })
  const [result] = await chrome.scripting.executeScript({ target: { tabId }, func: fn, args })
  return result?.result
}

async function saveProfileView(tab) {
  const person = await inPage(tab.id, 'content/linkedin.js', () => globalThis.JSA_LINKEDIN.profile(document, location.href))
  const companies = await api('/companies')
  const name = h('input', { value: person.name, required: true })
  const title = h('input', { value: person.title })
  const company = h('input', { value: person.company, list: 'companies', required: true })
  const kind = h('select', {}, KINDS.map(([v, l]) => h('option', { value: v }, l)))
  const notes = h('textarea', { rows: 2, placeholder: 'How you know them, what you have in common (e.g. Purdue)' })
  const save = h('button', { type: 'submit' }, 'Save person')
  const status = h('div')

  const form = h(
    'form',
    {
      onsubmit: async (e) => {
        e.preventDefault()
        save.disabled = true
        const known = companies.find((c) => c.name.toLowerCase() === company.value.trim().toLowerCase())
        try {
          const saved = await api('/contacts', {
            method: 'POST',
            body: {
              ...(known ? { companyId: known.id } : { companyName: company.value.trim() }),
              name: name.value.trim(),
              title: title.value.trim() || undefined,
              kind: kind.value,
              linkedinUrl: person.linkedinUrl,
              notes: notes.value.trim() || undefined,
            },
          })
          status.replaceChildren(note('ok', `Saved ${saved.name} at ${saved.companyName}. Find them under People.`))
        } catch (err) {
          status.replaceChildren(note('error', err.message))
          save.disabled = false
        }
      },
    },
    h('h1', {}, 'Save this person'),
    h('p', { class: 'faint' }, 'Check the details read from the page.'),
    field('Name', name),
    h('div', { class: 'row' }, field('Title', title), field('Company', company)),
    h('datalist', { id: 'companies' }, companies.map((c) => h('option', { value: c.name }))),
    field('Who they are', kind),
    field('Notes', notes),
    save,
    status,
  )
  show(form)
}

async function addRoleView(tab) {
  const role = await inPage(tab.id, 'content/linkedin.js', () => globalThis.JSA_LINKEDIN.job(document, location.href))
  const title = h('input', { value: role.title, required: true })
  const company = h('input', { value: role.company, required: true })
  const location = h('input', { value: role.location })
  const add = h('button', { type: 'submit' }, 'Add to my inbox')
  const status = h('div')
  const base = await baseUrl()

  show(
    h(
      'form',
      {
        onsubmit: async (e) => {
          e.preventDefault()
          add.disabled = true
          try {
            const res = await api('/jobs', {
              method: 'POST',
              body: { url: role.url, title: title.value.trim(), company: company.value.trim(), location: location.value.trim() || undefined },
            })
            status.replaceChildren(
              note(
                'ok',
                res.alreadySaved ? 'Already in your inbox. ' : 'Added. ',
                h('a', { href: `${base}/jobs?job=${res.job.id}`, target: '_blank' }, 'Open it in the app'),
              ),
            )
          } catch (err) {
            status.replaceChildren(note('error', err.message))
            add.disabled = false
          }
        },
      },
      h('h1', {}, 'Add this role'),
      h('p', { class: 'faint' }, 'It joins your inbox, where you can check your fit and track it.'),
      field('Title', title),
      h('div', { class: 'row' }, field('Company', company), field('Location', location)),
      add,
      status,
    ),
  )
}

async function fillView(tab) {
  const system = knownSystem(tab.url)
  const profile = await api('/profile')
  const hasProfile = profile.fields && (profile.fields.email || profile.fields.firstName)
  const base = await baseUrl()
  const fillButton = h('button', {}, 'Fill this application')
  const result = h('div')

  if (!hasProfile) {
    show(
      h('h1', {}, 'Fill this application'),
      note('warn', 'Your profile is empty. ', h('a', { href: `${base}/profile`, target: '_blank' }, 'Fill it in the app'), ' first; it can read your resume.'),
    )
    return
  }

  // On a hiring system the app knows, attach the resume; anywhere else, only if the owner ticks the box,
  // so an unfamiliar page can't collect the resume through a hidden upload
  const attach = h('input', { type: 'checkbox', checked: Boolean(system) })
  const attachLabel = h('label', { class: 'checkbox' }, attach, ` Attach my resume (${profile.resumeFileName ?? 'none in the app'})`)

  fillButton.addEventListener('click', async () => {
    fillButton.disabled = true
    fillButton.textContent = 'Filling…'
    try {
      const resume = attach.checked ? await resumeFile() : null
      const answers = await savedAnswers(tab)
      const report = await inPage(tab.id, 'content/fill.js', (p, r, a) => globalThis.JSA_FILL.fill(document, p, r, a), [
        profile.fields,
        resume,
        answers,
      ])
      // Counts toward which answers rise to the top of the bank; a failure here doesn't undo the fill
      report.answered.forEach((a) => api(`/answers/${a.answerId}/used`, { method: 'POST' }).catch(() => {}))
      result.replaceChildren(...reportView(report, profile.resumeFileName), recordForm(tab, report, profile.resumeFileName))
      fillButton.textContent = 'Fill again'
    } catch (err) {
      result.replaceChildren(note('error', err.message))
      fillButton.textContent = 'Fill this application'
    }
    fillButton.disabled = false
  })

  show(
    h('h1', {}, 'Fill this application'),
    h(
      'p',
      { class: 'muted' },
      system ? `${system} form. ` : '',
      'Fills your details into the fields you can see. Questions about work authorization, sponsorship, EEO or consent are left for you.',
    ),
    system ? null : h('p', { class: 'faint' }, `${new URL(tab.url).hostname} isn’t a hiring system the app knows; check it’s the real employer before attaching your resume.`),
    attachLabel,
    fillButton,
    result,
  )
}

/** The page's long-answer questions, matched against the answer bank: { question: { answerId, answer } }. */
async function savedAnswers(tab) {
  const questions = await inPage(tab.id, 'content/fill.js', () => globalThis.JSA_FILL.questions(document))
  if (!questions?.length) return {}
  const matches = await api('/answers/match', { method: 'POST', body: { questions: questions.slice(0, 50) } })
  return Object.fromEntries(matches.map((m) => [m.question, { answerId: m.answerId, answer: m.answer }]))
}

function reportView(report, resumeFileName) {
  const resumeLine = {
    attached: `Attached ${resumeFileName}.`,
    'already-attached': 'A resume was already attached.',
    'not-found': 'Couldn’t tell which upload is the resume; attach it yourself.',
    'no-upload-field': 'No resume upload on this page.',
    unsupported: 'Attach your resume yourself.',
    'not-attached': 'Resume not attached (you left it unticked).',
  }[report.resume]
  const nodes = [
    note(
      report.filled.length ? 'ok' : 'warn',
      report.filled.length ? `Filled ${report.filled.length} field${report.filled.length === 1 ? '' : 's'} (outlined in green). ` : 'Found no fields it recognises on this page. ',
      resumeLine,
    ),
  ]
  if (report.answered.length) {
    nodes.push(
      note(
        'info',
        h('strong', {}, 'From your answer bank (outlined in blue), read before you submit:'),
        h('ul', {}, report.answered.map((a) => h('li', {}, a.question))),
      ),
    )
  }
  if (report.needsYou.length) {
    nodes.push(
      note('warn', h('strong', {}, 'For you to answer (outlined in amber):'), h('ul', {}, report.needsYou.slice(0, 8).map((q) => h('li', {}, q)))),
    )
  }
  nodes.push(h('p', { class: 'faint' }, 'Check every answer, then submit it yourself.'))
  return nodes
}

/** After the owner submits, one click logs the application in the tracker. */
function recordForm(tab, report, resumeFileName) {
  const title = h('input', { value: report.pageTitle?.split(/[|–-]/)[0]?.trim() ?? '' })
  const company = h('input', { value: '' })
  const extra = h('div', { hidden: true }, h('p', { class: 'faint' }, 'This page isn’t a role the app knows. Name it:'), field('Title', title), field('Company', company))
  const record = h('button', { class: 'secondary' }, 'I submitted it: record the application')
  const status = h('div')
  record.addEventListener('click', async () => {
    record.disabled = true
    try {
      const pursuit = await api('/applications', {
        method: 'POST',
        body: {
          url: tab.url,
          resumeVersion: resumeFileName || undefined,
          ...(extra.hidden ? {} : { title: title.value.trim(), company: company.value.trim() }),
        },
      })
      status.replaceChildren(note('ok', `Recorded: ${pursuit.role.title} at ${pursuit.role.companyName} is now Applied.`))
    } catch (err) {
      if (err.status === 400 && extra.hidden) {
        extra.hidden = false
        status.replaceChildren()
      } else {
        status.replaceChildren(note('error', err.message))
      }
      record.disabled = false
    }
  })
  return h('div', {}, extra, record, status)
}

async function main() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
  const base = await baseUrl()
  document.getElementById('open-app').href = base
  try {
    switch (classify(tab?.url)) {
      case 'linkedin-profile':
        return await saveProfileView(tab)
      case 'linkedin-job':
        return await addRoleView(tab)
      case 'application':
        return await fillView(tab)
      case 'linkedin-other':
        return show(h('p', { class: 'muted' }, 'Open someone’s profile to save them, or a job to add it to your inbox.'))
      default:
        return show(h('p', { class: 'muted' }, 'Open a LinkedIn profile, a LinkedIn job, or a job application.'))
    }
  } catch (err) {
    show(note('error', err.message))
  }
}

main()
