package com.example.haut_audiomanagementsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@Data
@TableName("audio_assets")
public class AudioAsset {
    @TableId(type = IdType.ASSIGN_ID) // 雪花算法生成Long ID
    @JsonFormat(shape = JsonFormat.Shape.STRING) // 关键：防止前端精度丢失
    private Long id;

    private String fileName;      // 原始文件名
    private String filePath;      // 物理路径
    private Long fileSize;
    private String hashCode;      // MD5
    private Date createTime;
    private Integer status;       // 1:正常
}
