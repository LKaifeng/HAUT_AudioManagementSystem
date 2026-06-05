package com.example.haut_audiomanagementsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.haut_audiomanagementsystem.entity.SysUser;
import com.example.haut_audiomanagementsystem.mapper.SysUserMapper;
import com.example.haut_audiomanagementsystem.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
// 认证控制器，提供登录接口，返回 JWT token 和用户角色信息
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");

        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username).eq("password", password);
        SysUser user = userMapper.selectOne(wrapper);

        if (user != null) {
            String token = jwtUtil.generateToken(user.getUsername(), user.getRoleLevel());
            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("token", token);
            result.put("roleLevel", user.getRoleLevel());
            return result;
        }
        
        Map<String, Object> error = new HashMap<>();
        error.put("code", 401);
        error.put("msg", "用户名或密码错误");
        return error;
    }
}