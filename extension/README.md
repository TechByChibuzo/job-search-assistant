# Job Search Assistant: browser extension

A Chrome extension that works with the app running on your machine.

- **On a LinkedIn profile:** save the person to your People list, with their title, company and profile link.
- **On a LinkedIn job:** add the role to your inbox.
- **On a job application** (Greenhouse, Lever, Ashby, Workday, and most other forms by label): fill your contact details, links, current role and education from your Profile, fill long-answer questions that match one in your answer bank (outlined in blue), and attach your resume. Long questions with no saved answer can be drafted with Claude right in the popup: edit the draft, put it in the form, and save it for next time. Then, after you submit, record the application in the tracker.

What it never does:

- It never reads a page until you click it, and has no standing access to LinkedIn or any other site (`activeTab`).
- It never navigates, scrolls, clicks or sends anything on LinkedIn.
- It only fills fields you can see, and on sites other than Greenhouse, Lever, Ashby and Workday it attaches your resume only if you tick the box.
- It never answers work authorization, sponsorship, consent or similar questions; those are outlined in amber for you. Equal-opportunity questions (gender, Hispanic/Latino, veteran status) are answered only with answers you chose to save in your Profile; any you didn't save, and questions such as race, disability or gender identity, stay with you.
- It never ticks a box or presses submit, and never picks an answer to a question. In search-and-pick lists it chooses only plain facts from your Profile (country, city, school, degree), and only when one suggestion clearly matches; the rest are listed for you. Schools are listed by campus on many forms ("Texas A&M University - College Station"), so include the campus in your Profile's school.

Some companies, such as Stripe, show a Greenhouse or Lever form inside their own careers page. The extension can only read the page you click it on, not a form framed in from another site, so it offers to open the form on its own; click the extension again there.
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
