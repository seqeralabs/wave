package io.seqera.wave.tower.client

import io.seqera.wave.model.TowerTokens

interface TowerAuthTokensService {


    void updateAuthTokens(String endpoint, String providedRefreshToken, String providedAuthToken)

    TowerTokens refreshTokens(String endpoint, TowerTokens tokens)

    TowerTokens getTokens(String endpoint, String accessToken)
}
