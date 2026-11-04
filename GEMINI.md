# GEMINI.md

Instructions for the Gemini agent (Antigravity) working in this repository.

中臺機器人研究社 Bluetooth remote-control app: an Android app (Kotlin + Jetpack Compose, package `com.robotctust.robotcontroller`) plus Arduino firmware for the club's cars. The iOS app lives in a separate repo. `legacy/` holds the old App Inventor 2 project (v3.1) — reference only, never edit it.

This project is maintained by two people:

- **The user you are working with** — a club officer who is **not a programmer**. You do all coding, building and git work for them, automatically.
- **The maintainer (Johnlin)** — works with Claude Code using `CLAUDE.md`, watches the remote, and may push his own commits to the same branches.

> **Keep this file current.** When a change alters anything documented here, update it in the same commit. The Commands, Gotchas and Commit & versioning sections are shared with `CLAUDE.md` — change both files together.

## Talking to the user

- Always reply in **繁體中文**, in plain language. Don't assume they know git, Gradle or Kotlin terms; explain briefly what you did and why.
- Do the git workflow yourself without asking for permission at each step — but **stop and ask** in the cases listed under "Stop and ask".
- You cannot test Bluetooth or the real car. Whenever behaviour on the phone or car matters, tell the user exactly what to try (which screen, which button, what should happen) and wait for their answer.

## Commands

Build with Android Studio's bundled JDK. Android Studio creates `local.properties` (git-ignored, points to the Android SDK) the first time the project is opened.

```bash
# macOS
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

```powershell
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

Firmware (only if `arduino-cli` is installed; otherwise ask the user to press **Verify** in Arduino IDE):

```bash
arduino-cli compile --fqbn arduino:avr:uno firmware/bluetooth-remote-control
arduino-cli compile --fqbn arduino:avr:uno firmware/bluetooth-remote-control-arm   # needs: arduino-cli lib install Servo
```

**Verification before every commit:** `assembleDebug` and `testDebugUnitTest` succeed, and any firmware you touched compiles. Never commit code that doesn't build.

## Gotchas

- The Bluetooth protocol (`F/B/L/R/S`, `0`–`9`, `M<servo><angle>`) is shared by `BluetoothManager.kt` and both sketches — change all sides together and update the README protocol section.
- Both `firmware/*/motor.ino` files are identical copies; keep them in sync.
- Keep `targetSdk` unchanged unless the work is specifically about adopting new runtime behaviour; `compileSdk` can move independently when libraries require it.
- Never commit `local.properties`, keystores, passwords/API keys, `build/` output or `.apk` files (`.gitignore` covers these — don't weaken it).

## Commit & versioning workflow

Same rules as the club website (`robotctust/robotctust.com`) and `CLAUDE.md`, except that **you run git yourself and push to GitHub automatically**, so the maintainer always sees the latest work.

### Rules

- **Never commit directly to `main`.** The only commits on `main` are release merges (see "Release"). All work happens on a series branch `feature/<short-english-name>`.
- **One commit = one feature or fix = one Beta.** A series can have many Betas: a big feature with several parts becomes Beta 1, Beta 2, …, each a separate commit. Related docs (README, this file) go in the same commit.
- **Version** moves together in three places: the commit title, `versionName` and `versionCode` in `app/build.gradle.kts`. `versionCode` goes up by 1 on every bump (beta or release).
- The target version is picked when a series starts, from the current `versionName` on `main`: new features → next minor (`4.0.0` → series `4.1`); fixes / performance / refactoring only → next patch (`4.0.0` → series `4.0.1`).

  | Commit | Commit title | `versionName` |
  | :--- | :--- | :--- |
  | Beta on a minor series | `v4.1 Beta 2 feat: …` | `4.1-beta.2` |
  | Beta on a patch series | `v4.0.1 Beta 1 fix: …` | `4.0.1-beta.1` |
  | Release merge on `main` | `v4.1.0 release: …` | `4.1.0` |

- **Never** force-push, rebase or amend commits that are already pushed, reset `main`, delete branches (local or remote), or create tags / GitHub Releases (the maintainer uploads release APKs).

### At the start of every session

```bash
git fetch origin
git status            # must be clean; if not, ask the user what the leftover changes are
git branch -r --no-merged origin/main   # open series branches
```

- If an open `feature/*` branch exists, continue on it (`git switch <branch> && git pull --rebase`) — unless the user's request is unrelated to it; then ask the user before starting a second series (two open series would claim the same version numbers).
- If none exists, start a new series only when you are about to change files (see below).

### Start a series

```bash
git switch main && git pull --ff-only
git switch -c feature/<name>
```

### Make a Beta commit (after each finished feature or fix)

1. `git pull --rebase` on the branch first — the maintainer may have pushed commits. Read `versionName` / `versionCode` **after** pulling, so beta numbers never collide.
2. Bump the version: next beta number, `versionCode` + 1.
3. Run the verification above; fix until it passes.
4. Write the message to `COMMIT_MESSAGE.md` (git-ignored), then:

   ```bash
   git add -A
   git commit -F COMMIT_MESSAGE.md
   git push -u origin HEAD
   ```

   Delete `COMMIT_MESSAGE.md` afterwards. Tell the user which Beta was pushed and what to test on the phone.

### Release (merge the series back to `main`)

Only when the user says the feature is done **and** they have tested it on a phone (and on the car, if Bluetooth or firmware changed). If they haven't tested, ask them to first.

```bash
git switch <branch> && git pull --rebase && git push
git switch main && git pull --ff-only
git merge --no-ff --no-commit <branch>
```

Inside the merge, bump `versionName` to `X.Y.Z` and `versionCode` + 1, run the verification, write `RELEASE_NOTES.md` (git-ignored), then:

```bash
git add -A
git commit -F RELEASE_NOTES.md
git push origin main
```

It must be a real merge commit — never squash, never fast-forward. **Keep the branch** after merging. The next series starts from the updated `main`.

### Message format

Copy the style of `git log`. Title `vX.Y Beta N <type>: <中文標題>` (`type` is `feat` / `fix` / `chore` / `docs` / `refactor` / `perf`), a one-line English summary, then categorized bullets in 中文 (Feature Addition, Bug Fix, UI Adjustments, Refactoring, Version, …; omit empty ones). One line per bullet saying what changed; no per-file detail, no build logs.

```text
v4.1 Beta 1 feat: 新增車速儀表顯示

Show the current speed level as a gauge on the car control screen

Feature Addition:
- 自走車控制畫面新增速度儀表，即時顯示目前速度等級

Version:
- versionName 升至 4.1-beta.1，versionCode 升至 9
```

Release messages (`vX.Y.0 release: <摘要>`) summarize **all** of the series' Betas: the title, an English one-liner, an overview paragraph, categorized bullets, and a closing `Commit Summary:` paragraph.

### Stop and ask

Stop, explain the situation in simple words, and ask the user (suggest they contact Johnlin) when:

- `git pull`, `git merge` or `git push` reports a **conflict** or is rejected — never resolve it by discarding the other side's changes;
- the working tree has changes you didn't make;
- the build or tests fail and you can't fix them;
- the request is unrelated to an open series branch;
- the user asks for a release but hasn't tested on a phone.
