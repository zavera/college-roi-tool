# Callisto Brand and UI System

Reusable reference for building new products under the Callisto Tech brand.

---

## Company / Legal Identity

| Field | Value |
|---|---|
| Legal entity | Callisto Consulting Group LLC DBA Callisto Tech |
| State | Colorado, USA |
| Product | Astra (Financial Aid Advisor) |
| Domain | astra-ed.org |
| Contact | ambreen@callistotech.org |
| Copyright | 2025-2026 Callisto Consulting Group LLC DBA Callisto Tech |

---

## Logos

| File | Use |
|---|---|
| `callisto_high.png` | Primary logo, used in nav and header |
| `astra-logo.jpg` | Social / Open Graph image |

**Logo display:**
- Size: 38-40px square
- `border-radius: 8px`
- `object-fit: cover`

---

## Color Palette

Paste this block into any new project's `:root` CSS:

```css
:root {
  --navy:       #2d3040;   /* dark backgrounds, nav, headings */
  --navy-lt:    #3a3f55;   /* gradient partner for --navy */
  --primary:    #C9A87C;   /* gold/champagne -- main brand color */
  --primary-dk: #A68A5E;   /* darker gold for text on light bg */
  --primary-lt: #EAD9C0;   /* light gold tint for badges/bg */
  --accent:     #2d7a4f;   /* green for checkmarks, success states */
  --bg:         #f0f1f5;   /* page background */
  --card:       #ffffff;   /* card / panel backgrounds */
  --text:       #1a1a1a;   /* primary body text */
  --muted:      #666666;   /* secondary / caption text */
  --border:     #e0e0e0;   /* dividers and input borders */
}
```

Common gradients:
```css
/* Hero / dark header background */
background: linear-gradient(160deg, var(--navy) 0%, var(--navy-lt) 100%);

/* Pricing header, dark buttons */
background: linear-gradient(135deg, var(--navy) 0%, var(--navy-lt) 100%);

/* Subtle gold radial glow (hero overlay) */
background: radial-gradient(ellipse at 60% 40%, rgba(201,168,76,0.12) 0%, transparent 65%);
```

---

## Typography

### Google Font

```html
<link href="https://fonts.googleapis.com/css2?family=Josefin+Sans:wght@300;400&display=swap" rel="stylesheet">
```

### Font stack

| Role | Family | Weight | Size | Letter-spacing | Transform |
|---|---|---|---|---|---|
| Brand / logo name | Josefin Sans | 300 | 18-22px | 6px | uppercase |
| Section eyebrow labels | Josefin Sans | 400 | 11px | 4px | uppercase |
| Footer brand | Josefin Sans | 300 | 13px | 5px | uppercase |
| All other text | `-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif` | varies | varies | normal | none |

### Type scale

```css
/* Body default */
font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
font-size: 14px;
line-height: 1.65;

/* Hero H1 */
font-size: clamp(30px, 5vw, 52px);
font-weight: 800;
letter-spacing: -0.5px;

/* Section title */
font-size: clamp(22px, 3.5vw, 30px);
font-weight: 800;
letter-spacing: -0.3px;

/* Card heading */
font-size: 19px;
font-weight: 800;

/* Small label / badge */
font-size: 10-11px;
font-weight: 700;
letter-spacing: 2-3px;
text-transform: uppercase;
```

---

## UI Components

### Navigation bar

```css
nav {
  background: var(--navy);
  height: 64px;
  padding: 0 32px;
  display: flex;
  align-items: center;
  gap: 14px;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 2px 16px rgba(0,0,0,0.35);
}

/* Brand name */
.nav-brand-name {
  font-family: 'Josefin Sans', sans-serif;
  font-size: 18px;
  font-weight: 300;
  color: var(--primary);
  letter-spacing: 6px;
  text-transform: uppercase;
}

/* Regular nav link */
.nav-link {
  font-size: 13px;
  font-weight: 600;
  color: rgba(255,255,255,0.7);
  padding: 6px 12px;
  border-radius: 6px;
  transition: color .15s, background .15s;
}
.nav-link:hover { color: #fff; background: rgba(255,255,255,0.08); }

/* CTA pill */
.nav-cta {
  font-size: 13px;
  font-weight: 700;
  color: var(--navy);
  background: var(--primary);
  padding: 8px 18px;
  border-radius: 20px;
  transition: filter .15s;
}
.nav-cta:hover { filter: brightness(1.1); }
```

