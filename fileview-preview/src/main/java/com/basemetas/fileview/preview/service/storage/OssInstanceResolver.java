/*
 * Copyright 2025 BaseMetas
 */
package com.basemetas.fileview.preview.service.storage;

import com.basemetas.fileview.preview.common.exception.ErrorCode;
import com.basemetas.fileview.preview.common.exception.FileViewException;
import com.basemetas.fileview.preview.config.OssProperties;
import com.basemetas.fileview.preview.model.request.FilePreviewRequest;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class OssInstanceResolver {

    private final OssProperties ossProperties;

    public OssInstanceResolver(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
    }

    public ResolvedOss resolve(FilePreviewRequest request) {
        String storageName = normalize(request.getStorage());
        if (storageName == null) {
            if (isS3Request(request)) {
                // s3:// URL 未指定 storage：尝试自动推断实例或校验请求级凭证
                return resolveImplicitS3(request);
            }
            return ResolvedOss.skip();
        }

        OssProperties.OssInstance instance = ossProperties.getInstance(storageName);
        if (instance == null) {
            throw FileViewException.of(ErrorCode.INVALID_PARAMETER,
                    "未配置名为 '" + storageName + "' 的 OSS 实例");
        }

        String provider = normalize(instance.getProvider());
        if (provider == null) {
            throw FileViewException.of(ErrorCode.INVALID_PARAMETER,
                    "OSS 实例 '" + storageName + "' 缺少 provider 配置");
        }
        if (!"s3".equalsIgnoreCase(provider)) {
            throw FileViewException.of(ErrorCode.INVALID_PARAMETER,
                    "当前仅支持 provider=s3，实例 '" + storageName + "' 配置为: " + provider);
        }

        // 命名实例：endpoint/accessKey/secretKey/region 由服务端配置决定，不允许请求覆盖（防止凭证窃取）
        // 仅允许请求覆盖 bucket（同一实例可能有多个 bucket）和 pathStyleAccessEnabled
        Boolean pathStyle = request.getPathStyleAccessEnabled() != null
                ? request.getPathStyleAccessEnabled()
                : instance.getPathStyleAccessEnabled();

        return new ResolvedOss(
                storageName,
                provider,
                firstNonBlank(request.getBucket(), instance.getBucket()),
                instance.getRegion(),
                instance.getEndpoint(),
                instance.getAccessKey(),
                instance.getSecretKey(),
                pathStyle,
                false);
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * S3 请求未指定 storage 时的自动推断逻辑：
     * 1. 请求已携带 accessKey + secretKey → 透传（skip）
     * 2. 仅配置了一个 OSS 实例 → 自动使用该实例
     * 3. 其他情况 → 报错
     */
    private ResolvedOss resolveImplicitS3(FilePreviewRequest request) {
        String accessKey = normalize(request.getAccessKey());
        String secretKey = normalize(request.getSecretKey());

        // 凭证必须成对出现，禁止混合（防止 request.accessKey + instance.secretKey 的组合）
        if ((accessKey != null) != (secretKey != null)) {
            throw FileViewException.of(ErrorCode.INVALID_PARAMETER,
                    "accessKey 和 secretKey 必须同时提供或同时为空");
        }

        // 请求级凭证充分，直接透传
        if (accessKey != null && secretKey != null) {
            return ResolvedOss.skip();
        }

        // 尝试自动推断：仅有一个配置实例时自动使用
        Map<String, OssProperties.OssInstance> instances = ossProperties.getOss();
        if (instances != null && instances.size() == 1) {
            Map.Entry<String, OssProperties.OssInstance> entry = instances.entrySet().iterator().next();
            String instanceName = entry.getKey();
            OssProperties.OssInstance instance = entry.getValue();

            String provider = normalize(instance.getProvider());
            if (provider == null || !"s3".equalsIgnoreCase(provider)) {
                throw FileViewException.of(ErrorCode.INVALID_PARAMETER,
                        "自动推断的 OSS 实例 '" + instanceName + "' 的 provider 不是 s3（当前: "
                                + (provider == null ? "未配置" : provider) + "），无法用于 S3 下载");
            }

            Boolean pathStyle = request.getPathStyleAccessEnabled() != null
                    ? request.getPathStyleAccessEnabled()
                    : instance.getPathStyleAccessEnabled();

            return new ResolvedOss(
                    instanceName,
                    provider,
                    firstNonBlank(request.getBucket(), instance.getBucket()),
                    firstNonBlank(request.getRegion(), instance.getRegion()),
                    firstNonBlank(request.getEndpoint(), instance.getEndpoint()),
                    firstNonBlank(request.getAccessKey(), instance.getAccessKey()),
                    firstNonBlank(request.getSecretKey(), instance.getSecretKey()),
                    pathStyle,
                    false);
        }

        // 无法推断：凭证不足且无法确定唯一实例
        if (instances == null || instances.isEmpty()) {
            throw FileViewException.of(ErrorCode.MISSING_REQUIRED_PARAMETER,
                    "S3 预览未指定 storage 实例，且未配置任何 OSS 实例；请提供 accessKey 和 secretKey 或配置 OSS 实例");
        }
        throw FileViewException.of(ErrorCode.MISSING_REQUIRED_PARAMETER,
                "S3 预览未指定 storage 实例，且存在多个 OSS 配置（" + String.join(", ", instances.keySet())
                        + "），请通过 storage 参数指定实例名称，或提供 accessKey 和 secretKey");
    }

    private boolean isS3Request(FilePreviewRequest request) {
        String networkFileUrl = request.getNetworkFileUrl();
        return networkFileUrl != null && networkFileUrl.trim().toLowerCase().startsWith("s3://");
    }

    public record ResolvedOss(
            String storageName,
            String provider,
            String bucket,
            String region,
            String endpoint,
            String accessKey,
            String secretKey,
            Boolean pathStyleAccessEnabled,
            boolean skipResolution) {
        public static ResolvedOss skip() {
            return new ResolvedOss(null, null, null, null, null, null, null, null, true);
        }
    }
}
