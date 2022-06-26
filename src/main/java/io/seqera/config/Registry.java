package io.seqera.config;

@Deprecated
public interface Registry {

    String getName();
    String getHost();
    Auth getAuth();

}
