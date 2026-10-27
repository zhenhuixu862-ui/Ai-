# Bear MCP Single

基于 Spring Boot、Spring AI 和 Groovy 的 MCP Server 学习与演示项目。它把内置方法和动态脚本封装为 AI 客户端可发现、可调用的工具，并提供 Token、角色权限、调用审计和 Vue 管理界面。

项目展示的是 **AI Agent 的工具服务端**。模型选择工具、组织对话等行为由接入的 AI 客户端完成；本项目负责工具接入、执行和管理。

## 可以展示什么

| 能力 | 实现内容 |
| --- | --- |
| MCP 接入 | Streamable HTTP 端点 `/mcp`，工具发现与调用 |
| 内置工具 | 问候、时间、计算器等 Java 工具 |
| 动态工具 | 数据库存储参数 Schema 和 Groovy 脚本，运行时执行 |
| 访问控制 | Bearer Token 鉴权、角色能力授权、每把 Token 的能力选择 |
| 调用记录 | 保存工具、Prompt、Resource 调用的结果摘要、状态和耗时 |
| 外部系统接入 | HTTP 请求配置、动态脚本 `runRequest` 白名单，以及外部数据源查询与 `runSql` 白名单 |
| 管理与创作界面 | 用户、角色、Token、动态工具、API、Prompt、Resource 和 Skill 管理 |

仓库自带的 `echo_dynamic` 是无需外部 API 的回显示例。资源文件与 Skill 的上传、读取需要额外配置 OSS；飞书、SLS 日志和源码检索属于可选扩展。

## 技术与目录

| 部分 | 版本或依赖 |
| --- | --- |
| Java | JDK 17 |
| 后端 | Spring Boot 3.3.0、Spring AI 1.1.0 |
| 持久化 | MyBatis Starter 3.0.3、MySQL |
| 动态脚本 | Groovy 4.0.18 |
| 前端 | Vue 3.5、Ant Design Vue 4、Vite 6 |
| 构建 | Maven、npm；项目没有 Maven Wrapper，需要本机安装 Maven |

前端锁文件中的 Vite 版本为 6.4.3，Node 版本要求为 `^18.0.0 || ^20.0.0 || >=22.0.0`，可使用 Node 22。

```text
.
├── pom.xml
├── src/main/java/com/bear/mcp/single/
│   ├── gateway/          # MCP 请求鉴权和协议过滤
│   ├── core/             # 工具、脚本、权限、数据访问和审计
│   ├── admin/            # 管理接口
│   └── share/            # 创作与能力分享接口
├── src/main/resources/   # 环境变量配置、MyBatis 映射
├── src/test/             # 已有单元测试和学习示例
├── admin-web/            # Vue 管理前端
└── docs/
    ├── mysql-init.sql    # 新建演示库的表结构与示例数据
    └── DEMO.md           # 演示步骤与录屏脚本
```

## 本地启动（Windows PowerShell）

先安装 JDK 17、Maven、MySQL 和 Node.js/npm。基础演示不需要 Redis、RocketMQ、模型 API Key、OSS 或飞书凭证；使用 AI 客户端时，客户端的模型配置由客户端自行提供。

### 1. 初始化一个独立演示数据库

在项目根目录打开 PowerShell，连接本机 MySQL：

```powershell
mysql -u root -p
```

在 MySQL 提示符中执行：

```sql
CREATE DATABASE bear_mcp_demo DEFAULT CHARACTER SET utf8mb4;
USE bear_mcp_demo;
SELECT DATABASE();
SOURCE docs/mysql-init.sql;
quit;
```

**`mysql-init.sql` 开头包含 `DROP TABLE`，会删除当前选中数据库内同名的 MCP 表。** 它不包含 `CREATE DATABASE` 或 `USE`，必须先确认选中的是新建的 `bear_mcp_demo`，不要导入原项目或业务数据库。若该数据库已存在，请改用另一个新的演示库名。脚本使用相对于启动 MySQL 客户端时所在目录的路径；路径找不到时，改为 SQL 文件的完整路径。

新安装只需这一个初始化脚本。服务配置为 `spring.sql.init.mode=never`，启动时不会自动初始化或重建数据库。

### 2. 配置环境变量并启动后端

