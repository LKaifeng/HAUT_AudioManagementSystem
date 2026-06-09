package com.example.haut_audiomanagementsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("user_registration")
public class UserRegistration {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;
    private String password;
    private Date applyTime;
    private Integer status;
    private String reviewComment;
    private Integer reviewerId;
    private Date reviewTime;
}