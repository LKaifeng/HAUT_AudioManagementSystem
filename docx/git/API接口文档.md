# HAUT 音频管理系统技术文档

本项目是一个基于前后端分离架构的音频资产管理系统。后端使用 **Spring Boot + MyBatis Plus + JWT**，前端由单一的 **HTML5 + 原生 JavaScript** 构成。

---

## 一、 系统架构与包结构说明

后端源码包（Package）按照职责进行了合理的分层开发，具体结构及职责如下：

```
com.example.haut_audiomanagementsystem
├── config                  # 配置包
│   ├── MybatisPlusConfig.java  # MyBatis-Plus 插件配置（如分页插件）
│   └── WebConfig.java          # Spring MVC 的拦截器与跨域配置
├── controller              # 路由控制包（接口定义）
│   ├── AudioController.java    # 音频资源操作相关 API
│   └── AuthController.java     # 用户安全认证与管理 API
├── entity                  # 实体包
│   ├── AudioAsset.java         # 音频资产数据库映射实体
│   └── SysUser.java            # 系统用户数据库映射实体
├── interceptor             # 拦截器包（切面校验）
│   └── JwtInterceptor.java     # JWT Token 身份认证拦截器
├── mapper                  # 数据交互层(DAO)
│   ├── AudioAssetMapper.java   # 音频表持久化接口
│   └── SysUserMapper.java      # 用户表持久化接口
├── service                 # 核心业务逻辑包
│   └── AudioService.java       # 音频业务实现类（包含文件物理管理）
└── util                    # 工具包
    └── JwtUtil.java            # JWT 生成及解析工具类
```

---

## 二、 后端 API 路由及作用详解

系统路由可分为两大模块：**用户安全与管理 (Auth)** 和 **音频文件管理 (Audio)**。

### 1. 用户与认证路由 (`AuthController.java`)

基础路径：`/api/auth`

|路由地址|请求方式|身份鉴权|详细功能说明|操作逻辑|
|---|---|---|---|---|
|`/login`|`POST`|开放|用户登录验证|比对数据库用户名密码，若成功则通过 `JwtUtil` 生成载有用户名和角色等信息的 Token 返回给前端。|
|`/register`|`POST`|开放|用户自主注册|初次注册用户默认分配角色等级为 `1`（普通操作员）。|
|`/users`|`GET`|管理员|获取所有用户列表|主要是给管理员后台做用户展示。非管理员（角色等级为 0）访问将返回 `403`。|
|`/users`|`POST`|管理员|添加新系统用户|管理员在后台手动为系统新增管理员 (0) 或操作员 (1)。|
|`/users/{id}`|`PUT`|管理员|修改用户信息|允许重置指定 ID 用户的密码，或者调整其 `roleLevel` 角色等级。|
|`/users/{id}`|`DELETE`|管理员|删除特定系统用户|从系统移除该用户。|

---

### 2. 音频业务路由 (`AudioController.java`)

基础路径：`/api/audio`

|路由地址|请求方式|身份鉴权|详细功能说明|操作逻辑|
|---|---|---|---|---|
|`/upload`|`POST`|管理员 / 操作员|音频文件上传|权限拦截器过滤后，如果是管理员 (0) 或操作员 (1) 方能上传，后端随机化生成 UUID 并做物理保存。|
|`/list`|`GET`|所有登录用户|资产列表分页查询|需要 Token，调用 `AudioService` 后返回状态为 1 的音频。|
|`/{id}`|`PUT`|管理员|重命名音频资产|仅限管理员操作，修改其在页面所展示的物理友好文件名。|
|`/{id}`|`DELETE`|管理员|彻底删除音频|仅限管理员操作，数据库逻辑删除，硬盘执行物理文件破坏性删除。|
|`/cleanup`|`POST`|管理员|僵尸记录清理|清理数据库中状态激活但物理实体已被意外移除的音频链路。|
|`/stream/{id}`|`GET`|开放 (免拦截)|播放获取音频流|将物理硬盘上的流写回到 Response Body。此接口已在客户端防护，为了方便 HTML `<audio>` 标签直引而排除了拦截。|
|`/check/{id}`|`GET`|所有登录用户|文件状态健康检查|查看文件对应的磁盘大小及验证物理形态是否存在。|

