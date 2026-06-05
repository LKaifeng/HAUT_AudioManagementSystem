package com.example.haut_audiomanagementsystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.haut_audiomanagementsystem.entity.AudioAsset;
import com.example.haut_audiomanagementsystem.service.AudioService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;

@RestController
@RequestMapping("/api/audio")
@CrossOrigin(origins = "*") // 允许前端跨域访问
public class AudioController {

     @Autowired
    private AudioService audioService;
    @PostMapping("/upload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file,
                                         HttpServletRequest request) {
        try {
            Integer roleLevel = (Integer) request.getAttribute("roleLevel");
            
            if (roleLevel == null || (roleLevel != 0 && roleLevel != 1)) {
                return ResponseEntity.status(403).body("权限不足：只有管理员和操作员可以上传音频");
            }
            
            audioService.uploadAudio(file);
            return ResponseEntity.ok("上传成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Page<AudioAsset>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(audioService.listAudios(page, size));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id,
                                         HttpServletRequest request) {
        try {
            Integer roleLevel = (Integer) request.getAttribute("roleLevel");
            
            if (roleLevel == null || roleLevel != 0) {
                return ResponseEntity.status(403).body("权限不足：只有管理员可以删除音频");
            }
            
            audioService.deleteAudio(id);
            return ResponseEntity.ok("删除成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("删除失败");
        }
    }

    @GetMapping("/stream/{id}")
    public ResponseEntity<Resource> stream(@PathVariable Long id) {
        File file = audioService.getAudioFile(id);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .body(resource);
    }
}