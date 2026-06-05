package com.example.haut_audiomanagementsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_users")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;
    private String password;
    private Integer roleLevel; // 0:管理员, 1:操作员
}