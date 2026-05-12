/*
 * Copyright 2025 BaseMetas
 */
package com.basemetas.fileview.preview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "fileview.storage")
public class OssProperties {

    private Map<String, OssInstance> oss = new HashMap<>();

    public Map<String, OssInstance> getOss() {
        return oss;
    }

    public void setOss(Map<String, OssInstance> oss) {
        this.oss = oss;
    }

    public OssInstance getInstance(String name) {
        return name == null ? null : oss.get(name);
    }

    public static class OssInstance {
        private String provider;
        private String bucket;
        private String endpoint;
        private String region;
        private String accessKey;
        private String secretKey;
        private Boolean pathStyleAccessEnabled = Boolean.TRUE;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public Boolean getPathStyleAccessEnabled() { return pathStyleAccessEnabled; }
        public void setPathStyleAccessEnabled(Boolean pathStyleAccessEnabled) { this.pathStyleAccessEnabled = pathStyleAccessEnabled; }
    }
}
