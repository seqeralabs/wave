package io.seqera.wave.tower.client

import io.seqera.wave.model.TowerTokens
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class TowerAuthTokensServiceImpl implements TowerAuthTokensService {

    @Inject
    TowerTokensStore tokensStore

    @Override
    TowerTokens updateTowerAuthTokens(String endpoint, String providedRefreshToken, String providedAuthToken) {
        if (providedRefreshToken) {
            final tokens = new TowerTokens(authToken: providedAuthToken, refreshToken: providedRefreshToken, tokenKey: providedRefreshToken)
            // updates the stored tokens
            // for new requests
            tokensStore.put(tokensKey(endpoint, providedRefreshToken), tokens)
            return tokens
        } else {
            return new TowerTokens(authToken: properties, refreshToken: null, tokenKey: null)
        }
    }

    @Override
    TowerTokens refreshTokens(String endpoint, TowerTokens tokens) {
        tokensStore.put(tokensKey(endpoint,tokens.tokenKey), tokens)
        return tokens
    }

    @Override
    TowerTokens getTokens(String endpoint, TowerTokens tokens) {
        if (tokens.tokenKey) {
            return tokensStore.get(tokensKey(endpoint, tokens.tokenKey))
        } else {
            return tokens
        }
    }

    private static String tokensKey(String endpoint, String initialRefreshToken) {
        return "${endpoint}:${initialRefreshToken}"
    }
}
