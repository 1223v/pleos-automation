# A05 independent review — 2026-11-01

Review scope: R16/R17/R11, W25/W26/W16, T31/T41/T42; approved implementation plan lines 66, 90–92 and 112; current A05.1–A05.4 in `13_IMPLEMENTATION_TODO.md` and A05 section of `docs/superpowers/plans/2026-10-29-consistency-tdd-security.md`.

## Verdict

**Specification:** A05 functionality is substantially implemented, but final A05.4 validation remains pending at this review snapshot. Snapshot/version/hash preservation, start-cause migration, historical condition links, explanation API/client, first32+last96 event window and explicit omitted count are present. No unsupported lowering of the product completion criteria was found.

**Quality/security:** changes requested for **one Important async editor-integrity issue** below. No Critical issue found. The explanation path is read-only: strict response parsing rejects extra patch/command fields; referenced sequences must belong to the given node; displayed status and durable history are not rewritten by AI text. Auth, Origin rejection, request/response sizes, budget reservation, duplicate-ID handling and shared busy gate remain applied. Actual AI, external connector operations and seat unlock were not performed by this review.

## Important

### I1 — An older history-open operation can overwrite a newer editor interaction

Location: `android/app/src/main/java/com/pleos/routine/MainActivity.kt:138–148`, especially suspended DAO lookup at140 and unconditional editor replacement at143–148; caller319 launches into the screen-wide scope.

The function reads the stored routine asynchronously, then cancels whichever planning job currently exists, calls `load(edit.workflow)`, replaces the request text and opens a condition editor. Its only post-await check is driving UX restriction. It has no request generation, editor snapshot comparison or page check. Leaving the history page does not cancel this coroutine because the parent screen remains composed.

Controlled scheduling reproduction (derived directly from the code, **not executed in this read-only review**): delay the history DAO lookup; click a historical condition; while it is pending, return to the creator and change the working draft/request or begin a new planning request; release the lookup. The earlier request unconditionally replaces the newer draft and request, and cancels the newer planner. The existing autosave at MainActivity181–197 then persists the replacement. Two delayed history opens can likewise apply in completion order rather than last-user-selection order. Stored routine rows are not immediately overwritten, but the user's newer working draft is lost.

Fix suggestion: make history opening an explicitly supersedable operation. Capture a generation/editor identity before suspension and reject completion after a newer edit/load/navigation/entry or history-open request; cancel the old job where possible. Another small option is selecting the current routine from the already collected `saved` state synchronously and avoiding the DAO suspension for this UI-only decision. Add one controlled-delay test demonstrating that the newer editor interaction survives. The change should preserve the existing copy-on-different-snapshot rule.

## Minor observations

- `RunHistoryCard.kt:19–24,52–57` keys explanation state and stale-response checks only on `row`, while recovered-run evidence also depends on `partial`. `MainActivity.kt:317` initially supplies an empty event Flow value. For a recovered UNKNOWN row whose events have not loaded yet, an explanation can be requested with zero events and retained after the durable prefix arrives. The server accurately warns about insufficient evidence, and no status is upgraded, so this is not a safety bypass. Distinguish loading from a truly empty journal and/or include the effective evidence in the response identity.
- `RunHistory.kt:46` labels every AUTOMATIC cause as “자동 조건 진입”, although Runtime uses this cause for TIME and SEAT_OCCUPIED triggers as well. “자동 실행” or a label derived from the retained trigger would be more precise. The expanded card already displays the historical trigger, and the stored enum is correct.
- `MainActivity.kt:146` builds the condition-edit request with `RunHistory.events(row, emptyList())`; interrupted runs with only a durable journal lose their visible event context in that suggested text. Passing the loaded journal or querying it would improve the follow-up request without inferring a result.

## Reviewed evidence and limits

Read current `server/src/explanations.ts`, `planner.ts`, `http.ts`, `contracts.ts`, `server/test/run-explanations.test.ts`, `evidence/A05/server-report.md`; Android `RunHistory`, `RunExplanation`, `RunHistoryCard`, MainActivity history/simulation/actual-test integration, Runtime cause propagation, Room3 migrations, journal, explanation transport, unit/Compose/migration test sources. Checked strict Kotlin serialization and semantic-hash implementation. Read the exact Android before/after package `.local/A05/review-package.md`. Git status shows app/server untracked relative to the repository baseline; HEAD is not a reliable A05 diff.

- `server-final-green.log`:50PASS,0FAIL,0SKIP. `server-typecheck.log` and server handoff record typecheck success. The official SDK wire test replaces fetch; it is not a real provider call.
- `ui-green.log`:5 executed,4PASS/1FAIL. The failing history-copy assertion read an older working draft; the updated source now awaits the expected copied snapshot. That source fix needs its own completed build/retest result; this review does not convert a pending rerun into PASS.
- Room migration test source covers1→3 and2→3 in one passing test, including legacy missing cause and recovery preservation; these are not two separately executed test methods.
- Local fixture18787 and the HTTP UI integration test are implemented; execution was pending when this review was assigned. Pending execution is a verification gate, not a missing implementation finding.
- No tests, builds, actual provider calls or physical operations were run by this reviewer. Only this report was written. Core/app total counts and final APK/hash/log linkage remain the coordinator's consolidation responsibility.

**Declined to judge:** A05 attribution of all server changes without a pre-A05 full server baseline; actual OpenAI Korean explanation factuality; service-account entitlement; actual Pleos SDK reconnection; seat movement/STOP calibration; long-duration/deep-sleep behavior; final release10-run completion. These are missing provenance or inherited validation gates, not newly discovered A05 code defects. The model's natural-language truth cannot be proven by schema/reference validation alone; the UI correctly treats it as advisory and preserves UNKNOWN.

This verdict is limited to the source state reviewed before any response to I1. Re-review the affected lines after remediation and link the final validation evidence before marking A05.4 complete.
