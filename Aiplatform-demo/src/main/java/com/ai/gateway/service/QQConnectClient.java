package com.ai.gateway.service;

import com.ai.gateway.exception.OAuthException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** QQ Connect authorization-code client. QQ Connect does not issue an OIDC ID token. */
@Slf4j
@Service
public class QQConnectClient {
    private static final URI AUTHORIZATION_ENDPOINT = URI.create("https://graph.qq.com/oauth2.0/authorize");
    private static final URI TOKEN_ENDPOINT = URI.create("https://graph.qq.com/oauth2.0/token");
    private static final URI OPENID_ENDPOINT = URI.create("https://graph.qq.com/oauth2.0/me");
    private static final URI USER_INFO_ENDPOINT = URI.create("https://graph.qq.com/user/get_user_info");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String appId;
    private final String appKey;
    private final String redirectUri;
    private final String reviewStatus;
    private final URI authorizationEndpoint;
    private final URI tokenEndpoint;
    private final URI openIdEndpoint;
    private final URI userInfoEndpoint;

    @Autowired
    public QQConnectClient(RestTemplateBuilder builder,
                           ObjectMapper objectMapper,
                           @Value("${QQ_OAUTH_APP_ID:}") String appId,
                           @Value("${QQ_OAUTH_APP_KEY:}") String appKey,
                           @Value("${QQ_OAUTH_REDIRECT_URI:http://127.0.0.1:13000/oauth/callback}") String redirectUri,
                           @Value("${QQ_OAUTH_REVIEW_STATUS:pending}") String reviewStatus) {
        this(builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(5)).build(),
                objectMapper, appId, appKey, redirectUri, reviewStatus,
                AUTHORIZATION_ENDPOINT, TOKEN_ENDPOINT, OPENID_ENDPOINT, USER_INFO_ENDPOINT);
    }

    QQConnectClient(RestTemplate restTemplate, ObjectMapper objectMapper, String appId, String appKey,
                    String redirectUri, URI authorizationEndpoint, URI tokenEndpoint,
                    URI openIdEndpoint, URI userInfoEndpoint) {
        this(restTemplate, objectMapper, appId, appKey, redirectUri, "approved",
                authorizationEndpoint, tokenEndpoint, openIdEndpoint, userInfoEndpoint);
    }

    QQConnectClient(RestTemplate restTemplate, ObjectMapper objectMapper, String appId, String appKey,
                    String redirectUri, String reviewStatus, URI authorizationEndpoint, URI tokenEndpoint,
                    URI openIdEndpoint, URI userInfoEndpoint) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.appId = appId == null ? "" : appId.trim();
        this.appKey = appKey == null ? "" : appKey.trim();
        this.redirectUri = redirectUri == null ? "" : redirectUri.trim();
        this.reviewStatus = "approved".equalsIgnoreCase(reviewStatus == null ? "" : reviewStatus.trim())
                ? "approved" : "pending";
        this.authorizationEndpoint = authorizationEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.openIdEndpoint = openIdEndpoint;
        this.userInfoEndpoint = userInfoEndpoint;
    }

    public boolean isConfigured() {
        return !appId.isBlank() && !appKey.isBlank() && !redirectUri.isBlank();
    }

    public String reviewStatus() {
        return isConfigured() ? reviewStatus : "unconfigured";
    }

    public String authorizationUrl(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUri(authorizationEndpoint)
                .queryParam("response_type", "code")
                .queryParam("client_id", appId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "get_user_info")
                .queryParam("state", state)
                .build().encode().toUriString();
    }

    public QQIdentity exchange(String code) {
        requireConfigured();
        if (code == null || code.isBlank() || code.length() > 4096) {
            throw new OAuthException(400, "授权码无效");
        }

        try {
            Map<String, Object> token = getJson(UriComponentsBuilder.fromUri(tokenEndpoint)
                    .queryParam("grant_type", "authorization_code")
                    .queryParam("client_id", appId)
                    .queryParam("client_secret", appKey)
                    .queryParam("code", code)
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("fmt", "json")
                    .build().encode().toUriString(), "QQ 授权已失效，请重新登录");
            String accessToken = stringValue(token.get("access_token"));
            if (accessToken == null || accessToken.length() > 8192) {
                throw new OAuthException(401, "QQ 授权已失效，请重新登录");
            }

            Map<String, Object> openIdResponse = getJson(UriComponentsBuilder.fromUri(openIdEndpoint)
                    .queryParam("access_token", accessToken)
                    .queryParam("fmt", "json")
                    .build().encode().toUriString(), "QQ 身份验证失败");
            String openIdClientId = stringValue(openIdResponse.get("client_id"));
            String openId = stringValue(openIdResponse.get("openid"));
            if (openId == null || openId.length() > 255
                    || (openIdClientId != null && !appId.equals(openIdClientId))) {
                throw new OAuthException(401, "QQ 身份验证失败");
            }

            Map<String, Object> profile = getJson(UriComponentsBuilder.fromUri(userInfoEndpoint)
                    .queryParam("access_token", accessToken)
                    .queryParam("oauth_consumer_key", appId)
                    .queryParam("openid", openId)
                    .queryParam("format", "json")
                    .build().encode().toUriString(), "无法读取 QQ 账号资料");
            Object ret = profile.get("ret");
            if (ret instanceof Number number && number.intValue() != 0) {
                throw new OAuthException(502, "QQ 账号资料暂时无法读取");
            }
            String nickname = stringValue(profile.get("nickname"));
            if (nickname == null) nickname = "QQ 用户";
            if (nickname.length() > 255) nickname = nickname.substring(0, 255);
            String avatarUrl = safeAvatarUrl(profile.get("figureurl_qq_2"));
            if (avatarUrl == null) avatarUrl = safeAvatarUrl(profile.get("figureurl_qq_1"));
            return new QQIdentity(openId, nickname, avatarUrl);
        } catch (OAuthException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.warn("QQ Connect is temporarily unreachable ({})", e.getClass().getSimpleName());
            throw new OAuthException(503, "QQ 登录服务暂时不可用，请稍后重试", e);
        } catch (RestClientException e) {
            log.warn("QQ Connect request failed ({})", e.getClass().getSimpleName());
            throw new OAuthException(502, "QQ 登录暂时失败，请稍后重试", e);
        }
    }

    private Map<String, Object> getJson(String url, String failureMessage) {
        String body = restTemplate.getForObject(url, String.class);
        if (body == null || body.isBlank() || body.length() > 64_000) {
            throw new OAuthException(502, failureMessage);
        }
        try {
            return objectMapper.readValue(body, new TypeReference<>() { });
        } catch (Exception e) {
            // QQ may return a plain-text error when a credential or authorization code is rejected.
            throw new OAuthException(401, failureMessage, e);
        }
    }

    private String stringValue(Object value) {
        if (!(value instanceof String text)) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeAvatarUrl(Object value) {
        String raw = stringValue(value);
        if (raw == null || raw.length() > 500) return null;
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getPort() != -1
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || !(host.equals("qlogo.cn") || host.endsWith(".qlogo.cn")
                    || host.equals("qpic.cn") || host.endsWith(".qpic.cn")
                    || host.equals("qq.com") || host.endsWith(".qq.com"))) return null;
            return org.springframework.web.util.UriComponentsBuilder.fromUri(uri)
                    .scheme("https").build().toUriString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) throw new OAuthException(503, "QQ OAuth 尚未配置，请先设置 AppID、AppKey 和回调地址");
    }

    public record QQIdentity(String subject, String displayName, String avatarUrl) { }
}
