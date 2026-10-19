package com.ai.gateway.service;

import com.ai.gateway.exception.OAuthException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleOidcClientTest {
    private static final String CLIENT_ID = "local-google-client";
    private static final String NONCE = "local-nonce-value";
    private final AtomicReference<String> idToken = new AtomicReference<>();
    private final AtomicReference<String> tokenRequestBody = new AtomicReference<>("");
    private final AtomicReference<Boolean> rejectCode = new AtomicReference<>(false);
    private HttpServer server;
    private RSAKey signingKey;
    private GoogleOidcClient client;
    private URI baseUri;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048)
                .keyID("local-test-key")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", this::handleToken);
        server.createContext("/jwks", exchange -> write(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString()));
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = new GoogleOidcClient(new RestTemplate(), CLIENT_ID, "local-secret", "http://127.0.0.1/callback",
                baseUri.resolve("/authorize"), baseUri.resolve("/token"), baseUri.resolve("/jwks"));
        idToken.set(signedToken(signingKey, "https://accounts.google.com", List.of(CLIENT_ID), NONCE,
                Instant.now().plusSeconds(300), "google-sub-123"));
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void exchangesAuthorizationCodeWithPkceAndValidatesSignedIdentity() {
        String url = client.authorizationUrl("state-value", NONCE, "s256-challenge");
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("code_challenge=s256-challenge"));
        assertTrue(url.contains("code_challenge_method=S256"));

        GoogleOidcClient.GoogleOidcIdentity identity = client.exchangeAndVerify("one-time-code", "pkce-verifier", NONCE);

        assertEquals("google-sub-123", identity.subject());
        assertEquals("Local Google User", identity.displayName());
        assertEquals("verified@example.test", identity.verifiedEmail());
        assertTrue(tokenRequestBody.get().contains("grant_type=authorization_code"));
        assertTrue(tokenRequestBody.get().contains("code_verifier=pkce-verifier"));
    }

    @Test
    void rejectsWrongIssuerAudienceExpirationNonceAndSignature() throws Exception {
        assertRejected(signedToken(signingKey, "https://attacker.example", List.of(CLIENT_ID), NONCE,
                Instant.now().plusSeconds(300), "subject"), NONCE);
        assertRejected(signedToken(signingKey, "https://accounts.google.com", List.of("other-client"), NONCE,
                Instant.now().plusSeconds(300), "subject"), NONCE);
        assertRejected(signedToken(signingKey, "https://accounts.google.com", List.of(CLIENT_ID, "other-client"), NONCE,
                Instant.now().plusSeconds(300), "subject", "other-client"), NONCE);
        assertRejected(signedToken(signingKey, "https://accounts.google.com", List.of(CLIENT_ID), NONCE,
                Instant.now().minusSeconds(60), "subject"), NONCE);
        assertRejected(signedToken(signingKey, "https://accounts.google.com", List.of(CLIENT_ID), "wrong-nonce",
                Instant.now().plusSeconds(300), "subject"), NONCE);

        RSAKey attackerKey = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048)
                .keyID("attacker-key").keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).generate();
        assertRejected(signedToken(attackerKey, "https://accounts.google.com", List.of(CLIENT_ID), NONCE,
                Instant.now().plusSeconds(300), "subject"), NONCE);
    }

    @Test
    void rejectsAnExpiredOrAlreadyUsedAuthorizationCodeAtTokenExchange() {
        rejectCode.set(true);
        assertThrows(OAuthException.class, () -> client.exchangeAndVerify("expired-code", "verifier", NONCE));
    }

    private void assertRejected(String token, String nonce) {
        idToken.set(token);
        assertThrows(OAuthException.class, () -> client.exchangeAndVerify("one-time-code", "verifier", nonce));
    }

    private String signedToken(RSAKey key, String issuer, List<String> audience, String nonce,
                               Instant expiration, String subject) throws Exception {
        return signedToken(key, issuer, audience, nonce, expiration, subject, null);
    }

    private String signedToken(RSAKey key, String issuer, List<String> audience, String nonce,
                               Instant expiration, String subject, String authorizedParty) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(audience)
                .issueTime(Date.from(now.minusSeconds(10)))
                .expirationTime(Date.from(expiration))
                .claim("nonce", nonce)
                .claim("email", "verified@example.test")
                .claim("email_verified", true)
                .claim("name", "Local Google User");
        if (authorizedParty != null) builder.claim("azp", authorizedParty);
        JWTClaimsSet claims = builder.build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private void handleToken(HttpExchange exchange) throws java.io.IOException {
        tokenRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (Boolean.TRUE.equals(rejectCode.get()) || tokenRequestBody.get().contains("code=expired-code")) {
            write(exchange, 400, "{\"error\":\"invalid_grant\"}");
            return;
        }
        write(exchange, 200, "{\"access_token\":\"local-access-token\",\"token_type\":\"Bearer\",\"id_token\":\""
                + idToken.get() + "\"}");
    }

    private void write(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
