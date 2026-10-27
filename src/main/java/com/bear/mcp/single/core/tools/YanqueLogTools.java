package com.bear.mcp.single.core.tools;

import com.bear.mcp.single.core.logging.AliyunSlsLogService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class YanqueLogTools {
    private final AliyunSlsLogService logService;

    public YanqueLogTools(AliyunSlsLogService logService) {
        this.logService = logService;
    }

    @Tool(name = "query_yanque_logs",
            description = "查询 yanque-platform 在阿里云 SLS 中的运行日志，支持按 ERROR、异常名、traceId、中文关键词或业务包名检索")
    public Map<String, Object> queryYanqueLogs(
            @ToolParam(description = "查询关键词；不传或留空时查询全部日志", required = false) String keyword,
            @ToolParam(description = "查询最近多少分钟，默认30，范围1到1440", required = false) Integer minutes,
            @ToolParam(description = "最多返回多少条，默认20，范围1到100", required = false) Integer limit) {
        return logService.search(keyword, minutes, limit);
    }
}
