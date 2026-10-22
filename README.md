# Tessera

## Planning workflow (Claude Code skills)

A feature goes from a short description file to Jira tickets through four Claude Code skills. For the details of a skill, read its `SKILL.md` in `~/.claude/skills/<skill>/`.

**`/tessera-plan`** runs the whole flow in one session: spec → design → Jira. First it asks for everything it needs (the feature file and your Jira site, project, email and API token), and it doesn't start until it has all of them. You approve each step before the next one starts. If a run stops halfway, the next run continues from there.

Each step can also be run on its own:

| Skill | What it does | Needs |
|---|---|---|
| `/tessera-spec-writer` | Breaks a feature file into Jira epics → stories → subtasks with points and dependencies. Writes them as files ready to upload to Jira. | A feature description `.md` file |
| `/tessera-design` | Writes the UI/UX design plan (with the ui-ux-pro-max skill), then builds a Claude Design prototype from it. | A ticket, plan or spec-writer backlog |
| `/tessera-jira-integration` | Publishes the approved backlog to Jira, with links and design attachments. | An approved backlog + a Jira API token |

The Jira API token is never saved, so you give it each time.
