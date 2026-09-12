# AgentFlow AI — Release Candidate Audit & Implementation Report

Date: 2026-09-12
Source: `AgentFlowAI-main (4).zip`
Scope: source tree, Android configuration, provider policy, Mission security boundary, persistence, AI context, CI and tests.

## Executive result

Status after patch: **Release Candidate hardening applied; full build verification still required in a network-enabled Android CI environment.**

The supplied ZIP was a single source tree with no duplicate project. The audit found several real issues and the following fixes were applied directly to the replacement tree.

The local environment could not complete Gradle because the wrapper needs to download Gradle 8.9 from `services.gradle.org` and outbound network access is unavailable. Therefore this package must not be described as "compile verified" until CI executes the Gradle test/lint/assemble matrix.

## Changes applied

### 1. Google Play / Android API 36

Changed:

- `app/build.gradle.kts`
  - `compileSdk = 35` → `compileSdk = 36`
  - `targetSdk = 35` → `targetSdk = 36`

Reason: Google Play requires new apps and app updates to target Android 16/API 36 from 31 August 2026.

External verification:
- Google Play target API requirements:
  https://support.google.com/googleplay/android-developer/answer/11926878

### 2. Foreground Service hardening

Changed:

- `app/src/main/AndroidManifest.xml`

Added:

- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `androidx.work.impl.foreground.SystemForegroundService`
- `android:foregroundServiceType="dataSync"`
- `tools:node="merge"`

The existing `MissionWorker` already supplies `FOREGROUND_SERVICE_TYPE_DATA_SYNC` through `ForegroundInfo`; the missing manifest declaration was the configuration gap.

External verification:
- Android foreground service types:
  https://developer.android.com/develop/background-work/services/fgs/service-types
- Launching foreground services:
  https://developer.android.com/develop/background-work/services/fgs/launch

### 3. Free-model policy corrected

Changed:

- `ProviderPolicy.kt`
- `DefaultAgentFactory.kt`
- `ProviderPolicyTest.kt`

Groq GPT-OSS 20B/120B were removed from the free allow-list. Groq currently publishes paid token pricing for both models.

The default seeded agent was also changed from:

`Groq / openai/gpt-oss-20b`

to:

`Gemini / gemini-2.5-flash`

with fallback:

`Gemini / gemini-2.5-flash-lite`

Both are currently documented by Google with a Free Tier.

Important correction to the previous audit:
the earlier statement that `gemini-3.8-flash` is paid-only is **not correct according to the current Google pricing page**. The current official pricing page lists a Free Tier for Gemini 3.8 Flash. It remains in the explicit Gemini free catalog, while the catalog continues to reject unknown IDs.

External verification:
- Groq model pricing:
  https://console.groq.com/docs/models
- Groq GPT-OSS 20B:
  https://console.groq.com/docs/model/openai/gpt-oss-20b
- Groq GPT-OSS 120B:
  https://console.groq.com/docs/model/openai/gpt-oss-120b
- Gemini pricing:
  https://ai.google.dev/gemini-api/docs/pricing

### 4. Gemini thinking configuration fixed

Changed:

- `GeminiDtos.kt`
- `GeminiMapper.kt`
- `GeminiMapperTest.kt`

Behavior:

- Gemini 2.5 → numeric `thinkingBudget`
- Gemini 3.x → `thinkingLevel`
- Unknown Gemini families → no thinking configuration is emitted

Reason: Google documents `thinkingBudget` for Gemini 2.5 and `thinkingLevel` for Gemini 3.x.

External verification:
- Gemini thinking:
  https://ai.google.dev/gemini-api/docs/generate-content/thinking

### 5. Mission ownership hardened

Changed:

- `MissionEngine.kt`
- `PolicyEngine.kt`
- `PolicyEngineTest.kt`

The Engine now validates task ownership itself before mutation for:

- AssignTask
- AddDependency
- RemoveDependency
- RequestUserInput
- CompleteTask
- FailTask
- RetryTask

This is intentionally duplicated at the Engine boundary because `DecisionGateway` is not the only possible caller.

`RemoveDependency` no longer accepts an arbitrary dependency ID and deletes it directly. It must resolve to a dependency belonging to the current Mission.

### 6. Single MissionAction transaction

Changed:

- `MissionEngine.processAction()`

Non-artifact single actions are now executed inside `store.transaction`.

This closes the previous gap where:

`state mutation → event persistence`

could occur as separate operations for direct single-action calls.

Artifact publication remains outside the Room transaction because filesystem and SQLite cannot share a native ACID transaction; the existing artifact journal/recovery mechanism remains responsible for that boundary.

### 7. LoopGuard concurrency hardening

Changed:

- `DecisionGateway.kt`
- `LoopGuard.kt`
- `RoomMissionStore.kt`

`DecisionGateway.ingest()` now serializes the complete:

`inspect → execute → record`

sequence.

`RoomMissionStore.incrementLoopGuard()` is now itself wrapped in a Room transaction.

`RemoveDependency` also receives a stable LoopGuard signature instead of falling into the generic action-class signature.

### 8. Secret scanning hardened

Changed:

- `SecretDenylist.kt`
- `ReferenceManager.kt`
- `SecretDenylistTest.kt`

The previous content scanner only examined the first 8KB. It now scans the full supplied text.

The denylist also covers additional credential-like names/suffixes.

Text-like imported files are content-scanned before persistence.

This prevents a secret placed after the old 8KB boundary from bypassing the reference import guard.

### 9. Prompt-injection boundary improved

Changed:

