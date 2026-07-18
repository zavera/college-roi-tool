# CLAUDE.md

## Git identity

Always commit using the **zavera** account for this project — never any other account (e.g. work/JHU accounts).

- `git config user.name` = `zavera`
- `git config user.email` = `z.averambreen@gmail.com`

This is set locally in this repo's `.gitconfig` already. If commits ever show a different author, re-run:
```
git config --local user.name "zavera"
git config --local user.email "z.averambreen@gmail.com"
```

This applies to all GitHub operations, deployments, API keys, and account-specific actions for this project.

---

## FERPA Compliance

This application handles student financial aid data. FERPA (20 U.S.C. § 1232g, 34 C.F.R. Part 99) rules apply to every code change that touches student data.

### Hard rules — never violate these

- **No student data in logs.** Never log names, SSNs, DOB, EFC/SAI figures, income, assets, or any field extracted from tax documents. Log request IDs and error codes only.
- **No student data in URLs or query strings.** All sensitive data travels in POST bodies over HTTPS only.
- **No cross-user data exposure.** Every API endpoint that reads or writes student profile data must scope the query to the authenticated user's ID. Never accept a `userId` parameter from the client — always resolve it from the server-side session.
- **No AI training on student data.** When calling Groq or any LLM, confirm the provider contract prohibits using request data for model training. Document the provider and their data-use policy in the privacy policy.
- **Minimum necessary data.** Do not collect or store fields that are not directly required by a feature. If a feature is removed, its stored data column must be dropped via a Flyway migration.
- **Retention and deletion.** Student data must be fully deleted or anonymized within 30 days of account deletion. Any new data field introduced must be included in the deletion routine in `FafsaProfileService` (or equivalent).
- **Access controls.** All endpoints under `/api/fafsa/**` and `/api/user/**` must be secured behind Spring Security's authenticated session check. No endpoint may be accessed without a valid JSESSIONID or OAuth token.
- **No sharing without consent.** Do not pass student profile data to third-party services beyond those listed in the privacy policy (Groq and Anthropic/Claude for AI, Stripe for payments, Cloudflare/Railway for infrastructure). Anthropic/Claude was added when FAFSA Prep's asset-repositioning analysis moved off Groq — confirm the privacy policy lists Anthropic and its data-use policy before this ships to production.

### Code review checklist for any PR touching student data

- [ ] No PII in log statements
- [ ] Endpoint scopes query to `session.userId` — no client-supplied user ID
- [ ] New fields added to deletion routine
- [ ] No new third-party data recipient introduced without privacy policy update
- [ ] If AI is called: no raw PII in the prompt (use computed values like "AGI: $X" not "SSN: ...")

### Official source

FERPA implementing regulations: 34 C.F.R. Part 99
FSA Handbook (annual): https://fsapartners.ed.gov/knowledge-center/fsa-handbook

---

## Callisto Tech: Live-Search-First Product Philosophy

Every Callisto Tech product must eliminate context switching — users should never need to leave the product to verify information in a browser. This means:

### Core rule
**Live search is part of the reasoning context, not an optional enhancement.** Before every AI-generated output that references financial aid rules, regulations, or program requirements, fetch live content from authoritative sources (FSA Handbook, studentaid.gov, CFPB, etc.) and inject it into the model's context. Hardcoded rules go stale; live content does not.

Three implementation patterns exist. The first two satisfy the core rule; the third is a **deliberate, reviewed exception** — treat it as the exception, not a template to copy for new features without the same justification.

