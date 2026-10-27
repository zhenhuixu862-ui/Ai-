package com.bear.mcp.single.core.logging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AliyunSlsProperties {
    private final String endpoint;
    private final String project;
    private final String logstore;
    private final String accessKeyId;
    private final String accessKeySecret;

    public AliyunSlsProperties(
            @Value("${ALIYUN_SLS_ENDPOINT:}") String endpoint,
            @Value("${ALIYUN_SLS_PROJECT:}") String project,
            @Value("${ALIYUN_SLS_LOGSTORE:}") String logstore,
            @Value("${ALIBABA_CLOUD_ACCESS_KEY_ID:}") String accessKeyId,
            @Value("${ALIBABA_CLOUD_ACCESS_KEY_SECRET:}") String accessKeySecret) {
        this.endpoint = endpoint;
        this.project = project;
        this.logstore = logstore;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
    }

    public String getEndpoint() { return endpoint; }
    public String getProject() { return project; }
    public String getLogstore() { return logstore; }
    public String getAccessKeyId() { return accessKeyId; }
    public String getAccessKeySecret() { return accessKeySecret; }
}
