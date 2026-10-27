package com.bear.mcp.single.core.storage;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.OSSObject;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** 使用阿里云 OSS 保存 Skill 和 Resource 文件。 */
@Service
public class AliyunOssStorageService implements ObjectStorageService {
    private final AliyunOssStorageProperties properties;

    public AliyunOssStorageService(AliyunOssStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public PresignResult presignPut(String objectKey) {
        return presign(objectKey, HttpMethod.PUT);
    }

    @Override
    public PresignResult presignGet(String objectKey) {
        return presign(objectKey, HttpMethod.GET);
    }

    private PresignResult presign(String objectKey, HttpMethod method) {
        validateConfig();
        String normalizedKey = normalizeObjectKey(objectKey);
        OSS client = client();
        try {
            long expires = properties.getUploadExpireSeconds();
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    properties.getBucketName(), normalizedKey, method);
            request.setExpiration(new Date(System.currentTimeMillis() + expires * 1000));
            return new PresignResult(client.generatePresignedUrl(request).toString(), normalizedKey, expires);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public String downloadString(String objectKey) {
        validateConfig();
        OSS client = client();
        try (OSSObject object = client.getObject(properties.getBucketName(), normalizeObjectKey(objectKey))) {
            if (object.getObjectContent() == null) {
                return "";
            }
            return new String(object.getObjectContent().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("OSS 读取文件失败: " + e.getMessage(), e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void uploadString(String objectKey, String content) {
        validateConfig();
        byte[] bytes = (content != null ? content : "").getBytes(StandardCharsets.UTF_8);
        OSS client = client();
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            client.putObject(properties.getBucketName(), normalizeObjectKey(objectKey), input);
        } catch (Exception e) {
            throw new IllegalStateException("OSS 上传文件失败: " + e.getMessage(), e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public String downloadStringQuietly(String objectKey) {
        try {
            return downloadString(objectKey);
        } catch (Exception e) {
            return null;
        }
    }

    private OSS client() {
        return new OSSClientBuilder().build(
                properties.getEndpoint(), properties.getAccessKeyId(), properties.getAccessKeySecret());
    }

    private void validateConfig() {
        if (!StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getBucketName())
                || !StringUtils.hasText(properties.getAccessKeyId())
                || !StringUtils.hasText(properties.getAccessKeySecret())) {
            throw new IllegalStateException("OSS 配置不完整，请配置 ALIYUN_OSS_ENDPOINT/ALIYUN_OSS_BUCKET_NAME/ALIYUN_OSS_ACCESS_KEY_ID/ALIYUN_OSS_ACCESS_KEY_SECRET");
        }
    }

    private String normalizeObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        String key = objectKey.trim().replace('\\', '/');
        if (key.startsWith("/") || key.contains("..")) {
            throw new IllegalArgumentException("objectKey 不合法");
        }
        return key;
    }
}
