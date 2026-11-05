# Deploying Insight Hub v2

Decided by the developer on 2026-11-04 (PR #51): everything on free tiers, the backend as a Docker image.

**Live since 2026-11-04:** web <https://insights-hub-v2-one.vercel.app>, backend <https://insighthubv2.duckdns.org> (DuckDNS → the Elastic IP; the instance runs Ubuntu, user `ubuntu`). Deploy a new image: `cd /opt/insightshub && ./deploy.sh`, and let it finish (it prints `deploy: … is up`).

| Piece | Where | Notes |
|---|---|---|
| Web app (`web/`) | **Vercel** (Hobby) | Builds from `main` by itself; `/api/*` is rewritten to the backend |
| Backend image | **Docker Hub** (public repo; the image holds no secrets) | CI pushes `latest` + `sha-<7>` on every push to `main`, once every test job is green |
| Backend container | **AWS EC2 t3.micro, `ap-south-1` (Mumbai)**: 2 vCPU burstable, 1 GB RAM + 2 GB swap, 20 GB | Docker Compose: the backend + Caddy (HTTPS). Deploy = pull + restart |
| Postgres | **Neon** free, `us-east-2`, schema `v2` | Already in use; v1 stays in `public`. See "Region" below |
| Redis | **Upstash** free, TLS | Already in use |
| LLM | Groq (Gemini fallback dormant) | Already in use |

```
merge to main ──▶ CI: backend · web · e2e green ──▶ image job ──push──▶ Docker Hub
EC2: /opt/insightshub/deploy.sh ──pull──▶ Docker Hub, restart, health check, roll back if DOWN

browser ──▶ https://<app>.vercel.app ──/api/* rewrite (120 s)──▶ https://api.<domain> ──▶ caddy :443 ──▶ backend :8080
browser ──PRESS START──▶ https://api.<domain>/api/auth/login ──▶ GitHub ──▶ /api/auth/callback ──302──▶ <app>.vercel.app/dashboard#token
email "unsubscribe" link ──▶ https://api.<domain>/api/notifications/unsubscribe
```

The browser only calls the Vercel origin for `/api/*`, so the backend needs no CORS. The login, the
OAuth callback and the unsubscribe links go straight to the backend (the OAuth state cookie must
live on the backend's origin).

Files: [`Dockerfile`](../Dockerfile) + [`.dockerignore`](../.dockerignore) at the root (the image), the
`image-check` (PRs) and `image-publish` (`main`) jobs in [`.github/workflows/ci.yml`](../.github/workflows/ci.yml), and in [`docs/deploy/`](deploy/)
what goes on the instance: `compose.yaml`, `Caddyfile`, `deploy.sh`, `deploy.env.example`.

## Region: the instance and Neon are far apart ⚠️

The instance runs in Mumbai, next to the users (Asia/Kolkata). Neon is in Ohio (`us-east-2`), about
200 ms away, and **every API request makes several database round trips**, so a page that is quick
locally may take a second or more. The hourly and daily jobs don't mind. Options, for the developer:
1. Live with it at first and measure how the pages feel (the sync logs its own duration in `docker compose logs backend`).
2. Move v2 to a Neon project in the nearest Asian region (check Neon's region list; v1 keeps the old
   project and `public`): `pg_dump --schema=v2`, restore, change `spring.datasource.url`.
Upstash: if the database is new, pick its region next to the instance too.

---

## 1. EC2 instance (once)

**In the AWS console** (region `ap-south-1`):
- **Credit specification → standard.** t3 instances start in *unlimited* mode, which bills for CPU
  above the 10% baseline. Actions → Instance settings → Change credit specification.
- **Elastic IP**, associated with the instance: DNS and the OAuth callback need a stable address.
  (A public IPv4 address costs money outside the free tier; check Billing → Free tier.)
- **Security group:** SSH (22) from your own IP only; HTTP (80) and HTTPS (443) from anywhere (80 is
  needed for the Let's Encrypt challenge and the redirect to HTTPS). **Never open 8080**: compose
  binds it to `127.0.0.1` for the health check only.
- A **budget alert** (Billing → Budgets) at $1, so a surprise shows up the same day.

**DNS:** point `api.<domain>` (an A record) at the Elastic IP. Without a domain, a free
[DuckDNS](https://www.duckdns.org) name works (`<name>.duckdns.org`). Let's Encrypt doesn't issue
certificates for `*.amazonaws.com` names.

**On the instance** (Amazon Linux 2023, user `ec2-user`; Ubuntu below):

```bash
# Docker, the compose plugin (not packaged on AL2023) and curl (deploy.sh's health check)
sudo dnf install -y docker curl
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -fsSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
sudo systemctl enable --now docker          # containers come back after a reboot (restart: unless-stopped)
sudo usermod -aG docker ec2-user            # log out and in again
docker compose version

# Swap: 2 GB, for the startup spike (check `swapon --show`; skip if it already exists)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/90-swappiness.conf && sudo sysctl --system

# The deploy directory, owned by ec2-user
sudo install -d -o ec2-user -g ec2-user -m 0750 /opt/insightshub
# From your laptop:
#   scp docs/deploy/{compose.yaml,Caddyfile,deploy.sh,deploy.env.example} ec2-user@<host>:/opt/insightshub/
cd /opt/insightshub && chmod +x deploy.sh && mv deploy.env.example deploy.env
vi deploy.env        # IMAGE=docker.io/tushar2703/insight-hub (already set), API_DOMAIN=api.<domain>
```

On **Ubuntu** instead: Docker from Docker's apt repository (<https://docs.docker.com/engine/install/ubuntu/>,
`docker-ce` + `docker-compose-plugin`), and the user `ubuntu` instead of `ec2-user`.

## 2. The backend's secrets: `backend.env` (once)

`/opt/insightshub/backend.env`, mode `0600`, `KEY=value` per line, no quotes. Compose passes it to the
container as environment variables. It is deliberately **not** called `.env`: Compose would read a
`.env` for its own `${…}` substitution. Avoid `$` in values (or write `$$`).

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

Leave `API_DOCS_ENABLED` unset: Swagger stays off in production. The image sets
`SPRING_PROFILES_ACTIVE=prod` and the JVM flags; either can be overridden here.

```bash
chmod 600 /opt/insightshub/backend.env
```

## 3. Publishing and deploying

**Docker Hub (once):** the repository is `tushar2703/insight-hub` (public), and an
access token with **Read & Write** for CI (Account settings → Personal access tokens).

**GitHub (once):** Settings → Secrets and variables → Actions:

| Name | Kind | Value |
|---|---|---|
| `DOCKERHUB_USERNAME` | secret | `<dockerhub-user>` |
| `DOCKERHUB_TOKEN` | secret | The Read & Write token |
| `DOCKERHUB_IMAGE` | variable (or secret) | `tushar2703/insight-hub` (set 2026-11-04), no `docker.io/` and no tag. As a secret it works too, but shows as `***` in the logs |

**Every merge to `main`:** CI runs `backend`, `web` and `e2e`; when all three are green,
`image-publish` packages the jar, builds the image (`linux/amd64`) and pushes `:latest` and `:sha-<7>`.
**Pull requests never push:** `image-check` only builds the image (so a broken Dockerfile shows up
before the merge), without logging in to Docker Hub or touching its secrets. The web app deploys on Vercel by itself.

**Then on the instance:**

```bash
/opt/insightshub/deploy.sh                # pulls :latest
/opt/insightshub/deploy.sh sha-1a2b3c4    # a specific commit (pin, or roll back by hand)
```

`deploy.sh` tags the running image as `:previous`, pulls, restarts with `docker compose up -d`, waits
up to 3 minutes for `/actuator/health` to say `UP`, and otherwise prints the last log lines and goes
back to `:previous`. The first run also starts Caddy, which fetches the certificate (keep ports 80/443
open). On startup the backend runs Flyway against `v2` (a no-op on an up-to-date database).

**Check it:** `curl https://api.<domain>/actuator/health` → `{"status":"UP",…}`. Logs:
`docker compose --env-file deploy.env logs -f backend` (rotated at 3 × 10 MB per container).

**Building an image by hand** (without CI): `./mvnw -B -DskipTests package && docker build -t insightshub-backend .`

## 4. Production GitHub OAuth app (once)

GitHub → Settings → Developer settings → OAuth Apps → New. Keep the local one for development.
- Homepage URL: `https://<app>.vercel.app`
- Authorization callback URL: `https://api.<domain>/api/auth/callback`

Its client id and secret go in `backend.env` (step 2).

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
same as `PROXY_TIMEOUT_MS` locally. The first login's sync takes up to about 90 s (more with the
database in another region: see "Region").

## 6. Memory budget (t3.micro, 1 GB)

The image caps the JVM (`JAVA_TOOL_OPTIONS` in the `Dockerfile`): heap 400 MB, metaspace 192 MB, code
cache 64 MB, direct memory 64 MB, serial GC, C1 JIT only (`TieredStopAtLevel=1`: less memory and CPU,
enough for this load), `ExitOnOutOfMemoryError` (Docker restarts it). Compose limits the backend
container to 700 MB and Caddy to 128 MB.

**Measured on 2026-11-04:**
- The same flags on `it/E2eBackend` through the full-stack e2e suite (OAuth login, the first sync with
  five AI tasks on their fallbacks, the hero seed, every screen, a Settings save): **324 MB RSS after
  startup, 328 MB peak** (`VmHWM`).
- The image itself, started with the `prod` profile against local Postgres 17 + Redis 7: **started in
  4.9 s, health `UP`, 411 MiB** in `docker stats` (that figure includes the page cache), as user `app`.
- Add about 80–100 MB for `dockerd` + `containerd` and about 30 MB for Caddy: together well under
  1 GB, with swap for the startup spike.

Not measured: real LLM responses, and the hourly jobs with many users (capped by each job's `concurrency`).

## 7. Free-tier limits to keep an eye on

- **EC2:** check which free tier the account has (Billing → Free tier): accounts from before July
  2025 get 750 h/month of t3.micro for 12 months; newer accounts spend credits.
- **Docker Hub:** free accounts have pull rate limits; one instance pulling once per deploy is far below them.
- **Neon:** the compute suspends after 5 idle minutes and wakes on the next query. The hourly jobs
  wake it about 24 times a day. Idle pool connections may keep it awake; the backend's `prod` profile
  lets them close (`HANDOFF.md`). Watch compute hours in the Neon console during the first week.
- **Upstash:** every rate-limited request is a Redis call, plus the insights cache. Watch the
  monthly command count.
- **Vercel Hobby:** for non-commercial use, which this is.
- **Groq:** the app's key pays for every sync's five LLM calls (`sync-global` rate limit).

## 8. Release checklist

- [ ] Instance in `ap-south-1`, credits on **standard**, Elastic IP, security group (22 from you only; 80, 443)
- [ ] Docker + compose plugin, swap, `/opt/insightshub` with `compose.yaml`, `Caddyfile`, `deploy.sh`, `deploy.env`, `backend.env` (0600)
- [ ] `backend.env` with **new** `JWT_SECRET` and `ENCRYPTION_KEY`; the production OAuth app's id and secret
- [ ] Docker Hub repo + token; `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`, `DOCKERHUB_IMAGE` in GitHub; `image-publish` pushed on `main`
- [ ] DNS `api.<domain>` → the Elastic IP; `deploy.sh` succeeded; `https://api.<domain>/actuator/health` is `UP` with a valid certificate
- [ ] Vercel project, root `web`, the three variables, deployed from `main`
- [ ] Log in from the Vercel URL: PRESS START → GitHub → `/home`, the hero is there
- [ ] SAVE twice: the second one says RESTING (the cooldown); `/arena` is a 404
- [ ] `https://api.<domain>/actuator/env` is a 404 and `/swagger-ui.html` isn't served (401: Swagger is off)
- [ ] Next day: `docker compose --env-file deploy.env logs backend | grep -E "Scheduled activity refresh|Hero job done"` shows the 03:00 UTC refresh and the hourly hero runs
- [ ] A budget alert in AWS; Neon and Upstash usage checked after a week; page timings checked (see "Region")
