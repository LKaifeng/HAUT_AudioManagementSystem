# HAUT 音频管理系统


<div align="center">

  

基于 Spring Boot 3 + MyBatis-Plus + JWT 的现代化音频资产管理系统

  

</div>

## 功能特性

  

### 用户认证与权限管理

  

- 已实现 JWT Token 身份验证

- 已实现 基于角色的访问控制（RBAC）

    - **管理员** (roleLevel=0): 完整权限（上传、播放、编辑、删除、用户管理）

    - **操作员** (roleLevel=1): 基础权限（上传、播放）

- 已实现 用户注册、登录、增删改查

  

### 音频资产管理

  

- 已实现 音频文件上传（支持 MP3/WAV 格式，最大 10MB）

- 已实现 拖拽上传与实时进度条显示

- 已实现 音频列表分页查询（按创建时间降序）

- 已实现 音频流式播放（支持断点续传/拖动定位）

- 已实现 音频名称编辑（仅管理员）

- 已实现 音频删除（物理+逻辑双重删除，仅管理员）

- 已实现 无效记录自动清理

  

### 现代化前端界面

  

- 已实现 响应式侧边栏导航布局

- 已实现 渐变背景与卡片式设计

- 已实现 三大功能板块：音频乐库、上传音频、用户管理

- 已实现 空状态提示与错误处理

- 已实现 文件大小格式化显示

  

---

  

## 技术栈

  

### 后端

  

- **框架**: Spring Boot 3.2.5

- **ORM**: MyBatis-Plus 3.5.7

- **数据库**: MySQL 8.0+

- **安全**: JWT (jjwt 0.11.5)

- **工具**: Lombok, Jakarta EE

  

### 前端

  

- **核心技术**: HTML5 + CSS3 + 原生 JavaScript

- **UI设计**: 自定义 CSS（渐变、动画、Flexbox/Grid）

- **交互**: Fetch API, XMLHttpRequest（上传进度）

- **存储**: LocalStorage（Token 管理）

  

---

  

## 系统架构

  

```

┌─────────────────────────────────────────────┐

│              前端层 (index.html)             │

│  ┌──────────┬──────────┬──────────────────┐ │

│  │ 音频乐库 │ 上传音频 │   用户管理       │ │

│  └──────────└──────────└──────────────────┘ │

└──────────────────┬──────────────────────────┘

                   │ HTTP + JWT Token

┌──────────────────▼──────────────────────────┐

│          后端层 (Spring Boot)               │

│  ┌──────────────────────────────────────┐   │

│  │   JwtInterceptor (身份验证拦截器)    │   │

│  └──────────────────┬───────────────────┘   │

│                     │                        │

│  ┌──────────────────▼───────────────────┐   │

│  │   Controller 层 (路由分发)           │   │

│  │  • AuthController  • AudioController │   │

│  └──────────────────┬───────────────────┘   │

│                     │                        │

│  ┌──────────────────▼───────────────────┐   │

│  │   Service 层 (业务逻辑)              │   │

│  │  • AudioService (文件IO + 事务)     │   │

│  └──────────────────┬───────────────────┘   │

│                     │                        │

│  ┌──────────────────▼───────────────────┐   │

│  │   Mapper 层 (数据持久化)             │   │

│  │  • AudioAssetMapper • SysUserMapper  │   │

│  └──────────────────┬───────────────────┘   │

└─────────────────────┼───────────────────────┘

                      │

┌─────────────────────▼───────────────────────┐

│         数据存储层                           │

│  ┌──────────────┐  ┌─────────────────────┐  │

│  │   MySQL      │  │  文件系统           │  │

│  │  Database    │  │  存储路径           │  │

│  └──────────────┘  └─────────────────────┘  │

└─────────────────────────────────────────────┘

```

  

---

  

## 快速开始

  

### 环境要求

  

在开始之前，请确保您的开发环境已安装以下软件：

  

|软件|版本要求|下载地址|

|---|---|---|

