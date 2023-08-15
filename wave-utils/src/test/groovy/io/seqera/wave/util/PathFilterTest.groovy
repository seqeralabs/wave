package io.seqera.wave.util

import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Path
/**
 *
 * @author Munish Chouhan munish.chouhan@seqera.io
 */
class PathFilterTest extends Specification {

    @Unroll
    def 'should filter the paths based on ignore patterns'(){
        given:
        Set<String> ignorePatterns = ["*/ignore*","main.??","*/*/exclude*", "*.md", "!README.md"]
        def pathFilter = new PathFilter(ignorePatterns)

        when:
        boolean accepted = pathFilter.accept(Path.of(PATH))

        then:
        accepted == VALID

        where:
        PATH                    | VALID
        'this/ignore'           | false
        'this/that/exclude.txt' | false
        'this/hola.txt'         | true
        'this/hello.txt'        | true
        'this/that/ciao.txt'    | true
        'main.nf'               | false
        'main.txt'              | true
        'file.md'               | false
        'README.md'             | true
    }

}
