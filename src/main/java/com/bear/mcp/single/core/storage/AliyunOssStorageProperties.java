package com.bear.mcp.single.core.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 阿里云 OSS 配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "bear.storage.oss")
public class AliyunOssStorageProperties {
    private String endpoint;
    private String bucketName;
    private String accessKeyId;
    private String accessKeySecret;
    private Long uploadExpireSeconds = 600L;
}