---

## 三、 后端核心功能的代码级实现解析

### 1. 统一 JWT 过滤保护机制

- **拦截应用 (`WebConfig.java`)**：把 `JwtInterceptor` 注册到 Spring 拦截链中。匹配所有以 `/api/` 开头的接口。但**排除**了登录接口 `/api/auth/login` 与流获取接口 `/api/audio/stream/**`。
- **Token 解析校验 (`JwtInterceptor.java`)**： 从头部的 `Authorization` 中获取 `Bearer`  后的秘钥密文：
    
    ```
    Claims claims = jwtUtil.parseToken(token.substring(7));
    request.setAttribute("claims", claims);
    request.setAttribute("roleLevel", claims.get("roleLevel", Integer.class));
    ```
    
    并将其保存在 Request 上下文中方便下游 controller 直接使用。

### 2. 上传与文件安全控制 (`AudioService.java`)

每次接收到文件时，进行如下核心转换：

- **防覆盖转换**：原生文件名转 UUID 名存储：
    
    ```
    String extension = originalName.substring(originalName.lastIndexOf("."));
    String uuidName = UUID.randomUUID().toString() + extension;
    Path targetPath = Paths.get(storagePath, uuidName).normalize();
    ```
    
- **文件完整性提取**：在物理保存后，立即使用摘要算法获取其 MD5，防止同文件重复上传和便于一致性比对。
    
    ```
    String md5 = calculateMD5FromFile(targetPath.toFile());
    ```
    

### 3. 音频流的流式传输 (`AudioController.java`)

使用 HTML5 播放需要让前端能够进行拖拽进度等定位操作。

```
Resource resource = new FileSystemResource(file);
return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(getContentType(file.getName())))
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
        .header(HttpHeaders.ACCEPT_RANGES, "bytes") // 声明支持断点续传/按范围读取
        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()))
        .body(resource);
```

---

## 四、 前端系统的页面呈现与技术细节 (`index.html`)

前端为了轻量，整个 UI 全塞在一个 HTML 文件内，是极简实用的 SPA (单页面应用) 交互代表：

### 1. 单页面架构切换控制

界面没有真正的页面跳转。完全依靠给板块层级赋予 `active` / `hidden` CSS 类，同时切换 DOM 的显示形态：

```
function switchSection(sectionName) {
    document.querySelectorAll('.section-content').forEach(el => el.classList.remove('active'));
    document.getElementById(`section-${sectionName}`).classList.add('active');
}
```

### 2. 用户登录状态同步

登录成功后将关键身份指纹存入 LocalStorage，用于后续的 API 鉴权：

```
localStorage.setItem('token', data.token);
localStorage.setItem('roleLevel', data.roleLevel);
// 在发起请求时统一携带 Token
function getHeaders() {
    return { 'Authorization': 'Bearer ' + localStorage.getItem('token') };
}
```

### 3. XMLHttpReuquest 高级上传控制（带进度条）

为了向用户表现真实的上传进度，前端在“上传板块”放弃了 `fetch` 方式，采用了功能更加多态的 `XMLHttpRequest` 接口去实时计算数据分发比例：

```
const xhr = new XMLHttpRequest();
xhr.upload.addEventListener('progress', (e) => {
    if (e.lengthComputable) {
        const percentComplete = Math.round((e.loaded / e.total) * 100);
        progressBar.style.width = percentComplete + '%'; // 渲染动态伸展的进度条 UI
        progressText.textContent = `上传进度: ${percentComplete}%`;
    }
});
```

### 4. 基于角色的权限动态视图生成

渲染资产列表或展示“用户管理”时，系统将通过检查当前登录用户的 `currentRole` 角色级别，动态判断并展示/隐藏不同的控制操作控件：

```
// 在构建列表数据字符串拼接时：
let actionBtns = '';
if (currentRole === 0) { // 只有管理员（0）渲染编辑与删除按钮
    actionBtns = `
        <button class="btn-edit" onclick="startEdit('${itemId}', ...)">编辑</button>
        <button class="btn-delete" onclick="deleteAudio('${itemId}')">删除</button>
    `;
}
```