|JDK|17+|[Oracle JDK](https://www.oracle.com/java/technologies/downloads/)|

|Maven|3.6+|[Apache Maven](https://maven.apache.org/download.cgi)|

|MySQL|8.0+|[MySQL Community](https://dev.mysql.com/downloads/mysql/)|

|Git|2.30+|[Git SCM](https://git-scm.com/downloads)|

|IDE|IntelliJ IDEA / VS Code|推荐 IntelliJ IDEA Ultimate|

  

### 数据库配置

  

#### 1. 创建数据库

  

执行以下 SQL：

  

```

-- 创建数据库

CREATE DATABASE IF NOT EXISTS audio_db

    DEFAULT CHARACTER SET utf8mb4

    COLLATE utf8mb4_unicode_ci;

```

  

#### 2. 初始化表结构

  

执行项目根目录下的 `src/main/resources/schema.sql` 文件。

  

### 应用配置

  

打开 `src/main/resources/application.yml`，修改以下配置：

  

```

spring:

  datasource:

    url: jdbc:mysql://localhost:3306/audio_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai

    username: root          # 修改为您的 MySQL 用户名

    password: password      # 修改为您的 MySQL 密码

  

app:

  storage:

    path: D:/audio_files/  # 修改为您的存储路径，确保目录存在且有读写权限

  jwt:

    secret: YOUR_VERY_LONG_SECRET_KEY_FOR_JWT_SECURITY_DO_NOT_SHARE  # 修改为更复杂的密钥

```

  

### 启动项目

  

```

# 1. 编译并运行

mvn spring-boot:run

  

# 2. 或者打包后运行

mvn clean package -DskipTests

java -jar target/HAUT_AudioManagementSystem-0.0.1-SNAPSHOT.jar

```

  

---

  

## 项目结构

  

```

HAUT_AudioManagementSystem/

├── src/

│   ├── main/

│   │   ├── java/com/example/haut_audiomanagementsystem/ # Java 源码

│   │   │   ├── config/              # 配置类 (MyBatis, Web拦截器)

│   │   │   ├── controller/          # 控制器层

│   │   │   ├── entity/              # 实体类

│   │   │   ├── interceptor/         # JWT 拦截器

│   │   │   ├── mapper/              # 数据访问层

│   │   │   ├── service/             # 业务逻辑层

│   │   │   └── util/                # 工具类

│   │   └── resources/

│   │       ├── static/              # 前端页面

│   │       ├── application.yml      # 配置文件

│   │       └── schema.sql           # 数据库初始化

├── docx/                            # 项目文档记录

├── pom.xml                          # Maven 依赖

└── .gitignore

```

  

---

  

## 开发指南

  

### 1. Commit 提交规范

  

项目提倡采用 [Conventional Commits](https://www.conventionalcommits.org/) 规范， Commit 信息的标准格式如下：

  

```

<type>(<scope>): <subject>

  

<body>

  

<footer>

```

  

- **常见标识（type）**：

    - `feat`：引入新功能

    - `fix`：修复 Bug

    - `docs`：仅文档修改

    - `style`：不影响代码含义的格式变动（空格、格式化、缺少分号等）

    - `refactor`：既不修复 Bug 也不添加新功能的代码修改

    - `perf`：提高性能的代码修改

    - `test`：添加缺失的测试或修改现有的测试

    - `chore`：影响构建系统或外部依赖项的更改

- **Commit 示例**：

    ```

    feat(audio): 添加音频名称修改功能

    - 新增 PUT /api/audio/{id} 接口

    - 仅允许管理员身份调用

    - 添加参数校验逻辑

    Closes #15

    ```

  

### 2. Java 代码规范

  

- **命名规范**：

    - 类名：使用单字首字母大写命名法（PascalCase，如 `AudioController`）

    - 方法名与变量名：使用驼峰命名法（camelCase，如 `uploadAudio`）

    - 常量名：全大写并以下划线分隔（UPPER_SNAKE_CASE，如 `MAX_FILE_SIZE`）

    - 包名：统一为小写（如 `com.example.haut_audiomanagementsystem`）

- **异常处理**：

    - 禁止直接捕获顶层通用 `Exception`，须优先捕获具体业务异常类

    - 捕获的异常信息必须以合理级别记录系统日志，禁止静默吞调

  

---

  

## 合并分支流程
### 1. 新建并切换分支

  

开始开发新功能前，先拉取主分支最新代码，然后创建独立分支：

  

```

# 切换到主分支并更新

git checkout main

git pull origin main

  

# 基于主分支创建并切换到新分支

git checkout -b <分支名>

```

  

_命名规则示例：`feature/login` 或 `fix/upload-bug`_

  

---

  

### 2. 开发与提交

  

在本地开发代码并分批提交：

  

```

# 查看修改状态

git status

  

# 暂存并提交代码

git add .

git commit -m "feat: 完成登录接口"

```

  

---

  

### 3. 同步主分支

  

在推送代码前，建议先同步远程主分支，并在本地提前合并冲突：

  

```

# 1. 切换回主分支并拉取最新更改

git checkout main

git pull origin main

  

# 2. 切换回特性分支并将其合并

git checkout <分支名>

git merge main

```

  

_注意：若提示冲突，只需手动修改冲突代码，而后执行 `git add .` 与 `git commit`。_

  

---

  

### 4. 推送分支

  

将代码推送至远程仓库：

  

```

# 首次推送需关联远程分支

git push -u origin <分支名>

```

  

---

  

### 5. 合并分支

  

1. 在代码平台（GitHub / Gitee）上发起 **Pull Request (PR)**。

2. 指派团队成员进行代码审查。

3. 审查通过后，在网页端点击 **Merge** 合并。

4. 合并完成后，按提示删除远程特性分支。
  