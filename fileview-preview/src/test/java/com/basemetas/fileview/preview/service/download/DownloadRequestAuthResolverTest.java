package com.basemetas.fileview.preview.service.download;

import com.basemetas.fileview.preview.config.DownloadAuthForwardConfig;
import com.basemetas.fileview.preview.model.download.DownloadRequestAuthContext;
import com.basemetas.fileview.preview.utils.EncodingUtils;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class DownloadRequestAuthResolverTest {

    private DownloadRequestAuthResolver resolver;
    private DownloadAuthForwardConfig config;

    @BeforeEach
    void setUp() {
        resolver = new DownloadRequestAuthResolver();
        config = new DownloadAuthForwardConfig();

        ReflectionTestUtils.setField(resolver, "authForwardConfig", config);
        ReflectionTestUtils.setField(resolver, "encodingUtils", new EncodingUtils());
    }

    @Test
    void shouldResolveCookieToCookieOnSameHost() {
        config.setEnabled(true);
        config.setSameHostOnly(true);
        config.setRules(List.of(rule(
                DownloadAuthForwardConfig.SourceType.COOKIE,
                "FILE_SESSION",
                DownloadAuthForwardConfig.TargetType.COOKIE,
                "FILE_SESSION",
                "",
                "")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("files.example.com");
        request.setCookies(new Cookie("FILE_SESSION", "cookie-token"));

        DownloadRequestAuthContext context =
                resolver.resolve(request, "https://files.example.com/zhuandai/demo.docx");

        assertFalse(context.hasNoForwardedAuth());
        assertEquals("cookie-token", context.getCookies().get("FILE_SESSION"));
        assertTrue(context.getHeaders().isEmpty());
        assertNotEquals("public", context.resolveAuthContextHash());
    }

    @Test
    void shouldMapCookieToAuthorizationHeader() {
        config.setEnabled(true);
        config.setSameHostOnly(true);
        config.setRules(List.of(rule(
                DownloadAuthForwardConfig.SourceType.COOKIE,
                "FILE_SESSION",
                DownloadAuthForwardConfig.TargetType.HEADER,
                "Authorization",
                "Bearer ",
                "")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("preview.example.com");
        request.setCookies(new Cookie("FILE_SESSION", "abc123"));

        DownloadRequestAuthContext context =
                resolver.resolve(request, "https://preview.example.com/zhuandai/demo.docx");

        assertFalse(context.hasNoForwardedAuth());
        assertEquals("Bearer abc123", context.getHeaders().get("Authorization"));
        assertTrue(context.getCookies().isEmpty());
    }

    @Test
    void shouldSkipForwardingForDisallowedHost() {
        config.setEnabled(true);
        config.setSameHostOnly(true);
        config.setRules(List.of(rule(
                DownloadAuthForwardConfig.SourceType.COOKIE,
                "FILE_SESSION",
                DownloadAuthForwardConfig.TargetType.COOKIE,
                "FILE_SESSION",
                "",
                "")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("preview.example.com");
        request.setCookies(new Cookie("FILE_SESSION", "cookie-token"));

        DownloadRequestAuthContext context =
                resolver.resolve(request, "https://files.example.com/zhuandai/demo.docx");

        assertTrue(context.hasNoForwardedAuth());
        assertEquals("public", context.resolveAuthContextHash());
    }

    @Test
    void shouldAllowConfiguredHostPattern() {
        config.setEnabled(true);
        config.setSameHostOnly(false);
        config.setAllowedHostPatterns(List.of("*.example.com"));
        config.setRules(List.of(rule(
                DownloadAuthForwardConfig.SourceType.HEADER,
                "X-Auth-Token",
                DownloadAuthForwardConfig.TargetType.HEADER,
                "X-Forwarded-Token",
                "",
                "")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("preview.internal");
        request.addHeader("X-Auth-Token", "header-token");

        DownloadRequestAuthContext context =
                resolver.resolve(request, "https://files.example.com/zhuandai/demo.docx");

        assertFalse(context.hasNoForwardedAuth());
        assertEquals("header-token", context.getHeaders().get("X-Forwarded-Token"));
    }

    @Test
    void shouldAllowAnyHostWhenSameHostOnlyDisabledWithoutWhitelist() {
        config.setEnabled(true);
        config.setSameHostOnly(false);
        config.setRules(List.of(rule(
                DownloadAuthForwardConfig.SourceType.COOKIE,
                "FILE_SESSION",
                DownloadAuthForwardConfig.TargetType.COOKIE,
                "FILE_SESSION",
                "",
                "")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("preview.internal");
        request.setCookies(new Cookie("FILE_SESSION", "cookie-token"));

        DownloadRequestAuthContext context =
                resolver.resolve(request, "https://files.external.example/zhuandai/demo.docx");

        assertFalse(context.hasNoForwardedAuth());
        assertEquals("cookie-token", context.getCookies().get("FILE_SESSION"));
    }

    @Test
    void shouldRestrictToWhitelistWhenSameHostOnlyDisabledAndWhitelistConfigured() {
        config.setEnabled(true);
        config.setSameHostOnly(false);
        config.setAllowedHostPatterns(List.of("*.example.com"));
        config.setRules(List.of(rule(
                DownloadAuthForwardConfig.SourceType.COOKIE,
                "FILE_SESSION",
                DownloadAuthForwardConfig.TargetType.COOKIE,
                "FILE_SESSION",
                "",
                "")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("preview.internal");
        request.setCookies(new Cookie("FILE_SESSION", "cookie-token"));

        DownloadRequestAuthContext context =
                resolver.resolve(request, "https://files.other.net/zhuandai/demo.docx");

        assertTrue(context.hasNoForwardedAuth());
    }

    @Test
    void shouldNotTreatExactHostPatternAsWildcard() {
        config.setEnabled(true);
        config.setSameHostOnly(false);
        config.setAllowedHostPatterns(List.of("example.com"));
        config.setRules(List.of(rule(
                DownloadAuthForwardConfig.SourceType.COOKIE,
                "FILE_SESSION",
                DownloadAuthForwardConfig.TargetType.COOKIE,
                "FILE_SESSION",
                "",
                "")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("preview.internal");
        request.setCookies(new Cookie("FILE_SESSION", "cookie-token"));

        DownloadRequestAuthContext context =
                resolver.resolve(request, "https://files.example.com/zhuandai/demo.docx");

        assertTrue(context.hasNoForwardedAuth());
    }

    @Test
    void shouldSkipRuleWhenSourceTypeIsNull() {
        config.setEnabled(true);
        config.setSameHostOnly(true);
        DownloadAuthForwardConfig.ForwardRule rule = rule(
                DownloadAuthForwardConfig.SourceType.COOKIE,
                "FILE_SESSION",
                DownloadAuthForwardConfig.TargetType.COOKIE,
                "FILE_SESSION",
                "",
                "");
        rule.setSourceType(null);
        config.setRules(List.of(rule));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("files.example.com");
        request.setCookies(new Cookie("FILE_SESSION", "cookie-token"));

        DownloadRequestAuthContext context =
                resolver.resolve(request, "https://files.example.com/zhuandai/demo.docx");

        assertTrue(context.hasNoForwardedAuth());
    }

    @Test
    void shouldSkipRuleWhenTargetTypeIsNull() {
        config.setEnabled(true);
        config.setSameHostOnly(true);
        DownloadAuthForwardConfig.ForwardRule rule = rule(
                DownloadAuthForwardConfig.SourceType.COOKIE,
                "FILE_SESSION",
                DownloadAuthForwardConfig.TargetType.COOKIE,
                "FILE_SESSION",
                "",
                "");
        rule.setTargetType(null);
        config.setRules(List.of(rule));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("files.example.com");
        request.setCookies(new Cookie("FILE_SESSION", "cookie-token"));

        DownloadRequestAuthContext context =
                resolver.resolve(request, "https://files.example.com/zhuandai/demo.docx");

        assertTrue(context.hasNoForwardedAuth());
    }

    @Test
    void shouldSkipRuleWhenHeaderSourceNameIsCookie() {
        config.setEnabled(true);
        config.setSameHostOnly(true);
        config.setRules(List.of(rule(
                DownloadAuthForwardConfig.SourceType.HEADER,
                "Cookie",
                DownloadAuthForwardConfig.TargetType.HEADER,
                "Authorization",
                "Bearer ",
                "")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("files.example.com");
        request.addHeader("Cookie", "FILE_SESSION=cookie-token");

        DownloadRequestAuthContext context =
                resolver.resolve(request, "https://files.example.com/zhuandai/demo.docx");

        assertTrue(context.hasNoForwardedAuth());
    }

    @Test
    void shouldSkipRuleWhenHeaderTargetNameIsCookie() {
        config.setEnabled(true);
        config.setSameHostOnly(true);
        config.setRules(List.of(rule(
                DownloadAuthForwardConfig.SourceType.COOKIE,
                "FILE_SESSION",
                DownloadAuthForwardConfig.TargetType.HEADER,
                "Cookie",
                "",
                "")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("files.example.com");
        request.setCookies(new Cookie("FILE_SESSION", "cookie-token"));

        DownloadRequestAuthContext context =
                resolver.resolve(request, "https://files.example.com/zhuandai/demo.docx");

        assertTrue(context.hasNoForwardedAuth());
    }

    private DownloadAuthForwardConfig.ForwardRule rule(
            DownloadAuthForwardConfig.SourceType sourceType,
            String sourceName,
            DownloadAuthForwardConfig.TargetType targetType,
            String targetName,
            String valuePrefix,
            String valueSuffix) {
        DownloadAuthForwardConfig.ForwardRule rule = new DownloadAuthForwardConfig.ForwardRule();
        rule.setSourceType(sourceType);
        rule.setSourceName(sourceName);
        rule.setTargetType(targetType);
        rule.setTargetName(targetName);
        rule.setValuePrefix(valuePrefix);
        rule.setValueSuffix(valueSuffix);
        return rule;
    }
}
