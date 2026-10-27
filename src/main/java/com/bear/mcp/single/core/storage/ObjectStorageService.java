package com.bear.mcp.single.core.storage;

/** Skill 和 Resource 文件使用的对象存储接口。 */
public interface ObjectStorageService {

    PresignResult presignPut(String objectKey);

    PresignResult presignGet(String objectKey);

    String downloadString(String objectKey);

    void uploadString(String objectKey, String content);

    String downloadStringQuietly(String objectKey);

    record PresignResult(String uploadUrl, String objectKey, Long expires) {
    }
}