### Buttons

```css
/* Primary CTA */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--primary);
  color: var(--navy);
  font-size: 15px;
  font-weight: 800;
  padding: 14px 32px;
  border-radius: 8px;
  letter-spacing: 0.2px;
  transition: filter .15s;
}
.btn-primary:hover { filter: brightness(1.1); }

/* Ghost / secondary */
.btn-ghost {
  display: inline-flex;
  align-items: center;
  background: transparent;
  color: rgba(255,255,255,0.85);
  font-size: 14px;
  font-weight: 600;
  padding: 14px 28px;
  border-radius: 8px;
  border: 1.5px solid rgba(255,255,255,0.3);
  transition: border-color .15s, color .15s;
}
.btn-ghost:hover { border-color: rgba(255,255,255,0.6); color: #fff; }

/* Dark button with gold text (used in pricing) */
.btn-dark {
  background: linear-gradient(135deg, var(--navy) 0%, var(--navy-lt) 100%);
  color: var(--primary);
  font-size: 15px;
  font-weight: 800;
  padding: 14px;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  transition: filter .15s;
}
.btn-dark:hover { filter: brightness(1.15); }
```

### Cards

```css
.card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 28px;
  box-shadow: 0 1px 4px rgba(0,0,0,.05);
}

/* Top color accent strip */
.card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: var(--primary); /* or --navy-lt, --accent */
}

/* Badge inside card */
.badge {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 2px;
  text-transform: uppercase;
  padding: 3px 10px;
  border-radius: 20px;
}
```

### Focus / Accessibility

```css
a:focus-visible,
button:focus-visible {
  outline: 3px solid var(--primary);
  outline-offset: 2px;
  border-radius: 3px;
}

.skip-link {
  position: absolute;
  top: -100%;
  left: 8px;
  padding: 8px 16px;
  background: var(--navy);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  border-radius: 0 0 6px 6px;
  z-index: 9999;
  text-decoration: none;
  transition: top .15s;
}
.skip-link:focus { top: 0; }
```

### Form inputs

```css
input, select {
  border: 1.5px solid var(--border);
  border-radius: 6px;
  font-family: inherit;
  color: var(--text);
  background: #fff;
  padding: 10px 12px;
  font-size: 14px;
  transition: border-color .15s;
}
input:focus, select:focus { border-color: var(--primary); }
input:focus-visible, select:focus-visible {
  outline: 3px solid var(--primary);
  outline-offset: 1px;
}
```

### Highlight boxes

```css
/* Blue info box */
.highlight-box {
  background: #f0f7ff;
  border-left: 4px solid #3b82f6;
  border-radius: 0 8px 8px 0;
  padding: 14px 16px;
  font-size: 14px;
}

/* Gold info box */
.highlight-box-gold {
  background: #fdf8f2;
  border-left: 4px solid var(--primary-dk);
  border-radius: 0 8px 8px 0;
  padding: 14px 16px;
  font-size: 14px;
}

/* Warning notice */
.notice {
  background: #fffbeb;
  border: 1px solid #fcd34d;
  border-radius: 8px;
  padding: 14px 16px;
  font-size: 13px;
  color: #92400e;
}
```

### Layout

```css
/* Page sections */
.section { padding: 72px 32px; }

/* Content max-width */
.inner { max-width: 1000px; margin: 0 auto; }

/* Two-column grid */
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
@media (max-width: 700px) { .grid-2 { grid-template-columns: 1fr; } }

/* Mobile adjustments */
@media (max-width: 640px) {
  nav { padding: 0 16px; }
  .section { padding: 52px 20px; }
}
```

### Footer

```css
footer {
  background: var(--navy);
  border-top: 1px solid rgba(255,255,255,0.08);
  padding: 28px 32px;
  text-align: center;
}

.footer-brand {
  font-family: 'Josefin Sans', sans-serif;
  font-size: 13px;
  font-weight: 300;
  letter-spacing: 5px;
  text-transform: uppercase;
  color: var(--primary);
  margin-bottom: 12px;
}

.footer-links a { font-size: 12px; color: rgba(255,255,255,0.45); }
.footer-links a:hover { color: rgba(255,255,255,0.8); }
.footer-copy { font-size: 11px; color: rgba(255,255,255,0.25); }
```

