package io.seqera.wave

import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.RegistryAuth
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.test.DockerRegistryContainer
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ProxyClientTest extends Specification implements DockerRegistryContainer{

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Inject RegistryLookupService lookupService
    @Inject RegistryAuthService loginService
    @Inject RegistryCredentialsProvider credentialsProvider

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    def 'should call target blob' () {
        given:
        def IMAGE = 'library/hello-world'
        and:
        def proxy = new ProxyClient()
                .withImage(IMAGE)
                .withRegistry(getLocalTestRegistryInfo())
                .withLoginService(loginService)

        when:
        def resp1 = proxy.getString('/v2/library/hello-world/manifests/latest')
        and:
        println resp1.body()
        then:
        resp1.statusCode() == 200
    }

    def 'should call target blob on quay' () {
        given:
        def REG = 'quay.io'
        def IMAGE = 'biocontainers/fastqc'
        def registry = lookupService.lookup(REG)
        def creds = credentialsProvider.getDefaultCredentials(REG)
        and:
        def proxy = new ProxyClient()
                .withImage(IMAGE)
                .withRegistry(registry)
                .withLoginService(loginService)
                .withCredentials(creds)

        when:
        def resp1 = proxy.getString('/v2/biocontainers/fastqc/blobs/sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4')
        and:
        then:
        resp1.statusCode() == 200
    }

    def 'should lookup aws registry' () {
        when:
        def registry = lookupService.lookup('195996028523.dkr.ecr.eu-west-1.amazonaws.com')
        then:
        registry.name == '195996028523.dkr.ecr.eu-west-1.amazonaws.com'
        registry.host == new URI('https://195996028523.dkr.ecr.eu-west-1.amazonaws.com')
        registry.auth.realm == new URI('https://195996028523.dkr.ecr.eu-west-1.amazonaws.com/')
        registry.auth.service == 'ecr.amazonaws.com'
        registry.auth.type == RegistryAuth.Type.Basic
    }

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should call target manifest on amazon' () {
        given:
        def IMAGE = 'wave/kaniko'
        def REG = '195996028523.dkr.ecr.eu-west-1.amazonaws.com'
        def registry = lookupService.lookup(REG)
        def creds = credentialsProvider.getDefaultCredentials(REG)
        and:
        def proxy = new ProxyClient()
                .withImage(IMAGE)
                .withRegistry(registry)
                .withLoginService(loginService)
                .withCredentials(creds)

        when:
        def resp = proxy.getString("/v2/$IMAGE/manifests/0.1.0")
        then:
        resp.statusCode() == 200
    }

    void 'should lookup google artifactory' () {
        given:
        def KEY = '''
            {
              "type": "service_account",
              "project_id": "wave-test-361419",
              "private_key_id": "d5803f8fed3f882c7169dbffec319961bb1b690a",
              "private_key": "-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDjxHFu3uJBe/Rj\\nbm9akWuLm9QnTL52DOZCX7y9XSd/kqJfUK8mdMvkaJpGawQiqkhpEVJgtBlbFoTf\\n0ZURaNeA8XfMrgBKNcBP/abKmTCgMO5GvGbOgDlhL3aaXWCz/4yJYsY0k8dqXQ/G\\ncDksSlAdQC9/dkjXhfogGaHBSfu+pxaeB19EMwHQ/cNbEvaGRCx80iBIk82C0olo\\nkrc9gsnAPVbCRiNq6dwVN7QEU0d3LO9+UCRV/f4b5mzUrPdwWsoPdfctkl5avb81\\nCLqoPkUYCcsCTg7UZNBufhA5ApI36tB5BpCHohtkaM/vSaplKwa6Q5JoUygRQy0L\\nzy4qvfyTAgMBAAECggEAB4YbfnvfPl75a0aLEq+2oN2KsXGmNznd3AKMtm9UAAbU\\n4558eOkuWGL9+TSidB1cyaZk1VpIVs7YEidXFpnqOdijtOa+HJkB9GmbSG/JshZP\\nQZYKUSHBbiXCyTeA4rhl6PPoHnVlBsJycoM4vLZAnJMxxZZYeaYec8HlADmQ+nLJ\\ntEl60Jjs9Vg6xyNKV5CmyKavoYymq8qH2Tsk8+6k5+udYQjFAFvJWns15d6/Im79\\nwXeG243Jl83sGYsFvlc11Ktp1aWnJPA+d3jl840s3XmtIffu+zVqLFyOwuXHdZxi\\n6V8JJ3xz5Gcb60zfFoajQNq5RBRakW6Rgh5jao2nQQKBgQD28jRlu30fu4uvKwHF\\nVYd2ceRwIOAknpzUVJyfT3PudnNEmawJwQHvHsH6NTmPz3v2Wt7I3s56l3C6ZUML\\neIzuC8HrKtKKLJJYaYl66C6lNZOHPxC/eABI1p/PiuTPIWzLlURhbzc9/6ENOGTj\\naw3Qrl7Z3KpE2CR3RxOERsdDKwKBgQDsHjrTjaD9/DxnncUFcQbntjEwapJ4ik1Y\\n+b/3I5uOCwdNeFaJDdOllhxV+4oECXTukrWxtqZCeaNTsADyYFSKQGp84W/Zp7Lb\\nIn/r7EI9PTA27a8Iq2PXNzXPOUXmHBTYbgNeumpBTnT/3g85CsfOup5O1uLOdmxo\\nbhSd9DwYOQKBgFUQQ2pTthsrMEerqdV+y8XKH6VcPbl/hYhCiRz7cnTPCo+z26YU\\nfQUQdEMq+GQIVawbyygoT7m81tDuNrUJ1ondNPQ78QA1sEeSOxBCUGcKWII7ABrk\\nTDzK6YvFTWHoIqDSDxb9B2ts1d5G8cHAy6Z5miSztVc3mQAZVKi49MS3AoGBALm/\\nPj/WluO3Xj2VG70gIXJ/HUsdS4SQKDDqqF1fIawoeOI03L6MpgcJg9kQPI7YcaiM\\nuWrIaRq6XgYj21rQ9TCdZChBoJ/1EPratQ9/mMxcKmXLrXqAedaAlFAkmhxf0vY/\\n9V67/4LImbn/krIpDO5QWOFkoqARAU9V6doonG3hAoGABmKnn16paD2SppmrYFFS\\nSjAllpU46yreeG33aGcbSaMnnX7LHTO5/cg0u2TxvJrBjFQGf8AYedmrfRZICQcC\\nbuHJPD06Zd6XY77VZZXNsBtThqnUlX/dl9Mv4gyKzVJtH28oZFZfq5irDAG4pS4v\\nfMtnXx6Ok+KbwpWoVVgtqOA=\\n-----END PRIVATE KEY-----\\n",
              "client_email": "wave-user@wave-test-361419.iam.gserviceaccount.com",
              "client_id": "111715329731061897617",
              "auth_uri": "https://accounts.google.com/o/oauth2/auth",
              "token_uri": "https://oauth2.googleapis.com/token",
              "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
              "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/wave-user%40wave-test-361419.iam.gserviceaccount.com"
            }
            '''
        and:
        def registry = 'europe-southwest1-docker.pkg.dev'
        def USER = '_json_key' // or '_json_key_base64'

        expect:
        loginService.login(registry, USER, KEY)

    }
    
}
