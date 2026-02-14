package com.basemetas.fileview.preview.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HttpUtils URL安全校验功能测试类
 */
public class UrlSecurityValidatorTest {

    private HttpUtils httpUtils;

    @BeforeEach
    public void setUp() {
        httpUtils = new HttpUtils();
    }

    @Test
    public void testValidateWithEmptyUrl() {
        HttpUtils.UrlValidationResult result = httpUtils.validateUrlSecurity("");
        assertFalse(result.isValid());
        assertEquals("URL不能为空", result.getErrorMessage());
    }

    @Test
    public void testValidateWithNullUrl() {
        HttpUtils.UrlValidationResult result = httpUtils.validateUrlSecurity(null);
        assertFalse(result.isValid());
        assertEquals("URL不能为空", result.getErrorMessage());
    }

    @Test
    public void testValidateWithInvalidUrl() {
        HttpUtils.UrlValidationResult result = httpUtils.validateUrlSecurity("not-a-valid-url");
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("URL格式无效"));
    }

    @Test
    public void testValidateWithNoRestrictions() {
        // 未配置任何限制，应该允许所有URL
        ReflectionTestUtils.setField(httpUtils, "trustedSitesConfig", "");
        ReflectionTestUtils.setField(httpUtils, "untrustedSitesConfig", "");

        HttpUtils.UrlValidationResult result = httpUtils.validateUrlSecurity("https://example.com/file.pdf");
        assertTrue(result.isValid());
    }

    @Test
    public void testValidateWithTrustedSites() {
        // 配置信任站点
        ReflectionTestUtils.setField(httpUtils, "trustedSitesConfig", "example.com,trusted.com");
        ReflectionTestUtils.setField(httpUtils, "untrustedSitesConfig", "");

        // 应该允许信任站点
        HttpUtils.UrlValidationResult result1 = httpUtils.validateUrlSecurity("https://example.com/file.pdf");
        assertTrue(result1.isValid());

        HttpUtils.UrlValidationResult result2 = httpUtils.validateUrlSecurity("https://www.example.com/file.pdf");
        assertTrue(result2.isValid());

        // 应该拒绝非信任站点
        HttpUtils.UrlValidationResult result3 = httpUtils.validateUrlSecurity("https://untrusted-site.com/file.pdf");
        assertFalse(result3.isValid());
        assertTrue(result3.getErrorMessage().contains("不在信任列表中"));
    }

    @Test
    public void testValidateWithUntrustedSites() {
        // 配置不信任站点
        ReflectionTestUtils.setField(httpUtils, "trustedSitesConfig", "");
        ReflectionTestUtils.setField(httpUtils, "untrustedSitesConfig", "malicious.com,bad-site.net");

        // 应该允许其他站点
        HttpUtils.UrlValidationResult result1 = httpUtils.validateUrlSecurity("https://good-site.com/file.pdf");
        assertTrue(result1.isValid());

        // 应该拒绝不信任站点
        HttpUtils.UrlValidationResult result2 = httpUtils.validateUrlSecurity("https://malicious.com/file.pdf");
        assertFalse(result2.isValid());
        assertTrue(result2.getErrorMessage().contains("不信任列表"));

        HttpUtils.UrlValidationResult result3 = httpUtils.validateUrlSecurity("https://www.bad-site.net/file.pdf");
        assertFalse(result3.isValid());
    }

    @Test
    public void testValidateWithWildcardTrustedSites() {
        // 配置通配符信任站点
        ReflectionTestUtils.setField(httpUtils, "trustedSitesConfig", "*.example.com,specific.com");
        ReflectionTestUtils.setField(httpUtils, "untrustedSitesConfig", "");

        // 应该允许所有子域名
        HttpUtils.UrlValidationResult result1 = httpUtils.validateUrlSecurity("https://api.example.com/file.pdf");
        assertTrue(result1.isValid());

        HttpUtils.UrlValidationResult result2 = httpUtils.validateUrlSecurity("https://www.example.com/file.pdf");
        assertTrue(result2.isValid());

        HttpUtils.UrlValidationResult result3 = httpUtils.validateUrlSecurity("https://example.com/file.pdf");
        assertTrue(result3.isValid());

        // 应该允许特定站点
        HttpUtils.UrlValidationResult result4 = httpUtils.validateUrlSecurity("https://specific.com/file.pdf");
        assertTrue(result4.isValid());
    }

    @Test
    public void testValidateWithBothTrustedAndUntrustedSites() {
        // 配置信任站点和不信任站点
        ReflectionTestUtils.setField(httpUtils, "trustedSitesConfig", "example.com,*.trusted.com");
        ReflectionTestUtils.setField(httpUtils, "untrustedSitesConfig", "bad.example.com,malicious.com");

        // 黑名单优先：即使在信任列表的域下，黑名单中的子域名也应该被拒绝
        HttpUtils.UrlValidationResult result1 = httpUtils.validateUrlSecurity("https://bad.example.com/file.pdf");
        assertFalse(result1.isValid());
        assertTrue(result1.getErrorMessage().contains("不信任列表"));

        // 应该允许信任站点
        HttpUtils.UrlValidationResult result2 = httpUtils.validateUrlSecurity("https://www.example.com/file.pdf");
        assertTrue(result2.isValid());

        // 应该拒绝不在信任列表中的站点
        HttpUtils.UrlValidationResult result3 = httpUtils.validateUrlSecurity("https://other-site.com/file.pdf");
        assertFalse(result3.isValid());
    }

    @Test
    public void testValidateWithCaseInsensitivity() {
        // 配置信任站点（测试大小写不敏感）
        ReflectionTestUtils.setField(httpUtils, "trustedSitesConfig", "Example.COM,TrustED.net");
        ReflectionTestUtils.setField(httpUtils, "untrustedSitesConfig", "");

        HttpUtils.UrlValidationResult result1 = httpUtils.validateUrlSecurity("https://example.com/file.pdf");
        assertTrue(result1.isValid());

        HttpUtils.UrlValidationResult result2 = httpUtils.validateUrlSecurity("https://EXAMPLE.COM/file.pdf");
        assertTrue(result2.isValid());

        HttpUtils.UrlValidationResult result3 = httpUtils.validateUrlSecurity("https://www.TrustED.net/file.pdf");
        assertTrue(result3.isValid());
    }

    @Test
    public void testValidateWithComplexUrls() {
        ReflectionTestUtils.setField(httpUtils, "trustedSitesConfig", "example.com");
        ReflectionTestUtils.setField(httpUtils, "untrustedSitesConfig", "");

        // 带端口号
        HttpUtils.UrlValidationResult result1 = httpUtils.validateUrlSecurity("https://example.com:8080/file.pdf");
        assertTrue(result1.isValid());

        // 带查询参数
        HttpUtils.UrlValidationResult result2 = httpUtils.validateUrlSecurity("https://example.com/path/file.pdf?key=value");
        assertTrue(result2.isValid());

        // HTTP协议
        HttpUtils.UrlValidationResult result3 = httpUtils.validateUrlSecurity("http://example.com/file.pdf");
        assertTrue(result3.isValid());
    }
}