---

## Privacy Policy Summary

Full HTML source: `src/main/resources/static/privacy-policy.html`

**Key facts to adapt for new products:**

- Operator: Callisto Consulting Group LLC DBA Callisto Tech, Colorado LLC
- Effective: May 13, 2026
- Contact: ambreen@callistotech.org
- No data sold, no ad or tracking cookies
- Payment: Stripe only (PCI-DSS Level 1) -- card details never touch Astra servers
- Auth: Google OAuth 2.0 (receives name, email, Google ID only -- no password)
- AI provider: Groq (LLaMA 3.3 70B) -- data used only to fulfill requests, not for training
- Hosting: Railway Corp + Cloudflare
- Cookies: `JSESSIONID` (session, HttpOnly) + `oauth2_auth_req` (3-min OAuth state) only
- Compliant with Colorado Privacy Act (CPA), FERPA notice, COPPA
- Data deletion: within 30 days of account deletion request
- Governing law: Colorado

**Section structure to reuse:**
1. Who We Are
2. Scope
3. Information We Collect (direct / automatic / third-party auth)
4. How We Use Your Information
5. FERPA Compliance (if education context)
6. Colorado Privacy Act Rights
7. Third-Party Service Providers
8. AI-Generated Content Disclaimer
9. Data Retention
10. Security
11. Cookies
12. Children's Privacy
13. Changes to This Policy
14. Governing Law
15. Contact Us

---

## Tech Stack

All new Callisto products use this stack unless the project brief explicitly overrides a layer.

### Backend

| Layer | Choice | Notes |
|---|---|---|
| Language | Java 21 | Virtual threads (Project Loom) available; use where beneficial |
| Build | Maven | Standard `pom.xml`; no Gradle |
| Framework | Spring Boot 3.x | Web, Security, Data, Actuator starters as needed |
| AI integration | Spring AI | Use `spring-ai-*` starters; abstract the model behind a service interface so the provider can be swapped |
| Database | Flexible | PostgreSQL preferred for relational data; H2 for integration tests. Choose based on project need -- document the decision in the README |
| Migrations | Flyway | All schema changes as versioned SQL scripts under `src/main/resources/db/migration/` |
| Auth | Spring Security | Google OAuth2 + local username/password (bcrypt). Session via HttpOnly JSESSIONID cookie |

### Frontend

Vanilla HTML/CSS/JS served as static files from `src/main/resources/static/`. No JS framework unless the project brief requires one. Apply the Callisto brand system CSS variables and components defined in this file.

### Testing

Integration tests are mandatory -- unit tests alone are not sufficient.

| Type | Tool | Rule |
|---|---|---|
| Integration | Spring Boot Test + `@SpringBootTest` | Every service and controller must have at least one integration test hitting a real (H2 or Testcontainers) database |
| HTTP layer | MockMvc or RestAssured | Test request/response contracts, not just service logic |
| AI layer | Mock the Spring AI `ChatClient` / `ChatModel` bean | Do not call real LLM APIs in tests |
| DB | H2 in-memory for CI; real Postgres for local full-stack tests | Use `application-test.properties` to configure H2 |

Never mock the database in integration tests. Use H2 or Testcontainers -- the project was burned by mock/prod divergence before.

```xml
<!-- Required test dependencies in pom.xml -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>com.h2database</groupId>
  <artifactId>h2</artifactId>
  <scope>test</scope>
</dependency>
```

### CI / Deployment

| Concern | Approach |
|---|---|
| Platform | Railway (railway.app) |
| CI trigger | GitHub Actions on push to `main` and on PRs |
| Railway deploy | `railway up` via Railway GitHub integration (auto-deploy on merge to main) |
| Environment variables | Set in Railway dashboard; never commit secrets |
| Health check | Expose `/actuator/health` -- Railway uses this as the readiness probe |
| Port | Read from `$PORT` env var (Railway injects it): `server.port=${PORT:8080}` |

**railway.toml** (place at project root):
```toml
[build]
builder = "nixpacks"

[deploy]
startCommand = "java -jar target/*.jar"
healthcheckPath = "/actuator/health"
healthcheckTimeout = 30
restartPolicyType = "on_failure"
```

