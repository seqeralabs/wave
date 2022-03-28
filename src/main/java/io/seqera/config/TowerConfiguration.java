package io.seqera.config;

public interface TowerConfiguration {

    String getArch();

    Registry getDefaultRegistry();

    Registry findRegistry(String name);

}
