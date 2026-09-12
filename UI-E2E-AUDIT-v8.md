# AgentFlow AI — UI / E2E Audit v8

## Scope

Mission UI flows from creation through execution, Inspector review, revision, escalation, and explicit user override.

## Findings fixed

1. Mission workspace exposed Start/Pause/Resume/Cancel/Inspector without respecting the domain state machine. The UI now derives action availability from `MissionUiPolicy`.
2. A failed `startMission()` was followed by unconditional WorkManager enqueue. The UI now schedules only after a successful start and surfaces the failure.
3. Mission command failures were silently discarded by the ViewModel. Pause/resume/cancel failures now appear in the workspace.
4. `Approve Anyway` existed only in the service layer and was unreachable from the review UI. It is now an explicit, reason-recording action limited to review/escalation states.
5. Removed the unused legacy MissionWorkspace composable that contained a second direct start/schedule path, eliminating a future UI bypass.

## Manual E2E matrix

- CREATED → Start → PLANNING/EXECUTING
- Failed Start → no work enqueue + visible error
- EXECUTING → Pause → PAUSED → Resume → scheduled execution
- EXECUTING → Cancel → CANCELLED; terminal actions hidden
- REVIEWING → Inspector → Approve with valid artifact
- REVIEWING → Reject → REVISION_REQUIRED
- REVISION_REQUIRED → revised artifact → Inspector → Approve
- ESCALATED → Approve Anyway → APPROVED with recorded reason
- Restart app during active mission → recovery → UI reflects persisted state

## Verification limitation

The Gradle wrapper still cannot download Gradle 8.9 in the current environment because `services.gradle.org` is unreachable (`UnknownHostException`). Pure Kotlin policy tests were compiled and executed directly with `kotlinc`; the full Android/Gradle suite remains unverified until a network-enabled/cached Gradle environment is available.
