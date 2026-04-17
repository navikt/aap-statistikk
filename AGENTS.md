# Agent Guidelines

## Role

You are a senior software engineer assistant.

## Git

- **Never commit or push changes.** Always leave changes uncommitted for the user to review.
- The user will stage, commit, and push after reviewing.
- **Never push directly to `main`.** All changes must go through a pull request.
- **Never push to `main` even when asked to "just push it".** Always create a new branch and open a PR — no exceptions. This includes changes to `AGENTS.md` itself.

## Communication

Chat with the user in English. Code comments and commit messages should be in Norwegian bokmål — never nynorsk. If you don't know the bokmål term, ask.

Known terms (Norwegian): "bug" (not "bugg").

## Coding Guidelines

- **Never store temporary files in /tmp** — this includes PR body files, debug output, scripts, etc.
- Always create temporary files in the current project directory (the repo root)
- Use filenames like `temp_output.txt`, `debug_log.txt`, `pr-body.txt` etc. in the project root

### Small, Incremental Changes

- **Always make the smallest possible change** that moves toward the goal
- Add new methods/functions rather than modifying existing ones when possible
- Keep existing functionality working while adding new features
- One logical change at a time - don't combine multiple improvements
- Verify each change compiles and tests pass before moving to next change

### Documentation

- Don't add docstrings to methods and classes unless they are very complicated
- Prefer clear method names over documentation
- Add comments only when the code needs clarification
- **Always run `npx prettier --write <file>` on markdown files after creating or editing them — this is mandatory, not optional. Do not mark a task complete without having done this.**

### Immutable State

We prefer immutable state throughout the codebase. Avoid mutating objects and collections; instead, create new instances with the desired changes.

### Test-Driven Development

Always write tests before making functional changes. Tests should be written first to:

- Define expected behavior
- Drive the design of the implementation
- Ensure the changes work as intended

### Testing Strategy

For testing, prefer in-memory fakes over mocks. Fakes provide:

- More realistic behavior
- Better refactoring support
- Clearer test intentions
- Reduced coupling to implementation details

### Gradle Commands

- **Never use --no-daemon** when running Gradle tests or builds - it's slower
- The Gradle daemon improves build performance through caching and hot JVM
- **Always run `./gradlew test` after making code changes** to ensure all tests pass. While working, run tests on relevant files to save time.
- **Run `./gradlew detektMain` after significant code changes** to check code quality
- Fix any Detekt violations before committing
- Compiling alone is not sufficient — always run the full test suite
- **Do not mark a task complete without having run and verified tests pass**

### Domain Classes vs. External Contracts

Never use external contract types (e.g. `behandlingsflyt.kontrakt`) directly in domain logic, repositories, or database storage. Always map to local domain classes first. This keeps the domain decoupled from external contracts.

### BQBehandling Duplication Logic

**Important**: BQBehandling events are NOT saved if they are duplicates of the last event.

- Duplication check is in `BQBehandling.ansesSomDuplikat()` (BQSak.kt line 69)
- It compares all fields EXCEPT: `sekvensNummer`, `erResending`, `tekniskTid`, `endretTid`, `versjon`
- If two consecutive events differ ONLY in these ignored fields, the second one is NOT saved
- This means if only `saksbehandler` or `ansvarligEnhetKode` changes, a new event IS created
- But if saksbehandler stays the same across multiple behandling state changes, only the first is saved

### BigQuery Views

When adding a new BigQuery view under `.nais/bigquery/`:

- **Always add the new view to `.github/workflows/deploy_bigquery.yml`** under the `RESOURCE` list.
- Deployment order matters: views that are referenced by other views must appear **before** those views in the list.
