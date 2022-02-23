package io.seqera.config

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
class YamlConfiguration implements TowerConfiguration{

    protected static final String CONFIG_FILE_ENV = "TOWER_REG"
    protected static final String DEFAULT_CONFIG_FILE = "tower-reg.yml"
    protected static final int DEFAULT_PORT = 9090

    int port = DEFAULT_PORT
    String arch
    List<Registry> registries

    static TowerConfiguration newInstace(){
        String file = System.getenv(CONFIG_FILE_ENV) ?: DEFAULT_CONFIG_FILE
        Yaml parser = new Yaml(new Constructor(YamlConfiguration))
        TowerConfiguration configuration = parser.load((file as File).text)
        configuration
    }

    Registry getDefaultRegistry(){
        registries.first()
    }

    Registry findRegistry(String name){
        registries.find{ it.name == name} ?: defaultRegistry
    }

}
