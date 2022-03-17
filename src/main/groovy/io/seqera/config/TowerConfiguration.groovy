package io.seqera.config

interface TowerConfiguration {

    String getArch()

    Registry getDefaultRegistry()

    Registry findRegistry(String name)

}
