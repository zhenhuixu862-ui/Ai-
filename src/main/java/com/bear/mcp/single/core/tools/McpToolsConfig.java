package com.bear.mcp.single.core.tools;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolsConfig {

    /**
     * 注册内置 MCP Tools。
     *
     * <p>Spring AI 会扫描传入对象上的 @Tool 方法，并把它们暴露给 MCP Server。
     * 这里注册的是代码内置工具；数据库里的动态工具由 DynamicToolService 额外注入到 tools/list。</p>
     */
    @Bean
    public ToolCallbackProvider builtinTools(McpMetaTools mcpMetaTools, SystemTools systemTools,
                                             CalculatorTools calculatorTools,
                                             RequestConfigTools requestConfigTools,
                                             DynamicStudioTools dynamicStudioTools,
                                             ResourceStudioTools resourceStudioTools,
                                             PromptStudioTools promptStudioTools,
                                             PromptRuntimeTools promptRuntimeTools,
                                             SkillStudioTools skillStudioTools,
                                             DataSourceTools dataSourceTools,
                                             FeishuRemoteMcpTools feishuRemoteMcpTools,
                                             GetSkillTool getSkillTool,
                                             YanqueLogTools yanqueLogTools,
                                             RepositoryCommandTools repositoryCommandTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpMetaTools, systemTools, calculatorTools, requestConfigTools, dynamicStudioTools,
                        resourceStudioTools, promptStudioTools, promptRuntimeTools, skillStudioTools, dataSourceTools,
                        feishuRemoteMcpTools, getSkillTool, yanqueLogTools, repositoryCommandTools)
                .build();
    }
}
