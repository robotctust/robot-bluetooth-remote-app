# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

中臺機器人研究社 Bluetooth remote-control app: an Android app (Kotlin + Jetpack Compose, package `com.robotctust.robotcontroller`) plus Arduino firmware for the club's cars. The iOS app lives in a separate repo (`robotctust/robot-bluetooth-remote-app-for-ios`). `legacy/` holds the old App Inventor 2 project (v3.1) — reference only, don't edit.

> **Keep this file current.** When a change alters anything documented here, update it in the same piece of work. The Commands, Gotchas and Commit & versioning sections are shared with `GEMINI.md` — change both files together.

## Collaboration

A second club officer (not a programmer) develops with Gemini in Antigravity, driven by `GEMINI.md`. Unlike Claude, that agent commits **and pushes** on its own: Beta commits on `feature/*` branches, and release merges into `main` once its user has tested on a phone. So the remote moves without the maintainer:

- Before **every new request** (not just once per session), run `git fetch origin` and check `git branch -r --no-merged origin/main`. If the work belongs to an open series branch, continue on it after `git pull --rebase --autostash`. Never start a second parallel series without asking — both would claim the same version numbers.
- New series: `git switch main && git pull --ff-only`, then cut the branch and confirm `git merge-base --is-ancestor origin/main HEAD` succeeds.
- Read `versionName` / `versionCode` only after pulling, so beta numbers never collide.

## Commands

Use Android Studio's bundled JDK (no system JDK on the maintainer's Mac). `local.properties` (git-ignored) points to `~/Library/Android/sdk`.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # only template example tests so far
arduino-cli compile --fqbn arduino:avr:uno firmware/bluetooth-remote-control
arduino-cli compile --fqbn arduino:avr:uno firmware/bluetooth-remote-control-arm   # needs the Servo library
```

Verification before writing a commit message: `assembleDebug` + `testDebugUnitTest` succeed, and any firmware you touched compiles.

## Gotchas

- The Bluetooth protocol (`F/B/L/R/S`, `0`–`9`, `M<servo><angle>`) is shared by `BluetoothManager.kt` and both sketches — change all sides together and update the README protocol section.
- Both `firmware/*/motor.ino` files are identical copies; keep them in sync.
- Keep `targetSdk` unchanged unless the work is specifically about adopting new runtime behaviour; `compileSdk` can move independently when libraries require it.

## Commit & versioning workflow

Same rules as the club website (`robotctust/robotctust.com`, its CLAUDE.md "Commit & versioning workflow"), adapted to Android:

- **One commit = one feature or fix.** Related docs (README, this file) go in the same commit.
- **Claude never runs `git add` / `git commit`.** When work is finished and verified, Claude writes the message to `COMMIT_MESSAGE.md` (git-ignored) at the repo root and bumps the version; the maintainer reviews, commits, and deletes the file.
- **Version** moves together in three places: the commit title, `versionName` and `versionCode` in `app/build.gradle.kts`. Beta uses `X.Y-beta.N` (patch series: `X.Y.Z-beta.N`) for `versionName`; `versionCode` goes up by 1 on every bump (beta or release) so each build installs over the previous one. New features → next minor; fix / perf / refactor only → next patch.

  | Commit title | `versionName` |
  | :--- | :--- |
  | `v4.0 Beta 1 feat: …` | `4.0-beta.1` |
  | `v4.0.1 Beta 1 fix: …` | `4.0.1-beta.1` |
  | `v4.0.0 release: …` (the `--no-ff` merge commit on `main`, which also does the bump) | `4.0.0` |

- **Branches & releases:** each version series gets its own branch cut from the latest `main` (`feature/<name>`); its beta commits stay there. To release: `git checkout main && git merge --no-ff --no-commit <branch>`, Claude bumps the version inside the merge and writes `RELEASE_NOTES.md` (git-ignored), and the maintainer concludes with `git commit -F RELEASE_NOTES.md` — a real merge commit, never squash or fast-forward. Keep release branches after merging. Upload the release APK to GitHub Releases (APKs are never committed).
- **Message format:** `vX.Y Beta N <type>: <中文標題>` (plain `vX.Y.Z release: …` on `main`), a one-line English summary, then categorized bullets in 中文 (Feature Addition, Bug Fix, UI Adjustments, Refactoring, Version, …; omit empty ones). One line per bullet saying what changed; no per-file detail, no verification log. Release messages summarize all betas: overview paragraph, categorized bullets, and a closing `Commit Summary:` paragraph.
