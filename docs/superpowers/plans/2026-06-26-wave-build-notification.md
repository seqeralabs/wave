# Wave Build Notification Preference Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `waveBuildNotification` enum preference to the Tower `User` model and gate `sendCompletionEmail` on it, so users can control whether they receive Wave build completion emails (always, on failure only, or never).

**Architecture:** A new `WaveBuildNotification` enum carries three constants (`ALWAYS_ON`, `ON_ERROR`, `ALWAYS_OFF`) and a `defaultValue()` factory (returns `ALWAYS_ON`). The enum is added as a nullable field on `User`, deserialized from Tower's user-info JSON via Moshi. In `MailServiceImpl.sendCompletionEmail`, two early-return guards check the resolved preference against the build outcome before `spooler.sendMail` is called. The existing `noEmail` flag in `BuildRequest` / `onBuildEvent` is untouched — it is a system-level sub-build suppressor and takes priority.

**Tech Stack:** Groovy, Micronaut 4, Spock 2, Moshi JSON

## Global Constraints

- Java 21+, Groovy with `@CompileStatic` on all new production classes
- Spock 2 for all tests (`extends Specification`)
- Run a single test class: `./gradlew test --tests 'fully.qualified.ClassName'`
- Run the full suite: `./gradlew test`
- The `noEmail` field on `BuildRequest` and its check in `onBuildEvent` must remain unchanged
- Null `waveBuildNotification` in the payload must be treated as `ALWAYS_ON` (backward compatible default)

---

### Task 1: `WaveBuildNotification` enum

**Files:**
- Create: `src/main/groovy/io/seqera/wave/tower/WaveBuildNotification.groovy`
- Create (test): `src/test/groovy/io/seqera/wave/tower/WaveBuildNotificationTest.groovy`

**Interfaces:**
- Produces: `io.seqera.wave.tower.WaveBuildNotification` — enum with constants `ALWAYS_ON`, `ON_ERROR`, `ALWAYS_OFF`; static method `defaultValue(): WaveBuildNotification` returning `ALWAYS_ON`

---

- [ ] **Step 1: Write the failing test**

Create `src/test/groovy/io/seqera/wave/tower/WaveBuildNotificationTest.groovy`:

```groovy
package io.seqera.wave.tower

import spock.lang.Specification

class WaveBuildNotificationTest extends Specification {

    def 'should define three constants'() {
        expect:
        WaveBuildNotification.values().length == 3
        WaveBuildNotification.valueOf('ALWAYS_ON')  == WaveBuildNotification.ALWAYS_ON
        WaveBuildNotification.valueOf('ON_ERROR')   == WaveBuildNotification.ON_ERROR
        WaveBuildNotification.valueOf('ALWAYS_OFF') == WaveBuildNotification.ALWAYS_OFF
    }

    def 'defaultValue returns ALWAYS_ON'() {
        expect:
        WaveBuildNotification.defaultValue() == WaveBuildNotification.ALWAYS_ON
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```
./gradlew test --tests 'io.seqera.wave.tower.WaveBuildNotificationTest'
```

Expected: compilation error — `WaveBuildNotification` does not exist yet.

- [ ] **Step 3: Create the enum**

Create `src/main/groovy/io/seqera/wave/tower/WaveBuildNotification.groovy`:

```groovy
/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.tower

import groovy.transform.CompileStatic

@CompileStatic
enum WaveBuildNotification {
    ALWAYS_ON,
    ON_ERROR,
    ALWAYS_OFF

    static WaveBuildNotification defaultValue() { ALWAYS_ON }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```
./gradlew test --tests 'io.seqera.wave.tower.WaveBuildNotificationTest'
```

Expected: BUILD SUCCESSFUL, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/groovy/io/seqera/wave/tower/WaveBuildNotification.groovy \
        src/test/groovy/io/seqera/wave/tower/WaveBuildNotificationTest.groovy
git commit -m "feat: add WaveBuildNotification enum"
```

---

### Task 2: Add `waveBuildNotification` field to `User`

**Files:**
- Modify: `src/main/groovy/io/seqera/wave/tower/User.groovy`
- Create (test): `src/test/groovy/io/seqera/wave/tower/UserTest.groovy`

**Interfaces:**
- Consumes: `WaveBuildNotification` (Task 1)
- Produces: `User.waveBuildNotification: WaveBuildNotification` — nullable; Moshi deserializes it from JSON key `"waveBuildNotification"` using the enum constant name (e.g. `"ON_ERROR"`); absent key leaves field as `null`

---

- [ ] **Step 1: Write the failing test**

Create `src/test/groovy/io/seqera/wave/tower/UserTest.groovy`:

```groovy
package io.seqera.wave.tower

import spock.lang.Specification

class UserTest extends Specification {

