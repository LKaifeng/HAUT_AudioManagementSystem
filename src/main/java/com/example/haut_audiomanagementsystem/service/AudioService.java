package com.example.haut_audiomanagementsystem.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.haut_audiomanagementsystem.entity.AudioAsset;
import com.example.haut_audiomanagementsystem.mapper.AudioAssetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Date;
import java.util.UUID;

@Service
public class AudioService {

    @Resource
    private AudioAssetMapper audioAssetMapper;

    @Value("${app.storage.path}")
    private String storagePath;

    /**
     * 上传音频
     */
    @Transactional(rollbackFor = Exception.class)
    public void uploadAudio(MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("文件为空");

        // 1. 生成唯一文件名 UUID.mp3
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.matches(".*\\.(mp3|wav)$")) {
            throw new IllegalArgumentException("仅支持 mp3 或 wav 格式");
        }
        String extension = originalName != null ? originalName.substring(originalName.lastIndexOf(".")) : ".mp3";
        String uuidName = UUID.randomUUID().toString() + extension;
        
        // 2. 确保目录存在
        File dir = new File(storagePath);
        if (!dir.exists()) dir.mkdirs();

        // 3. 保存物理文件
       Path filePath = Paths.get(storagePath, uuidName);
        // 优化：使用 transferTo 避免内存溢出
        file.transferTo(filePath.toFile());

        // 优化：计算 MD5
        String md5 = calculateMD5(file.getBytes());

        // 4. 写入数据库
        AudioAsset asset = new AudioAsset();
        asset.setFileName(originalName);
        asset.setFilePath(filePath.toString());
        asset.setFileSize(file.getSize());
        asset.setHashCode(md5);
        asset.setCreateTime(new Date());
        asset.setStatus(1);
        // 计算 MD5 hash_code
        
        audioAssetMapper.insert(asset);
    }
    private String calculateMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
    /**
     * 分页查询 (按创建时间降序)
     */
    public Page<AudioAsset> listAudios(int page, int size) {
        Page<AudioAsset> pageInfo = new Page<>(page, size);
        QueryWrapper<AudioAsset> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1)
               .orderByDesc("create_time");
        return audioAssetMapper.selectPage(pageInfo, wrapper);
    }
    
    /**
     * 删除音频 (物理+逻辑)
     */
    @Transactional
    public void deleteAudio(Long id) {
        AudioAsset asset = audioAssetMapper.selectById(id);
        if (asset == null) return;

        // 1. 先执行数据库逻辑删除 (MP 会根据配置自动 update status = 0)
        audioAssetMapper.deleteById(id);

        // 2. 再删除物理文件
        File file = new File(asset.getFilePath());
        if (file.exists()) {
            file.delete();
        }
    }
    
    /**
     * 获取文件流 (用于播放)
     */
    public File getAudioFile(Long id) {
        AudioAsset asset = audioAssetMapper.selectById(id);
        if (asset == null) return null;
        return new File(asset.getFilePath());
    }
}