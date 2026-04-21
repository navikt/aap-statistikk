---
name: create-pr
description: Lag en pull request med gjeldende endringer — oppretter branch, committer, pusher og åpner PR
allowed-tools: Bash(git:*) Bash(gh pr:*) Bash(gh repo:*)
---

# PR Skill

Du er en Git- og GitHub-ekspert. Din jobb er å ta alle ucommittede og/eller upushede endringer i repoet og lage en ferdig pull request klar for review.

## Tillatte verktøy

Du har tilgang til **alle** git- og gh-verktøy: `git`, `gh`, og alle GitHub MCP-verktøy.

## Arbeidsflyt

Følg disse stegene i rekkefølge:

### 1. Kartlegg nåværende tilstand

```bash
git status
git --no-pager diff --stat
git --no-pager log origin/main..HEAD --oneline
```

Sjekk:
- Er det ucommittede endringer (staged eller unstaged)?
- Er det commits som ikke er pushet?
- Hvilken branch er vi på?
- Er vi allerede på main/master? I så fall må vi opprette en ny branch.

### 2. Opprett branch om nødvendig

Hvis vi er på `main` eller `master`, opprett en ny branch basert på innholdet i endringene:

```bash
# Eksempel — tilpass navn til endringene
git checkout -b feat/kort-beskrivelse-av-endring
```

Branchnavnet skal:
- Starte med `feat/`, `fix/`, `chore/` eller `refactor/` avhengig av type endring
- Være kort og beskrivende (maks 5 ord, bruk bindestrek)
- Være på norsk bokmål hvis det er naturlig, ellers engelsk

### 3. Commit endringer

Hvis det er ucommittede endringer:

```bash
git add -A
git commit -m "<type>: <kort beskrivelse>

<valgfri lengre beskrivelse av hva som er endret og hvorfor>
```

Commit-meldingen skal:
- Følge Conventional Commits (`feat:`, `fix:`, `chore:`, `refactor:`, `docs:`, `test:`)
- Være på norsk bokmål
- Ha en kort, beskrivende tittel (maks 72 tegn)
- Ha en lengre beskrivelse hvis endringene er komplekse

### 4. Push til remote

```bash
git push -u origin HEAD
```

### 5. Opprett pull request

```bash
gh pr create \
  --title "<tittel>" \
  --body "<beskrivelse>" \
  --base main
```

PR-beskrivelsen skal inneholde:
- **Hva** er endret (kort oppsummering)
- **Hvorfor** er det endret (kontekst og motivasjon)
- **Hvordan teste** hvis relevant
- Referanse til issue hvis det finnes (`Closes #123`)

### 6. Rapporter resultatet

Skriv ut PR-lenken og en kort oppsummering av hva som ble gjort.

## Regler

### ✅ Alltid

- Inkluder `Co-authored-by: Copilot` i commit-meldingen, men KUN hvis copilot var med å skrive.
- Sett `--base main`
- Sjekk `git status` før du gjør noe
- Bruk norsk bokmål i commit-meldinger og PR-tittel/-beskrivelse

### ⚠️ Spør først

- Hvis det er konflikt med remote branch
- Hvis endringene ser ut til å tilhøre flere logiske commits (vurder om de bør splittes)
- Hvis du er usikker på hva PR-en skal hete

### 🚫 Aldri

- Push direkte til `main` eller `master`
- Force-push (`git push --force`) uten eksplisitt godkjenning
- Commit hemmeligheter eller credentials
- Overskriv eksisterende commits med `git commit --amend` på en delt branch
