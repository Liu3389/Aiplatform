package com.ai.gateway.service;

import com.ai.gateway.exception.OAuthException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/** Google authorization-code + OIDC ID-token validation. */
@Slf4j
@Service
public class GoogleOidcClient {
    private static final URI AUTHORIZATION_ENDPOINT = URI.create("https://accounts.google.com/o/oauth2/v2/auth");
    private static final URI TOKEN_ENDPOINT = URI.create("https://oauth2.googleapis.com/token");
    private static final URI JWKS_ENDPOINT = URI.create("https://www.googleapis.com/oauth2/v3/certs");

    private final RestTemplate restTemplate;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final URI authorizationEndpoint;
    private final URI tokenEndpoint;
    private final DefaultJWTProcessor<SecurityContext> jwtProcessor;

    @Autowired
    public GoogleOidcClient(RestTemplateBuilder builder,
                            @Value("${GOOGLE_OAUTH_CLIENT_ID:}") String clientId,
                            @Value("${GOOGLE_OAUTH_CLIENT_SECRET:}") String clientSecret,
                            @Value("${GOOGLE_OAUTH_REDIRECT_URI:http://127.0.0.1:13000/oauth/callback}") String redirectUri) {
        this(builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(5)).build(), clientId, clientSecret, redirectUri,
                AUTHORIZATION_ENDPOINT, TOKEN_ENDPOINT, JWKS_ENDPOINT);
    }

    GoogleOidcClient(RestTemplate restTemplate,
                     String clientId,
                     String clientSecret,
                     String redirectUri,
                     URI authorizationEndpoint,
                     URI tokenEndpoint,
                     URI jwksEndpoint) {
        this.restTemplate = restTemplate;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri;
        this.authorizationEndpoint = authorizationEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        try {
            JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(jwksEndpoint.toURL(),
                    new DefaultResourceRetriever(3000, 5000));
            this.jwtProcessor = new DefaultJWTProcessor<>();
            this.jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
            // OIDC claims are validated explicitly below so every required check is visible and testable.
            this.jwtProcessor.setJWTClaimsSetVerifier((claims, context) -> { });
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Google JWKS endpoint", e);
        }
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && redirectUri != null && !redirectUri.isBlank();
    }

    public String authorizationUrl(String state, String nonce, String codeChallenge) {
        requireConfigured();
        return UriComponentsBuilder.fromUri(authorizationEndpoint)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("prompt", "select_account")
                .build().encode().toUriString();
    }

    public GoogleOidcIdentity exchangeAndVerify(String code, String codeVerifier, String expectedNonce) {
        requireConfigured();
        if (code == null || code.isBlank() || code.length() > 4096) {
            throw new OAuthException(400, "授权码无效");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            Map<String, Object> tokenResponse = restTemplate.exchange(
                    tokenEndpoint,
                    org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() { }
            ).getBody();
            Object rawToken = tokenResponse == null ? null : tokenResponse.get("id_token");
            if (!(rawToken instanceof String idToken) || idToken.isBlank() || idToken.length() > 20_000) {
                throw new OAuthException(401, "第三方身份验证失败");
            }
            JWTClaimsSet claims = jwtProcessor.process(idToken, null);
            validateClaims(claims, expectedNonce);
            String subject = claims.getSubject();
            String email = Boolean.TRUE.equals(claims.getClaim("email_verified"))
                    ? claims.getStringClaim("email") : null;
            String displayName = claims.getStringClaim("name");
            if (displayName == null || displayName.isBlank()) displayName = email;
            if (displayName == null || displayName.isBlank()) displayName = "Google 用户";
            return new GoogleOidcIdentity(subject, displayName, email);
        } catch (OAuthException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google OIDC token exchange or validation failed: {}", e.getClass().getSimpleName());
            throw new OAuthException(401, "第三方身份验证失败", e);
        }
    }

    private void validateClaims(JWTClaimsSet claims, String expectedNonce) throws Exception {
        String issuer = claims.getIssuer();
        if (!"https://accounts.google.com".equals(issuer) && !"accounts.google.com".equals(issuer)) {
            throw new OAuthException(401, "第三方身份验证失败");
        }
        Collection<String> audiences = claims.getAudience();
        if (audiences == null || !audiences.contains(clientId)) {
            throw new OAuthException(401, "第三方身份验证失败");
        }
        String authorizedParty = claims.getStringClaim("azp");
        if ((audiences.size() > 1 && !clientId.equals(authorizedParty))
                || (authorizedParty != null && !clientId.equals(authorizedParty))) {
            throw new OAuthException(401, "第三方身份验证失败");
        }
        Instant now = Instant.now();
        Instant expiration = claims.getExpirationTime() == null ? null : claims.getExpirationTime().toInstant();
        Instant issuedAt = claims.getIssueTime() == null ? null : claims.getIssueTime().toInstant();
        Instant notBefore = claims.getNotBeforeTime() == null ? null : claims.getNotBeforeTime().toInstant();
        if (expiration == null || !expiration.isAfter(now.minusSeconds(30))
                || issuedAt == null || issuedAt.isAfter(now.plusSeconds(60))
                || (notBefore != null && notBefore.isAfter(now.plusSeconds(30)))) {
            throw new OAuthException(401, "第三方身份验证失败");
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank() || claims.getSubject().length() > 255) {
            throw new OAuthException(401, "第三方身份验证失败");
        }
        String nonce = claims.getStringClaim("nonce");
        if (expectedNonce == null || nonce == null || !MessageDigest.isEqual(
                nonce.getBytes(StandardCharsets.UTF_8), expectedNonce.getBytes(StandardCharsets.UTF_8))) {
            throw new OAuthException(401, "第三方身份验证失败");
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) throw new OAuthException(503, "Google OAuth 尚未配置");
    }

    public record GoogleOidcIdentity(String subject, String displayName, String verifiedEmail) { }
}
