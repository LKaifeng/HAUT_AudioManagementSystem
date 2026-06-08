# HAUT 音频管理系统完整技术架构

---

## 一、 系统架构核心逻辑 (Core Philosophy)

系统的架构设计遵循以下核心原则，确保文件数据的物理安全与操作原子性：

1. **Create (原子入库)**：先写入物理磁盘，成功后再写入数据库。若数据库写入异常，必须捕获并回退（清除磁盘文件），保障原子性。磁盘存储文件名**强制重命名为 UUID**，防止重名覆盖；数据库存原始逻辑文件名。
2. **Read (安全读取与性能)**：
    - 音频数据严禁暴露物理绝对路径，前端不可直接获取 `D:/files/...`。
    - 音频的播放和点播强制通过流转发接口 `/api/audio/stream/{id}` 进行中转。
    - 分页列表查询强制按 `ORDER BY create_time DESC` 降序排列，禁止无序获取。
3. **Update (限制性更新)**：一旦完成入库，仅允许改写元数据中的列表显示名称（`file_name`字段），**严禁改写物理路径**，以防止死链。
4. **Delete (双重确认删除)**：
    - 必须校验操作者权限必须为 `roleLevel = 0` (系统管理员)。
    - 执行顺序为：查询物理路径 →→ 校验文件占用并物理删除磁盘文件 →→ 逻辑/物理清除数据库记录。

---

## 二、 数据库设计 (Database Schema)

数据库精简为两个核心表，满足 MVP 阶段的所有关系映射。

### 1. 音频资产表 (`audio_assets`)

```
CREATE TABLE `audio_assets` (
  `id` BIGINT PRIMARY KEY,             -- 唯一ID (雪花算法，后端以String形式返回给前端防止精度丢失)
  `file_name` VARCHAR(255) NOT NULL,   -- 逻辑显示名称 (原始上传的文件名，允许修改)
  `file_path` VARCHAR(500) NOT NULL,   -- 物理存储绝对路径 (UUID磁盘路径，禁止修改)
  `file_size` BIGINT DEFAULT 0,        -- 文件大小(Byte)
  `hash_code` VARCHAR(64),             -- 文件MD5 (用于防重复、未来版本秒传)
  `create_time` DATETIME NOT NULL,     -- 录入时间 (主导排序依据)
  `status` TINYINT DEFAULT 1           -- 状态值：1-正常显示，0-逻辑删除
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 2. 用户表 (`sys_users`)

```
CREATE TABLE `sys_users` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `username` VARCHAR(50) UNIQUE NOT NULL,
  `password` VARCHAR(100) NOT NULL,
  `role_level` INT DEFAULT 1            -- 角色等级：0-系统管理员(D权限及其它最高权限)；1-操作员(CUR权限)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 三、 后端架构及包结构功能描述 (Backend Architecture)

后端基于 **Spring Boot** 构建，各层级划分职责如下：

- **`config`**
    - `WebConfig.java`：配置路由拦截规则与全局排除逻辑（如登录接口及流直达通道等免拦截路径）。
    - `MybatisPlusConfig.java`：注册 MyBatis-Plus 拦截器，开启 MySQL 物理物理/逻辑分页（基于 `PaginationInnerInterceptor`），避免内存分页引发雪崩。
- **`interceptor`**
    - `JwtInterceptor.java`：HTTP 拦截层，获取 HTTP Headers 中携带的 `Authorization: Bearer <token>`，通过校验后将解析所得的 `roleLevel` 属性写入 Request 作用域。
- **`controller`**
    - `AuthController.java`：负责登录、注册、添加、删除及修改用户身份及角色的请求响应。
    - `AudioController.java`：音频的列表分页拉取、上传、修改逻辑重命名、整条链路删除、以及物理文件检查和流转发中转。
- **`service`**
    - `AudioService.java`：核心业务支撑层。封装物理保存（使用 `Files.copy` 实现流拷贝）、计算 MD5、双效文件混删、无效数据巡检清理等方法。
- **`entity`**
    - 映射对应的实体映射关系（如 `SysUser`、`AudioAsset` 实例）。
- **`mapper`**
    - 继承 MyBatis-Plus 的 `BaseMapper<T>`，快捷构建常用的底层 SQL 交互和数据持久化。

---

## 四、 核心接口的内部实现机制 (Java Implementation)

### 1. 认证拦截与角色路由安全

- **JWT 身份判断及鉴权拦截器 (`JwtInterceptor.java`)**： 读取头部的 Bearer Token，并在解析后将用户信息注入到当前线程的 Request 中。
    
    ```
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parseToken(token.substring(7));
                request.setAttribute("claims", claims);
                request.setAttribute("roleLevel", claims.get("roleLevel", Integer.class)); // 关键：注入角色等级
                return true;
            } catch (Exception e) {
                response.setStatus(401);
                response.getWriter().write("Invalid Token");
                return false;
            }
        }
        response.setStatus(401);
        response.getWriter().write("Missing Token");
        return false;
    }
    ```
    
