/*
 * Copyright 2025 BaseMetas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.basemetas.fileview.convert.strategy;

import com.basemetas.fileview.convert.config.FileTypeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FileConvertContext 单元测试
 * 验证重构后的策略选择逻辑
 */
public class FileConvertContextTest {
    
    @Mock
    private FileConvertStrategy imageStrategy;
    
    @Mock
    private FileConvertStrategy archiveStrategy;
    
    @Mock
    private FileConvertStrategy ofdStrategy;
    
    @Mock
    private FileConvertStrategy documentStrategy;
    
    private FileTypeMapper fileTypeMapper;
    private FileConvertContext context;
    
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 配置策略的支持类型
        // when(imageStrategy.getSupportedSourceFormats()).thenReturn("image");
        // when(archiveStrategy.getSupportedSourceFormats()).thenReturn("archive");
        // when(ofdStrategy.getSupportedSourceFormats()).thenReturn("ofd");
        // when(documentStrategy.getSupportedSourceFormats()).thenReturn("tdf");
        
        // 创建真实的FileTypeMapper
        fileTypeMapper = new FileTypeMapper();
        
        // 创建被测试的FileConvertContext
        context = new FileConvertContext(
            Arrays.asList(imageStrategy, archiveStrategy, ofdStrategy, documentStrategy),
            fileTypeMapper
        );
    }
    
    @Test
    public void testGetStrategy_ByStrategyType() {
        // 直接使用策略类型名称获取策略
        assertEquals(imageStrategy, context.getStrategy("image"));
        assertEquals(archiveStrategy, context.getStrategy("archive"));
        assertEquals(ofdStrategy, context.getStrategy("ofd"));
        assertEquals(documentStrategy, context.getStrategy("tdf"));
    }
    
    @Test
    public void testGetStrategy_ByImageExtension() {
        // 通过图片扩展名获取策略
        assertEquals(imageStrategy, context.getStrategy("png"));
        assertEquals(imageStrategy, context.getStrategy("jpg"));
        assertEquals(imageStrategy, context.getStrategy("jpeg"));
        assertEquals(imageStrategy, context.getStrategy("tiff"));
        assertEquals(imageStrategy, context.getStrategy("bmp"));
        assertEquals(imageStrategy, context.getStrategy("gif"));
    }
    
    @Test
    public void testGetStrategy_ByArchiveExtension() {
        // 通过压缩文件扩展名获取策略
        assertEquals(archiveStrategy, context.getStrategy("zip"));
        assertEquals(archiveStrategy, context.getStrategy("jar"));
        assertEquals(archiveStrategy, context.getStrategy("tar"));
        assertEquals(archiveStrategy, context.getStrategy("rar"));
        assertEquals(archiveStrategy, context.getStrategy("7z"));
    }
    
    @Test
    public void testGetStrategy_BySpecialArchiveExtension() {
        // 测试特殊的压缩格式（tar.gz, tgz）
        assertEquals(archiveStrategy, context.getStrategy("tar.gz"));
        assertEquals(archiveStrategy, context.getStrategy("tgz"));
    }
    
    @Test
    public void testGetStrategy_ByDocumentExtension() {
        // 通过文档扩展名获取策略
        assertEquals(documentStrategy, context.getStrategy("docx"));
        assertEquals(documentStrategy, context.getStrategy("doc"));
        assertEquals(documentStrategy, context.getStrategy("pdf"));
        assertEquals(documentStrategy, context.getStrategy("txt"));
        assertEquals(documentStrategy, context.getStrategy("rtf"));
    }
    
    @Test
    public void testGetStrategy_ByOfdExtension() {
        // 通过OFD扩展名获取策略
        assertEquals(ofdStrategy, context.getStrategy("ofd"));
    }
    
    @Test
    public void testGetStrategy_CaseInsensitive() {
        // 测试大小写不敏感
        assertEquals(imageStrategy, context.getStrategy("PNG"));
        assertEquals(imageStrategy, context.getStrategy("JpG"));
        assertEquals(archiveStrategy, context.getStrategy("ZIP"));
        assertEquals(documentStrategy, context.getStrategy("DOCX"));
    }
    
    @Test
    public void testGetStrategy_UnsupportedExtension() {
        // 测试不支持的扩展名
        assertNull(context.getStrategy("unknown"));
        assertNull(context.getStrategy("xyz"));
    }
    
    @Test
    public void testConvertFileByPath_SimpleExtension() {
        // 配置mock策略的行为
        when(imageStrategy.convert(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(true);
        
        // 测试通过文件路径转换
        boolean result = context.convertFileByPath(
            "D:/test/image.png",
            "D:/output",
            "result",
            "jpg"
        );
        
        assertTrue(result);
        verify(imageStrategy, times(1)).convert(
            eq("D:/test/image.png"),
            eq("D:/output"),
            eq("result"),
            eq("jpg")
        );
    }
    
    @Test
    public void testConvertFileByPath_SpecialExtension() {
        // 配置mock策略的行为
        when(archiveStrategy.convert(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(true);
        
        // 测试tar.gz特殊扩展名
        boolean result = context.convertFileByPath(
            "D:/backup/files.tar.gz",
            "D:/output",
            "extracted",
            "zip"
        );
        
        assertTrue(result);
        verify(archiveStrategy, times(1)).convert(
            eq("D:/backup/files.tar.gz"),
            eq("D:/output"),
            eq("extracted"),
            eq("zip")
        );
    }
    
    @Test
    public void testConvertFileByPath_NoExtension() {
        // 测试没有扩展名的文件
        assertThrows(IllegalArgumentException.class, () -> {
            context.convertFileByPath(
                "D:/test/noextension",
                "D:/output",
                "result",
                "jpg"
            );
        });
    }
    
    @Test
    public void testConvertFile_Success() {
        // 配置mock策略的行为
        when(imageStrategy.convert(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(true);
        
        // 测试直接转换
        boolean result = context.convertFile(
            "png",
            "D:/test/image.png",
            "D:/output",
            "result",
            "jpg"
        );
        
        assertTrue(result);
    }
    
    @Test
    public void testConvertFile_UnsupportedType() {
        // 测试不支持的文件类型
        assertThrows(IllegalArgumentException.class, () -> {
            context.convertFile(
                "unsupported",
                "D:/test/file.xyz",
                "D:/output",
                "result",
                "jpg"
            );
        });
    }
    
    @Test
    public void testStrategyIntegration() {
        // 集成测试：确保所有策略都正确注册
        assertNotNull(context.getStrategy("image"));
        assertNotNull(context.getStrategy("archive"));
        assertNotNull(context.getStrategy("ofd"));
        assertNotNull(context.getStrategy("tdf"));
        
        // 通过扩展名也能正确获取策略
        assertNotNull(context.getStrategy("png"));
        assertNotNull(context.getStrategy("zip"));
        assertNotNull(context.getStrategy("ofd"));
        assertNotNull(context.getStrategy("docx"));
    }
}
