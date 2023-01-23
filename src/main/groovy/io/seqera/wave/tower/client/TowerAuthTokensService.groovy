package io.seqera.wave.tower.client

interface TowerAuthTokensService {

    void updateAuthTokens(String endpoint, String providedRefreshToken, String providedAuthToken)

    JwtAuth refreshTokens(String endpoint, String originalAuthToken, JwtAuth tokens)

    JwtAuth getJwtAuth(String endpoint, String accessToken)

}
