package com.basemetas.fileview.preview.service;

import com.basemetas.fileview.preview.model.archive.ExtractResult;
import com.basemetas.fileview.preview.model.archive.ExtractionDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FilePreviewServiceEnhancedTest {

    @Test
    public void testShouldExtractLogic() {
        // 测试shouldExtract返回true的情况
        String filePath = "/path/to/archive.zip/internal/file.txt";
        ExtractionDecision decision = new ExtractionDecision(true, "有效的压缩包路径格式");
        
        // 验证逻辑是否正确执行
        assertTrue(decision.isShouldExtract());
        assertEquals("有效的压缩包路径格式", decision.getReason());
    }

    @Test
    public void testShouldNotExtractLogic() {
        // 测试shouldExtract返回false的情况
        String filePath = "/path/to/normal/file.txt";
        ExtractionDecision decision = new ExtractionDecision(false, "文件已存在于文件系统中");
        
        // 验证逻辑是否正确执行
        assertFalse(decision.isShouldExtract());
        assertEquals("文件已存在于文件系统中", decision.getReason());
    }
}