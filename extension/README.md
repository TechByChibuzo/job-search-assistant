# Job Search Assistant: browser extension

A Chrome extension that works with the app running on your machine.

- **On a LinkedIn profile:** save the person to your People list, with their title, company and profile link.
- **On a LinkedIn job:** add the role to your inbox.
- **On a job application** (Greenhouse, Lever, Ashby, Workday, and most other forms by label): fill your contact details, links, current role and education from your Profile, and attach your resume. Then, after you submit, record the application in the tracker.

What it never does:

- It never reads a page until you click it, and has no standing access to LinkedIn or any other site (`activeTab`).
- It never navigates, scrolls, clicks or sends anything on LinkedIn.
- It never answers work authorization, sponsorship, EEO, consent or similar questions; those are outlined in amber for you.
- It never ticks a box, picks an option, or presses submit.
- It only talks to your own app on `localhost`.

## Install

1. Start the app: `docker compose up -d` in the repository root.
2. In Chrome, open `chrome://extensions` and turn on **Developer mode** (top right).
3. Click **Load unpacked** and choose this `extension` folder.
4. Pin it: click the puzzle-piece icon in the toolbar, then the pin next to **Job Search Assistant**.
5. In the app, open **Profile** and fill it in (or press **Fill from my resume**), then save.

The extension's id is fixed by the public key in `manifest.json` (`ohabbfmnmocehoafnooeknchjhanfomk`), and the API allows requests from exactly that id.

## Develop

```sh
npm install
npm test     # the page reader and form filler against sample pages and a real Lever form
npm run lint
```

After changing a file, click the reload icon on the extension's card in `chrome://extensions`.

The page reader (`content/linkedin.js`) and the form filler (`content/fill.js`) are plain scripts that Chrome injects only when you click. They are written as functions of a `document`, so the tests run them against sample pages in jsdom.
