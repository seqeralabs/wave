package io.seqera.wave.tower.client


import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class TowerAuthTokensServiceImpl implements TowerAuthTokensService {

    @Inject
    TowerTokensStore tokensStore

    @Override
    void updateAuthTokens(String endpoint, String providedRefreshToken, String providedAuthToken) {
        if (providedRefreshToken) {
            final tokens = new JwtAuth(providedAuthToken, providedRefreshToken)
            tokensStore.put(tokensKey(endpoint,providedAuthToken), tokens)
        }
    }

    @Override
    JwtAuth refreshTokens(String endpoint, String originalAuthToken, JwtAuth tokens) {
        tokensStore.put(tokensKey(endpoint,originalAuthToken), tokens)
        return tokens
    }

    @Override
    JwtAuth getJwtAuth(String endpoint, String accessToken) {
        return tokensStore.get(tokensKey(endpoint, accessToken))?: new JwtAuth(accessToken)
    }


    private static String tokensKey(String endpoint, String initialRefreshToken) {
        return "${endpoint}:${initialRefreshToken}"
    }
}
