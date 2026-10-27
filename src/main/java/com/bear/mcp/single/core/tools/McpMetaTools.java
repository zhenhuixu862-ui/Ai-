package com.bear.mcp.single.core.tools;

import com.bear.mcp.single.core.context.McpUserContext;
import com.bear.mcp.single.core.context.McpUserContextHolder;
import com.bear.mcp.single.core.entity.McpPromptTemplateEntity;
import com.bear.mcp.single.core.entity.McpResourceEntity;
import com.bear.mcp.single.core.prompt.PromptAccessService;
import com.bear.mcp.single.core.prompt.PromptTemplateService;
import com.bear.mcp.single.core.resource.ResourceAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MCP 元能力桥接工具。
 *
 * <p>有些客户端只稳定展示 tools/list，不会主动探索 prompts/list 或 resources/list。
 * 这里把 Prompt / Resource 的发现和读取能力包装成内置 Tool，让 Agent 换会话后也能走对链路。</p>
 */
@Component
public class McpMetaTools {

    private final PromptAccessService promptAccessService;
    private final PromptTemplateService promptTemplateService;
    private final ResourceAccessService resourceAccessService;
    private final ObjectMapper objectMapper;

    public McpMetaTools(PromptAccessService promptAccessService,
                        PromptTemplateService promptTemplateService,
                        ResourceAccessService resourceAccessService,
                        ObjectMapper objectMapper) {
        this.promptAccessService = promptAccessService;
        this.promptTemplateService = promptTemplateService;
        this.resourceAccessService = resourceAccessService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "list_mcp_prompts",
            description = "列出当前 Token 可见的 MCP Prompts。用户询问有哪些 Prompt、需要选择查询链路、或需要了解 Prompt 推荐工具时调用。")
    public Map<String, Object> listMcpPrompts(
            @ToolParam(description = "可选关键词，按 name、title、description 或 linked_tools 过滤", required = false)
            String keyword
    ) {
        McpUserContext context = McpUserContextHolder.get();
        List<Map<String, Object>> prompts = promptAccessService.listAccessiblePrompts(context).stream()
                .map(this::toPromptMap)
                .filter(item -> matches(item, keyword))
                .toList();

        Map<String, Object> result = success("查询成功");
        result.put("total", prompts.size());
        result.put("prompts", prompts);
        return result;
    }

    @Tool(name = "list_mcp_resources",
            description = "列出当前 Token 可见的 MCP Resources。用户询问知识库、制度文档、表结构、指标口径或需要寻找 Resource URI 时优先调用。")
    public Map<String, Object> listMcpResources(
            @ToolParam(description = "可选关键词，按 uri、name、description 或 mimeType 过滤", required = false)
            String keyword
    ) {
        McpUserContext context = McpUserContextHolder.get();
        List<Map<String, Object>> resources = resourceAccessService.listAccessibleResources(context).stream()
                .map(this::toResourceMap)
                .filter(item -> matches(item, keyword))
                .toList();

        Map<String, Object> result = success("查询成功");
        result.put("total", resources.size());
        result.put("resources", resources);
        return result;
    }

    @Tool(name = "read_mcp_resource",
            description = "读取指定 MCP Resource 的 Markdown 正文。先用 list_mcp_resources 找到 URI，再用本工具读取原文并基于原文回答。")
    public Map<String, Object> readMcpResource(
            @ToolParam(description = "Resource URI，例如 bear://docs/company_policy_test", required = true)
            String uri
    ) {
        if (uri == null || uri.isBlank()) {
            return failure("uri 不能为空");
        }

        McpUserContext context = McpUserContextHolder.get();
        McpResourceEntity entity = resourceAccessService.findAccessibleByUri(context, uri.trim());
        if (entity == null) {
            return failure("Resource 不存在、未发布或无权限读取: " + uri);
        }

        Map<String, Object> result = success("读取成功");
        result.put("uri", entity.getResourceUri());
        result.put("name", entity.getName());
        result.put("description", entity.getDescription());
        result.put("mimeType", entity.getMimeType() == null ? "text/markdown" : entity.getMimeType());
        result.put("text", resourceAccessService.readText(entity));
        return result;
    }

    private Map<String, Object> toPromptMap(McpPromptTemplateEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", entity.getPromptName());
        item.put("title", entity.getTitle());
        item.put("description", entity.getDescription());
        item.put("arguments", promptTemplateService.parseArgumentsSchema(entity.getArgumentsSchema()));
        item.put("linked_tools", parseJsonArray(entity.getLinkedToolNames()));
        return item;
    }

    private Map<String, Object> toResourceMap(McpResourceEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("uri", entity.getResourceUri());
        item.put("name", entity.getName());
        item.put("description", entity.getDescription());
        item.put("mimeType", entity.getMimeType() == null ? "text/markdown" : entity.getMimeType());
        return item;
    }

    private Object parseJsonArray(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean matches(Map<String, Object> item, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        return item.values().stream()
                .anyMatch(value -> value != null
                        && String.valueOf(value).toLowerCase(Locale.ROOT).contains(normalized));
    }

    private Map<String, Object> success(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        return result;
    }

    private Map<String, Object> failure(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }
}
