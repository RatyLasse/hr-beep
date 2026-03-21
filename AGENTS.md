# AGENTS.md

## Repo workflow

- At the start of each conversation, run `git pull` to ensure you are working on the latest code from the remote before making any changes.
- Use Git regularly during development.
- After each meaningful, working milestone, create a normal commit with a clear message.
- Push committed work to the tracked remote branch so the repo stays backed up and inspectable.
- Do not leave substantial implemented changes uncommitted unless the user explicitly asks for that.
- When fixing a bug or regression, add or update an automated test that fails before the fix and passes after it.
- When changing monitoring/session state behavior, avoid duplicating state-reset logic across files; prefer shared helpers or other single-source-of-truth logic.
- After each completed app change, run the relevant automated tests, at minimum `./gradlew testDebugUnitTest`.
- After each completed app change, rebuild and install the app if the user's phone is connected over ADB.
- When an item from `IDEAS.txt` has been implemented, remove that item from `IDEAS.txt`.
