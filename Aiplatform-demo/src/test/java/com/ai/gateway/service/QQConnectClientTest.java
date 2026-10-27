package com.ai.gateway.service;

import com.ai.gateway.exception.OAuthException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QQConnectClientTest {
    private static final String APP_ID = "local-qq-app-id";
    private static final String APP_KEY = "local-qq-app-key";
    private static final String REDIRECT = "http://127.0.0.1:13000/oauth/callback";

    @Test
    void buildsAuthorizationUrlAndExchangesCodeForOpenIdAndProfile() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QQConnectClient client = client(restTemplate);

        String authorizationUrl = client.authorizationUrl("single-use-state");
        assertTrue(authorizationUrl.startsWith("https://qq.test/authorize?"));
        assertTrue(authorizationUrl.contains("client_id=" + APP_ID));
        assertTrue(authorizationUrl.contains("state=single-use-state"));
        assertEquals(REDIRECT, UriComponentsBuilder.fromUriString(authorizationUrl).build().getQueryParams().getFirst("redirect_uri"));

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://qq.test/token?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("client_id", APP_ID))
                .andExpect(queryParam("client_secret", APP_KEY))
                .andExpect(queryParam("code", "single-use-code"))
                .andRespond(withSuccess("{\"access_token\":\"local-access-token\",\"expires_in\":7776000}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://qq.test/openid?")))
                .andExpect(queryParam("access_token", "local-access-token"))
                .andRespond(withSuccess("{\"client_id\":\"" + APP_ID + "\",\"openid\":\"qq-openid-123\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://qq.test/userinfo?")))
                .andExpect(queryParam("oauth_consumer_key", APP_ID))
                .andExpect(queryParam("openid", "qq-openid-123"))
                .andRespond(withSuccess("{\"ret\":0,\"nickname\":\"本地 QQ 用户\",\"figureurl_qq_2\":\"http://q.qlogo.cn/avatar.png\"}", MediaType.APPLICATION_JSON));

        QQConnectClient.QQIdentity identity = client.exchange("single-use-code");

        assertEquals("qq-openid-123", identity.subject());
        assertEquals("本地 QQ 用户", identity.displayName());
        assertEquals("https://q.qlogo.cn/avatar.png", identity.avatarUrl());
        server.verify();
    }

    @Test
    void rejectsOpenIdReturnedForAnotherApplication() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QQConnectClient client = client(restTemplate);
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://qq.test/token?")))
                .andRespond(withSuccess("{\"access_token\":\"local-access-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://qq.test/openid?")))
                .andRespond(withSuccess("{\"client_id\":\"another-app\",\"openid\":\"qq-openid-123\"}", MediaType.APPLICATION_JSON));

        OAuthException error = assertThrows(OAuthException.class, () -> client.exchange("single-use-code"));

        assertEquals(401, error.getStatus());
        server.verify();
    }

    @Test
    void ignoresAvatarUrlsOutsideTencentHosts() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        QQConnectClient client = client(restTemplate);
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://qq.test/token?")))
                .andRespond(withSuccess("{\"access_token\":\"local-access-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://qq.test/openid?")))
                .andRespond(withSuccess("{\"client_id\":\"" + APP_ID + "\",\"openid\":\"qq-openid-123\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://qq.test/userinfo?")))
                .andRespond(withSuccess("{\"ret\":0,\"nickname\":\"QQ 用户\",\"figureurl_qq_2\":\"https://evil.example/avatar.png\"}", MediaType.APPLICATION_JSON));

        QQConnectClient.QQIdentity identity = client.exchange("single-use-code");

        assertEquals("QQ 用户", identity.displayName());
        assertNull(identity.avatarUrl());
        server.verify();
    }

    @Test
    void refusesToStartWhenAppCredentialsAreMissing() {
        QQConnectClient client = new QQConnectClient(new RestTemplate(), new ObjectMapper(), "", "", REDIRECT,
                URI.create("https://qq.test/authorize"), URI.create("https://qq.test/token"),
                URI.create("https://qq.test/openid"), URI.create("https://qq.test/userinfo"));

        OAuthException error = assertThrows(OAuthException.class, () -> client.authorizationUrl("state"));

        assertEquals(503, error.getStatus());
        assertTrue(error.getMessage().contains("AppID"));
    }

    @Test
    void reportsPendingReviewSeparatelyFromCredentialAvailability() {
        RestTemplate restTemplate = new RestTemplate();
        QQConnectClient client = new QQConnectClient(restTemplate, new ObjectMapper(), APP_ID, APP_KEY, REDIRECT,
                "pending", URI.create("https://qq.test/authorize"), URI.create("https://qq.test/token"),
                URI.create("https://qq.test/openid"), URI.create("https://qq.test/userinfo"));

        assertTrue(client.isConfigured());
        assertEquals("pending", client.reviewStatus());
    }

    private QQConnectClient client(RestTemplate restTemplate) {
        return new QQConnectClient(restTemplate, new ObjectMapper(), APP_ID, APP_KEY, REDIRECT,
                URI.create("https://qq.test/authorize"), URI.create("https://qq.test/token"),
                URI.create("https://qq.test/openid"), URI.create("https://qq.test/userinfo"));
    }
}
