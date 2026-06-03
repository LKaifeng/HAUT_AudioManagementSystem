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