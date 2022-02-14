package io.seqera

import org.yaml.snakeyaml.Yaml

class RegConfiguration {

    private String CONFIG_FILE_ENV = "TOWER_REG"
    private String DEFAULT_CONFIG_FILE = "tower-reg.yml"

    private int port
    private String username
    private String password
    private String arch

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

    RegConfiguration(){
        String file = System.getenv(CONFIG_FILE_ENV) ?: DEFAULT_CONFIG_FILE
        Yaml parser = new Yaml()
        Map configuration = parser.load((file as File).text)
        this.port = configuration['port'] as int
        this.username = configuration['username']
        this.password = configuration['password']
        this.arch = configuration['arch']
    }

}
