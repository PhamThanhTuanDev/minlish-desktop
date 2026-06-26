# MinLish Desktop

Desktop overlay app for studying vocabulary without switching browser tabs.

## Run

```bash
mvn -q -DskipTests package
java -jar target/minlish-desktop-0.0.1-SNAPSHOT.jar
```

You will be asked for:

- API Base URL
- Set ID

Then choose login mode:

- `Dang nhap Google` (recommended)
- `Dang nhap Email/Password`

When choosing Google login, the app opens your browser and after success it returns JWT to the desktop app automatically.

Environment variables you can prefill:

- `MINLISH_API_BASE_URL`
- `MINLISH_EMAIL`
- `MINLISH_PASSWORD`
- `MINLISH_SET_ID`

## Backend requirement for Google desktop login

The backend must include endpoint:

- `GET /api/auth/google/desktop/start?redirectUri=http://127.0.0.1:PORT/oauth-callback`

And OAuth2 success/failure handlers should redirect to that `redirectUri` with query params:

- success: `accessToken=...`
- failure: `error=google_login_failed`
