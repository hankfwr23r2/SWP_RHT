# Local Turnstile testing

The default configuration keeps normal CAPTCHA validation. To run automated browser
tests locally with Cloudflare's official always-pass keys:

```powershell
docker compose -f docker-compose.yml -f docker-compose.test.yml up -d --build backend frontend
```

Open `http://localhost:5173`. The widget should show its testing state and pass
automatically. Backend authentication still validates the dummy token with Cloudflare;
credentials and role checks remain active. Internet access to Cloudflare is required.

The `local-turnstile-test` Spring profile accepts a missing `action` only for official
test secret keys after successful Siteverify validation. Real keys still require the
expected action. Test keys are rejected without this profile or alongside a `prod` or
`production` profile. Do not deploy this Compose override to a public environment.

Restore the regular configuration and real keys from your normal environment:

```powershell
docker compose -f docker-compose.yml up -d --build backend frontend
```

Rebuilding the frontend is necessary because Vite embeds the site key at build time.
This override does not delete or reset the database.

Reference: https://developers.cloudflare.com/turnstile/troubleshooting/testing/
