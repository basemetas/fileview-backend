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
package com.basemetas.fileview.convert.mapper;

import com.basemetas.fileview.convert.config.FileCategory;
import com.basemetas.fileview.convert.config.FileTypeMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileTypeMapper 单元测试
 */
public class FileTypeMapperTest {
    
    private FileTypeMapper fileTypeMapper;
    
    @BeforeEach
    public void setUp() {
        fileTypeMapper = new FileTypeMapper();
    }
    
    @Test
    public void testGetFileCategory_ImageExtensions() {
        assertEquals(FileCategory.IMAGE, fileTypeMapper.getFileCategory("png"));
        assertEquals(FileCategory.IMAGE, fileTypeMapper.getFileCategory("jpg"));
        assertEquals(FileCategory.IMAGE, fileTypeMapper.getFileCategory("jpeg"));
        assertEquals(FileCategory.IMAGE, fileTypeMapper.getFileCategory("tiff"));
        assertEquals(FileCategory.IMAGE, fileTypeMapper.getFileCategory("bmp"));
        assertEquals(FileCategory.IMAGE, fileTypeMapper.getFileCategory("gif"));
        assertEquals(FileCategory.IMAGE, fileTypeMapper.getFileCategory("webp"));
    }
    
    @Test
    public void testGetFileCategory_ArchiveExtensions() {
        assertEquals(FileCategory.ARCHIVE, fileTypeMapper.getFileCategory("zip"));
        assertEquals(FileCategory.ARCHIVE, fileTypeMapper.getFileCategory("jar"));
        assertEquals(FileCategory.ARCHIVE, fileTypeMapper.getFileCategory("tar"));
        assertEquals(FileCategory.ARCHIVE, fileTypeMapper.getFileCategory("rar"));
        assertEquals(FileCategory.ARCHIVE, fileTypeMapper.getFileCategory("7z"));
        assertEquals(FileCategory.ARCHIVE, fileTypeMapper.getFileCategory("tar.gz"));
        assertEquals(FileCategory.ARCHIVE, fileTypeMapper.getFileCategory("tgz"));
    }
    
    @Test
    public void testGetFileCategory_DocumentExtensions() {
        assertEquals(FileCategory.DOCUMENT, fileTypeMapper.getFileCategory("docx"));
        assertEquals(FileCategory.DOCUMENT, fileTypeMapper.getFileCategory("doc"));
        assertEquals(FileCategory.DOCUMENT, fileTypeMapper.getFileCategory("pdf"));
        assertEquals(FileCategory.DOCUMENT, fileTypeMapper.getFileCategory("txt"));
        assertEquals(FileCategory.DOCUMENT, fileTypeMapper.getFileCategory("rtf"));
    }
    
    @Test
    public void testGetFileCategory_OfdExtensions() {
        assertEquals(FileCategory.OFD, fileTypeMapper.getFileCategory("ofd"));
    }
    
    @Test
    public void testGetFileCategory_CaseInsensitive() {
        assertEquals(FileCategory.IMAGE, fileTypeMapper.getFileCategory("PNG"));
        assertEquals(FileCategory.IMAGE, fileTypeMapper.getFileCategory("JpG"));
        assertEquals(FileCategory.ARCHIVE, fileTypeMapper.getFileCategory("ZIP"));
        assertEquals(FileCategory.DOCUMENT, fileTypeMapper.getFileCategory("DOCX"));
    }
    
    @Test
    public void testGetFileCategory_UnsupportedExtension() {
        assertNull(fileTypeMapper.getFileCategory("unknown"));
        assertNull(fileTypeMapper.getFileCategory("xyz"));
        assertNull(fileTypeMapper.getFileCategory(""));
        assertNull(fileTypeMapper.getFileCategory(null));
    }
    
    @Test
    public void testGetStrategyType() {
        assertEquals("image", fileTypeMapper.getStrategyType("png"));
        assertEquals("archive", fileTypeMapper.getStrategyType("zip"));
        assertEquals("ofd", fileTypeMapper.getStrategyType("ofd"));
        assertEquals("tdf", fileTypeMapper.getStrategyType("docx"));
        assertNull(fileTypeMapper.getStrategyType("unknown"));
    }
    
    @Test
    public void testExtractExtension_SimpleExtensions() {
        assertEquals("png", fileTypeMapper.extractExtension("test.png"));
        assertEquals("jpg", fileTypeMapper.extractExtension("photo.jpg"));
        assertEquals("docx", fileTypeMapper.extractExtension("document.docx"));
        assertEquals("zip", fileTypeMapper.extractExtension("archive.zip"));
    }
    
    @Test
    public void testExtractExtension_SpecialExtensions() {
        assertEquals("tar.gz", fileTypeMapper.extractExtension("archive.tar.gz"));
        assertEquals("tgz", fileTypeMapper.extractExtension("backup.tgz"));
    }
    
    @Test
    public void testExtractExtension_WithPath() {
        assertEquals("png", fileTypeMapper.extractExtension("/path/to/file.png"));
        assertEquals("tar.gz", fileTypeMapper.extractExtension("D:/files/backup.tar.gz"));
        assertEquals("docx", fileTypeMapper.extractExtension("C:\\Users\\test\\document.docx"));
    }
    