1. **Tavily fetch, then inject (any model).** A separate `TavilySearchClient` call fetches live content first, then the result is interpolated into the prompt (as `{{liveSearchContent}}`, `{{searchSection}}`, or a feature-specific placeholder) before the generation call. This is the default pattern — cheap, works the same whether the generation step is Groq or Claude. Used by Ask Astra (`FsaHandbookService`, `CreditOfferSearchService`, Groq); Scholarships (`ScholarshipService`, Tavily fetch → Claude Haiku renders/ranks the results, ~10-20s, no `output_config.effort` set since that parameter 400s on Haiku 4.5 — see #4 for why it isn't Claude's own `web_search` tool); Award Assist (`AwardAssistService`, Tavily fetch of school/major-specific facts → Claude Haiku, ~20-30s — see § "Award Assist: deterministic math vs AI" below); and Post-Grad Debt Relief's AI Assist recommendation, State-Specific Assistance, and private-lender-research summaries (`DebtManagementController` → `AnthropicService`, Claude Haiku — see § "Post-Grad Debt Relief" below). Post-Grad's two SSE-streamed hardship-letter generators (`streamHardshipLetter`, `streamPrivateHardshipLetter`) are still on Groq — migrating those needs a different streaming integration and wasn't part of this pass.
2. **Claude features → Claude's native `web_search` tool.** Declare the `web_search` tool directly on the request (restricted to the relevant domains via `allowedDomains`) instead of a separate fetch — the search and the generation happen in one call. **Tried on Scholarships (2026-07-18) and reverted** — see #4. Still valid for a feature where Tavily's specific multi-query/domain fan-out doesn't fit and token cost isn't the binding constraint.
3. **Exception — FAFSA Prep asset-repositioning → hardcoded reference table.** This tab moved off both Tavily and live `web_search` to control token cost (each web-search round trip was 20–60s and multiple tool-call rounds). Instead, `AnthropicService.getAssetRepositioningAdvice` reads pre-written FSA Handbook / studentaid.gov excerpts from the `fafsa_handbook_reference` table (entity: `FafsaHandbookReference`, seeded idempotently by `FafsaHandbookReferenceSeeder` on every startup — local H2 and prod Postgres alike, since Flyway is disabled locally) and injects them via `{{handbookContent}}` in `prompts/fafsa-asset-repositioning-claude-prompt.txt`, same as the old Tavily-fed Groq prompt did. **This content goes stale and has no automatic refresh** — whoever owns this tab must manually review and update the seeded rows whenever the FSA Handbook changes (at minimum, once a year at the new award-year cycle; also on any mid-year SAI formula or PPY rule change). Don't copy this pattern to a new feature just because it's cheaper — it requires an explicit token-cost tradeoff decision each time, the same way this one was made.
4. **Data point — Scholarships tried #2 and reverted to #1.** Removed Tavily entirely and had Claude Sonnet (medium effort, `web_search` restricted to the scholarship-site allowlist) search directly. It worked and returned correct results, but took ~60s+ per search (vs. ~10-20s with Tavily) burning far more tokens on the multi-round tool loop — reverted same day. Takeaway: Claude's `web_search` tool is well suited to a single, open-ended research question; it's a poor fit for replacing a hand-tuned multi-query fan-out search (national + state + major + school-specific queries in parallel) that Tavily was already doing well. Don't re-attempt this for Scholarships without a specific reason to revisit the tradeoff.

### When to apply this
- Any feature calling Groq about FAFSA rules → search `fsapartners.ed.gov` first (Tavily)
- Any feature calling Groq about federal loan programs → search `studentaid.gov` first (Tavily)
- Any feature calling Groq about credit/refinancing → search `studentaid.gov` and `consumerfinance.gov` (Tavily)
- Scholarship guidance → Tavily fetch (multi-query: national/state/major/school-specific), then Claude Haiku formats/ranks the results — do not swap this to Claude's own `web_search` tool without re-reading pattern #4 below, and don't add `output_config.effort` back (400s on Haiku 4.5)
- Any new feature calling Claude for a single, open-ended research question (not a multi-query fan-out) → declaring the `web_search` tool on the request is a reasonable default
- Any new feature needing a specialized multi-query/domain-scoped search (like Scholarships') → prefer Tavily fetch-then-inject over Claude's `web_search` tool, which does its own generic multi-round search and won't replicate hand-tuned query fan-out cheaply
- New AI features in any Callisto product → evaluate a live-search source before building the prompt, and pick the pattern matching the model and the shape of the search need

### Implementation pattern
**Tavily (Groq features):** Use `TavilySearchClient.searchHandbook()` with `include_domains`, `search_depth: "advanced"`, `include_raw_content: true`. Inject the retrieved content as `{{liveSearchContent}}` or similar in the prompt template. If live search fails, fall back gracefully with a note so the model uses its training knowledge with appropriate uncertainty.

**Claude web_search (Claude features):** Declare `WebSearchTool20260209` on the `MessageCreateParams` with `allowedDomains` set to the authoritative sources for that feature, and instruct the prompt to search before answering. No separate fetch step or placeholder substitution needed — the tool call and citations happen inside the same Claude response. Note: this tool version requires Opus 4.6+/4.7/4.8, Sonnet 5, or Sonnet 4.6 — it is not supported on Haiku models; use the basic `web_search_20250305` variant if you need search on a Haiku-tier model.

**Hardcoded reference table (FAFSA Prep asset-repositioning only):** Query `FafsaHandbookReferenceRepository.findByAwardYearOrderById(awardYear)` and inject the formatted rows into `{{handbookContent}}`. To add or correct content, edit `FafsaHandbookReferenceSeeder` (it only inserts rows missing for a given `(topic, awardYear)` — it never overwrites a row already in the DB, so a direct prod DB edit sticks until that row is deleted).

### Why this matters
Financial aid rules change annually (new SAI formula, PPY changes, SAVE plan litigation, PSLF waivers). Specificity and accuracy of financial data are non-negotiable for a product that counselors and families rely on for real decisions. Stale rules = wrong advice = harm to students. This is exactly the risk being taken on for FAFSA Prep's asset-repositioning tab in exchange for lower token cost — mitigate it with the manual-review cadence above.

### Claude proactive guidance
When reviewing code in this project:
- If you see a Groq prompt with hardcoded regulation text → suggest replacing with a live Tavily search
- If you see a Claude prompt with hardcoded regulation text outside FAFSA Prep's asset-repositioning tab → suggest adding the `web_search` tool instead
- If you see a new AI feature without a live-search source → flag it and recommend one matching its model
- If a Groq prompt template lacks `{{liveSearchContent}}` → ask whether it should have one
- If `fafsa_handbook_reference` hasn't been reviewed in the last year, or a new award year has started → flag it as due for a content refresh
- If you see a change that would make the Financial Gap Analysis, Student Profile, Key Metrics, earnings-context table, or repayment-scenario table on Award Assist call an LLM → push back; see below, these are deterministic by design and must stay that way
- If you see a new `objectMapper.readValue(...)` call parsing a Claude or Groq response → check it strips markdown fences first (`GroqService.stripMarkdownFences`); this has caused a raw-JSON-dumped-into-the-UI bug twice now (AI Assist recommendation, then the lender-research summarizer)
- If you see a fallback for a JSON-parse failure that puts the raw model text into a user-facing field (e.g. `Map.of("rationale", rawText)`) → flag it; that's what caused the AI Assist bug — fall back to a clean generic message instead

### Award Assist: deterministic math vs AI (2026-07-18)

Award Assist's output is a deliberate split between two kinds of content — don't blur this line when touching either side:

**Deterministic, no AI, ever:**
- **Financial Gap Analysis** (`#gapAnalysisCard`/`#gapAnalysisContent`) — `recalculateGap()`/`renderSingleGap()`/`renderCompareAccordions()` in `app.html`, pure client-side arithmetic on the aid-package form fields (COA − gift aid − loans − work-study − family contribution). No network call at all.
- **Student & School Profile** table, **Key Metrics** banner, **"For Context: Earnings After Graduation"** table, and the **repayment-scenario table** (10-year standard-plan amortization at 6.5%) inside the AI Financial Summary output — all built in `renderCollegeSection()` in `app.html` from `profile` (client-computed) and `selectedCollege` (Scorecard data), not from `groqData`/the AI response. The amortization math (`pmtCalc`) is a formula, not a model call.

**AI-generated (Claude Haiku + live Tavily search, `AwardAssistService` → `AnthropicService.getFinancialAdvice`):**
- `schoolMajorResources` (professional societies, freshman-year resources), `scholarships`, `employmentOpportunities`, `keyConsiderations` — these need real, current, school-specific facts (club names, office names, deadlines) that go stale and that Claude would otherwise have to invent from training knowledge. `AwardAssistService` runs 4 Tavily queries scoped to the specific college + major, and the prompt (`prompts/award-assist-claude-prompt.txt`) explicitly instructs the model to say a detail is "unconfirmed" rather than fabricate it when the search results don't cover it — this is intentional and shouldn't be relaxed.

If asked to change how the AI section works, that's the surface to touch (`AwardAssistService`, `AnthropicService.getFinancialAdvice`, the prompt file). If asked to change the math/figures shown, that's `app.html`'s `renderCollegeSection`/`recalculateGap` — never route those through an LLM call.

### Post-Grad Debt Relief (2026-07-18)

Migrated three of this tab's Groq calls to Claude Haiku + Tavily, and fixed two real bugs found while doing it — see `AnthropicService.getRepaymentRecommendation` / `getStateAssistanceSummary` / `summarizeSearchResults`, and `DebtManagementController`:

- **AI Assist recommendation** — was silently broken: Groq's `callGroq(prompt, 700, 0.2)` gave it too little token budget for the JSON schema (gpt-oss-120b's internal reasoning ate into the 700 before the JSON body was written), truncating responses mid-JSON. The parse-failure fallback then did `Map.of("rationale", aiJson)` — dumping the raw, truncated JSON text directly into the UI as if it were the model's prose. Fixed by migrating to Haiku with real headroom (2048 tokens) and replacing the fallback with a clean generic message instead of the raw text.
- **PSLF eligibility check** — `checkPslfEligibility(employerName)` only ever checked the employer (government keyword match, then IRS 501(c)(3) lookup) and completely ignored `employmentStatus`, even though full-time employment is a hard PSLF requirement and that field was already being collected in "Your Loan Situation". Fixed: `checkPslfEligibility(employerName, employmentStatus)` now flags "qualifying employer but not full-time" as ineligible with a specific note, instead of returning a blanket "eligible" for any government/nonprofit employer regardless of hours.
- **State-Specific Assistance** — was Groq, generic to the state only (ignored the borrower's actual federal/private balance and income), and returned "3-5 paragraphs" of free text. Now Claude Haiku, prompted with the borrower's real federal/private balance and income, returning structured JSON (`federalPrograms`/`privateRefinancing`/`advocacy` bullet arrays) rendered as bullet lists — and `privateRefinancing` is explicitly told to stay empty if there's no private balance, matching the "never refinance federal into private" warning already shown elsewhere on this tab.
- **Private Lender Research** — `renderPrivateLenderResearch` used to dump every raw Tavily snippet (up to 9 blocks) onto the page for the forbearance and FAQ sections. Now `AnthropicService.summarizeSearchResults` condenses each into a 3-5 sentence summary + up to 3 source links. Loan agreement/promissory-note terms are intentionally NOT summarized (exact legal wording matters there) — just truncated to a shorter snippet.
- **Silent bug caught while fixing the above:** the first version of `summarizeSearchResults` didn't strip markdown code fences before `objectMapper.readValue(...)`, so Claude's ` ```json ... ``` `-wrapped responses failed to parse and the raw fenced JSON leaked into the UI — the exact same class of bug as the AI Assist one above, just introduced fresh. Fixed by adding `GroqService.stripMarkdownFences(raw)` before parsing. **If you add another JSON-parsing call site in this codebase, always strip fences first — Claude and Groq both sometimes wrap JSON in markdown despite being told not to.**

---

## Institutional Multi-Tenancy

This product is designed for institutional use (financial aid offices, counseling centers). The data model reflects this:

- `institutions` table: each institution is a named entity with a unique `code`
- `user_institutions`: many-to-many join (counselor ↔ institution), with `active` flag
- `student_institutions`: many-to-many join (student ↔ institution), with `active` flag
- Email uniqueness is scoped per institution — one email cannot have two accounts at the same institution
- On login, the user's institution is resolved from their email → session; never from a client-supplied parameter
- All institution-scoped queries must derive `institutionId` from the server-side session (same FERPA rule as `userId`)
- Demo institution: "Callisto Tech" (`callisto-tech`), seeded in V8 migration; all new users auto-enroll here
- The chatbot (Ask Astra) answers questions scoped to the logged-in counselor's active institution only
