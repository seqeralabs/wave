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

package io.seqera.wave.service.blob.transfer

import spock.lang.Specification

import java.time.Duration

import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.impl.BlobCacheStore
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class TransferManagerTest extends Specification {

    def "handle should process valid transferId"() {
        given:
        def blobStore = Mock(BlobCacheStore)
        def queue = Mock(TransferQueue)
        def transferStrategy = Mock(TransferStrategy)
        def blobConfig = Mock(BlobCacheConfig)
        def manager = new TransferManager(blobStore: blobStore, queue: queue, transferStrategy: transferStrategy, blobConfig: blobConfig)

        and:
        def blob = BlobCacheInfo.create('http://foo.com', 's3://foo/com', [:], [:])

        when:
        manager.handle(blob.objectUri)

        then:
        1 * blobStore.get(blob.objectUri) >> blob
        thrown(RuntimeException) // this is thrown from handle0 method
    }

    def "handle should log error for unknown transferId"() {
        given:
        def transferId = 'unknown'
        def blobStore = Mock(BlobCacheStore)
        def queue = Mock(TransferQueue)
        def transferStrategy = Mock(TransferStrategy)
        def blobConfig = Mock(BlobCacheConfig)
        def manager = new TransferManager(blobStore: blobStore, queue: queue, transferStrategy: transferStrategy, blobConfig: blobConfig)

        when:
        manager.handle(transferId)

        then:
        1 * blobStore.get(transferId) >> null
        0 * manager.handle0(_)
    }

    def "handle0 should complete transfer when status is completed"() {
        given:
        def blobStore = Mock(BlobCacheStore)
        def transferStrategy = Mock(TransferStrategy)
        def blobConfig = Mock(BlobCacheConfig)
        def manager = new TransferManager(blobStore: blobStore, transferStrategy: transferStrategy, blobConfig: blobConfig)
        def blob = BlobCacheInfo.create('http://foo.com', 's3://foo/com', [:], [:])
        def transfer = Transfer.succeeded('logs')
        blobConfig.statusDuration >> Duration.ofMinutes(5)
        transferStrategy.status(blob) >> transfer

        when:
        manager.handle0(blob)

        then:
        1 * blobStore.storeBlob(blob.objectUri, _, blobConfig.statusDuration)
        1 * transferStrategy.cleanup(_)
    }

    def "handle0 should fail transfer when status is unknown and duration exceeds grace period"() {
        given:
        def blobStore = Mock(BlobCacheStore)
        def transferStrategy = Mock(TransferStrategy)
        def blobConfig = new BlobCacheConfig(transferTimeout: Duration.ofSeconds(1), graceDuration: Duration.ofSeconds(1))
        def manager = new TransferManager(blobStore: blobStore, transferStrategy: transferStrategy, blobConfig: blobConfig)
        def info = BlobCacheInfo.create('http://foo.com', 's3://foo/com', [:], [:])
        def transfer = Transfer.unknown('logs')
        transferStrategy.status(info) >> transfer

        when:
        sleep 1_000 //sleep for grace period
        manager.handle0(info)

        then:
        1 * blobStore.storeBlob(info.objectUri, _, blobConfig.failureDuration)
        1 * transferStrategy.cleanup(_)
    }

    def "handle0 should requeue transfer when duration is within limits"() {
        given:
        def blobStore = Mock(BlobCacheStore)
        def transferStrategy = Mock(TransferStrategy)
        def blobConfig = new BlobCacheConfig(transferTimeout: Duration.ofSeconds(1))
        def queue = Mock(TransferQueue)
        def manager = new TransferManager(blobStore: blobStore, transferStrategy: transferStrategy, blobConfig: blobConfig, queue: queue)
        def info = BlobCacheInfo.create('http://foo.com', 's3://foo/com', [:], [:])
        def transfer = Transfer.running()
        transferStrategy.status(info) >> transfer

        when:
        manager.handle0(info)

        then:
        1 * queue.offer(info.objectUri)
    }

    def "handle0 should timeout transfer when duration exceeds max limit"() {
        given:
        def blobStore = Mock(BlobCacheStore)
        def transferStrategy = Mock(TransferStrategy)
        def blobConfig = new BlobCacheConfig(transferTimeout: Duration.ofSeconds(1))
        def manager = new TransferManager(blobStore: blobStore, transferStrategy: transferStrategy, blobConfig: blobConfig)
        def info = BlobCacheInfo.create('http://foo.com', 's3://foo/com', [:], [:])
        def transfer = Transfer.running()
        transferStrategy.status(info) >> transfer

        when:
        sleep 1_100 * 2 //await timeout
        manager.handle0(info)

        then:
        1 * blobStore.storeBlob(info.objectUri, _, blobConfig.failureDuration)
    }
}
