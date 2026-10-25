# markitDONE

Personal todo planner with recurring todos.

## Stack

| Folder | Stack |
|---|---|
| `backend/` | Java 25, Spring Boot 4.1.1, Gradle 9.8.2, PostgreSQL 18 |
| `frontend/` | Angular 22.2, PrimeNG 22.1, TypeScript |
| `e2e/` | Playwright |
| `docker/` | Docker, docker-compose, nginx |

## Run locally

### Infrastructure only

For local development, you can start just the database and mail services, then run the backend and frontend in your IDE.

First, set up the required JWT secret:
```bash
export MARKITDONE_JWT_SECRET=your-secret-here
```

Or add it to a `.env` file in the repo root (it will be used by `docker compose`).

Then start the infrastructure:
```bash
docker compose up -d
```

In a new terminal, start the backend with the `dev` profile:
```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=dev'
```

In another terminal, start the frontend dev server (the app will be available at `http://localhost:4200`). Ensure you have Node 24 installed (see `frontend/.nvmrc`):
```bash
cd frontend
npm ci
npx ng serve
```

You can access Mailpit (the mail UI) at `http://localhost:8025`.

### Full stack in Docker

To run the entire stack with all services in Docker, you must first set up the required JWT secret:
```bash
export MARKITDONE_JWT_SECRET=your-secret-here
```

Or add it to a `.env` file in the repo root.

Then start all services:
```bash
docker compose --profile app up -d --build
```

The app will be available at `http://localhost:8081`. Stop the services with:
```bash
docker compose --profile app down
```

## Formatting

Code formatting is enforced using Spotless. To format all Java, Gradle, and YAML files in the backend:

```bash
cd backend && ./gradlew spotlessApply
```

To check if code matches the formatting rules without applying changes:

```bash
cd backend && ./gradlew spotlessCheck
```

## Design system

The source of truth for UI work is [`docs/design-system/MASTER.md`](docs/design-system/MASTER.md). It contains the design plan, component specifications, and page definitions.

## Planning workflow (Tessera)

A feature goes from a short description file to Jira tickets through four Claude Code skills. For the details of a skill, read its `SKILL.md` in `~/.claude/skills/<skill>/`.

**`/tessera-plan`** runs the whole flow in one session: spec → design → Jira. First it asks for everything it needs (the feature file and your Jira site, project, email and API token), and it doesn't start until it has all of them. You approve each step before the next one starts. If a run stops halfway, the next run continues from there.

Each step can also be run on its own:

| Skill | What it does | Needs |
|---|---|---|
| `/tessera-spec-writer` | Breaks a feature file into Jira epics → stories → subtasks with points and dependencies. Writes them as files ready to upload to Jira. | A feature description `.md` file |
| `/tessera-design` | Writes the UI/UX design plan (with the ui-ux-pro-max skill), then builds a Claude Design prototype from it. | A ticket, plan or spec-writer backlog |
| `/tessera-jira-integration` | Publishes the approved backlog to Jira, with links and design attachments. | An approved backlog + a Jira API token |

The Jira API token is never saved, so you give it each time.
