# AgentFlow AI — Final Hardening Audit v9

## Scope
 
This pass hardens persistence boundaries identified after the v8 UI/E2E audit. The focus is on preventing partially committed mission state during cancellation, user-input completion, and Inspector approval decisions.

## Fixes in v9

### 1. Mission cancellation is transactional

`MissionEngine.cancelMission()` now wraps the mission state transition and cancellation of RUNNING/READY tasks in one `MissionStore.transaction`.

Result: if task cancellation fails, the mission status/event are rolled back instead of leaving `CANCELLED` with active tasks.

Regression coverage:
- `MissionEngineTest.cancelRollsBackMissionWhenTaskCancellationFails`

### 2. User-input submission is transactional

`MissionEngine.submitUserInput()` now commits the USER_INPUT_RECEIVED event, mission transition, and WAITING_FOR_USER task transitions as one transaction.

The in-memory `resumeTarget` is removed only after the persistence transaction succeeds, avoiding an in-memory resume target being lost after a failed persistence operation.

Regression coverage:
- `MissionEngineTest.submitUserInputRollsBackAuditAndStatusWhenTaskUpdateFails`

### 3. Inspector status + audit event consistency

Inspector approval, rejection-to-revision, escalation, rejection-cap escalation, and user override now use `MissionStore.persistMissionStatus()` so mission state and its audit event are committed together.

User override now records `INSPECTOR_APPROVED` rather than the generic `MISSION_COMPLETED` event, preserving the Inspector audit semantics.

Regression coverage:
- `InspectorFlowTest.userOverrideRollsBackMissionWhenAuditEventFails`
- `InspectorFlowTest.userOverrideWritesApprovedEventAndStatusAtomically`

## Security / release checks reviewed

- `android:allowBackup="false"` is enabled.
- Cleartext traffic is disabled with `android:usesCleartextTraffic="false"`.
- Provider API keys are kept through Android Keystore-backed encryption rather than Room.
- HTTP logging is disabled by default; the optional logger redacts common API-key and Bearer-token patterns.
- Room migrations 1→2 and 2→3 are explicitly registered; there is no destructive migration fallback.
- WorkManager uses unique mission work and exponential backoff.

## Remaining release blockers / limitations

1. Full Android/Gradle test suite could not be executed in this environment because the Gradle wrapper attempts to download Gradle 8.9 from `services.gradle.org`, which is unreachable (`UnknownHostException`).
2. The v9 tests therefore have been added as regression coverage but are not claimed as executed by Gradle here.
3. Room and filesystem artifact consistency remains only partially atomic: artifact content is written to the filesystem before its Room metadata row is inserted. Startup persistence auditing detects missing/mismatched artifact content, but a crash between those two operations can still leave an orphan file.
4. Full device-level E2E (cold start, WorkManager execution, Room persistence, Compose UI) remains a manual/device verification step.

## Release recommendation

**NOT RELEASE-VERIFIED in this environment.**

The source-level hardening changes are included in v9, but a release candidate should only be marked verified after running the complete Gradle unit/instrumentation suite and a device/emulator E2E pass with network and process-death scenarios.
