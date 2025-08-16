/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.api

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SubmitContainerTokenRequestTest extends Specification {

    def 'should copy a request' () {
        given:
        def req = new SubmitContainerTokenRequest(
                towerAccessToken: 'a1',
                towerRefreshToken: 'a2',
                towerEndpoint: 'a3',
                towerWorkspaceId: 4,
                containerImage: 'a5',
                containerFile:  'a6',
                containerConfig: new ContainerConfig(entrypoint: ['this','that']),
                condaFile: 'a8',
                containerPlatform: 'a10',
                buildRepository: 'a11',
                cacheRepository: 'a12',
                timestamp: 'a13',
                fingerprint: 'a14',
                freeze: true,
                format: 'sif',
                dryRun: true,
                workflowId: 'id123',
                containerIncludes: ['busybox:latest'],
                packages: new PackagesSpec(type: PackagesSpec.Type.CONDA, environment: 'foo', entries: ['bar']),
                nameStrategy: ImageNameStrategy.imageSuffix,
                mirror: true,
                scanMode: ScanMode.async,
                scanLevels: List.of(ScanLevel.LOW, ScanLevel.MEDIUM),
                buildCompression: BuildCompression.gzip,
        )

        when:
        def copy = req.copyWith(Map.of())
        then:
        copy.towerAccessToken == req.towerAccessToken
        copy.towerRefreshToken == req.towerRefreshToken
        copy.towerEndpoint == req.towerEndpoint
        copy.towerWorkspaceId == req.towerWorkspaceId
        copy.containerImage == req.containerImage
        copy.containerFile == req.containerFile
        copy.containerConfig == req.containerConfig
        copy.condaFile == req.condaFile
        copy.containerPlatform == req.containerPlatform
        copy.buildRepository == req.buildRepository
        copy.cacheRepository == req.cacheRepository
        copy.timestamp == req.timestamp
        copy.fingerprint == req.fingerprint
        copy.freeze == req.freeze
        copy.format == req.format
        copy.dryRun == req.dryRun
        copy.workflowId == req.workflowId
        copy.containerIncludes == req.containerIncludes
        copy.packages == req.packages
        copy.nameStrategy == req.nameStrategy
        copy.mirror == req.mirror
        copy.scanMode == req.scanMode
        copy.scanLevels == req.scanLevels
        copy.buildCompression == req.buildCompression
        and:
        copy.formatSingularity()

        when:
        def other = req.copyWith(
                towerAccessToken: 'b1',
                towerRefreshToken: 'b2',
                towerEndpoint: 'b3',
                towerWorkspaceId: 44,
                containerImage: 'b5',
                containerFile:  'b6',
                containerConfig: new ContainerConfig(entrypoint: ['foo','bar']),
                condaFile: 'b8',
                containerPlatform: 'b10',
                buildRepository: 'b11',
                cacheRepository: 'b12',
                imageName: 'testImageName',
                timestamp: 'b13',
                fingerprint: 'b14',
                freeze: false,
                format: 'foo',
                dryRun: false,
                workflowId: 'id123',
                containerIncludes: ['other:image'],
                nameStrategy: ImageNameStrategy.tagPrefix,
                mirror: false,
                scanMode: ScanMode.required,
                scanLevels: List.of(ScanLevel.HIGH),
                buildCompression: BuildCompression.estargz,
        )
        then:
        other.towerAccessToken == 'b1'
        other.towerRefreshToken == 'b2'
        other.towerEndpoint == 'b3'
        other.towerWorkspaceId == 44
        other.containerImage == 'b5'
        other.containerFile == 'b6'
        other.containerConfig == new ContainerConfig(entrypoint: ['foo','bar'])
        other.condaFile == 'b8'
        other.containerPlatform == 'b10'
        other.buildRepository == 'b11'
        other.cacheRepository == 'b12'
        other.timestamp == 'b13'
        other.fingerprint == 'b14'
        other.freeze == false
        other.format == 'foo'
        other.dryRun == false
        other.workflowId == 'id123'
        other.containerIncludes == ['other:image']
        other.nameStrategy == ImageNameStrategy.tagPrefix
        other.mirror == false
        other.scanMode == ScanMode.required
        other.scanLevels == List.of(ScanLevel.HIGH)
        other.buildCompression == BuildCompression.estargz
        and:
        !other.formatSingularity()
    }

}
