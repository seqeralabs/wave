package io.seqera

import groovy.transform.Immutable
import org.yaml.snakeyaml.Yaml

class RegConfiguration {

    protected static final String CONFIG_FILE_ENV = "TOWER_REG"
    protected static final String DEFAULT_CONFIG_FILE = "tower-reg.yml"
    protected static final String DEFAULT_REGISTRY = 'registry-1.docker.io'
    protected static final String DEFAULT_PORT = 9090

    final int port
    final String username
    final String password
    final String arch
    final String targetRegistry
    final String authUrl
    final String authService

    RegConfiguration(){
        String file = System.getenv(CONFIG_FILE_ENV) ?: DEFAULT_CONFIG_FILE
        Yaml parser = new Yaml()
        Map configuration = parser.load((file as File).text)
        this.port = (configuration['port'] ?: DEFAULT_PORT) as int
        this.arch = configuration['arch']
        this.targetRegistry = configuration['registry'] ?: DEFAULT_REGISTRY
        this.username = configuration['auth']['username']
        this.password = configuration['auth']['password']
        this.authUrl = configuration['auth']['authUrl']
        this.authService = configuration['auth']['authService']
    }

}
