package io.seqera

import spock.lang.Specification

import java.time.Duration
import java.time.temporal.TemporalUnit

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.env.DefaultEnvironment
import io.micronaut.context.env.Environment
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.config.DefaultConfiguration
import io.seqera.config.FileStorageConfiguration
import io.seqera.config.Registry
import io.seqera.config.StorageConfiguration
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.io>
 */
class ConfigurationTest extends Specification{

    void "test configuration"() {
        given:

        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, [
                "TEST_USER": 'theUser',
                "towerreg.arch": 'test',
                "towerreg.layerPath": 'pack/layers/layer.json',
                "towerreg.registries.test": [
                        host:'quay.io',
                        auth: [username: '${TEST_USER}',password: 'pwd',url: 'quay.io/v2/auth',service: 'quay.io']
                ]
        ])

        when:
        DefaultConfiguration teamConfiguration = ctx.getBean(DefaultConfiguration)

        then:
        teamConfiguration
        teamConfiguration.arch == 'test'
        ctx.getBeansOfType(Registry).size()==1
        ctx.getBean(Registry).getName()=='test'
        ctx.getBean(Registry).getAuth().username=='theUser'

        cleanup:
        ctx.close()
    }

    void "test storage configuration"() {
        given:

        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, [
                "TEST_USER": 'theUser',
                "towerreg.arch": 'test',
                "towerreg.layerPath": 'pack/layers/layer.json',
                "towerreg.storage.maximumSize": 1234,
                "towerreg.storage.expireAfter": '12m',
        ])

        when:
        DefaultConfiguration teamConfiguration = ctx.getBean(DefaultConfiguration)

        then:
        teamConfiguration
        teamConfiguration.arch == 'test'
        ctx.getBeansOfType(StorageConfiguration).size()==1
        ctx.getBean(StorageConfiguration).maximumSize == 1234
        ctx.getBean(StorageConfiguration).expireAfter.toSeconds() == Duration.ofSeconds(12*60).toSeconds()

        cleanup:
        ctx.close()
    }

    void "test default file storage configuration"() {
        given:

        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, [
                "TEST_USER": 'theUser',
                "towerreg.arch": 'test',
                "towerreg.layerPath": 'pack/layers/layer.json',
                "towerreg.storage.file.path": '/a/path',
        ])

        when:
        DefaultConfiguration teamConfiguration = ctx.getBean(DefaultConfiguration)

        then:
        teamConfiguration
        teamConfiguration.arch == 'test'
        ctx.getBeansOfType(FileStorageConfiguration).size()==1
        ctx.getBean(FileStorageConfiguration).path == '/a/path'
        !ctx.getBean(FileStorageConfiguration).storeRemotes

        cleanup:
        ctx.close()
    }

    void "test custom file storage configuration"() {
        given:

        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, [
                "TEST_USER": 'theUser',
                "towerreg.arch": 'test',
                "towerreg.layerPath": 'pack/layers/layer.json',
                "towerreg.storage.file.path": '/a/path',
                "towerreg.storage.file.storeRemotes": 'true',
        ])

        when:
        DefaultConfiguration teamConfiguration = ctx.getBean(DefaultConfiguration)

        then:
        teamConfiguration
        teamConfiguration.arch == 'test'
        ctx.getBeansOfType(FileStorageConfiguration).size()==1
        ctx.getBean(FileStorageConfiguration).path == '/a/path'
        ctx.getBean(FileStorageConfiguration).storeRemotes

        cleanup:
        ctx.close()
    }
}
