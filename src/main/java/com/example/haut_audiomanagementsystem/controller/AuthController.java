package com.example.haut_audiomanagementsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.haut_audiomanagementsystem.entity.SysUser;
import com.example.haut_audiomanagementsystem.entity.UserRegistration;
import com.example.haut_audiomanagementsystem.mapper.SysUserMapper;
import com.example.haut_audiomanagementsystem.mapper.UserRegistrationMapper;
import com.example.haut_audiomanagementsystem.util.JwtUtil;

import io.jsonwebtoken.Claims;

// import com.example.haut_audiomanagementsystem.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 认证控制器，提供登录接口，返回 JWT token 和用户角色信息
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private UserRegistrationMapper registrationMapper;

    @Autowired
    private JwtUtil jwtUtil;

    // @Autowired
    // private PasswordUtil passwordUtil;
    // 出bug了暂时无法解除，加密密码无法调取

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

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> registerData) {
        String username = registerData.get("username");
        String password = registerData.get("password");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "用户名和密码不能为空");
            return error;
        }

        QueryWrapper<SysUser> userWrapper = new QueryWrapper<>();
        userWrapper.eq("username", username.trim());
        SysUser existUser = userMapper.selectOne(userWrapper);

        if (existUser != null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "用户名已存在");
            return error;
        }

        QueryWrapper<UserRegistration> regWrapper = new QueryWrapper<>();
        regWrapper.eq("username", username.trim()).eq("status", 0);
        UserRegistration pendingReg = registrationMapper.selectOne(regWrapper);

        if (pendingReg != null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "该用户名的注册申请正在审核中");
            return error;
        }

        UserRegistration registration = new UserRegistration();
        registration.setUsername(username.trim());
        registration.setPassword(password);
        registration.setApplyTime(new Date());
        registration.setStatus(0);

        registrationMapper.insert(registration);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "注册申请已提交，请等待管理员审核");
        return result;
    }

    @GetMapping("/registrations")
    public Map<String, Object> getPendingRegistrations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");

        System.out.println("=== 获取注册申请列表 ===");
        System.out.println("请求参数 - page: " + page + ", size: " + size);
        System.out.println("当前用户角色: " + roleLevel);

        if (roleLevel == null || roleLevel != 0) {
            System.out.println("权限不足，拒绝访问");
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以查看注册申请");
            return error;
        }

        QueryWrapper<UserRegistration> countWrapper = new QueryWrapper<>();
        countWrapper.eq("status", 0);
        long totalCount = registrationMapper.selectCount(countWrapper);
        System.out.println("数据库中 status=0 的记录总数: " + totalCount);

        QueryWrapper<UserRegistration> allWrapper = new QueryWrapper<>();
        allWrapper.eq("status", 0).orderByDesc("apply_time");
        List<UserRegistration> allRecords = registrationMapper.selectList(allWrapper);
        System.out.println("查询到的记录数: " + allRecords.size());
        if (!allRecords.isEmpty()) {
            System.out.println("第一条记录: id=" + allRecords.get(0).getId() + ", username="
                    + allRecords.get(0).getUsername() + ", status=" + allRecords.get(0).getStatus());
        }

        QueryWrapper<UserRegistration> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 0).orderByDesc("apply_time");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserRegistration> pagination = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                page, size);
        com.baomidou.mybatisplus.core.metadata.IPage<UserRegistration> resultPage = registrationMapper
                .selectPage(pagination, wrapper);

        System.out.println("分页查询结果 - total: " + resultPage.getTotal() + ", records: " + resultPage.getRecords().size());

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("registrations", resultPage.getRecords());
        result.put("total", resultPage.getTotal());
        result.put("pages", resultPage.getPages());
        result.put("current", resultPage.getCurrent());
        result.put("size", resultPage.getSize());
        return result;
    }

        @PostMapping("/registrations/{id}/approve")
    public Map<String, Object> approveRegistration(@PathVariable Integer id,
            @RequestBody(required = false) Map<String, String> data,
            HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");
        Claims claims = (Claims) request.getAttribute("claims");
        String username = claims != null ? claims.getSubject() : null;

        System.out.println("=== 审核通过 ===");
        System.out.println("申请ID: " + id);
        System.out.println("当前用户角色: " + roleLevel);
        System.out.println("当前用户名: " + username);

        if (roleLevel == null || roleLevel != 0) {
            System.out.println("权限不足，拒绝访问");
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以审核注册申请");
            return error;
        }

        UserRegistration registration = registrationMapper.selectById(id);
        if (registration == null) {
            System.out.println("注册申请不存在");
            Map<String, Object> error = new HashMap<>();
            error.put("code", 404);
            error.put("msg", "注册申请不存在");
            return error;
        }

        if (registration.getStatus() != 0) {
            System.out.println("该申请已处理");
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "该申请已处理");
            return error;
        }

        QueryWrapper<SysUser> userWrapper = new QueryWrapper<>();
        userWrapper.eq("username", registration.getUsername());
        SysUser existUser = userMapper.selectOne(userWrapper);

        if (existUser != null) {
            System.out.println("用户名已存在");
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "用户名已存在，无法通过审核");
            return error;
        }

        SysUser newUser = new SysUser();
        newUser.setUsername(registration.getUsername());
        newUser.setPassword(registration.getPassword());
        newUser.setRoleLevel(1);
        
        try {
            userMapper.insert(newUser);
            System.out.println("创建新用户成功，ID: " + newUser.getId());
        } catch (Exception e) {
            System.err.println("创建用户失败: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("msg", "创建用户失败: " + e.getMessage());
            return error;
        }

        registration.setStatus(1);
        
        if (username != null && !username.isEmpty()) {
            QueryWrapper<SysUser> adminWrapper = new QueryWrapper<>();
            adminWrapper.eq("username", username);
            SysUser adminUser = userMapper.selectOne(adminWrapper);
            if (adminUser != null) {
                registration.setReviewerId(adminUser.getId());
                System.out.println("审核人ID: " + adminUser.getId());
            } else {
                registration.setReviewerId(null);
                System.out.println("警告：找不到审核人信息，username=" + username);
            }
        } else {
            registration.setReviewerId(null);
            System.out.println("警告：Token中未包含用户名信息");
        }
        
        registration.setReviewTime(new Date());
        if (data != null && data.containsKey("comment")) {
            registration.setReviewComment(data.get("comment"));
        }
        
        try {
            registrationMapper.updateById(registration);
            System.out.println("更新注册申请状态成功");
        } catch (Exception e) {
            System.err.println("更新注册申请失败: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("msg", "更新注册申请失败: " + e.getMessage());
            return error;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "审核通过，用户已创建");
        return result;
    }

    @PostMapping("/registrations/{id}/reject")
    public Map<String, Object> rejectRegistration(@PathVariable Integer id,
            @RequestBody(required = false) Map<String, String> data,
            HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");
        Claims claims = (Claims) request.getAttribute("claims");
        String username = claims != null ? claims.getSubject() : null;

        System.out.println("=== 拒绝申请 ===");
        System.out.println("申请ID: " + id);
        System.out.println("当前用户角色: " + roleLevel);
        System.out.println("当前用户名: " + username);

        if (roleLevel == null || roleLevel != 0) {
            System.out.println("权限不足，拒绝访问");
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以审核注册申请");
            return error;
        }

        UserRegistration registration = registrationMapper.selectById(id);
        if (registration == null) {
            System.out.println("注册申请不存在");
            Map<String, Object> error = new HashMap<>();
            error.put("code", 404);
            error.put("msg", "注册申请不存在");
            return error;
        }

        if (registration.getStatus() != 0) {
            System.out.println("该申请已处理");
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "该申请已处理");
            return error;
        }

        registration.setStatus(2);
        
        if (username != null && !username.isEmpty()) {
            QueryWrapper<SysUser> adminWrapper = new QueryWrapper<>();
            adminWrapper.eq("username", username);
            SysUser adminUser = userMapper.selectOne(adminWrapper);
            if (adminUser != null) {
                registration.setReviewerId(adminUser.getId());
                System.out.println("审核人ID: " + adminUser.getId());
            } else {
                registration.setReviewerId(null);
                System.out.println("警告：找不到审核人信息，username=" + username);
            }
        } else {
            registration.setReviewerId(null);
            System.out.println("警告：Token中未包含用户名信息");
        }
        
        registration.setReviewTime(new Date());
        if (data != null && data.containsKey("comment")) {
            registration.setReviewComment(data.get("comment"));
        }
        
        try {
            registrationMapper.updateById(registration);
            System.out.println("更新注册申请状态成功");
        } catch (Exception e) {
            System.err.println("更新注册申请失败: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("msg", "更新注册申请失败: " + e.getMessage());
            return error;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "已拒绝该注册申请");
        return result;
    }

    @GetMapping("/users")
    public Map<String, Object> getUsers(HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");

        if (roleLevel == null || roleLevel != 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以查看用户列表");
            return error;
        }

        List<SysUser> users = userMapper.selectList(null);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("users", users);
        return result;
    }

    @PostMapping("/users")
    public Map<String, Object> addUser(@RequestBody Map<String, Object> userData, HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");

        if (roleLevel == null || roleLevel != 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以添加用户");
            return error;
        }

        String username = (String) userData.get("username");
        String password = (String) userData.get("password");
        Integer role = userData.get("role") != null ? ((Number) userData.get("role")).intValue() : 1;

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "用户名和密码不能为空");
            return error;
        }

        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username.trim());
        SysUser existUser = userMapper.selectOne(wrapper);

        if (existUser != null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("msg", "用户名已存在");
            return error;
        }

        SysUser newUser = new SysUser();
        newUser.setUsername(username.trim());
        newUser.setPassword(password);
        newUser.setRoleLevel(role);

        userMapper.insert(newUser);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "添加成功");
        return result;
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable Integer id,
            @RequestBody Map<String, Object> userData,
            HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");

        if (roleLevel == null || roleLevel != 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以修改用户");
            return error;
        }

        SysUser user = userMapper.selectById(id);
        if (user == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 404);
            error.put("msg", "用户不存在");
            return error;
        }

        if (userData.containsKey("password")) {
            String newPassword = (String) userData.get("password");
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                user.setPassword(newPassword);
            }
        }

        if (userData.containsKey("roleLevel")) {
            user.setRoleLevel(((Number) userData.get("roleLevel")).intValue());
        }

        userMapper.updateById(user);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "修改成功");
        return result;
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable Integer id, HttpServletRequest request) {
        Integer roleLevel = (Integer) request.getAttribute("roleLevel");

        if (roleLevel == null || roleLevel != 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 403);
            error.put("msg", "权限不足：只有管理员可以删除用户");
            return error;
        }

        SysUser user = userMapper.selectById(id);
        if (user == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 404);
            error.put("msg", "用户不存在");
            return error;
        }

        userMapper.deleteById(id);

        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("msg", "删除成功");
        return result;
    }
}