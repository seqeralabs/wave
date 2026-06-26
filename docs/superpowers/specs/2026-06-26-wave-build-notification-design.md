# Wave Build Notification Preference — Design Spec

**Date:** 2026-06-26
**Branch:** robnewman-user-build-notification

---

## Overview

Add a `waveBuildNotification` user preference to the Tower user-info payload. This controls whether Wave sends a build-completion email for a given user, based on build outcome. The preference lives on the `User` model (deserialized from Tower's user-info API response) and is checked inside `sendCompletionEmail` in `MailServiceImpl`.

---

## Enum: `WaveBuildNotification`

**File:** `src/main/groovy/io/seqera/wave/tower/WaveBuildNotification.groovy`
**Package:** `io.seqera.wave.tower`

Three constants:

| Constant | JSON value | Human-readable label | Behaviour |
|---|---|---|---|
| `ALWAYS_ON` | `"ALWAYS_ON"` | Always On | Email sent regardless of build outcome |
| `ON_ERROR` | `"ON_ERROR"` | On Error | Email sent only when the build fails |
| `ALWAYS_OFF` | `"ALWAYS_OFF"` | Always Off | No email sent, regardless of build outcome |

A `defaultValue()` static factory method returns `ALWAYS_ON`. This is the single source of truth for the null-fallback — no magic strings or repeated literals in callers.

JSON serialization uses Moshi's default enum adapter (constant name as string). No custom adapter needed.

---

## Model change: `User`

**File:** `src/main/groovy/io/seqera/wave/tower/User.groovy`

Add one nullable field:

```groovy
WaveBuildNotification waveBuildNotification
```

- No `@NotNull` — field is absent from older Tower clients and must be nullable for backward compatibility.
- `@EqualsAndHashCode` automatically includes the new field.
- Default (null) is treated as `ALWAYS_ON` by the gate logic.

---

## Gate logic: `sendCompletionEmail`

**File:** `src/main/groovy/io/seqera/wave/service/mail/impl/MailServiceImpl.groovy`

The existing `noEmail` check in `onBuildEvent` is unchanged — it is a system-level flag that suppresses email for sub-builds in multi-platform builds, and it takes priority regardless of user preference.

Inside `sendCompletionEmail`, after the recipient is resolved, add the preference gate before `spooler.sendMail`:

```groovy
final pref = user?.waveBuildNotification ?: WaveBuildNotification.defaultValue()
if( pref == WaveBuildNotification.ALWAYS_OFF )
    return
if( pref == WaveBuildNotification.ON_ERROR && result.succeeded() )
    return
```

Decision table:

| Preference | Build succeeded | Send email? |
|---|---|---|
| `null` (→ ALWAYS_ON) | yes | yes |
| `null` (→ ALWAYS_ON) | no | yes |
| `ALWAYS_ON` | yes | yes |
| `ALWAYS_ON` | no | yes |
| `ON_ERROR` | yes | no |
| `ON_ERROR` | no | yes |
| `ALWAYS_OFF` | yes | no |
| `ALWAYS_OFF` | no | no |

`result.succeeded()` uses the existing `BuildResult` method (`exitStatus == 0`). The `BuildResult.unknown()` fallback has `exitStatus == -1`, so it correctly counts as a failure for `ON_ERROR`.

---

## Testing

**File:** `src/test/groovy/io/seqera/wave/service/mail/MailServiceImplTest.groovy`

New Spock test cases cover all eight rows of the decision table above. Each test:
- Stubs a `BuildRequest` mock with `identity.user.waveBuildNotification` set to the relevant value
- Provides a `BuildResult` with `exitStatus` 0 (success) or 1 (failure)
- Asserts `spooler.sendMail` is called exactly 1 time (expect email) or 0 times (no email)

Existing tests are unaffected. `MultiPlatformBuildServiceTest` `noEmail` tests are unaffected.

---

## Files changed

| File | Change |
|---|---|
| `src/main/groovy/io/seqera/wave/tower/WaveBuildNotification.groovy` | New — enum with three constants and `defaultValue()` |
| `src/main/groovy/io/seqera/wave/tower/User.groovy` | Add nullable `waveBuildNotification` field |
| `src/main/groovy/io/seqera/wave/service/mail/impl/MailServiceImpl.groovy` | Add preference gate in `sendCompletionEmail` |
| `src/test/groovy/io/seqera/wave/service/mail/MailServiceImplTest.groovy` | Add 8 new test cases for the decision table |

---

## Out of scope

- No changes to `BuildRequest`, `PlatformId`, or `MailService` interface signature.
- No changes to the email template (`build-notification.html`).
- No UI or API surface changes to Wave itself — the preference is set on the Tower side and arrives via the user-info response.
