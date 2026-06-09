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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
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
        if (file.isEmpty())
            throw new IllegalArgumentException("文件为空");

        // 1. 生成唯一文件名 UUID.mp3
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.matches(".*\\.(mp3|wav)$")) {
            throw new IllegalArgumentException("仅支持 mp3 或 wav 格式");
        }
        String extension = originalName != null ? originalName.substring(originalName.lastIndexOf(".")) : ".mp3";
        String uuidName = UUID.randomUUID().toString() + extension;

        // 2. 确保目录存在
        File dir = new File(storagePath);
        if (!dir.exists())
            dir.mkdirs();

        // 3. 保存物理文件
        Path targetPath = Paths.get(storagePath, uuidName).normalize();

        // 使用 Files.copy 替代 transferTo
        Files.createDirectories(targetPath.getParent());
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 从已保存的文件计算 MD5，而不是从 MultipartFile
        String md5 = calculateMD5FromFile(targetPath.toFile());

        // 4. 写入数据库
        AudioAsset asset = new AudioAsset();
        asset.setFileName(originalName);
        asset.setFilePath(targetPath.toAbsolutePath().toString());
        asset.setFileSize(file.getSize());
        asset.setHashCode(md5);
        asset.setCreateTime(new Date());
        asset.setStatus(1);

        audioAssetMapper.insert(asset);
    }

    private String calculateMD5FromFile(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            byte[] digest = md.digest(fileBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算文件MD5失败", e);
        }
    }

    /**
     * 分页查询 (按创建时间降序)
     */
    public Page<AudioAsset> listAudios(int page, int size, String keyword) {
        Page<AudioAsset> pageInfo = new Page<>(page, size);
        QueryWrapper<AudioAsset> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);

        if (keyword != null && !keyword.trim().isEmpty()) {
            String searchKeyword = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like("file_name", searchKeyword)
                    .or()
                    .like("tags", searchKeyword));
        }

        wrapper.orderByDesc("create_time");
        return audioAssetMapper.selectPage(pageInfo, wrapper);
    }

    public Page<AudioAsset> listAudios(int page, int size) {
        return listAudios(page, size, null);
    }

    /**
     * 删除音频 (物理+逻辑)
     */
    @Transactional
    public void deleteAudio(Long id) {
        AudioAsset asset = audioAssetMapper.selectById(id);
        if (asset == null) {
            throw new IllegalArgumentException("音频不存在");
        }

        // 1. 先保存文件路径，因为逻辑删除后可能无法获取
        String filePath = asset.getFilePath();

        // 2. 执行数据库逻辑删除 (MP 会根据配置自动 update status = 0)
        int result = audioAssetMapper.deleteById(id);
        if (result == 0) {
            throw new RuntimeException("删除失败，记录不存在");
        }

        // 3. 再删除物理文件
        File file = new File(filePath);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (!deleted) {
                System.err.println("警告：物理文件删除失败: " + filePath);
            }
        }
    }

    /**
     * 修改音频名称
     */
    @Transactional
    public void updateAudioName(Long id, String newFileName) {
        AudioAsset asset = audioAssetMapper.selectById(id);
        if (asset == null || asset.getStatus() != 1) {
            throw new IllegalArgumentException("音频不存在");
        }
        asset.setFileName(newFileName);
        audioAssetMapper.updateById(asset);
    }

    /**
     * 清理无效记录（文件不存在但数据库仍有记录）
     */
    @Transactional
    public int cleanupInvalidRecords() {
        List<AudioAsset> allAudios = audioAssetMapper.selectList(
                new QueryWrapper<AudioAsset>().eq("status", 1));

        int cleanedCount = 0;
        for (AudioAsset asset : allAudios) {
            File file = new File(asset.getFilePath());
            if (!file.exists()) {
                audioAssetMapper.deleteById(asset.getId());
                cleanedCount++;
                System.out.println("已清理无效记录 ID: " + asset.getId() + ", 文件名: " + asset.getFileName());
            }
        }

        return cleanedCount;
    }

    /**
     * 获取音频资产信息
     */
    public AudioAsset getAudioAssetById(Long id) {
        return audioAssetMapper.selectById(id);
    }

    /**
     * 获取文件流 (用于播放)
     */
    public File getAudioFile(Long id) {
        AudioAsset asset = audioAssetMapper.selectById(id);
        if (asset == null) {
            System.err.println("数据库中未找到音频记录，ID: " + id);
            return null;
        }

        if (asset.getStatus() != 1) {
            System.err.println("音频状态异常，ID: " + id + ", 状态: " + asset.getStatus());
            return null;
        }

        File file = new File(asset.getFilePath());
        if (!file.exists()) {
            System.err.println("物理文件不存在，ID: " + id + ", 路径: " + asset.getFilePath());
            return null;
        }

        return file;
    }

}