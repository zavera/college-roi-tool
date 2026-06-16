# CLAUDE.md

## Git identity

Always commit using the **zavera** account for this project — never any other account (e.g. work/JHU accounts).

- `git config user.name` = `zavera`
- `git config user.email` = `zaver.ambreen@gmail.com`

This is set locally in this repo's `.gitconfig` already. If commits ever show a different author, re-run:
```
git config --local user.name "zavera"
git config --local user.email "zaver.ambreen@gmail.com"
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
- **No sharing without consent.** Do not pass student profile data to third-party services beyond those listed in the privacy policy (Groq for AI, Stripe for payments, Cloudflare/Railway for infrastructure).

### Code review checklist for any PR touching student data

- [ ] No PII in log statements
- [ ] Endpoint scopes query to `session.userId` — no client-supplied user ID
- [ ] New fields added to deletion routine
- [ ] No new third-party data recipient introduced without privacy policy update
- [ ] If AI is called: no raw PII in the prompt (use computed values like "AGI: $X" not "SSN: ...")

### Official source

FERPA implementing regulations: 34 C.F.R. Part 99
FSA Handbook (annual): https://fsapartners.ed.gov/knowledge-center/fsa-handbook
