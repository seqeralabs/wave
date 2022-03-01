package io.seqera.util

import groovy.json.JsonSlurper
import spock.lang.Specification

import java.nio.file.Files
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
    }

}

