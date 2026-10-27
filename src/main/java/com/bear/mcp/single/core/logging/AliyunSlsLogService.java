package com.bear.mcp.single.core.logging;

import com.aliyun.openservices.log.Client;
import com.aliyun.openservices.log.common.LogContent;
import com.aliyun.openservices.log.common.QueriedLog;
import com.aliyun.openservices.log.exception.LogException;
import com.aliyun.openservices.log.request.GetLogsRequest;
import com.aliyun.openservices.log.response.GetLogsResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AliyunSlsLogService {
    private static final int DEFAULT_MINUTES = 30;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_MINUTES = 24 * 60;
    private static final int MAX_LIMIT = 100;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final AliyunSlsProperties properties;

    public AliyunSlsLogService(AliyunSlsProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> search(String keyword, Integer minutes, Integer limit) {
        requireConfiguration();
        int safeMinutes = clamp(minutes, DEFAULT_MINUTES, 1, MAX_MINUTES);
        int safeLimit = clamp(limit, DEFAULT_LIMIT, 1, MAX_LIMIT);
        String query = StringUtils.hasText(keyword) ? keyword.trim() : "*";
        int to = Math.toIntExact(Instant.now().getEpochSecond() + 1);
        int from = to - safeMinutes * 60;
        GetLogsRequest request = new GetLogsRequest(properties.getProject(), properties.getLogstore(),
                from, to, "", query, 0, safeLimit, true);

        Client client = new Client(properties.getEndpoint(), properties.getAccessKeyId(),
                    properties.getAccessKeySecret());
        try {
            GetLogsResponse response = client.GetLogs(request);
            List<Map<String, Object>> logs = new ArrayList<>();
            for (QueriedLog queriedLog : response.GetLogs()) {
                Map<String, Object> item = new LinkedHashMap<>();
                int timestamp = queriedLog.GetLogItem().GetTime();
                item.put("time", DISPLAY_TIME.format(Instant.ofEpochSecond(timestamp)));
                item.put("timestamp", timestamp);
                item.put("source", queriedLog.GetSource());
                for (LogContent content : queriedLog.GetLogItem().GetLogContents()) {
                    item.put(content.GetKey(), content.GetValue());
                }
                logs.add(item);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", query);
            result.put("from", DISPLAY_TIME.format(Instant.ofEpochSecond(from)));
            result.put("to", DISPLAY_TIME.format(Instant.ofEpochSecond(to)));
            result.put("count", logs.size());
            result.put("completed", response.IsCompleted());
            result.put("logs", logs);
            return result;
        } catch (LogException e) {
            throw new IllegalStateException("查询阿里云 SLS 日志失败：" + e.GetErrorCode() + " - " + e.getMessage(), e);
        } finally {
            client.shutdown();
        }
    }

    private void requireConfiguration() {
        if (!StringUtils.hasText(properties.getEndpoint()) || !StringUtils.hasText(properties.getProject())
                || !StringUtils.hasText(properties.getLogstore()) || !StringUtils.hasText(properties.getAccessKeyId())
                || !StringUtils.hasText(properties.getAccessKeySecret())) {
            throw new IllegalStateException("阿里云 SLS 查询配置不完整，请检查服务器环境变量");
        }
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        int actual = value == null ? defaultValue : value;
        return Math.max(min, Math.min(actual, max));
    }
}
