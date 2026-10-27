package com.bear.mcp.single.core.tools;

import com.bear.mcp.single.core.feishu.FeishuRemoteMcpService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单体版 Bear 对外暴露的飞书远程 MCP 工具。
 */
@Component
public class FeishuRemoteMcpTools {

    private static final Pattern DOC_PATH = Pattern.compile("/(?:docx|wiki|docs)/([A-Za-z0-9]+)");

    private final FeishuRemoteMcpService feishuRemoteMcpService;

    public FeishuRemoteMcpTools(FeishuRemoteMcpService feishuRemoteMcpService) {
        this.feishuRemoteMcpService = feishuRemoteMcpService;
    }

    @Tool(name = "feishu_mcp_list_tools",
            description = """
                    列出飞书官方远程 MCP 当前可用工具。Bear MCP 作为统一入口，内部代理请求 https://mcp.feishu.cn/mcp。
                    Bear 从服务端配置的 App ID / App Secret 自动获取 tenant_access_token，不向 Agent 暴露访问凭证。
                    allowed_tools 可限制本次发现的飞书工具，多个工具名用英文逗号分隔。""")
    public Map<String, Object> listFeishuMcpTools(
            @ToolParam(description = "允许发现的飞书 MCP 工具列表，逗号分隔，如 search-doc,fetch-doc,create-doc", required = false)
            String allowed_tools
    ) {
        return feishuRemoteMcpService.listTools(allowed_tools);
    }

    @Tool(name = "feishu_mcp_call_tool",
            description = """
                    通用飞书远程 MCP 代理调用工具。可调用飞书官方 MCP 支持的工具，例如 search-doc、fetch-doc、create-doc、update-doc。
                    Bear 会把 X-Lark-MCP-Allowed-Tools 限制为 tool_name 对应的单个飞书工具。""")
    public Map<String, Object> callFeishuMcpTool(
            @ToolParam(description = "飞书 MCP 工具名，如 search-doc、fetch-doc、create-doc、update-doc", required = true)
            String tool_name,
            @ToolParam(description = "飞书 MCP 工具参数 JSON 对象字符串，如 {\"docID\":\"xxxx\"}", required = false)
            String arguments_json
    ) {
        return feishuRemoteMcpService.callTool(tool_name, arguments_json);
    }

    @Tool(name = "feishu_mcp_fetch_doc",
            description = """
                    通过飞书官方远程 MCP 读取云文档完整内容。参数二选一：doc_id 为文档 token，或 doc_url 为完整飞书文档链接。
                    目标飞书账号必须对文档有访问权限。""")
    public Map<String, Object> fetchFeishuDoc(
            @ToolParam(description = "飞书云文档 token，与飞书 MCP fetch-doc 的 docID 对应；与 doc_url 二选一", required = false)
            String doc_id,
            @ToolParam(description = "飞书文档完整 URL，将自动解析 /docx/、/wiki/、/docs/ 路径中的 token；与 doc_id 二选一", required = false)
            String doc_url
    ) {
        String docId = resolveDocId(doc_id, doc_url);
        if (!StringUtils.hasText(docId)) {
            return failure("请提供 doc_id 或可解析的 doc_url");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("doc_id", docId);
        return feishuRemoteMcpService.callTool("fetch-doc", args);
    }

    @Tool(name = "feishu_mcp_search_doc",
            description = """
                    通过飞书官方远程 MCP 搜索云文档。Bear 统一接入飞书 MCP，但搜索范围仍由飞书账号权限决定。""")
    public Map<String, Object> searchFeishuDoc(
            @ToolParam(description = "搜索关键词，如项目名、文档标题或内容关键词", required = true)
            String keyword
    ) {
        if (!StringUtils.hasText(keyword)) {
            return failure("keyword 不能为空");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keyword", keyword.trim());
        return feishuRemoteMcpService.callTool("search-doc", args);
    }

    @Tool(name = "feishu_mcp_create_doc",
            description = """
                    通过飞书官方远程 MCP 创建云文档。常用参数是 title 和 markdown；如果飞书工具 schema 变化，可改用 feishu_mcp_call_tool 传完整 arguments_json。""")
    public Map<String, Object> createFeishuDoc(
            @ToolParam(description = "文档标题", required = true)
            String title,
            @ToolParam(description = "Markdown 文档正文内容，支持飞书扩展语法；超长内容可后续用 update-doc 追加", required = false)
            String markdown
    ) {
        if (!StringUtils.hasText(title)) {
            return failure("title 不能为空");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("title", title.trim());
        if (StringUtils.hasText(markdown)) {
            args.put("markdown", markdown);
        }
        return feishuRemoteMcpService.callTool("create-doc", args);
    }

    @Tool(name = "feishu_mcp_add_comment",
            description = "通过飞书官方远程 MCP 给云文档添加全文评论。参数二选一：doc_id 或 doc_url。")
    public Map<String, Object> addFeishuDocComment(
            @ToolParam(description = "飞书云文档 token；与 doc_url 二选一", required = false)
            String doc_id,
            @ToolParam(description = "飞书文档完整 URL；与 doc_id 二选一", required = false)
            String doc_url,
            @ToolParam(description = "要添加的评论文本", required = true)
            String comment
    ) {
        String docId = resolveDocId(doc_id, doc_url);
        if (!StringUtils.hasText(docId)) {
            return failure("请提供 doc_id 或可解析的 doc_url");
        }
        if (!StringUtils.hasText(comment)) {
            return failure("comment 不能为空");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("doc_id", docId);
        args.put("elements", List.of(Map.of("type", "text", "text", comment)));
        return feishuRemoteMcpService.callTool("add-comments", args);
    }

    private static String resolveDocId(String docId, String docUrl) {
        if (StringUtils.hasText(docId)) {
            return docId.trim();
        }
        if (!StringUtils.hasText(docUrl)) {
            return null;
        }
        Matcher matcher = DOC_PATH.matcher(docUrl.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Map<String, Object> failure(String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", false);
        out.put("message", message);
        return out;
    }
}