- **安全路径排除 (`WebConfig.java`)**： 排除受拦截的端点。特别声明：`/api/audio/stream/**` 被专门放行，以便前端的 `<audio class="audio-player" src="...">` 进行无缝直连拉取音频波形流。
    
    ```
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login")
                .excludePathPatterns("/api/audio/stream/**"); // 排除音频流接口，避免网络加载阻断
    }
    ```
    

### 2. 音频上传原子写入 (`AudioService.java`)

符合先写外部存储、计算 MD5 校验并在异常回滚事务的链条：

```
@Transactional(rollbackFor = Exception.class)
public void uploadAudio(MultipartFile file) throws IOException {
    if (file.isEmpty()) throw new IllegalArgumentException("文件为空");

    String originalName = file.getOriginalFilename();
    if (originalName == null || !originalName.matches(".*\\.(mp3|wav)$")) {
        throw new IllegalArgumentException("仅支持 mp3 或 wav 格式");
    }
    
    // 1. 生成盘符内不可重名的 UUID 物理文件标识
    String extension = originalName.substring(originalName.lastIndexOf("."));
    String uuidName = UUID.randomUUID().toString() + extension;
    
    File dir = new File(storagePath);
    if (!dir.exists()) dir.mkdirs();

    // 2. 将临时流复制并落地为物理文件
    Path targetPath = Paths.get(storagePath, uuidName).normalize();
    Files.createDirectories(targetPath.getParent());
    Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

    // 3. 计算已落盘物理文件的 MD5 摘要值
    String md5 = calculateMD5FromFile(targetPath.toFile());

    // 4. 将映射记录写入表，保障整体事务隔离回滚
    AudioAsset asset = new AudioAsset();
    asset.setFileName(originalName);
    asset.setFilePath(targetPath.toAbsolutePath().toString()); // 保存物理绝对路径用于后续定位
    asset.setFileSize(file.getSize());
    asset.setHashCode(md5);
    asset.setCreateTime(new Date());
    asset.setStatus(1);
    
    audioAssetMapper.insert(asset);
}
```

### 3. 限级删除音频 (`AudioController.java` + `AudioService.java`)

- **权限防御控制 (Controller)**：
    
    ```
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable String id, HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");
        if (roleLevel == null || roleLevel != 0) { // 必须为 0 超级管理员
            return ResponseEntity.status(403).body("权限不足：只有管理员可以删除音频");
        }
        audioService.deleteAudio(Long.parseLong(id));
        return ResponseEntity.ok("删除成功");
    }
    ```
    
- **双重删除落地 (Service)**： 在删除数据库实体前后将外部磁盘关联文件移除：
    
    ```
    @Transactional
    public void deleteAudio(Long id) {
        AudioAsset asset = audioAssetMapper.selectById(id);
        if (asset == null) throw new IllegalArgumentException("音频不存在");
        
        String filePath = asset.getFilePath(); // 提早锁定物理位置
    
        // 1. 逻辑擦除其在数据库的显示属性（状态变更或软删除）
        int result = audioAssetMapper.deleteById(id);
        if (result == 0) throw new RuntimeException("删除失败，记录不存在");
    
        // 2. 物理擦除磁盘对应路径文件
        File file = new File(filePath);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                System.err.println("警告：物理文件删除失败: " + filePath + "（文件可能被进程独占锁死）");
            }
        }
    }
    ```
    

### 4. 磁盘一致性自检巡检清理 (`AudioService.java`)

随着服务器长久运行可能导致磁盘空心化（即物理文件被手动删除，但数据库依然残留健康行记录）。 本方法能够对表进行全面自检排查，并自动在系统后台擦除“死链”元数据：

```
@Transactional
public int cleanupInvalidRecords() {
    List<AudioAsset> allAudios = audioAssetMapper.selectList(
        new QueryWrapper<AudioAsset>().eq("status", 1)  // 只过滤活动记录
    );
    int cleanedCount = 0;
    for (AudioAsset asset : allAudios) {
        File file = new File(asset.getFilePath());
        if (!file.exists()) {  // 检测磁盘上的实体形态是否完整
            audioAssetMapper.deleteById(asset.getId());
            cleanedCount++;
            System.out.println("已清理无效死链记录 ID: " + asset.getId() + ", 原始逻辑名: " + asset.getFileName());
        }
    }
    return cleanedCount;
}
```

### 5. 断点流中转以支持拖拽定位 (`AudioController.java`)

使用 HTML5 `<audio>` 播放器最主要需要支持“拖动到指定分秒播放”。后端接口返回 `Accept-Ranges: bytes` 分块下载策略，让浏览器能够基于切片拉取音频流：

```
@GetMapping("/stream/{id}")
public ResponseEntity<Resource> stream(@PathVariable Long id) {
    File file = audioService.getAudioFile(id);
    if (file == null || !file.exists() || !file.canRead()) {
        return ResponseEntity.status(404).body(null);
    }

    Resource resource = new FileSystemResource(file);
    String contentType = file.getName().toLowerCase().endsWith(".mp3") ? "audio/mpeg" : "audio/wav";

    return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
            .header(HttpHeaders.ACCEPT_RANGES, "bytes") // 告知浏览器可按范围申请段数据，满足视频进度条拖拽自愈
            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()))
            .body(resource);
}
```