    @Test
    public void testExtractExtension_EdgeCases() {
        assertEquals("", fileTypeMapper.extractExtension(""));
        assertEquals("", fileTypeMapper.extractExtension(null));
        assertEquals("", fileTypeMapper.extractExtension("noextension"));
        assertEquals("txt", fileTypeMapper.extractExtension(".txt"));
    }
    
    @Test
    public void testIsSupported() {
        assertTrue(fileTypeMapper.isSupported("png"));
        assertTrue(fileTypeMapper.isSupported("zip"));
        assertTrue(fileTypeMapper.isSupported("docx"));
        assertTrue(fileTypeMapper.isSupported("tar.gz"));
        assertFalse(fileTypeMapper.isSupported("unknown"));
        assertFalse(fileTypeMapper.isSupported(""));
        assertFalse(fileTypeMapper.isSupported(null));
    }
    
    @Test
    public void testRegisterExtension() {
        // 注册新扩展名
        fileTypeMapper.registerExtension(FileCategory.IMAGE, "svg");
        
        assertEquals(FileCategory.IMAGE, fileTypeMapper.getFileCategory("svg"));
        assertEquals("image", fileTypeMapper.getStrategyType("svg"));
        assertTrue(fileTypeMapper.isSupported("svg"));
    }
    
    @Test
    public void testRegisterExtensions() {
        // 批量注册新扩展名
        fileTypeMapper.registerExtensions(FileCategory.ARCHIVE, "tar.bz2", "tar.xz");
        
        assertTrue(fileTypeMapper.isSupported("tar.bz2"));
        assertTrue(fileTypeMapper.isSupported("tar.xz"));
        assertEquals(FileCategory.ARCHIVE, fileTypeMapper.getFileCategory("tar.bz2"));
    }
    
    @Test
    public void testGetSupportedExtensions() {
        Set<String> imageExtensions = fileTypeMapper.getSupportedExtensions(FileCategory.IMAGE);
        assertNotNull(imageExtensions);
        assertTrue(imageExtensions.contains("png"));
        assertTrue(imageExtensions.contains("jpg"));
        assertTrue(imageExtensions.contains("jpeg"));
        
        // 验证返回的集合是不可修改的
        assertThrows(UnsupportedOperationException.class, () -> {
            imageExtensions.add("test");
        });
    }
    
    @Test
    public void testGetAllSupportedExtensions() {
        Set<String> allExtensions = fileTypeMapper.getAllSupportedExtensions();
        assertNotNull(allExtensions);
        assertTrue(allExtensions.size() > 0);
        assertTrue(allExtensions.contains("png"));
        assertTrue(allExtensions.contains("zip"));
        assertTrue(allExtensions.contains("docx"));
        
        // 验证返回的集合是不可修改的
        assertThrows(UnsupportedOperationException.class, () -> {
            allExtensions.add("test");
        });
    }
    
    @Test
    public void testGetAllCategoryMappings() {
        Map<FileCategory, Set<String>> mappings = fileTypeMapper.getAllCategoryMappings();
        assertNotNull(mappings);
        
        assertTrue(mappings.containsKey(FileCategory.IMAGE));
        assertTrue(mappings.containsKey(FileCategory.ARCHIVE));
        assertTrue(mappings.containsKey(FileCategory.OFD));
        assertTrue(mappings.containsKey(FileCategory.DOCUMENT));
        
        Set<String> imageExts = mappings.get(FileCategory.IMAGE);
        assertTrue(imageExts.contains("png"));
        
        // 验证返回的map是不可修改的
        assertThrows(UnsupportedOperationException.class, () -> {
            mappings.put(FileCategory.IMAGE, Set.of("test"));
        });
    }
    
    @Test
    public void testRemoveExtension() {
        // 先确认扩展名存在
        assertTrue(fileTypeMapper.isSupported("png"));
        
        // 移除扩展名
        assertTrue(fileTypeMapper.removeExtension("png"));
        
        // 确认已移除
        assertFalse(fileTypeMapper.isSupported("png"));
        assertNull(fileTypeMapper.getFileCategory("png"));
        
        // 尝试移除不存在的扩展名
        assertFalse(fileTypeMapper.removeExtension("nonexistent"));
    }
    
    @Test
    public void testGetSupportedExtensionCount() {
        int initialCount = fileTypeMapper.getSupportedExtensionCount();
        assertTrue(initialCount > 0);
        
        // 添加新扩展名
        fileTypeMapper.registerExtension(FileCategory.IMAGE, "svg");
        assertEquals(initialCount + 1, fileTypeMapper.getSupportedExtensionCount());
        
        // 移除扩展名
        fileTypeMapper.removeExtension("svg");
        assertEquals(initialCount, fileTypeMapper.getSupportedExtensionCount());
    }
    
    @Test
    public void testFileCategoryEnumValues() {
        assertEquals("image", FileCategory.IMAGE.getStrategyType());
        assertEquals("archive", FileCategory.ARCHIVE.getStrategyType());
        assertEquals("ofd", FileCategory.OFD.getStrategyType());
        assertEquals("tdf", FileCategory.DOCUMENT.getStrategyType());
        
        assertEquals("图片文件", FileCategory.IMAGE.getDescription());
        assertEquals("压缩文件", FileCategory.ARCHIVE.getDescription());
        assertEquals("OFD文件", FileCategory.OFD.getDescription());
        assertEquals("文档文件", FileCategory.DOCUMENT.getDescription());
    }
}
