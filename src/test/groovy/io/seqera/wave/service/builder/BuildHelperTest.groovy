package io.seqera.wave.service.builder

import java.nio.file.Files

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.storage.reader.ContentReaderFactory
import io.seqera.wave.util.Packer
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildHelperTest extends Specification  {

    def 'should add container config to dockerfile' () {

        when:
        def config = new ContainerConfig()
        def result = BuildHelper.appendConfigToDockerFile('FROM foo', config)
        then:
        result == 'FROM foo'

        when:
        def layers = [
                new ContainerLayer('https://some.host', '012abc'),
                new ContainerLayer('data:fafafa', '000aaa'),
                new ContainerLayer('data:xyz', '999fff')]
        config = new ContainerConfig(
                workingDir: '/some/work/dir',
                env: ['FOO=one', 'BAR=two'],
                cmd:['/this','--that'],
                entrypoint: ['/my','--entry'],
                layers: layers)
        result = BuildHelper.appendConfigToDockerFile('FROM foo', config)
        then:
        result == '''\
                FROM foo
                ADD layer-012abc.tar.gz /
                ADD layer-000aaa.tar.gz /
                ADD layer-999fff.tar.gz /
                WORKDIR /some/work/dir
                ENV FOO=one BAR=two
                ENTRYPOINT ["/my", "--entry"]
                CMD ["/this", "--that"]
                '''.stripIndent()
    }

    def 'should save layers to context dir' () {
        given:
        def folder = Files.createTempDirectory('test')
        def file1 = folder.resolve('file1'); file1.text = "I'm file one"
        def file2 = folder.resolve('file2'); file2.text = "I'm file two"
        and:
        def cl = new Packer().layer(folder, [file1])
        def l1 = new ContainerLayer(location: "http://localhost:9901/some.tag.gz", tarDigest: cl.tarDigest, gzipDigest: cl.gzipDigest, gzipSize: cl.gzipSize)
        and:
        def l2 = new Packer().layer(folder, [file2])
        def config = new ContainerConfig(layers: [l1,l2])

        and:
        HttpHandler handler = { HttpExchange exchange ->
            def body = ContentReaderFactory.of(cl.location).readAllBytes()
            exchange.getResponseHeaders().add("Content-Type", "application/tar+gzip")
            exchange.sendResponseHeaders(200, body.size())
            exchange.getResponseBody() << body
            exchange.getResponseBody().close()

        }
        and:
        HttpServer server = HttpServer.create(new InetSocketAddress(9901), 0);
        server.createContext("/", handler);
        server.start()

        when:
        BuildHelper.saveLayersToContext(config, folder)
        then:
        Files.exists(folder.resolve("layer-${l1.gzipDigest.replace(/sha256:/,'')}.tar.gz"))
        Files.exists(folder.resolve("layer-${l2.gzipDigest.replace(/sha256:/,'')}.tar.gz"))

        cleanup:
        folder?.deleteDir()
        server?.stop(0)
    }

    def 'should create build file given a container image' () {

        when:
        def req = new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest', freeze: true)
        def result = BuildHelper.createBuildFile(req)
        then:
        result == '''\
            # wave generated container file
            FROM ubuntu:latest
            '''.stripIndent(true)

        when:
        req = new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest', freeze: true, containerConfig: new ContainerConfig(env:['FOO=1', 'BAR=2']))
         result = BuildHelper.createBuildFile(req)
        then:
        result == '''\
            # wave generated container file
            FROM ubuntu:latest
            ENV FOO=1 BAR=2
            '''.stripIndent(true)
    }

    def 'should create build file given a container file' () {

        when:
        def req = new SubmitContainerTokenRequest(containerFile: 'FROM foo\nRUN this\n', freeze: true)
        def result = BuildHelper.createBuildFile(req)
        then:
        // nothing to do here =>  returns null
        result == null

        when:
        req = new SubmitContainerTokenRequest(containerFile: 'FROM foo\nRUN this\n', freeze: true, containerConfig: new ContainerConfig(env:['FOO=1', 'BAR=2'], workingDir: '/work/dir'))
        result = BuildHelper.createBuildFile(req)
        then:
        // nothing to do here =>  returns null
        result == '''\
            FROM foo
            RUN this
             
            # wave generated container file
            WORKDIR /work/dir
            ENV FOO=1 BAR=2
            '''.stripIndent()
    }

    def 'should throw an error' () {
        when:
        def req = new SubmitContainerTokenRequest(containerFile: 'FROM foo\nRUN this\n', freeze: false)
        BuildHelper.createBuildFile(req)
        then:
        thrown(AssertionError)
    }

    def 'should create build request given a container image' () {

        when:
        def req = new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest', freeze: true)
        def result = BuildHelper.createBuildRequest(req)
        then:
        new String(result.containerFile.decodeBase64()) == '''\
            # wave generated container file
            FROM ubuntu:latest
            '''.stripIndent(true)

        when:
        req = new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest', freeze: true, containerConfig: new ContainerConfig(env:['FOO=1', 'BAR=2']))
        result = BuildHelper.createBuildRequest(req)
        then:
        new String(result.containerFile.decodeBase64()) == '''\
            # wave generated container file
            FROM ubuntu:latest
            ENV FOO=1 BAR=2
            '''.stripIndent(true)
    }

    def 'should create build request' () {
        given:
        def ENCODED = 'FROM foo\nRUN this\n'.bytes.encodeBase64().toString()
        when:
        def req = new SubmitContainerTokenRequest(containerFile: ENCODED, freeze: true)
        def result = BuildHelper.createBuildRequest(req)
        then:
        result.containerFile == req.containerFile

        when:
        req = new SubmitContainerTokenRequest(containerFile: ENCODED, freeze: true, containerConfig: new ContainerConfig(env:['FOO=1', 'BAR=2'], workingDir: '/work/dir'))
        result = BuildHelper.createBuildRequest(req)
        then:
        // nothing to do here =>  returns null
        new String(result.containerFile.decodeBase64()) == '''\
            FROM foo
            RUN this
             
            # wave generated container file
            WORKDIR /work/dir
            ENV FOO=1 BAR=2
            '''.stripIndent()
    }
}
