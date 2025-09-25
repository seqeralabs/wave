/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.service.metric.impl

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.seqera.fixtures.redis.RedisTestContainer
import io.seqera.wave.service.counter.impl.LocalCounterProvider
import io.seqera.wave.service.metric.MetricsCounterStore
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

import static io.seqera.wave.service.metric.MetricsConstants.*
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class MetricsServiceImplTest extends Specification implements RedisTestContainer{

    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    def 'should increment build count and return the correct count' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementBuildsCounter(platformId1, 'amd64')
        metricsService.incrementBuildsCounter(platformId2, 'arm64')
        metricsService.incrementBuildsCounter(null, null)

        then:
        def res1 = metricsService.getOrgCount(PREFIX_BUILDS, date, null)
        res1.count == 3
        res1.orgs == ['org1.com': 1, 'org2.com': 1, anonymous:1]
        and:
        def res2 = metricsService.getOrgCount(PREFIX_BUILDS, null, 'org1.com')
        res2.count == 1
        res2.orgs == ['org1.com': 1]
        and:
        def res3 = metricsService.getOrgCount(PREFIX_BUILDS, date, 'org2.com')
        res3.count == 1
        res3.orgs == ['org2.com': 1]
        and:
        def res4 = metricsService.getOrgCount(PREFIX_BUILDS, date, null)
        res4.count == 3
        res4.orgs == ['org1.com': 1, 'org2.com': 1, anonymous:1]
    }

    def 'should increment pull count and return the correct count' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementPullsCounter(platformId1, 'amd64')
        metricsService.incrementPullsCounter(platformId2, 'arm64')
        metricsService.incrementPullsCounter(null, null)

        then:
        def res1 = metricsService.getOrgCount(PREFIX_PULLS, null, 'org1.com')
        res1.count == 1
        res1.orgs == ['org1.com': 1]
        and:
        def res2 = metricsService.getOrgCount(PREFIX_PULLS,date, 'org2.com')
        res2.count == 1
        res2.orgs == ['org2.com': 1]
        and:
        def res3 = metricsService.getOrgCount(PREFIX_PULLS,date, null)
        res3.count == 3
        res3.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
    }

    def 'should increment fusion pull count and return the correct count' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementFusionPullsCounter(platformId1, 'amd64')
        metricsService.incrementFusionPullsCounter(platformId2, 'arm64')
        metricsService.incrementFusionPullsCounter(null, null)

        then:
        def res1 = metricsService.getOrgCount(PREFIX_FUSION,null, 'org1.com')
        res1.count == 1
        res1.orgs == ['org1.com': 1]
        and:
        def res2 = metricsService.getOrgCount(PREFIX_FUSION, date, 'org2.com')
        res2.count == 1
        res2.orgs == ['org2.com': 1]
        and:
        def res3 = metricsService.getOrgCount(PREFIX_FUSION, date, null)
        res3.count == 3
        res3.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
    }

    @Unroll
    def 'should get correct metrics key'() {
        expect:
        MetricsServiceImpl.getKey(PREFIX, DAY, ORG, ARCH) == KEY

        where:
        PREFIX        | DAY          | ORG    | ARCH        | KEY
        PREFIX_BUILDS | null         | null   | null        | null
        PREFIX_BUILDS | null         | 'wave' | null        | 'builds/o/wave'
        PREFIX_BUILDS | '2024-03-25' | 'wave' | null        | 'builds/o/wave/d/2024-03-25'
        PREFIX_BUILDS | '2024-03-25' | null   | null        | 'builds/d/2024-03-25'
        PREFIX_PULLS  | null         | null   | null        | null
        PREFIX_PULLS  | null         | 'wave' | null        | 'pulls/o/wave'
        PREFIX_PULLS  | '2024-03-25' | 'wave' | null        | 'pulls/o/wave/d/2024-03-25'
        PREFIX_PULLS  | '2024-03-25' | null   | null        | 'pulls/d/2024-03-25'
        PREFIX_FUSION | null         | null   | null        | null
        PREFIX_FUSION | null         | 'wave' | null        | 'fusion/o/wave'
        PREFIX_FUSION | '2024-03-25' | 'wave' | null        | 'fusion/o/wave/d/2024-03-25'
        PREFIX_FUSION | '2024-03-25' | null   | null        | 'fusion/d/2024-03-25'
        PREFIX_BUILDS | '2024-03-25' | 'wave' | 'amd64'     | 'builds/o/wave/a/amd64/d/2024-03-25'
        PREFIX_BUILDS | '2024-03-25' | null   | 'arm64'     | 'builds/a/arm64/d/2024-03-25'
        PREFIX_PULLS  | null         | 'wave' | 'amd64'     | 'pulls/o/wave/a/amd64'
        PREFIX_FUSION | '2024-03-25' | 'wave' | 'arm64'     | 'fusion/o/wave/a/arm64/d/2024-03-25'
    }


    @Unroll
    def'should get correct org name'(){
        expect:
        MetricsServiceImpl.getOrg(EMAIL) == ORG

        where:
        EMAIL               | ORG
        'user@example.com'  | 'example.com'
        'john.doe@test.org' | 'test.org'
        'foo@bar.co.uk'     | 'bar.co.uk'
        'invalid_email'     | null
        null                | null
    }

    def 'should get correct org count'(){
        given:
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementBuildsCounter(platformId1, 'amd64')
        metricsService.incrementBuildsCounter(platformId2, 'arm64')
        metricsService.incrementBuildsCounter(null, null)
        metricsService.incrementPullsCounter(platformId1, 'amd64')
        metricsService.incrementPullsCounter(platformId2, 'arm64')
        metricsService.incrementPullsCounter(null, null)
        metricsService.incrementFusionPullsCounter(platformId1, 'amd64')
        metricsService.incrementFusionPullsCounter(platformId2, 'arm64')
        metricsService.incrementFusionPullsCounter(null, null)
        metricsService.incrementMirrorsCounter(platformId1, 'amd64')
        metricsService.incrementMirrorsCounter(platformId2, 'arm64')
        metricsService.incrementMirrorsCounter(null, null)
        metricsService.incrementScansCounter(platformId1, 'amd64')
        metricsService.incrementScansCounter(platformId2, 'arm64')
        metricsService.incrementScansCounter(null, null)
        and:
        def buildOrgCounts = metricsService.getAllOrgCount(PREFIX_BUILDS)
        def pullOrgCounts = metricsService.getAllOrgCount(PREFIX_PULLS)
        def fusionOrgCounts = metricsService.getAllOrgCount(PREFIX_FUSION)
        def mirrorOrgCounts = metricsService.getAllOrgCount(PREFIX_MIRRORS)
        def scanOrgCounts = metricsService.getAllOrgCount(PREFIX_SCANS)
        def emptyOrgCounts = metricsService.getAllOrgCount(null)

        then:
        buildOrgCounts.metric == PREFIX_BUILDS
        buildOrgCounts.count == 3
        buildOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        pullOrgCounts.metric == PREFIX_PULLS
        pullOrgCounts.count == 3
        pullOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        fusionOrgCounts.metric == PREFIX_FUSION
        fusionOrgCounts.count == 3
        fusionOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        mirrorOrgCounts.metric == PREFIX_MIRRORS
        mirrorOrgCounts.count == 3
        mirrorOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        scanOrgCounts.metric == PREFIX_SCANS
        scanOrgCounts.count == 3
        scanOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        emptyOrgCounts.metric == null
        emptyOrgCounts.count == 0
        emptyOrgCounts.orgs == [:]
    }

    def 'should get correct org count per date'(){
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementBuildsCounter(platformId1, 'amd64')
        metricsService.incrementBuildsCounter(platformId2, 'arm64')
        metricsService.incrementBuildsCounter(null, null)
        metricsService.incrementPullsCounter(platformId1, 'amd64')
        metricsService.incrementPullsCounter(platformId2, 'arm64')
        metricsService.incrementPullsCounter(null, null)
        metricsService.incrementFusionPullsCounter(platformId1, 'amd64')
        metricsService.incrementFusionPullsCounter(platformId2, 'arm64')
        metricsService.incrementFusionPullsCounter(null, null)
        metricsService.incrementMirrorsCounter(platformId1, 'amd64')
        metricsService.incrementMirrorsCounter(platformId2, 'arm64')
        metricsService.incrementMirrorsCounter(null, null)
        metricsService.incrementScansCounter(platformId1, 'amd64')
        metricsService.incrementScansCounter(platformId2, 'arm64')
        metricsService.incrementScansCounter(null, null)
        and:
        def buildOrgCounts = metricsService.getOrgCount(PREFIX_BUILDS, date, null)
        def pullOrgCounts = metricsService.getOrgCount(PREFIX_PULLS, date, null)
        def fusionOrgCounts = metricsService.getOrgCount(PREFIX_FUSION, date, null)
        def mirrorOrgCounts = metricsService.getOrgCount(PREFIX_MIRRORS, date, null)
        def scanOrgCounts = metricsService.getOrgCount(PREFIX_SCANS, date, null)

        then:
        buildOrgCounts.metric == PREFIX_BUILDS
        buildOrgCounts.count == 3
        buildOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        pullOrgCounts.metric == PREFIX_PULLS
        pullOrgCounts.count == 3
        pullOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        fusionOrgCounts.metric == PREFIX_FUSION
        fusionOrgCounts.count == 3
        fusionOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        mirrorOrgCounts.metric == PREFIX_MIRRORS
        mirrorOrgCounts.count == 3
        mirrorOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        scanOrgCounts.metric == PREFIX_SCANS
        scanOrgCounts.count == 3
        scanOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
    }

    def 'should throw exception when prefix is null' (){
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)

        when:
        metricsService.getOrgCount(null, date, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'prefix is required to construct a key'
    }

    def 'should increment build count and return the correct count' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementBuildsCounter(platformId1, 'amd64')
        metricsService.incrementBuildsCounter(platformId2, 'arm64')
        metricsService.incrementBuildsCounter(null, null)

        then:
        def res1 = metricsService.getOrgCount(PREFIX_BUILDS, date, null)
        res1.count == 3
        res1.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        def res2 = metricsService.getOrgCount(PREFIX_BUILDS, null, 'org1.com')
        res2.count == 1
        res2.orgs == ['org1.com': 1]
        and:
        def res3 = metricsService.getOrgCount(PREFIX_BUILDS, date, 'org2.com')
        res3.count == 1
        res3.orgs == ['org2.com': 1]
        and:
        def res4 = metricsService.getOrgCount(PREFIX_BUILDS, date, null)
        res4.count == 3
        res4.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
    }

    def 'should increment pull count and return the correct count with arch' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementPullsCounter(platformId1, 'amd64')
        metricsService.incrementPullsCounter(platformId2, 'arm64')
        metricsService.incrementPullsCounter(null, null)

        then:
        def res1 = metricsService.getOrgCount(PREFIX_PULLS, null, 'org1.com', null)
        res1.count == 1
        res1.orgs == ['org1.com': 1]
        res1.arch == null
        and:
        def res2 = metricsService.getOrgCount(PREFIX_PULLS,date, 'org2.com', null)
        res2.count == 1
        res2.orgs == ['org2.com': 1]
        res2.arch == null
        and:
        def res3 = metricsService.getOrgCount(PREFIX_PULLS,date, null, null)
        res3.count == 3
        res3.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        res3.arch == null
        and:
        def res4 = metricsService.getOrgCount(PREFIX_PULLS, null, 'org1.com', 'amd64')
        res4.count == 1
        res4.orgs == ['org1.com': 1]
        res4.arch == 'amd64'
        and:
        def res5 = metricsService.getOrgCount(PREFIX_PULLS,date, 'org2.com', 'arm64')
        res5.count == 1
        res5.orgs == ['org2.com': 1]
        res5.arch == 'arm64'
        and:
        def res6 = metricsService.getOrgCount(PREFIX_PULLS,date, null, null)
        res6.count == 3
        res6.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        res6.arch == null
    }

    def 'should increment fusion pull count and return the correct count with arch' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementFusionPullsCounter(platformId1, 'amd64')
        metricsService.incrementFusionPullsCounter(platformId2, 'arm64')
        metricsService.incrementFusionPullsCounter(null, null)

        then:
        def res1 = metricsService.getOrgCount(PREFIX_FUSION,null, 'org1.com', null)
        res1.count == 1
        res1.orgs == ['org1.com': 1]
        res1.arch == null
        and:
        def res2 = metricsService.getOrgCount(PREFIX_FUSION, date, 'org2.com', null)
        res2.count == 1
        res2.orgs == ['org2.com': 1]
        res2.arch == null
        and:
        def res3 = metricsService.getOrgCount(PREFIX_FUSION, date, null, null)
        res3.count == 3
        res3.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        res3.arch == null
        and:
        def res4 = metricsService.getOrgCount(PREFIX_FUSION,null, 'org1.com', 'amd64')
        res4.count == 1
        res4.orgs == ['org1.com': 1]
        res4.arch == 'amd64'
        and:
        def res5 = metricsService.getOrgCount(PREFIX_FUSION, date, 'org2.com', 'arm64')
        res5.count == 1
        res5.orgs == ['org2.com': 1]
        res5.arch == 'arm64'
        and:
        def res6 = metricsService.getOrgCount(PREFIX_FUSION, date, null, null)
        res6.count == 3
        res6.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        res6.arch == null
    }

    def 'should get correct org count per date and arch'(){
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementBuildsCounter(platformId1, 'amd64')
        metricsService.incrementBuildsCounter(platformId2, 'arm64')
        metricsService.incrementBuildsCounter(null, null)
        metricsService.incrementPullsCounter(platformId1, 'amd64')
        metricsService.incrementPullsCounter(platformId2, 'arm64')
        metricsService.incrementPullsCounter(null, null)
        metricsService.incrementFusionPullsCounter(platformId1, 'amd64')
        metricsService.incrementFusionPullsCounter(platformId2, 'arm64')
        metricsService.incrementFusionPullsCounter(null, null)
        metricsService.incrementMirrorsCounter(platformId1, 'amd64')
        metricsService.incrementMirrorsCounter(platformId2, 'arm64')
        metricsService.incrementMirrorsCounter(null, null)
        metricsService.incrementScansCounter(platformId1, 'amd64')
        metricsService.incrementScansCounter(platformId2, 'arm64')
        metricsService.incrementScansCounter(null, null)
        and:
        def buildOrgCounts = metricsService.getOrgCount(PREFIX_BUILDS, date, null, null)
        def pullOrgCounts = metricsService.getOrgCount(PREFIX_PULLS, date, null, null)
        def fusionOrgCounts = metricsService.getOrgCount(PREFIX_FUSION, date, null, null)
        def mirrorOrgCounts = metricsService.getOrgCount(PREFIX_MIRRORS, date, null, null)
        def scanOrgCounts = metricsService.getOrgCount(PREFIX_SCANS, date, null, null)
        def buildOrgAndArchCounts = metricsService.getOrgCount(PREFIX_BUILDS, date, null, 'arm64')
        def pullOrgAndArchCounts = metricsService.getOrgCount(PREFIX_PULLS, date, null, 'amd64')
        def fusionOrgAndArchCounts = metricsService.getOrgCount(PREFIX_FUSION, date, null, 'arm64')
        def mirrorOrgAndArchCounts = metricsService.getOrgCount(PREFIX_MIRRORS, date, null, 'amd64')
        def scanOrgAndArchCounts = metricsService.getOrgCount(PREFIX_SCANS, date, null, 'arm64')

        then:
        buildOrgCounts.metric == PREFIX_BUILDS
        buildOrgCounts.count == 3
        buildOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        pullOrgCounts.metric == PREFIX_PULLS
        pullOrgCounts.count == 3
        pullOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        fusionOrgCounts.metric == PREFIX_FUSION
        fusionOrgCounts.count == 3
        fusionOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        mirrorOrgCounts.metric == PREFIX_MIRRORS
        mirrorOrgCounts.count == 3
        mirrorOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        scanOrgCounts.metric == PREFIX_SCANS
        scanOrgCounts.count == 3
        scanOrgCounts.orgs == ['org1.com': 1, anonymous:1, 'org2.com': 1]
        and:
        buildOrgAndArchCounts.metric == PREFIX_BUILDS
        buildOrgAndArchCounts.count == 1
        buildOrgAndArchCounts.orgs == ['org2.com': 1]
        and:
        pullOrgAndArchCounts.metric == PREFIX_PULLS
        pullOrgAndArchCounts.count == 1
        pullOrgAndArchCounts.orgs == ['org1.com': 1]
        and:
        fusionOrgAndArchCounts.metric == PREFIX_FUSION
        fusionOrgAndArchCounts.count == 1
        fusionOrgAndArchCounts.orgs == ['org2.com': 1]
        and:
        mirrorOrgAndArchCounts.metric == PREFIX_MIRRORS
        mirrorOrgAndArchCounts.count == 1
        mirrorOrgAndArchCounts.orgs == ['org1.com': 1]
        and:
        scanOrgAndArchCounts.metric == PREFIX_SCANS
        scanOrgAndArchCounts.count == 1
        scanOrgAndArchCounts.orgs == ['org2.com': 1]
    }

    def 'should get correct org count per arch'(){
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementBuildsCounter(platformId1, 'amd64')
        metricsService.incrementBuildsCounter(platformId2, 'arm64')
        metricsService.incrementBuildsCounter(null, null)
        metricsService.incrementPullsCounter(platformId1, 'amd64')
        metricsService.incrementPullsCounter(platformId2, 'arm64')
        metricsService.incrementPullsCounter(null, null)
        metricsService.incrementFusionPullsCounter(platformId1, 'amd64')
        metricsService.incrementFusionPullsCounter(platformId2, 'arm64')
        metricsService.incrementFusionPullsCounter(null, null)
        metricsService.incrementMirrorsCounter(platformId1, 'amd64')
        metricsService.incrementMirrorsCounter(platformId2, 'arm64')
        metricsService.incrementMirrorsCounter(null, null)
        metricsService.incrementScansCounter(platformId1, 'amd64')
        metricsService.incrementScansCounter(platformId2, 'arm64')
        metricsService.incrementScansCounter(null, null)
        and:
        def buildOrgAndArchCounts = metricsService.getOrgCount(PREFIX_BUILDS, null, null, 'arm64')
        def pullOrgAndArchCounts = metricsService.getOrgCount(PREFIX_PULLS, null, null, 'amd64')
        def fusionOrgAndArchCounts = metricsService.getOrgCount(PREFIX_FUSION, null, null, 'arm64')
        def mirrorOrgAndArchCounts = metricsService.getOrgCount(PREFIX_MIRRORS, null, null, 'amd64')
        def scanOrgAndArchCounts = metricsService.getOrgCount(PREFIX_SCANS, null, null, 'arm64')

        then:
        buildOrgAndArchCounts.metric == PREFIX_BUILDS
        buildOrgAndArchCounts.count == 1
        buildOrgAndArchCounts.orgs == ['org2.com': 1]
        buildOrgAndArchCounts.arch == 'arm64'
        and:
        pullOrgAndArchCounts.metric == PREFIX_PULLS
        pullOrgAndArchCounts.count == 1
        pullOrgAndArchCounts.orgs == ['org1.com': 1]
        pullOrgAndArchCounts.arch == 'amd64'
        and:
        fusionOrgAndArchCounts.metric == PREFIX_FUSION
        fusionOrgAndArchCounts.count == 1
        fusionOrgAndArchCounts.orgs == ['org2.com': 1]
        fusionOrgAndArchCounts.arch == 'arm64'
        and:
        mirrorOrgAndArchCounts.metric == PREFIX_MIRRORS
        mirrorOrgAndArchCounts.count == 1
        mirrorOrgAndArchCounts.orgs == ['org1.com': 1]
        mirrorOrgAndArchCounts.arch == 'amd64'
        and:
        scanOrgAndArchCounts.metric == PREFIX_SCANS
        scanOrgAndArchCounts.count == 1
        scanOrgAndArchCounts.orgs == ['org2.com': 1]
        scanOrgAndArchCounts.arch == 'arm64'
    }

    @Unroll
    def 'extract correct org name from key'(){
        expect:
        MetricsServiceImpl.extractOrgFromKey(KEY) == ORG
        where:
        KEY                                 | ORG
        'builds/o/org1.com/d/2024-05-30'    | 'org1.com'
        'pulls/o/org2.com/d/2024-05-29'     | 'org2.com'
        'fusion/o/org3.com/d/2024-04-30'    | 'org3.com'
        'fusion/d/2024-04-30'               | 'unknown'
    }

    @Unroll
    def 'extract correct org name from key with arch'(){
        expect:
        MetricsServiceImpl.extractOrgFromArchKey(KEY) == ORG
        where:
        KEY                                         | ORG
        'builds/o/org1.com/a/arm64/d/2024-05-30'    | 'org1.com'
        'pulls/o/org2.com/a/amd64/d/2024-05-29'     | 'org2.com'
        'fusion/o/org3.com/a/arm64/d/2024-04-30'    | 'org3.com'
        'fusion/a/amd64/d/2024-04-30'               | 'unknown'
    }

    @Unroll
    def 'extract correct arch from key'(){
        expect:
        MetricsServiceImpl.extractArchFromKey(KEY) == ARCH
        where:
        KEY                                         | ARCH
        'builds/o/org1.com/a/arm64/d/2024-05-30'    | 'arm64'
        'pulls/o/org2.com/a/amd64/d/2024-05-29'     | 'amd64'
        'fusion/o/org3.com/a/arm64/d/2024-04-30'    | 'arm64'
        'fusion/a/amd64/d/2024-04-30'               | 'amd64'
        'builds/o/org1.com/d/2024-05-30'            | 'unknown'
    }
}
