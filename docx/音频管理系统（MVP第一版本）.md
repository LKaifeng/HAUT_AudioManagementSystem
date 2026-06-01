# 音频管理系统 

## 一、 系统架构核心逻辑

- **Create**: 文件上传到磁盘 + 记录写入数据库（原子操作）。
- **Read**: 分页查询，且必须包含“物理存在性监控”标记。
- **Update**: 仅限元数据（如名称、备注）改写，禁止改写物理路径。
- **Delete**: 物理文件删除 + 数据库记录抹除。

---

## 二、 数据库设计 (MySQL)

**核心修正**：表 ID 使用 `VARCHAR` 或 `BIGINT`（前端需转 String）

```
-- 1. 音频资产表
CREATE TABLE `audio_assets` (
  `id` VARCHAR(64) PRIMARY KEY,        -- 唯一ID (建议UUID或雪花算法String)
  `file_name` VARCHAR(255) NOT NULL,   -- 逻辑显示名称
  `file_path` VARCHAR(500) NOT NULL,   -- 物理存储绝对路径
  `file_size` BIGINT DEFAULT 0,        -- 文件大小(Byte)
  `hash_code` VARCHAR(64),             -- 文件MD5(用于秒传和去重)
  `create_time` DATETIME NOT NULL,     -- 录入时间 (用于排序)
  `status` TINYINT DEFAULT 1           -- 1:正常, 0:逻辑删除, 2:物理丢失
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 用户表 (权限控制)
CREATE TABLE `sys_users` (
  `id` INT PRIMARY KEY AUTO_INCREMENT,
  `username` VARCHAR(50) UNIQUE,
  `password` VARCHAR(100),
  `role_level` INT DEFAULT 1            -- 0:超级管理员(D权限), 1:操作员(CUR权限)
);
```

---

## 三、 后端接口核心实现 (Spring Boot)

### 1.  文件上传

必须先存文件再写库，若写库失败需回滚（删除文件）。

```
@PostMapping("/upload")
@Transactional
public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
    // 1. 物理保存
    String fileName = file.getOriginalFilename();
    String filePath = storagePath + UUID.randomUUID() + ".mp3";
    File dest = new File(filePath);
    file.transferTo(dest); 

    // 2. 数据库写入
    AudioAsset asset = new AudioAsset();
    asset.setFileName(fileName);
    asset.setFilePath(filePath);
    asset.setCreateTime(new Date());
    mapper.insert(asset);
    
    return ResponseEntity.ok("Upload Success");
}
```

### 2. 查询（顺序）

禁止自由排序，强制 `ORDER BY create_time DESC`。

```
-- 接口：GET /api/audio/list
SELECT * FROM audio_assets 
WHERE status = 1 
ORDER BY create_time DESC 
LIMIT 0, 10;
```

### 3. 修改：限制性更新（仅可更改音频名）

**修正点**：仅允许修改 `file_name`。

- **原因**：音频文件一旦入库，修改物理路径会导致链接断开（死链），MVP 阶段严禁修改 `file_path`。

### 4.  删除：物理同步删除

**修正点**：必须校验权限（JWT Level 0）且执行物理删除。

```
@DeleteMapping("/{id}")
public ResponseEntity<?> delete(@PathVariable String id, @RequestAttribute("role") Integer role) {
    // 1. 权限拦截 (Level 0 Check)
    if (role != 0) return ResponseEntity.status(403).body("无权删除");

    // 2. 查出物理路径
    AudioAsset asset = mapper.selectById(id);
    
    // 3. 执行物理删除
    File file = new File(asset.getFilePath());
    if (file.exists()) {
        boolean deleted = file.delete();
        if (!deleted) return ResponseEntity.error("磁盘文件被占用，无法删除");
    }

    // 4. 清除数据库记录
    mapper.deleteById(id);
    return ResponseEntity.ok("Delete Success");
}
```

---

## 四、 安全（防止精度和路径问题）

1. **路径混淆 (Obfuscation)**：
    - 前端 `GET /list` 返回的 JSON 中，不要直接暴露完整物理路径 `D:/files/...`，只返回 ID，播放时通过 `/api/stream/{id}` 接口进行流转发处理。
2. **ID 精度防爆 (Frontend)**：
    - 如果 ID 用的是 Long，后端必须配置：
        
        ```
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long id;
        ```
        
3. **防止重名覆盖**：
    - 磁盘存储文件名应强制改为 `UUID.mp3`，数据库 `file_name` 存原始名称。

---

## 五、 MVP 测试用例 (Apifox 验证)

|场景|动作|预期结果|
|---|---|---|
|**正常录入**|上传 5MB mp3 文件|磁盘产生随机名文件，DB 产生正序记录|
|**越权删除**|使用 Level 1 Token 调用 DELETE|返回 403，物理文件仍在磁盘|
|**物理一致性**|手动在磁盘删掉文件再点删除|接口应能优雅处理“物理文件不存在”的情况，并在 DB 抹除记录|
|**精度测试**|点击最后一条记录的 Edit|能够准确弹窗（证明 ID 没被 JS 截断）|

---

## 六、 MVP 开发清单总结

1. **数据库**：仅两个表（资产、用户）。
2. **文件 IO**：固定一个存储根目录，禁止路径穿越。
3. **权限**：拦截器判断请求 Header 里的 Token 级别。
4. **排序**：入库时间就是唯一排序标准，不可更改。