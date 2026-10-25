# Setting up job-alert emails

The app can read job-alert emails (from LinkedIn, Microsoft, Meta, Apple or any careers site) from one Gmail label and add their roles to the inbox. It uses Google's own sign-in with **read-only** access, reads only messages under that label, and runs once a day with the sync.

This needs a Google OAuth client of your own. It's free and takes about ten minutes, once.

## 1. Create the Google OAuth client

1. Go to [console.cloud.google.com](https://console.cloud.google.com) and create a project, for example "Job Search Assistant".
2. **APIs & Services → Library:** search for **Gmail API** and enable it.
3. **Google Auth Platform** (the OAuth consent screen):
   - **Branding:** app name "Job Search Assistant", your email as the support and developer contact.
   - **Audience:** user type **External**. Add your Gmail address under **Test users**.
   - **Data access:** add the scope `https://www.googleapis.com/auth/gmail.readonly`.
4. **Clients → Create client:** type **Web application**. Under **Authorized redirect URIs** add:
   - `http://localhost:8081/api/gmail/callback` (the app in Docker)
   - `http://localhost:5173/api/gmail/callback` (the dev server, if you run it)
5. Copy the **client ID** and **client secret**.

**About weekly sign-ins.** While the app's publishing status is **Testing**, Google expires its refresh tokens after 7 days, so you'd need to press Connect again each week. To avoid that, set **Audience → Publishing status** to **In production**. Google then shows a "Google hasn't verified this app" warning when you connect; since it's your own app, choose **Advanced → Go to Job Search Assistant**. Verification is only needed for apps other people use.

## 2. Give the app the client

Copy `.env.example` to `.env` in the repository root and fill in:

```sh
GOOGLE_CLIENT_ID=1234-abc.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-...
```

`.env` is ignored by git. Then restart the app:

```sh
docker compose up -d --build
```

For the dev server, export the same variables and `GMAIL_REDIRECT_URI=http://localhost:5173/api/gmail/callback` before `./mvnw spring-boot:run`.

## 3. File alert emails silently

So the alerts never buzz your phone, a Gmail filter moves them out of your inbox:

1. Open one alert email, then **⋮ → Filter messages like this** (or search `from:jobalerts-noreply@linkedin.com`).
2. **Create filter**, then tick:
   - **Skip the Inbox (Archive it)**
   - **Mark as read**
   - **Apply the label:** create one called `Job alerts`
3. Repeat for each sender: your LinkedIn job alerts, careers.microsoft.com, metacareers.com and so on. You can also combine them in one filter: `from:(jobalerts-noreply@linkedin.com OR microsoft.com OR metacareers.com)`.

Gmail only notifies for mail that lands in the inbox, so these never make a sound.

## 4. Connect

In the app, open **Companies → Job alert emails → Connect Gmail**, approve read-only access, and press **Import now**. After that, alerts are read every morning with the sync.

The app reads emails from the last 30 days under the label, each one once. Roles outside the US are skipped like any other. Emails it finds no roles in are counted on the Companies page, so a new alert format doesn't go unnoticed.

## Disconnecting

**Disconnect** on the Companies page revokes the app's access at Google and deletes the stored token. You can also remove it at [myaccount.google.com/permissions](https://myaccount.google.com/permissions).
