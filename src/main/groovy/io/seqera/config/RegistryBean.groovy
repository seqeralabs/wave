package io.seqera.config

import groovy.transform.builder.Builder

@Builder
@Deprecated
class RegistryBean implements Registry{

    private String name
    private String host
    private Auth auth

    @Override
    String getName() {
        return name
    }

    @Override
    String getHost() {
        return host
    }

    @Override
    Auth getAuth() {
        return auth
    }


    @Override
    String toString() {
        return "RegistryInfo{" +
                "name='" + name + '\'' +
                ", host='" + host + '\'' +
                ", auth=" + auth +
                '}';
    }
}
