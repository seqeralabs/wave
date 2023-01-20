package io.seqera.wave.tower.client

import io.seqera.wave.model.TowerTokens
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class TowerAuthTokensServiceImpl implements TowerAuthTokensService {

    @Inject
    TowerTokensStore tokensStore

    @Override
    void updateAuthTokens(String endpoint, String providedRefreshToken, String providedAuthToken) {
        if (providedRefreshToken) {
            final tokens = new TowerTokens(authToken: providedAuthToken, refreshToken: providedRefreshToken, tokenKey: providedAuthToken)
            tokensStore.put(tokensKey(endpoint,providedAuthToken), tokens)
        }
    }

    @Override
    TowerTokens refreshTokens(String endpoint, TowerTokens tokens) {
        tokensStore.put(tokensKey(endpoint,tokens.tokenKey), tokens)
        return tokens
    }

    @Override
    TowerTokens getTokens(String endpoint, String accessToken) {
        return tokensStore.get(tokensKey(endpoint, accessToken))?: new TowerTokens(authToken: accessToken)
    }


    private static String tokensKey(String endpoint, String initialRefreshToken) {
        return "${endpoint}:${initialRefreshToken}"
    }
}
