# AgentFlow AI

Personal Android Mission Control for specialized AI agents.

Not SaaS. Not multi-user. Not a chatbot. **Not a fixed pipeline.**

**Rule:** the LLM proposes. The Engine decides.

```
Project → Mission → R&D → Dynamic Task Graph (DAG)
       → specialists (parallel when ready)
       → R&D synthesis → MissionArtifact
       → Inspector → APPROVED | REVISION_REQUIRED | ESCALATED
```

There is no `Pipeline`, `PipelineTemplate`, `PipelineStep`, or `stepIndex`.
Readiness comes from task dependencies. R&D picks agents per mission.

This application does not use a Pipeline abstraction.

## Status

Personal build. CI runs unit tests, lint, and a debug APK.

Package: `com.agentflow`  
Stack: Kotlin, Compose, Material 3, Room, Ktor, WorkManager, Kotlin Serialization, Android Keystore.

## Architecture

```
UI → ViewModel → Repository → Domain / Engine → ProviderManager → AI Provider

Project → Mission → Dynamic Task Graph → Agents
        → R&D synthesis → MissionArtifact → Inspector
        → APPROVED | REVISION_REQUIRED (dynamic revision tasks) | ESCALATED
```

The LLM proposes. DecisionGateway + PolicyEngine + LoopGuard decide. MissionEngine mutates state.

## Providers and API keys

Supported: Gemini, Groq, OpenRouter.

- One API key per provider, entered in Settings → AI Providers.
- Keys are stored in Android Keystore via `SecureApiKeyStore`.
- Agents do **not** store API keys. An Agent stores Provider + Model only.
- `freeOnly = true` by default. `ProviderPolicy` rejects paid/unknown-cost models.

Manual live check:

1. Get a free-tier key from the provider.
2. Settings → AI Providers → paste key → Save.
3. Test connection.
4. Settings → Agents → pick Provider + compatible Model → Save.
5. Test Agent (uses AgentBrain → ProviderManager).

## Agents

Default roster is seeded per Project (R&D Orchestrator, Researcher, Coder, Reviewer, Inspector, Synthesizer, plus specialists).

Each Agent has `AgentModelConfig`. Changing an Agent model does not change the provider key.

`AgentModelCompatibilityValidator` rejects models that fail free-only policy or required capabilities (VISION, STRUCTURED_OUTPUT, REASONING, CODE, TOOLS).

## Inspector

Mission → Inspector review loads the real artifact and review records.

- Approve → APPROVED
- Reject → REVISION_REQUIRED and a dynamic “Review Inspector Findings” task for the orchestrator
- Escalate → ESCALATED

No placeholder review route.

## Requirements

- Android Studio Ladybug / Koala+
- JDK 17
- compileSdk / targetSdk 35, minSdk 26
- Gradle 8.9 (wrapper)

## Setup

1. Clone the repository.
2. Open the `AgentFlowAI` folder in Android Studio.
3. Sync Gradle (generates the wrapper JAR if missing: `gradle wrapper --gradle-version 8.9`).
4. Run on a device/emulator (API 26+).
5. In Settings, add provider API keys. They go to Android Keystore only — never Room, never git.

Free-only is enforced by `ProviderPolicy`. Paid / unknown-cost models are refused.

Supported providers: Gemini, Groq, OpenRouter (free models).

## Build

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Release minify is **off**. Signing keys must stay in GitHub Secrets / local env — never commit a keystore.

## Tests

JVM unit tests cover:

- Mission / Task state machines
- DAG cycle detection and readiness
- MissionEngine (create, parallel tasks, pause/resume/cancel, synthesis)
- JSON contracts + DecisionGateway + LoopGuard
- Inspector reject → revision → approve / escalate / user override
- Provider free-only + sanitized errors
- Chat streaming (fakes)
- Context / secret denylist
- Storage cleanup (user data protected)
- WorkManager policy (skip paused/terminal)

No unit test calls a live AI API.

### Verified in CI

- unit tests
- lint
- debug APK assemble

### Not verified in CI

- real provider HTTP calls
- physical-device process death
- full live Mission → Inspector end-to-end
- instrumentation / Compose UI tests (no emulator job)

## CI

`.github/workflows/android.yml` (and `ci.yml`):

1. JDK 17  
2. `testDebugUnitTest`  
3. `lintDebug`  
4. `assembleDebug`  
5. `lintRelease`  
6. `assembleRelease` (unsigned release APK; signing remains external)  
7. Upload artifact `agentflow-ai-debug-apk`

The job fails if tests, lint, or the APK fail. No `|| true`.

## Architecture notes

| Layer | Authority |
|---|---|
| UI | observes + user actions |
| WorkManager | durable schedule only |
| DecisionGateway | parse untrusted JSON |
| PolicyEngine + LoopGuard | allow / deny |
| MissionEngine | only mutator of mission state |
| Room | metadata source of truth |
| Filesystem | large references / artifacts |
| Keystore | API keys |

Direct Chat is not a Mission. “Send to Mission” is explicit.

Inspector is a quality gate. Revisions are planned by R&D on the same DAG.

Startup recovery: `AgentFlowApp` lists active missions and enqueues unique work `mission-{id}` with `KEEP`.

Notifications only for: user input, completed, failed, Inspector escalation.

Storage budget 500 MB. Automatic cleanup deletes cache/tmp only.

## Security

- No API keys in Room, logs, notifications, or git.
- AI output is untrusted. Actions must pass contract → policy → loop → engine.
- Secret denylist blocks `.env`, `*.pem`, `*.key`, keystore files, `google-services.json`, `local.properties`.
- Artifact paths are generated by the app, never taken from the model.
- Artifact files use same-directory atomic replacement and startup orphan cleanup.
- `usesCleartextTraffic=false`.
- Android backup/device-transfer rules explicitly exclude app files, databases, and preferences.

## Remaining limitations

- Gradle wrapper JAR is generated locally / by Studio; CI needs `gradlew` + jar present after first wrapper generation.
- Graph UI is a DAG list, not a pinch-zoom canvas.
- SAF folder picker UX is partial; `ReferenceManager` is complete.
- Release signing is not configured in-repo (by design).
- This sandbox cannot run `./gradlew` (no Android SDK). Run the commands above on a machine with the SDK.

## Manual device smoke test

1. Install the debug APK from Actions (`agentflow-ai-debug-apk`).
2. Launch AgentFlow AI.
3. Create a Project.
4. Settings → add one free provider key (Groq / Gemini / OpenRouter).
5. Confirm the key is accepted (stored in Android Keystore, not Room).
6. Create/load default agents.
7. Open Direct Chat with any specialist.
8. Send a short request.
9. Confirm a streaming reply (requires a live key; CI uses fakes).
10. Create a Mission with a simple goal.
11. Start the Mission.
12. Confirm it enters planning via the orchestrator agent (capability, not the name “R&D”).
13. Confirm dynamic tasks appear (no fixed pipeline).
14. Let specialists run when dependencies are satisfied.
15. Confirm synthesis produces an Implementation Plan artifact.
16. Confirm Inspector review.
17. Approve or reject; rejection must spawn revision planning, not a hardcoded sequence.
18. Confirm the Mission ends APPROVED, REVISION_REQUIRED, or ESCALATED — never stuck RUNNING after force-stop.
