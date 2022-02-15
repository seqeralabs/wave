package io.seqera

import org.yaml.snakeyaml.Yaml

class RegConfiguration {

    protected static final String CONFIG_FILE_ENV = "TOWER_REG"
    protected static final String DEFAULT_CONFIG_FILE = "tower-reg.yml"
    protected static final String DEFAULT_REGISTRY = 'registry-1.docker.io'
    protected static final String DEFAULT_PORT = 9090

    private int port
    private String username
    private String password
    private String arch
    private String targetRegistry = DEFAULT_REGISTRY

    int getPort() {
        return port
    }

    String getUsername() {
        return username
    }

    String getPassword() {
        return password
    }

    String getArch() {
        return arch
    }

    String getTargetRegistry() {
        return targetRegistry
    }

    RegConfiguration(){
        String file = System.getenv(CONFIG_FILE_ENV) ?: DEFAULT_CONFIG_FILE
        Yaml parser = new Yaml()
        Map configuration = parser.load((file as File).text)
        this.port = (configuration['port'] ?: DEFAULT_PORT) as int
        this.username = configuration['username']
        this.password = configuration['password']
        this.arch = configuration['arch']
        this.targetRegistry = configuration['registry'] ?: this.targetRegistry
    }

}
