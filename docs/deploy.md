# Deploying Insight Hub v2

Decided by the developer on 2026-11-04: everything on free tiers.

| Piece | Where | Notes |
|---|---|---|
| Web app (`web/`) | **Vercel** (Hobby) | Builds from `main`; `/api/*` is rewritten to the backend |
| Backend (Spring Boot jar) | **AWS EC2 t3.micro** (2 vCPU burstable, 1 GB RAM + 2 GB swap, 20 GB disk) | systemd service behind Caddy (HTTPS) |
| Postgres | **Neon** free, `us-east-2`, schema `v2` | Already in use; v1 stays in `public` |
| Redis | **Upstash** free, TLS | Already in use |
| LLM | Groq (Gemini fallback dormant) | Already in use |

```
browser ──▶ https://<app>.vercel.app ──/api/* rewrite (120 s)──▶ https://api.<domain> ──▶ Caddy :443 ──▶ Spring :8080
browser ──PRESS START──▶ https://api.<domain>/api/auth/login ──▶ GitHub ──▶ /api/auth/callback ──302──▶ <app>.vercel.app/dashboard#token
email "unsubscribe" link ──▶ https://api.<domain>/api/notifications/unsubscribe
```

The browser only ever calls the Vercel origin for `/api/*`, so the backend needs no CORS. The login,
the OAuth callback and the unsubscribe links go straight to the backend (the OAuth state cookie must
live on the backend's origin).

Files in [`docs/deploy/`](deploy/): `insightshub.service` (systemd), `Caddyfile`, `deploy.sh` (install a
jar, restart, roll back). The deploy workflow: [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml).

---

## 1. EC2 instance (once)

**Region:** `us-east-2` (Ohio), next to Neon. Every request reads Postgres, so another region adds a
round trip to each one. Put Upstash in a nearby AWS region too, if it isn't already.

**In the AWS console:**
- **Credit specification → standard.** t3 instances start in *unlimited* mode, which bills for CPU
  above the 10% baseline. Actions → Instance settings → Change credit specification.
- **Elastic IP**, associated with the instance: DNS and the OAuth callback need a stable address.
  (A public IPv4 address costs money outside the free tier; check Billing → Free tier.)
- **Security group:** SSH (22) from your own IP only (or from anywhere, keys only, for the deploy workflow: step 3); HTTP (80) and HTTPS (443) from anywhere (80 is
  needed for the Let's Encrypt challenge and the redirect to HTTPS). **Never open 8080.**
- A **budget alert** (Billing → Budgets) at $1, so a surprise shows up the same day.

**DNS:** point `api.<domain>` (an A record) at the Elastic IP. Without a domain, a free
[DuckDNS](https://www.duckdns.org) name works (`<name>.duckdns.org`). Let's Encrypt doesn't issue
certificates for `*.amazonaws.com` names.

**On the instance** (Amazon Linux 2023, user `ec2-user`; Ubuntu equivalents below):

```bash
# Java 21 and curl (deploy.sh's health check)
sudo dnf install -y java-21-amazon-corretto-headless curl

# Swap: 2 GB, used only for the startup spike (check `swapon --show`; skip if it already exists)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/90-swappiness.conf && sudo sysctl --system

# The service user and its directory
sudo useradd --system --home /opt/insightshub --shell /sbin/nologin insightshub
sudo install -d -o insightshub -g insightshub -m 0750 /opt/insightshub /opt/insightshub/releases

# From your laptop, copy the three files over:
#   scp docs/deploy/{insightshub.service,Caddyfile,deploy.sh} ec2-user@<host>:/tmp/
sudo install -m 0644 /tmp/insightshub.service /etc/systemd/system/insightshub.service
sudo install -m 0755 /tmp/deploy.sh /opt/insightshub/deploy.sh
sudo systemctl daemon-reload && sudo systemctl enable insightshub
```

**Caddy** (HTTPS). Amazon Linux has no Caddy package, so install the static binary:

```bash
curl -fsSL "https://caddyserver.com/api/download?os=linux&arch=amd64" -o /tmp/caddy
sudo install -m 0755 /tmp/caddy /usr/bin/caddy
sudo groupadd --system caddy
sudo useradd --system --gid caddy --home-dir /var/lib/caddy --create-home --shell /sbin/nologin caddy
sudo install -d /etc/caddy && sudo install -m 0644 /tmp/Caddyfile /etc/caddy/Caddyfile
sudo sed -i 's/api.example.com/api.<domain>/' /etc/caddy/Caddyfile    # your real name
# The official unit: https://github.com/caddyserver/dist/blob/master/init/caddy.service
sudo curl -fsSL https://raw.githubusercontent.com/caddyserver/dist/master/init/caddy.service -o /etc/systemd/system/caddy.service
sudo systemctl daemon-reload && sudo systemctl enable --now caddy
```

On **Ubuntu** instead: `sudo apt install openjdk-21-jre-headless curl`, Caddy from its apt repository
(<https://caddyserver.com/docs/install#debian-ubuntu-raspbian>, which brings its own unit), the
user `ubuntu` instead of `ec2-user`, and `/usr/sbin/nologin`.

## 2. The backend's `.env` (once)

`/opt/insightshub/.env`, owned by `insightshub`, mode `0600` (the app loads it from its working
directory through `spring.config.import`). Properties format, `KEY=value`, no quotes needed:

| Key | Value |
|---|---|
| `DB_USER`, `DB_PASSWD` | Neon (the same database; the app only touches schema `v2`) |
| `REDIS_HOST`, `REDIS_PASSWD` | Upstash (TLS on 6379) |
| `JWT_SECRET`, `ENCRYPTION_KEY` | **New for production**: `openssl rand -base64 32` each. Changing `ENCRYPTION_KEY` later makes every stored GitHub token unreadable (users log in again) |
| `GROQ_API_KEY` | Groq (`GEMINI_API_KEY` optional) |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | The **production** OAuth app (step 4) |
| `FRONTEND_URL` | `https://<app>.vercel.app` (no trailing slash) |
| `BACKEND_URL` | `https://api.<domain>` |
| `DIGEST_ENABLED`, `DIGEST_FROM`, `MAIL_*` | Leave out (digest off) until SMTP is chosen; see `docs/run-locally.md` |

Leave `API_DOCS_ENABLED` unset: Swagger stays off in production.

```bash
sudo install -o insightshub -g insightshub -m 0600 /tmp/env /opt/insightshub/.env && rm /tmp/env
```

## 3. First deploy and every deploy after it

**From GitHub Actions** (recommended): Actions → **Deploy backend** → Run workflow, on `main`, after
its CI is green. The workflow builds the jar on GitHub's runner, copies it to the instance, and runs
`deploy.sh`, which points `app.jar` at the new release, restarts the service, waits up to 3 minutes
for `/actuator/health` to say `UP`, and otherwise rolls back to the previous jar (it keeps three).

Repository settings → Environments → **production** (the workflow only runs from `main`):

| Name | Kind | Value |
|---|---|---|
| `EC2_HOST` | secret | `api.<domain>` (or the Elastic IP) |
| `EC2_USER` | secret | `ec2-user` |
| `EC2_SSH_KEY` | secret | A private key made **for the deploy only** (`ssh-keygen -t ed25519 -f deploy_key -N ''`; add `deploy_key.pub` to `~ec2-user/.ssh/authorized_keys`) |
| `EC2_KNOWN_HOSTS` | secret | The output of `ssh-keyscan -t ed25519 <host>`, checked against the instance's own key (`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` on it) |
| `API_URL` | variable | `https://api.<domain>` (the final public health check) |

**Port 22 and GitHub:** GitHub's runners connect from changing addresses (thousands of ranges), so the
workflow needs port 22 open to everyone, with password logins off (the EC2 default: keys only). If
you'd rather keep 22 limited to your own IP, deploy by hand instead (below) and skip the secrets.

**By hand** (no SSH from GitHub):

```bash
./mvnw -B -DskipTests package
scp target/insightshub-0.0.1-SNAPSHOT.jar ec2-user@<host>:/tmp/insightshub-$(git rev-parse --short HEAD).jar
ssh ec2-user@<host> "sudo /opt/insightshub/deploy.sh /tmp/insightshub-$(git rev-parse --short HEAD).jar"
```

Never build on the instance: Maven on 1 GB swaps for many minutes.

**Check it:** `curl https://api.<domain>/actuator/health` → `{"status":"UP",…}`. Logs:
`journalctl -u insightshub -f`. On startup the app runs Flyway against `v2` (a no-op on a database
that's up to date).

## 4. Production GitHub OAuth app (once)

GitHub → Settings → Developer settings → OAuth Apps → New. Keep the local one for development.
- Homepage URL: `https://<app>.vercel.app`
- Authorization callback URL: `https://api.<domain>/api/auth/callback`

Its client id and secret go in the backend's `.env` (step 2).

## 5. Vercel (once)

New project from `tushar27x/insightsHubV2`:
- **Root directory:** `web`. Framework: Next.js (detected).
- **Environment variables** (Production):

  | Name | Value |
  |---|---|
  | `NEXT_PUBLIC_MOCK` | `0` |
  | `BACKEND_URL` | `https://api.<domain>` (the `/api/*` rewrite) |
  | `NEXT_PUBLIC_BACKEND_URL` | `https://api.<domain>` (the login link) |

  Both URLs are read **at build time** (`next.config.ts`, `NEXT_PUBLIC_*`): after changing one,
  redeploy.
- Vercel deploys every push to `main` by itself, and a preview per PR. Previews call the same
  backend, but the OAuth app only redirects to the production URL, so login only works there.

**Timeouts:** Vercel lets an external rewrite wait up to **120 s** for the backend's first byte, the
same as `PROXY_TIMEOUT_MS` locally. The first login's sync takes up to about 90 s.

## 6. Memory budget (t3.micro)

`insightshub.service` caps the JVM: heap 400 MB, metaspace 192 MB, code cache 64 MB, direct memory
64 MB, serial GC, C1 JIT only (`TieredStopAtLevel=1`: less memory and CPU, enough for this load),
`ExitOnOutOfMemoryError` (systemd restarts it).

**Measured on 2026-11-04:** with exactly these flags, `it/E2eBackend` (Testcontainers) through the
full-stack e2e suite (OAuth login, the first sync with five AI tasks on their fallbacks, the hero
seed, every screen, a Settings save): **324 MB RSS after startup, 328 MB peak** (`VmHWM`). That
leaves the OS, Caddy (~30 MB) and the page cache about 600 MB; swap covers the startup spike.
Not measured: real LLM responses (larger bodies, nothing large kept) and the hourly jobs with many
users (capped by each job's `concurrency`).

## 7. Free-tier limits to keep an eye on

- **EC2:** check which free tier the account has (Billing → Free tier): accounts from before July
  2025 get 750 h/month of t3.micro for 12 months; newer accounts spend credits.
- **Neon:** the compute suspends after 5 idle minutes and wakes on the next query (a cold query takes
  well under a second). The hourly jobs wake it about 24 times a day. Open idle connections in the
  pool may keep it awake; the backend's `prod` profile lets them close (see `HANDOFF.md`). Watch
  compute hours in the Neon console during the first week.
- **Upstash:** every rate-limited request is a Redis call, plus the insights cache. Watch the
  monthly command count in the console.
- **Vercel Hobby:** for non-commercial use, which this is.
- **Groq:** the app's key pays for every sync's five LLM calls (`sync-global` rate limit).

## 8. Release checklist

- [ ] Instance in `us-east-2`, credits on **standard**, Elastic IP, security group (22 from you only; 80, 443)
- [ ] DNS `api.<domain>` → the Elastic IP; `https://api.<domain>/actuator/health` is `UP` with a valid certificate
- [ ] `.env` with **new** `JWT_SECRET` and `ENCRYPTION_KEY`, mode 0600
- [ ] Production OAuth app; its id and secret in `.env`
- [ ] Vercel project, root `web`, the three variables, deployed from `main`
- [ ] Log in from the Vercel URL: PRESS START → GitHub → `/home`, the hero is there
- [ ] SAVE twice: the second one says RESTING (the cooldown); `/arena` is a 404
- [ ] `https://api.<domain>/actuator/env` is a 404 and `/swagger-ui.html` isn't served (401: Swagger is off)
- [ ] Next day: `journalctl -u insightshub --since yesterday | grep -E "Scheduled activity refresh|Hero job done"` shows the 03:00 UTC refresh and the hourly hero runs
- [ ] A budget alert in AWS; Neon and Upstash usage checked after a week