**GitHub Actions workflow** (``.github/workflows/ci.yml``):
```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:
jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - run: mvn -B verify
```

`mvn verify` runs unit tests, integration tests (`*IT.java` / `*IntegrationTest.java`), and package. CI must be green before merge.

### Project Skeleton

Minimum directory structure for a new Callisto Spring Boot project:

```
PROJECT-NAME/
  src/
    main/
      java/org/callistotech/PROJECT/
        ProjectApplication.java
        config/
        controller/
        service/
        model/
        repository/
      resources/
        static/          (HTML/CSS/JS frontend)
        db/migration/    (Flyway scripts V1__init.sql etc.)
        application.properties
        application-local.properties   (gitignored)
    test/
      java/org/callistotech/PROJECT/
        controller/      (*ControllerIT.java)
        service/         (*ServiceIT.java)
      resources/
        application-test.properties    (H2 config)
  .github/
    workflows/
      ci.yml
  railway.toml
  pom.xml
  LICENSE
  .gitignore
```

**`.gitignore` must include:**
```
target/
application-local.properties
*.env
.env
```

---

## Proprietary License

```
Copyright (c) 2025-2026 Callisto Consulting Group LLC DBA Callisto Tech.
All rights reserved.

PROPRIETARY AND CONFIDENTIAL

This software, including all source code, design, logic, prompt architecture, AI system
design, model configurations, algorithms, documentation, and all associated materials
(collectively, the "Software"), is the exclusive proprietary and confidential property
of Callisto Consulting Group LLC DBA Callisto Tech ("the Company"), a limited liability
company organized under the laws of the State of Colorado, USA.

NOTICE: All rights reserved. Unauthorized copying, reproduction, modification,
distribution, transmission, republication, display, or performance of this Software,
in whole or in part, is strictly prohibited. No portion of this Software may be
copied, reproduced, or used in any manner whatsoever without the express prior
written permission of Callisto Consulting Group LLC DBA Callisto Tech.

RESTRICTIONS: This Software is provided solely for authorized internal use.
Any use of this Software outside of authorized purposes -- including but not limited to
reverse engineering, decompiling, disassembling, scraping, or creating derivative works --
is expressly forbidden and may result in severe civil and criminal penalties under
applicable federal and state law, including but not limited to the Computer Fraud and
Abuse Act (18 U.S.C. 1030) and the Colorado Criminal Code.

DATA PRIVACY: This Software processes student financial aid data and personally
identifiable information. Unauthorized access to, disclosure of, or misuse of such
data may constitute violations of the Family Educational Rights and Privacy Act (FERPA),
the Colorado Privacy Act (C.R.S. 6-1-1301 et seq.), and other applicable privacy laws.

NO WARRANTY: This Software is provided "as is" without warranty of any kind, express
or implied, including but not limited to the warranties of merchantability, fitness for
a particular purpose, and noninfringement.

GOVERNING LAW: This license and any disputes arising hereunder shall be governed by
and construed in accordance with the laws of the State of Colorado, without regard to
its conflict of law provisions. Any legal action shall be brought exclusively in the
courts of competent jurisdiction located in Colorado.

Violators will be prosecuted to the maximum extent permitted by applicable law.

For licensing inquiries, please contact: ambreen@callistotech.org
```

**How to apply to a new project:**
- Copy the block above into a `LICENSE` file at the project root
- Update the year range if needed
- Remove the FERPA/DATA PRIVACY paragraph if the new product does not handle student data

---

## SEO / Meta Tags Template

```html
<title>PRODUCT -- TAGLINE</title>
<meta name="description" content="...">
<meta name="robots" content="index, follow">
<link rel="canonical" href="https://DOMAIN/">
<meta name="author" content="Astra">

<!-- Open Graph -->
<meta property="og:type" content="website">
<meta property="og:url" content="https://DOMAIN/">
<meta property="og:title" content="...">
<meta property="og:description" content="...">
<meta property="og:image" content="https://DOMAIN/astra-logo.jpg">
<meta property="og:site_name" content="PRODUCT">

<!-- Twitter Card -->
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="...">
<meta name="twitter:description" content="...">
<meta name="twitter:image" content="https://DOMAIN/astra-logo.jpg">
```