仍在项目根目录的 PowerShell 中执行，先替换两个占位值：

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/bear_mcp_demo?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '替换为自己的本地MySQL密码'
$env:BEAR_ADMIN_JWT_SECRET = '替换为自己生成并妥善保存的至少32字符随机密钥'
mvn spring-boot:run
```

`BEAR_ADMIN_JWT_SECRET` 必须设置，并在后续启动中保持一致；它也参与管理端保存的 Token 等数据的加密。环境变量应保留在本地，不能提交真实值。程序不会自动加载 `.env` 文件；上面的变量只对当前 PowerShell 及其启动的进程生效。

默认后端端口为 `8090`。配置默认库名是 `bear_mcp`，因此使用上面的演示库时必须设置 `DB_URL`。如更改 `SERVER_PORT`，同时调整 `admin-web/vite.config.js` 的后端代理和客户端地址。

### 3. 启动前端

另开一个 PowerShell，在项目根目录执行：

```powershell
Set-Location admin-web
npm ci
npm run dev
```

打开 Vite 输出的本地地址，默认是 `http://localhost:5173`。前端 `/api` 请求代理到 `http://localhost:8090`。

初始化 SQL 明确提供了下面的公开演示凭据：

| 用途 | 演示值 |
| --- | --- |
| 管理员用户名 | `demo-admin` |
| 管理员密码 | `admin123` |
| MCP Bearer Token | `mcp_dev_token` |

这些值只用于独立的本地演示库，不是个人或生产凭据。对外部署前应替换演示账号与 Token。管理端登录 JWT 与 MCP Token 是两种不同凭据；MCP 鉴权使用数据库中的 Token 记录。

### 4. 连接 MCP 客户端

在支持 **Streamable HTTP 和自定义请求头** 的 MCP 客户端中添加：

| 配置项 | 值 |
| --- | --- |
| 服务名称 | `bear-mcp-demo` |
| URL | `http://localhost:8090/mcp` |
| 请求头 | `Authorization: Bearer mcp_dev_token` |

对于使用 `mcpServers` / `url` / `headers` 配置格式的客户端，可参考：

```json
{
  "mcpServers": {
    "bear-mcp-demo": {
      "url": "http://localhost:8090/mcp",
      "headers": {
        "Authorization": "Bearer mcp_dev_token"
      }
    }
  }
}
```

具体配置文件位置和传输类型字段以所用客户端为准。客户端应完成 MCP 初始化、会话维护和工具发现；`/mcp` 是协议接口，不是浏览器中的管理页面。

连接后先调用 `hello`，再调用 `echo_dynamic`，最后到管理端查看审计记录。完整的输入和讲解顺序见 [演示指南](docs/DEMO.md)。

## 可选扩展

| 扩展 | 需要补充的配置 |
| --- | --- |
| Resource / Skill 文件存储 | `ALIYUN_OSS_ENDPOINT`、`ALIYUN_OSS_BUCKET_NAME`、`ALIYUN_OSS_ACCESS_KEY_ID`、`ALIYUN_OSS_ACCESS_KEY_SECRET` |
| 飞书远程 MCP | `FEISHU_APP_ID`、`FEISHU_APP_SECRET` 和相应权限 |
| 阿里云 SLS 日志查询 | `ALIYUN_SLS_ENDPOINT`、`ALIYUN_SLS_PROJECT`、`ALIYUN_SLS_LOGSTORE`、`ALIBABA_CLOUD_ACCESS_KEY_ID`、`ALIBABA_CLOUD_ACCESS_KEY_SECRET` |
| 外部数据库 | 在管理端添加自己的演示数据源，配置只读账号并授权所需工具 |
| 源码检索 | 配置 `YANQUE_SOURCE_ROOT` 指向可公开的本地示例仓库，并安装对应检索命令；默认 `./demo-repository` 不随仓库提供 |

上述扩展需要各自的账号、权限或外部资源，未配置时不应作为基础演示步骤。

## 验证与限制

本次作品整理未执行完整客户端到服务端的端到端联调，也未验证外部集成。仓库不包含已经录制的演示视频或在线部署地址；`docs/DEMO.md` 提供的是待实际运行和录制的步骤。

项目保留现有学习代码和测试，可在项目根目录执行 `mvn test`，在 `admin-web` 执行 `npm run build`。构建或单元测试通过也不代表外部服务已经联通。

Groovy 使用代码检查、AST 限制和执行等待超时，不能视为运行不可信代码的强隔离沙箱。HTTP 配置的限流为单进程内存实现。当前请求执行器只支持 HTTP，SOA、Hessian 等字段是扩展预留。

本项目用于学习和作品展示，个人贡献应按实际开发内容与提交记录说明，不包含未经验证的性能或生产稳定性承诺。公开前应确认有权公开所含源码，并保留原有来源与许可说明；本次整理没有为原工程新增开源许可证。
