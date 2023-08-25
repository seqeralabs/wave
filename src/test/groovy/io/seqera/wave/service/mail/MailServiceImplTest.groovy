package io.seqera.wave.service.mail

import spock.lang.Specification

import java.time.Instant

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.mail.impl.MailServiceImpl
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = 'mail')
class MailServiceImplTest extends Specification {

    @Inject MailServiceImpl service

    def 'should create build mail' () {
        given:
        def recipient = 'foo@gmail.com'
        def result = BuildResult.completed('12345', 0, 'pull foo:latest', Instant.now())
        def request= Mock(BuildRequest)

        when:
        def mail = service.buildCompletionMail(request, result, recipient)
        then:
        1* request.getContainerFile() >> 'from foo';
        1* request.getTargetImage() >> 'wave/build:xyz'
        1* request.getPlatform() >> ContainerPlatform.DEFAULT
        1* request.getCondaFile() >> null
        1* request.getSpackFile() >> null
        and:
        mail.to == recipient
        mail.body.contains('from foo')
        and:
        !mail.body.contains('Conda file')
        !mail.body.contains('Spack file')

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

        // check it add the spack file content
        when:
        mail = service.buildCompletionMail(request, result, recipient)
        then:
        1* request.getTargetImage() >> 'wave/build:xyz'
        1* request.getPlatform() >> ContainerPlatform.DEFAULT
        1* request.getSpackFile() >> 'some-spac-recipe'
        and:
        mail.to == recipient
        mail.body.contains('Spack file')
        mail.body.contains('some-spac-recipe')
    }

}
