package io.seqera.config

interface TowerConfiguration {

    int getPort()

    String getArch()

    Registry getDefaultRegistry()

    Registry findRegistry(String name)

}
