package io.seqera.auth

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SimpleAuthProvider extends BaseAuthProvider {

    @Override
    protected String getUsername() {
        return null
    }

    @Override
    protected String getPassword() {
        return null
    }

    @Override
    protected String getAuthUrl() {
        return null
    }

    @Override
    protected String getService() {
        return null
    }

    @Override
    boolean isSsl() {
        return false
    }

    @Override
    String getTokenFor(String image) {
        return ""
    }
}