    def 'waveBuildNotification is null when not set'() {
        given:
        def user = new User(id: 1L, userName: 'alice', email: 'alice@example.com')
        expect:
        user.waveBuildNotification == null
    }

    def 'waveBuildNotification stores enum value'() {
        given:
        def user = new User(id: 1L, userName: 'alice', email: 'alice@example.com',
                waveBuildNotification: WaveBuildNotification.ON_ERROR)
        expect:
        user.waveBuildNotification == WaveBuildNotification.ON_ERROR
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```
./gradlew test --tests 'io.seqera.wave.tower.UserTest'
```

Expected: compilation error — `User` has no `waveBuildNotification` property.

- [ ] **Step 3: Add the field to `User`**

In `src/main/groovy/io/seqera/wave/tower/User.groovy`, add one field after `email`. The full updated file:

```groovy
/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.tower

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.serde.moshi.MoshiSerializable
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@ToString(includeNames = true, includePackage = false, includes = 'id,userName,email')
@EqualsAndHashCode
@CompileStatic
class User implements MoshiSerializable {

    Long id

    @NotNull
    @Size(max = 40)
    String userName

    @NotNull
    @Size(max = 255)
    String email

    WaveBuildNotification waveBuildNotification

}
```

- [ ] **Step 4: Run the test to verify it passes**

```
./gradlew test --tests 'io.seqera.wave.tower.UserTest'
```

Expected: BUILD SUCCESSFUL, 2 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/groovy/io/seqera/wave/tower/User.groovy \
        src/test/groovy/io/seqera/wave/tower/UserTest.groovy
git commit -m "feat: add waveBuildNotification field to User"
```

---

### Task 3: Gate logic in `sendCompletionEmail` + tests

**Files:**
- Modify: `src/main/groovy/io/seqera/wave/service/mail/impl/MailServiceImpl.groovy`
- Modify (test): `src/test/groovy/io/seqera/wave/service/mail/MailServiceImplTest.groovy`

**Interfaces:**
- Consumes: `WaveBuildNotification.defaultValue()` (Task 1), `User.waveBuildNotification` (Task 2), `BuildResult.succeeded(): boolean` (existing), `MailSpooler.sendMail(Mail): void` (existing)
- Produces: `sendCompletionEmail` respects the preference; behaviour is unchanged when the field is null (defaults to `ALWAYS_ON`, sends on every outcome)

---

- [ ] **Step 1: Write the failing tests**

The `MailServiceImplTest` is a `@MicronautTest` spec. We need to replace the real `MailSpoolerImpl` bean with a Spock `Mock` so we can assert whether `sendMail` was called. Add the `@MockBean` declaration plus `@Inject MailSpooler spooler` to the class, then add the parametrized test.

Final state of `src/test/groovy/io/seqera/wave/service/mail/MailServiceImplTest.groovy`:

```groovy
/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.mail

import spock.lang.Specification

import java.time.Instant

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.mail.impl.MailServiceImpl
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.tower.WaveBuildNotification
import jakarta.inject.Inject

@MicronautTest(environments = 'mail')
class MailServiceImplTest extends Specification {

    @Inject MailServiceImpl service

    @Inject
    MailSpooler spooler

    @MockBean(MailSpoolerImpl)
    MailSpooler mockSpooler() {
        Mock(MailSpooler)
    }

    def 'should create build mail' () {
        given:
        def recipient = 'foo@gmail.com'
        def result = BuildResult.completed('12345', 0, 'pull foo:latest', Instant.now(), 'abc')
        def request= Mock(BuildRequest)

        when:
        def mail = service.buildCompletionMail(request, result, recipient)
        then:
        1* request.getContainerFile() >> 'from foo';
        1* request.getTargetImage() >> 'seqera.io/wave/build:xyz'
        1* request.getPlatform() >> ContainerPlatform.DEFAULT
        1* request.getCondaFile() >> null
        and:
        mail.to == recipient
        mail.body.contains('from foo')
        mail.body.contains('seqera&#8203;.io/wave/build:xyz')
        and:
        !mail.body.contains('Conda file')

        // check it adds the Conda file content
        when:
        mail = service.buildCompletionMail(request, result, recipient)
        then:
        1* request.getTargetImage() >> 'wave/build:xyz'
        1* request.getPlatform() >> ContainerPlatform.DEFAULT
        1* request.getCondaFile() >> 'bioconda::foo'
        and:
        mail.to == recipient
        mail.body.contains('Conda file')
        mail.body.contains('bioconda::foo')

    }

    def 'should replace dot with non breaking name' () {
        expect:
        MailServiceImpl.preventLinkFormatting(NAME) == EXPECTED

        where:
        NAME                         | EXPECTED
        null                         | null
        'foo'                        | 'foo'
        'www.host.com/this/that'     | 'www&#8203;.host&#8203;.com/this/that'
    }

    def 'sendCompletionEmail respects waveBuildNotification preference'() {
        given:
        def user = new User(id: 1L, userName: 'testuser', email: 'test@example.com',
                waveBuildNotification: pref)
        def identity = new PlatformId(user, null, null, null, null)
        def request = Stub(BuildRequest) {
            getIdentity()      >> identity
            getIp()            >> '1.2.3.4'
            getOffsetId()      >> 'UTC'
            getTargetImage()   >> 'wave/build:test'
            getFormat()        >> null
            getCompression()   >> null
            getPlatform()      >> ContainerPlatform.DEFAULT
            getBuildTemplate() >> null
            getContainerFile() >> 'FROM ubuntu'
            getCondaFile()     >> null
        }
        def result = BuildResult.completed('bd-test_0', exitCode, 'logs', Instant.now(),
                exitCode == 0 ? 'sha256:abc' : null)

        when:
        service.sendCompletionEmail(request, result)

        then:
        expectedCalls * spooler.sendMail(_)

        where:
        pref                             | exitCode | expectedCalls
        null                             | 0        | 1
        null                             | 1        | 1
        WaveBuildNotification.ALWAYS_ON  | 0        | 1
        WaveBuildNotification.ALWAYS_ON  | 1        | 1
        WaveBuildNotification.ON_ERROR   | 0        | 0
        WaveBuildNotification.ON_ERROR   | 1        | 1
        WaveBuildNotification.ALWAYS_OFF | 0        | 0
        WaveBuildNotification.ALWAYS_OFF | 1        | 0
    }
}
```

Note: `@MockBean(MailSpoolerImpl)` references the concrete class `io.seqera.wave.service.mail.impl.MailSpoolerImpl`. Add the import:
```groovy
import io.seqera.wave.service.mail.impl.MailSpoolerImpl
```

- [ ] **Step 2: Run the tests to verify the new cases fail**

```
./gradlew test --tests 'io.seqera.wave.service.mail.MailServiceImplTest'
```

Expected: existing tests pass; the 3 new rows with `expectedCalls == 0` fail because `sendCompletionEmail` currently always calls `sendMail` when a recipient is present.

- [ ] **Step 3: Implement the gate logic**

Add the import for `WaveBuildNotification` in `MailServiceImpl` and replace `sendCompletionEmail`. The full updated method block in `src/main/groovy/io/seqera/wave/service/mail/impl/MailServiceImpl.groovy`:

At the top of the file add after the existing static imports:
```groovy
import io.seqera.wave.tower.WaveBuildNotification
```

Replace the existing `sendCompletionEmail` method (the `@Override` block starting at line 73) with:

```groovy
@Override
void sendCompletionEmail(BuildRequest request, BuildResult build) {
    final user = request.identity.user
    final recipient = user ? user.email : config.from
    if( !recipient ) {
        log.debug "Missing email recipient from build id=$build.buildId - user=$user"
        return
    }
    final result = build ?: BuildResult.unknown()
    final pref = user?.waveBuildNotification ?: WaveBuildNotification.defaultValue()
    if( pref == WaveBuildNotification.ALWAYS_OFF )
        return
    if( pref == WaveBuildNotification.ON_ERROR && result.succeeded() )
        return
    final mail = buildCompletionMail(request, result, recipient)
    spooler.sendMail(mail)
}
```

- [ ] **Step 4: Run the tests to verify all pass**

```
./gradlew test --tests 'io.seqera.wave.service.mail.MailServiceImplTest'
```

Expected: BUILD SUCCESSFUL — all existing tests plus all 8 parametrized rows pass.

- [ ] **Step 5: Run the full test suite to check for regressions**

```
./gradlew test
```

Expected: BUILD SUCCESSFUL, no regressions.

- [ ] **Step 6: Commit**

```bash
git add src/main/groovy/io/seqera/wave/service/mail/impl/MailServiceImpl.groovy \
        src/test/groovy/io/seqera/wave/service/mail/MailServiceImplTest.groovy
git commit -m "feat: gate sendCompletionEmail on waveBuildNotification user preference"
```
