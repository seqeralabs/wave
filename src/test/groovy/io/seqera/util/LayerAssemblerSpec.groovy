package io.seqera.util

import groovy.json.JsonSlurper
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
class LayerAssemblerSpec extends Specification {

    def 'should create a layer' () {
        given:
        def root = Files.createTempDirectory('test')
        def folder = Paths.get(root.toAbsolutePath().toString(), "pack")

        and:
        def layerAssembler = LayerAssembler.newInstance(
                LayerAssembler.SOURCE_DIR, folder.toAbsolutePath().toString())

        when:
        def resp = layerAssembler.buildLayer();

        then:
        def json = new JsonSlurper().parseText(resp.text) as Map
        json.containsKey("entrypoint")
        json.containsKey("workingDir")
        json.containsKey("cmd")
        json.containsKey("append")
        json.append.location == "$root/pack/layers/layer.tar.gzip"
        json.append.gzipSize

        and:
        def sout = new StringBuilder()
        def serr = new StringBuilder()
        def proc = "sha256sum $json.append.location".execute()
        proc.consumeProcessOutput(sout, serr)
        proc.waitForOrKill(1000)
        def sha256sum = sout.toString().split(' ').first()

        then:
        json.append.gzipDigest == "sha256:$sha256sum"

        cleanup:
        root.toFile().deleteDir()
    }

    def 'should fails if input dir doesnt exist' () {
        given:
        def root = Files.createTempDirectory('test')
        def folder = Paths.get(root.toAbsolutePath().toString(), "pack")

        and:
        def layerAssembler = LayerAssembler.newInstance(
                "this directory doesnt exist", folder.toAbsolutePath().toString())

        when:
        def resp = layerAssembler.buildLayer();

        then:
        def err = thrown(NoSuchFileException)

        cleanup:
        root.toFile().deleteDir()
    }

}