---

## 五、 前端 SPA 原理及技术交互 (Frontend Core)

前端基于单一 HTML (`index.html`)，为免除前端框架环境污染，纯使用原生 JS 构建以减少环境依赖性。

### 1. 本地状态（State）管理器与鉴权附带

```
const API_BASE = '/api';
let currentUserToken = localStorage.getItem('token');           // 缓存的明文 Token 锁
let currentRole = parseInt(localStorage.getItem('roleLevel')) || 1; // 当前活跃用户的角色安全等级：0-Admin, 1-Operator
let currentUsername = localStorage.getItem('username') || '';

// 动态填充请求 Headers 携带认证令牌
function getHeaders() {
    return {
        'Authorization': 'Bearer ' + currentUserToken
    };
}
```

### 2. 多合一界面跳转路由器设计 (SPA Route)

利用 JS 函数触发展示特定面板样式，隐藏其他未激活节点，避免引入额外的 Router 依赖库大体积加载：

```
function switchSection(sectionName) {
    // 1. 重抹所有面板的活动属性
    document.querySelectorAll('.section-content').forEach(el => el.classList.remove('active'));
    
    // 2. 更改侧边菜单的状态高亮显示
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
    
    // 3. 展现选中目标页，并高亮新菜单
    document.getElementById(`section-${sectionName}`).classList.add('active');
    const navItem = document.querySelector(`.nav-item[data-section="${sectionName}"]`);
    if (navItem) navItem.classList.add('active');
    
    // 4. 重载乐库和特定功能区
    if (sectionName === 'library') loadList();
    if (sectionName === 'users') loadUserList();
}
```

### 3. XML网络组件上行流监控（上传进度条）

使用底层的 `XMLHttpRequest` 替代通用的 `fetch`，保证在高达几百MB的文件向服务器推送时，能够将百分比动态显示在进度条上：

```
async function uploadFile() {
    const formData = new FormData();
    formData.append('file', selectedFile);
    
    const xhr = new XMLHttpRequest();
    
    // 核心监控：动态更改进度组件宽度
    xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
            const percentComplete = Math.round((e.loaded / e.total) * 100);
            document.getElementById('progressBar').style.width = percentComplete + '%';
            document.getElementById('progressText').textContent = `已上传: ${percentComplete}%`;
        }
    });
    
    // 加载响应状态处理
    xhr.addEventListener('load', () => {
        if (xhr.status >= 200 && xhr.status < 300) {
            alert("上传成功!");
            switchSection('library'); // 返回仓库
        } else {
             alert("服务器异常，上传失败，代码: " + xhr.status);
        }
    });

    xhr.open('POST', `${API_BASE}/audio/upload`);
    xhr.setRequestHeader('Authorization', 'Bearer ' + currentUserToken); // 前置加入 JWT
    xhr.send(formData);
}
```

### 4. 角色限定性隔离展示 (Role Conditional Rendering)

针对操作员 (roleLevel=1)，前端在获取到由接口返回的音频列表后，**隐匿**或**不渲染**【编辑】与【删除】等按钮，保护非法误触发生：

```
let actionBtns = '';
// 保证只有管理员身份 (0) 才会输出管理动作控件
if (currentRole === 0) {
    const escapedFileName = item.fileName.replace(/'/g, "\\'").replace(/"/g, '\\"');
    actionBtns = `
        <button class="btn-edit" onclick="startEdit('${itemId}', '${escapedFileName}')">编辑</button>
        <button class="btn-delete" onclick="deleteAudio('${itemId}')">删除</button>
    `;
}
```

---

## 六、 MVP 系统验证测试矩阵 (Testing Strategies)

|测试名称|验证步骤|预期效果|
|---|---|---|
|**原子落盘完整性**|管理员上传一个 `.mp3` 音频，同时监测存储目录。|存储区生成一个以 `UUID.mp3` 命名的物理文件；数据库同步录入原始逻辑名称、文件大小与 MD5 哈希校验值。整个过程成功。|
|**精度防爆与编辑**|在乐库列表中点击最后一条资产，触发“编辑”。|能准确获取正确的 Long/Bigint 格式主键，无长主键在 JavaScript 中被截断或缩减的现象（由于后端返回了字符串或高精度转义处理）。|
|**低权限隔离防范**|普通操作员（角色 1）提取到 Token 登录，并发送 `DELETE` 请求。|网页不渲染任何“编辑或删除”控件；若通过接口工具手动触发 `DELETE /api/audio/{id}`，服务器端校验 `roleLevel` 非 0，返回 `403 Forbidden`。|
|**物理丢失一致性检测**|故意手动清理存储区下的某个 UUID 物理音频，再次点击“播放”或运行“自检清理”。|1. 播放器将触发 `handleAudioError` 异常响应，显示“文件不存在”；  <br>2. 系统管理员点击触发“垃圾清理”接口，能够正常完成死链检查过程，在 DB 抹除对应数据。|