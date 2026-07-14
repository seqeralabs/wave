package io.seqera.wave.controller

import spock.lang.Specification

import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.inspect.ContainerInspectServiceImpl
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.validation.ValidationServiceImpl
/**
 * test for {@link ContainerController} with mocks
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ContainerControllerTest1 extends Specification {
    void 'returning null id when image digest exists but build is marked failed in database'() {
        given:
        def config = Mock(BuildConfig) {
            getBuildWorkspace() >> '/some/path'
            getDefaultBuildRepository() >> 'default/build'
            getDefaultCacheRepository() >> 'default/cache'
        }
        def validation = new ValidationServiceImpl(buildConfig:config)
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def persistenceService = Mock(PersistenceService)
        def registryProxyService = Mock(RegistryProxyService)
        def controller = new ContainerController(inspectService: dockerAuth, buildConfig: config, validationService: validation,
                persistenceService: persistenceService, registryProxyService: registryProxyService)
        def build = Mock(BuildRequest) {
            getContainerId() >> 'container123'
            getTargetImage() >> 'image123'
        }
        registryProxyService.getImageDigest(build) >> 'digest123'
        persistenceService.loadBuildSucceed('image123', 'digest123') >> null

        when:
        def result = controller.checkBuild(build, false)

        then:
        result.id == null
        result.targetImage == 'image123'
        result.cached
        result.succeeded
    }
}
