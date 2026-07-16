package com.basemetas.fileview.preview.service.download;

import com.basemetas.fileview.preview.model.download.DownloadRequestAuthContext;
import java.net.URI;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DownloadRequestAuthSupportTest {

    @Test
    void shouldApplyHeadersAndCookiesToHttpRequestBuilder() {
        DownloadRequestAuthContext authContext = new DownloadRequestAuthContext();
        authContext.getHeaders().put("Authorization", "Bearer token-1");
        authContext.getHeaders().put("Cookie", "SESSION=from-header");
        authContext.getHeaders().put("Host", "should-be-ignored");
        authContext.getCookies().put("FILE_SESSION", "cookie-token");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("https://files.example.com/demo.docx"))
                .GET();

        DownloadRequestAuthSupport.applyTo(builder, authContext,
                LoggerFactory.getLogger(DownloadRequestAuthSupportTest.class));

        HttpRequest request = builder.build();
        assertEquals("Bearer token-1", request.headers().firstValue("Authorization").orElse(null));
        assertEquals("FILE_SESSION=cookie-token", request.headers().firstValue("Cookie").orElse(null));
        assertFalse(request.headers().firstValue("Host").isPresent());
    }
}