- `MissionContextFactory.kt`

References and previous task results are now explicitly framed as untrusted data:

`<untrusted-reference-data>`

`<untrusted-task-results>`

The model is explicitly told not to follow instructions contained in those blocks.

This does not replace PolicyEngine validation; it is an additional defense-in-depth layer.

### 10. Mission event audit no longer stops at 200 events

Changed:

- `MissionEventDao.kt`
- `RoomMissionStore.kt`

Persistence consistency checks now use a full chronological event query instead of the previous 200-event UI-style page.

### 11. Notification permission flow added

Changed:

- `MainActivity.kt`

On Android 13/API 33+, the app now requests `POST_NOTIFICATIONS` through the Android runtime permission API.

The Worker remains responsible only for background work and does not attempt to request runtime permissions.

External verification:
- Android notification runtime permission:
  https://developer.android.com/develop/ui/compose/notifications/notification-permission

### 12. CI updated for API 36

Changed:

- `.github/workflows/android.yml`

CI now installs:

- `platforms;android-36`
- `build-tools;36.0.0`

The redundant `.github/workflows/ci.yml` was removed.

The Android workflow still runs:

- unit tests
- debug lint
- release lint
- debug assemble
- release assemble

## Android 15 dataSync assessment

The current Worker uses `dataSync`.

Android 15+ limits `dataSync` foreground services to a total of 6 hours per app in a rolling 24-hour period, with `Service.onTimeout()` behavior.

AgentFlow currently has a 30-minute Mission runtime limit, so a single Mission is below the platform limit. However, multiple background Missions can accumulate toward the shared application limit.

This is therefore **not treated as a blocker in this patch**, but it remains a release verification item.

External verification:
- Android 15 behavior changes:
  https://developer.android.com/about/versions/15/behavior-changes-15
- FGS timeout documentation:
  https://developer.android.com/develop/background-work/services/fgs/timeout

## Files modified

1. `app/build.gradle.kts`
2. `app/src/main/AndroidManifest.xml`
3. `app/src/main/java/com/agentflow/data/network/ai/gemini/GeminiDtos.kt`
4. `app/src/main/java/com/agentflow/data/network/ai/gemini/GeminiMapper.kt`
5. `app/src/main/java/com/agentflow/data/dao/MissionEventDao.kt`
6. `app/src/main/java/com/agentflow/data/repository/RoomMissionStore.kt`
7. `app/src/main/java/com/agentflow/domain/agent/DefaultAgentFactory.kt`
8. `app/src/main/java/com/agentflow/domain/mission/MissionContextFactory.kt`
9. `app/src/main/java/com/agentflow/domain/mission/MissionEngine.kt`
10. `app/src/main/java/com/agentflow/domain/policy/DecisionGateway.kt`
11. `app/src/main/java/com/agentflow/domain/policy/LoopGuard.kt`
12. `app/src/main/java/com/agentflow/domain/policy/PolicyEngine.kt`
13. `app/src/main/java/com/agentflow/domain/provider/ProviderPolicy.kt`
14. `app/src/main/java/com/agentflow/domain/reference/ReferenceManager.kt`
15. `app/src/main/java/com/agentflow/domain/reference/SecretDenylist.kt`
16. `app/src/main/java/com/agentflow/ui/MainActivity.kt`
17. `.github/workflows/android.yml`

## Tests modified/added

- `GeminiMapperTest`
  - Gemini 2.5 thinking budget
  - Gemini 3.x thinking level
  - unknown model family behavior

- `ProviderPolicyTest`
  - Groq GPT-OSS not free
  - Gemini free-tier catalog
  - OpenRouter `:free`

- `SecretDenylistTest`
  - additional credential filenames
  - secret detection beyond the previous 8KB boundary

- `PolicyEngineTest`
  - cross-Mission RequestUserInput rejection
  - cross-Mission RemoveDependency rejection

## Verification status

### Completed statically

- ZIP extracted successfully.
- Single source tree verified.
- Kotlin/XML/YAML files patched.
- Required Android 36 target configuration applied.
- Foreground service declarations added.
- Security ownership checks added.
- Provider catalog corrected.
- Gemini mapping corrected.
- New unit-test cases added.
- CI SDK installation updated.

### Not completed in this environment

The following could not be executed because Gradle 8.9 could not be downloaded:

```text
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
```

The failure happened before project compilation, at the Gradle distribution download step.

## Required release verification

Run in CI or a development machine with Android SDK 36 and network access:

```bash
chmod +x gradlew

./gradlew clean
./gradlew testDebugUnitTest --stacktrace
./gradlew lintDebug --stacktrace
./gradlew lintRelease --stacktrace
./gradlew assembleDebug --stacktrace
./gradlew assembleRelease --stacktrace
```

Then perform device tests on Android 14, Android 15 and Android 16.

Minimum E2E scenarios:

```text
Create Mission
→ Planning
→ Tasks
→ Parallel execution
→ Synthesis
→ Artifact
→ Review

Reject
→ Revision
→ New Artifact
→ Review

Pause
Resume
Cancel
Process death
Provider timeout
Provider 429
Network loss
Worker retry
App restart
Notification permission denied
```

## Final engineering assessment

The architecture does not require a rewrite.

The correct strategy is targeted hardening:

```text
API 36
→ Android runtime correctness
→ provider cost policy
→ Mission ownership
→ atomic mutation
→ concurrency protection
→ secret/prompt-injection protection
→ persistence verification
→ E2E validation
```

After the Gradle/Android matrix passes, the project should be treated as a **Release Candidate**, not before.

