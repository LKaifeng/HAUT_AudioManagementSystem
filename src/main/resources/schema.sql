CREATE TABLE IF NOT EXISTS audio_assets (
    id BIGINT NOT NULL COMMENT '主键ID',
    file_name VARCHAR(255) COMMENT '原始文件名',
    file_path VARCHAR(500) COMMENT '物理存储路径',
    file_size BIGINT COMMENT '文件大小(字节)',
    hash_code VARCHAR(64) COMMENT 'MD5哈希值',
    create_time DATETIME COMMENT '创建时间',
    status INT DEFAULT 1 COMMENT '状态: 1正常, 0删除',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频资产表';


CREATE TABLE IF NOT EXISTS sys_users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE,
    password VARCHAR(100),
    role_level INT DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 初始测试账户数据
-- 管理员账户 (role_level = 0): 拥有所有权限，包括删除功能
INSERT IGNORE INTO sys_users (username, password, role_level) VALUES 
    ('admin', '123456', 0),
    ('manager', 'admin123', 0);

-- 操作员账户 (role_level = 1): 只能上传和播放，不能删除
INSERT IGNORE INTO sys_users (username, password, role_level) VALUES 
    ('operator1', 'user123', 1),
    ('operator2', 'test123', 1),
    ('guest', 'guest123', 1);