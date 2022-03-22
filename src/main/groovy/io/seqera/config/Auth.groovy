package io.seqera.config

import io.seqera.util.StringUtils

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
class Auth {

    String username
    String password
    String url
    String service

    @Override
    String toString() {
        "Auth[service=$service; username=$username; password=${StringUtils.redact(password)}; url=$url]"
    }
}
