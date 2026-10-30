# Jenkins LTS Setup — Calculator CI/CD Pipeline

How the Jenkins controller for this project is configured, and why each piece is
needed. No secrets appear in this file: tokens and passwords live only in the
Jenkins credential store and in Jenkins' own configuration.

---

## 1. Controller

Jenkins LTS runs natively on macOS via Homebrew (not in a container):

```bash
brew install jenkins-lts
brew services start jenkins-lts
```

| Property | Value |
|---|---|
| Version | 2.580.2 |
| Local URL | http://127.0.0.1:8080 |
| `JENKINS_HOME` | `~/.jenkins` |
| Service | LaunchAgent `sh.brew.jenkins-lts.plist` |

**Why native rather than a Jenkins container?** The Deploy stage runs
`docker compose up` with a bind-mounted `./nginx/nginx.conf`. From inside a
container talking to the host Docker socket, that relative path would be
resolved by the *host* daemon against a path that only exists inside the
container, and the mount would silently break. Running Jenkins natively means
the workspace path Jenkins sees is the same path the Docker daemon sees.

---

## 2. Security

Jenkins shipped here with `SecurityRealm$None` + `AuthorizationStrategy$Unsecured`
— i.e. no login at all. That is survivable while it only listens on
`127.0.0.1`, but this project exposes Jenkins through a public tunnel so GitHub
can deliver webhooks, and an unsecured Jenkins on a public URL is remote code
execution for anyone who has the link (the `/scriptText` console runs arbitrary
Groovy as the Jenkins user).

Current configuration:

| Setting | Value |
|---|---|
| Security realm | `HudsonPrivateSecurityRealm` (Jenkins' own user database) |
| Authorization | `FullControlOnceLoggedInAuthorizationStrategy` |
| Anonymous read | **disabled** |
| Admin user | `garv17` |

`/github-webhook/` still accepts anonymous `POST` because the GitHub plugin
registers it as an `UnprotectedRootAction` — so locking down the UI does not
break webhook delivery. A `GET` on it returns `405 Method Not Allowed`, which is
a convenient way to prove the endpoint is reachable through the tunnel.

---

## 3. Build tool visibility (PATH / JAVA_HOME)

The Homebrew LaunchAgent starts Jenkins with a bare
`PATH=/usr/bin:/bin:/usr/sbin:/sbin`. Homebrew installs to `/opt/homebrew/bin`,
so `mvn` and `docker` were **invisible to every `sh` step** — the pipeline would
fail at Build with `mvn: command not found`.

Fixed with global environment variables under
*Manage Jenkins → System → Global properties → Environment variables*:

| Variable | Value |
|---|---|
| `PATH` | `/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin` |
| `JAVA_HOME` | `/opt/homebrew/opt/openjdk@17` |

`JAVA_HOME` matters separately: Maven refuses to start without it
(*"The JAVA_HOME environment variable is not defined correctly"*). It points at
JDK **17** to match `<java.version>17</java.version>` in `pom.xml`, even though
the Jenkins JVM itself runs on JDK 21.

These are controller-level settings, which keeps the `Jenkinsfile` free of
machine-specific paths.

---

## 4. Pipeline job

Job name: **`calculator-devops`** (type: Pipeline).

| Setting | Value |
|---|---|
| Definition | Pipeline script from SCM |
| SCM | Git — `https://github.com/GarvRajput17/SPE_MiniProj.git` |
| Branch | `*/main` |
| Script path | `Jenkinsfile` |
| Lightweight checkout | enabled |
| GitHub project | `https://github.com/GarvRajput17/SPE_MiniProj/` |
| Build retention | last 25 builds |

The repository is public, so **no SCM credentials are required** for checkout.

Lightweight checkout fetches only the `Jenkinsfile` to decide what to run; the
pipeline's own `Checkout` stage then does the full `checkout scm` into the
workspace.

---

## 5. Triggers

Declared in the `Jenkinsfile` so the trigger configuration is versioned with the
code rather than living only in Jenkins' UI:

```groovy
triggers {
    githubPush()            // primary — fires on webhook delivery
    pollSCM('H/2 * * * *')  // fallback — checks for new commits every 2 minutes
}
```

Both are intentional. The webhook gives near-instant builds but depends on the
public tunnel being up; polling keeps the project demonstrable if the tunnel is
down. The problem statement accepts either, and lists webhook as preferred.

> Triggers declared in a `Jenkinsfile` only take effect **after** the first build
> runs, because Jenkins has to read the file before it knows about them. The job
> was therefore bootstrapped with the same triggers in its `config.xml`.

### Exposing Jenkins to GitHub

Jenkins binds to `127.0.0.1`, so GitHub cannot reach it directly. An ngrok
tunnel forwards it:

```bash
ngrok http 8080
```

Then set *Manage Jenkins → System → Jenkins URL* to the public HTTPS URL (this
also makes build links in notification emails clickable), and add a webhook on
GitHub under *Settings → Webhooks*:

| Field | Value |
|---|---|
| Payload URL | `https://<ngrok-subdomain>.ngrok-free.app/github-webhook/` |
| Content type | `application/json` |
| Events | Just the `push` event |

Or create it from the CLI:

```bash
gh auth login -s admin:repo_hook
gh api repos/GarvRajput17/SPE_MiniProj/hooks \
  -f name=web -f active=true -f 'events[]=push' \
  -f config[url]="https://<ngrok-subdomain>.ngrok-free.app/github-webhook/" \
  -f config[content_type]=json
```

> ⚠️ **A free ngrok URL changes on every restart.** After restarting ngrok you
> must update both the Jenkins URL and the GitHub webhook, or auto-triggering
> silently falls back to the 2-minute poll. A reserved ngrok domain (or
> `cloudflared`) avoids this.

---

## 6. Credentials

Stored in the Jenkins credential store, never in the repository:

| ID | Kind | Username | Used by |
|---|---|---|---|
| `dockerhub-creds` | Username with password | `garvrajput17` | `Push` stage |

The password field holds a **Docker Hub personal access token** with
*Read & Write* permission, not the account password. The `Push` stage consumes it
via `withCredentials`, which masks the value in console output:

```groovy
withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', ...)]) {
    sh '''
        echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
        ...
    '''
}
```

Two details that matter:

- The `sh` body is **single-quoted**, so Groovy does not interpolate the secret
  into the command string — the shell expands it at runtime instead. Using
  double quotes here would bake the token into the build log.
- `--password-stdin` keeps the token out of the process argument list, where it
  would otherwise be visible to `ps`.

> **Credential IDs are not masked** anywhere in Jenkins — they appear in the UI,
> in logs and in `credentials.xml`. Never paste a token into the *ID* field.

---

## 7. Email notifications

*Manage Jenkins → System → E-mail Notification*:

| Setting | Value |
|---|---|
| SMTP server | `smtp.gmail.com` |
| Port | `587` |
| Use TLS | yes (STARTTLS) |
| Use SSL | no |
| Auth user | `garvgrez@gmail.com` |
| Auth password | Gmail **App Password** (16 chars, 2FA required) |
| System admin address | `Jenkins <garvgrez@gmail.com>` |

Gmail rejects ordinary account passwords for SMTP, so an App Password from
<https://myaccount.google.com/apppasswords> is mandatory.

The `Jenkinsfile` `post` blocks send on both outcomes:

```groovy
post {
    success { mail to: "${NOTIFICATION_EMAIL}", subject: "SUCCESS: ...", body: ... }
    failure { mail to: "${NOTIFICATION_EMAIL}", subject: "FAILURE: ...", body: ... }
}
```

---

## 8. Verifying the setup

```bash
# Jenkins is up and secured (expect 403 anonymous, 200 authenticated)
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/api/json
curl -s -o /dev/null -w '%{http_code}\n' -u '<user>:<pass>' http://127.0.0.1:8080/api/json

# Webhook endpoint reachable through the tunnel (405 = exists, POST-only)
curl -s -o /dev/null -w '%{http_code}\n' https://<ngrok>.ngrok-free.app/github-webhook/

# Per-stage results of the latest build
curl -sg -u '<user>:<pass>' \
  http://127.0.0.1:8080/job/calculator-devops/lastBuild/wfapi/describe

# Deployed stack
docker compose -p spe_miniproj ps
curl -s http://localhost/api/v1/add?a=2\&b=3
```

A green run looks like this:

```
Checkout       SUCCESS     2.2s
Build          SUCCESS     3.8s
Test           SUCCESS     3.8s      27 tests, 0 failures
Docker Build   SUCCESS     4.6s
Push           SUCCESS    36.6s      garvrajput17/calculator:<build>, :latest
Deploy         SUCCESS    13.1s      2 replicas + nginx healthy
```

---

## 9. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `mvn: command not found` | Homebrew not on Jenkins' `PATH` | Set global `PATH` (§3) |
| `The JAVA_HOME environment variable is not defined correctly` | `JAVA_HOME` unset for the agent | Set global `JAVA_HOME` (§3) |
| `Could not find credentials entry with ID 'dockerhub-creds'` | Credential missing or ID typo | Re-add with the exact ID (§6) |
| `denied: requested access to the resource is denied` on push | Token is read-only, or image namespace ≠ Docker Hub account | Use a Read & Write token; check `DOCKER_IMAGE` |
| `no match for platform in manifest` during Docker Build | Base image has no `arm64` variant | See the base-image note in `README.md` §4 |
| Push to GitHub does not start a build | ngrok URL rotated | Re-point Jenkins URL + webhook (§5); poll still covers it within 2 min |
| Build hangs in Deploy, then times out | A container never reports healthy | Stage dumps `compose ps` + logs on timeout; check `/actuator/health` |
