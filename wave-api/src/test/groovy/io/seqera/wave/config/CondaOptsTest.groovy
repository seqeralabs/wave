/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.config

import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CondaOptsTest extends Specification {

    def 'should validate equals and hashcode'  () {
        given:
        def c1 = new CondaOpts(mambaImage: 'foo:one', basePackages: 'x y', commands: ['this','that'])
        def c2 = new CondaOpts(mambaImage: 'foo:one', basePackages: 'x y', commands: ['this','that'])
        def c3 = new CondaOpts(mambaImage: 'foo:two', basePackages: 'x y', commands: ['this','that'])
        
        expect:
        c1 == c2
        c1 != c3
        and:
        c1.hashCode() == c2.hashCode()
        c1.hashCode() != c3.hashCode()
    }

    def 'check conda options' () {
        when:
        def opts = new CondaOpts([:])
        then:
        opts.mambaImage == CondaOpts.DEFAULT_MAMBA_IMAGE
        opts.basePackages == 'conda-forge::procps-ng'
        !opts.commands

        when:
        opts = new CondaOpts([
                mambaImage:'foo:latest',
                commands: ['this','that'],
                basePackages: 'some::more-package'
        ])
        then:
        opts.mambaImage == 'foo:latest'
        opts.basePackages == 'some::more-package'
        opts.commands == ['this','that']


        when:
        opts = new CondaOpts([
                basePackages: null
        ])
        then:
        !opts.basePackages
        !opts.commands
    }

    @Unroll
    def "should convert to string" () {
        expect:
        new CondaOpts(OPTS).toString() == EXPECTED
        where:
        OPTS    | EXPECTED
        [:]     | "CondaOpts(mambaImage=mambaorg/micromamba:1.5.10-noble; basePackages=conda-forge::procps-ng, commands=null)"
        [mambaImage: 'foo:1.0', basePackages: 'this that', commands: ['X','Y']] \
                | "CondaOpts(mambaImage=foo:1.0; basePackages=this that, commands=X,Y)"
    }
}
