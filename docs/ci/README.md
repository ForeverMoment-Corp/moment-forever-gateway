# Gateway CI/CD Guide (Beginner Friendly)

This document explains the CI pipeline added for the gateway service, why it exists, how it works, and how to verify it safely.

## WHAT

CI (Continuous Integration) is the automated process that runs every time code is pushed or a pull request is opened.

In this repository, CI is defined in:

- `.github/workflows/ci.yml`

The workflow currently runs on:

- `push` to `main`
- `pull_request` targeting `main`

> Placeholder note: if your team uses a different primary branch (for example `master` or `develop`), update the branch filters in `ci.yml`.

## WHY

CI helps you catch problems early and keep code shippable:

- Builds Java code in a consistent environment.
- Runs tests on every change.
- Produces a Docker image tagged with the immutable commit SHA.
- Pushes images to Docker Hub using secure GitHub secrets.
- Optionally updates `latest` only from `main`.

This creates a clear separation:

- **CI** = build, test, package, publish artifact (Docker image).
- **CD** = deployment to runtime environments (not included in this workflow).

## HOW

The workflow executes these stages:

1. **Checkout source**
   - Uses `actions/checkout`.

2. **Set up Java with Maven cache**
   - Uses `actions/setup-java` with Temurin JDK 17 and Maven dependency cache.

3. **Build + test (Maven)**
   - Uses `./mvnw` if present, otherwise `mvn`.
   - Runs `clean verify`.

4. **Dependency/security scan placeholder**
   - A placeholder step where you can add OWASP Dependency-Check, Trivy, Snyk, CodeQL, etc.

5. **Docker build**
   - Builds image from `Dockerfile`.

6. **Docker Hub login**
   - Uses:
     - `secrets.DOCKERHUB_USERNAME`
     - `secrets.DOCKERHUB_TOKEN`

7. **Docker image push**
   - Always pushes immutable tag:
     - `<dockerhub_repo>:${{ github.sha }}`
   - Pushes `latest` only when branch is `main`.

8. **Concurrency protection**
   - Cancel in-progress runs for the same ref to avoid duplicate outdated pipelines.

## Required GitHub configuration

Add these **Repository Secrets**:

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`

Add this **Repository Variable**:

- `DOCKERHUB_REPOSITORY` (example: `your-dockerhub-org/forever-moment-gateway`)

> Placeholder values above must be replaced with your real Docker Hub org/image name.

## HOW TO VERIFY

Use this checklist after committing the workflow:

1. Open a pull request to `main`.
2. Confirm the **Gateway CI** workflow starts automatically.
3. Verify these jobs/steps pass:
   - Java setup + cache
   - Maven build/test
   - Docker build
   - Docker Hub login
   - Docker push
4. In Docker Hub, confirm a new tag matching commit SHA exists.
5. If run is on `main`, confirm `latest` was also updated.

## CI vs CD clarity (important)

This workflow intentionally does **not** contain:

- `kubectl` commands
- ArgoCD commands
- Any deployment step to dev/stage/prod

Those belong to separate CD workflows/pipelines.

## Troubleshooting quick tips

- **Maven command not found**: ensure `mvnw` exists and is executable; fallback uses `mvn`.
- **Docker push fails with unauthorized**: re-check Docker Hub secrets.
- **DOCKERHUB_REPOSITORY missing**: add repository variable in GitHub settings.
- **Wrong branch trigger**: update branch filters from `main` to your team standard.
