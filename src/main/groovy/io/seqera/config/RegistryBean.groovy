package io.seqera.config

import groovy.transform.builder.Builder

@Builder
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
        return "RegistryBean{" +
                "name='" + name + '\'' +
                ", host='" + host + '\'' +
                ", auth=" + auth +
                '}';
    }
}
