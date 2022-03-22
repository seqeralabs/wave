package io.seqera.util

import groovy.json.JsonSlurper
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 */
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

        and: 'the sha256 is valid'
        def sout = new StringBuilder()
        def serr = new StringBuilder()
        def proc = "sha256sum $json.append.location".execute()
        proc.consumeProcessOutput(sout, serr)
        proc.waitForOrKill(1000)
        def sha256sum = sout.toString().split(' ').first()

        then:
        json.append.gzipDigest == "sha256:$sha256sum"

        when: 'we extract the tar and compare extracted files'
        def sout2 = new StringBuilder()
        def serr2 = new StringBuilder()
        def file = Files.createTempFile("layer","sh",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-x---"))).toFile()
        file.text='''
        [ $(uname) = Darwin ] && TAR=gtar || TAR=tar
        
        SOURCE_DIR='''+new File(LayerAssembler.SOURCE_DIR).absolutePath+'''
        LAYER_TAR='''+root.toAbsolutePath()+'''/pack/layers/layer.tar
        UNPACK_DIR='''+root.toAbsolutePath()+'''/pack_tar

        #rem extract the created tar and compare files permissions agains original files
                
        #dump original files permissions
        cd $SOURCE_DIR
        find * -exec stat -c '%F %g %u %s %Y %n' {} \\; > /tmp/original.txt
        
        $TAR -xf $LAYER_TAR -C $UNPACK_DIR
        cd $UNPACK_DIR
        find * -exec stat -c '%F %g %u %s %Y %n' {} \\; > /tmp/untar.txt
                
        echo original:
        cat /tmp/original.txt
        echo untar:
        cat /tmp/untar.txt
        
        diff /tmp/original.txt /tmp/untar.txt && echo ok || echo fail       
        '''

        def proc2 = "sh $file.absolutePath".execute()
        proc2.consumeProcessOutput(sout2, serr2)
        proc2.waitForOrKill(1000)
        println sout2.toString()

        then:
        sout2.toString().split('\n').last()=='ok'

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

