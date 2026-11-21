/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.service.visibility

import spock.lang.Specification
import spock.lang.Unroll

import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.request.ContainerRequestService
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveScanRecord
/**
 * The default is the whole point: a field that older clients never send must not change what
 * they get today.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class RecordVisibilityServiceTest extends Specification {

    private RecordVisibilityService serviceWith(ContainerBuildService builds = null, ContainerRequestService requests = null) {
        final svc = new RecordVisibilityService()
        svc.@buildService = builds
        svc.@containerService = requests
        return svc
    }

    @Unroll
    def 'a record with isPublic=#flag is public: #expected'() {
        given:
        def svc = serviceWith()

        expect:
        svc.isPublic(flag) == expected

        where:
        flag  | expected
        null  | true      // absent means public - older clients never send the field
        true  | true
        false | false
    }

    def 'a missing scan is public'() {
        given: 'nothing to withhold'
        def svc = serviceWith()

        expect: 'the caller renders its own not-found page'
        svc.isScanPublic(null)
    }

    def 'a scan inherits its build'() {
        given:
        def builds = Mock(ContainerBuildService)
        def svc = serviceWith(builds)
        def scan = new WaveScanRecord(id: 'sc-1', buildId: 'bd-1')

        when:
        def result = svc.isScanPublic(scan)

        then:
        1 * builds.getBuildRecord('bd-1') >> new WaveBuildRecord(buildId: 'bd-1', isPublic: false)
        and:
        !result
    }

    def 'a scan whose build is public is public'() {
        given:
        def builds = Mock(ContainerBuildService)
        def svc = serviceWith(builds)

        when:
        def result = svc.isScanPublic(new WaveScanRecord(id: 'sc-1', buildId: 'bd-1'))

        then:
        1 * builds.getBuildRecord('bd-1') >> new WaveBuildRecord(buildId: 'bd-1', isPublic: true)
        and:
        result
    }

    def 'a scan with no parent is public'() {
        given: 'a scan submitted against an existing image through POST /view/scans'
        def svc = serviceWith(Mock(ContainerBuildService))

        expect: 'nobody declared it private, so it renders as it does today'
        svc.isScanPublic(new WaveScanRecord(id: 'sc-1'))
    }

    // A container request that triggers a build names both parents. The build record is shared
    // between every caller that asked for the same image, so neither parent may override the other.
    @Unroll
    def 'a scan with two parents is public only when both are: build=#build request=#request'() {
        given:
        def builds = Mock(ContainerBuildService)
        def requests = Mock(ContainerRequestService)
        def svc = serviceWith(builds, requests)
        def scan = new WaveScanRecord(id: 'sc-1', buildId: 'bd-1', requestId: 'cr-1')

        when:
        def result = svc.isScanPublic(scan)

        then:
        _ * builds.getBuildRecord('bd-1') >> new WaveBuildRecord(buildId: 'bd-1', isPublic: build)
        _ * requests.loadContainerRecord('cr-1') >> Mock(WaveContainerRecord) { getIsPublic() >> request }
        and:
        result == expected

        where:
        build | request | expected
        null  | null    | true    // neither declared anything
        true  | true    | true
        false | null    | false   // the shared build is private
        null  | false   | false   // the submitter's own request is private
        true  | false   | false   // a public shared build must not override the submitter
        false | true    | false
    }

    def 'a scan whose parent cannot be loaded is public'() {
        given:
        def builds = Mock(ContainerBuildService)
        def svc = serviceWith(builds)

        when:
        def result = svc.isScanPublic(new WaveScanRecord(id: 'sc-1', buildId: 'bd-gone'))

        then:
        1 * builds.getBuildRecord('bd-gone') >> null
        and: 'this governs rendering, not confidentiality - it does not fail closed'
        result
    }
}
