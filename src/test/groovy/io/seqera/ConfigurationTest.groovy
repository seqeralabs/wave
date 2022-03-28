package io.seqera

import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.env.DefaultEnvironment
import io.micronaut.context.env.Environment
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.config.DefaultConfiguration
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.io>
 */
@MicronautTest
class ConfigurationTest extends Specification{

    void "test configuration"() {
        given:

        ApplicationContext ctx = ApplicationContext.run(ApplicationContext, [
                "TEST_USER": 'theUser',
                "towerreg.arch": 'test',
                "towerreg.registries": [
                        [ name: '${TEST_USER}', host:'quay.io',authConfig:'test',
                          auth:[ name: 'test',username: '${TEST_USER}',password: 'pwd',url: 'quay.io/v2/auth',service: 'quay.io']
                        ]
                ]
        ])

        when:
        DefaultConfiguration teamConfiguration = ctx.getBean(DefaultConfiguration)

        then:
        teamConfiguration
        teamConfiguration.arch == 'test'
        teamConfiguration.registries.size()==1
        teamConfiguration.registries.first().name=='theUser'
        teamConfiguration.registries.first().auth.username=='theUser'

        cleanup:
        ctx.close()
    }

}
