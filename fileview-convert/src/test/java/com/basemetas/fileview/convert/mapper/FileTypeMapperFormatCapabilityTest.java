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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileTypeMapper格式能力测试
 */
public class FileTypeMapperFormatCapabilityTest {
    
    @Autowired
    private FileTypeMapper fileTypeMapper;
    
    @BeforeEach
    public void setUp() {
        fileTypeMapper = new FileTypeMapper();
    }
    
    // ==================== 源格式测试 ====================
    
    @Test
    public void testGetSupportedSourceFormats_Document() {
        Set<String> formats = fileTypeMapper.getSupportedSourceFormats(FileCategory.DOCUMENT);
        assertNotNull(formats);
        assertTrue(formats.size() > 0);
        assertTrue(formats.contains("docx"));
        assertTrue(formats.contains("doc"));
        assertTrue(formats.contains("pdf"));
        assertTrue(formats.contains("txt"));
    }
    
    @Test
    public void testGetSupportedSourceFormats_Spreadsheet() {
        Set<String> formats = fileTypeMapper.getSupportedSourceFormats(FileCategory.SPREADSHEET);
        assertNotNull(formats);
        assertTrue(formats.contains("xlsx"));
        assertTrue(formats.contains("xls"));
        assertTrue(formats.contains("csv"));
        assertTrue(formats.contains("ods"));
    }
    
    @Test
    public void testGetSupportedSourceFormats_Presentation() {
        Set<String> formats = fileTypeMapper.getSupportedSourceFormats(FileCategory.PRESENTATION);
        assertNotNull(formats);
        assertTrue(formats.contains("pptx"));
        assertTrue(formats.contains("ppt"));
        assertTrue(formats.contains("odp"));
    }
    
    @Test
    public void testGetSupportedSourceFormats_Image() {
        Set<String> formats = fileTypeMapper.getSupportedSourceFormats(FileCategory.IMAGE);
        assertNotNull(formats);
        assertTrue(formats.contains("png"));
        assertTrue(formats.contains("jpg"));
        assertTrue(formats.contains("tiff"));
        assertTrue(formats.contains("bmp"));
    }
    
    @Test
    public void testGetSupportedSourceFormats_Ofd() {
        Set<String> formats = fileTypeMapper.getSupportedSourceFormats(FileCategory.OFD);
        assertNotNull(formats);
        assertTrue(formats.contains("ofd"));
    }
    
    // ==================== 目标格式测试 ====================
    
    @Test
    public void testGetSupportedTargetFormats_Document() {
        Set<String> formats = fileTypeMapper.getSupportedTargetFormats(FileCategory.DOCUMENT);
        assertNotNull(formats);
        assertTrue(formats.contains("pdf"));
        assertTrue(formats.contains("png"));
        assertTrue(formats.contains("html"));
        assertTrue(formats.contains("docx"));
    }
    
    @Test
    public void testGetSupportedTargetFormats_Spreadsheet() {
        Set<String> formats = fileTypeMapper.getSupportedTargetFormats(FileCategory.SPREADSHEET);
        assertNotNull(formats);
        assertTrue(formats.contains("pdf"));
        assertTrue(formats.contains("xlsx"));
        assertTrue(formats.contains("csv"));
        assertTrue(formats.contains("png"));
    }
    
    @Test
    public void testGetSupportedTargetFormats_Presentation() {
        Set<String> formats = fileTypeMapper.getSupportedTargetFormats(FileCategory.PRESENTATION);
        assertNotNull(formats);
        assertTrue(formats.contains("pdf"));
        assertTrue(formats.contains("png"));
        assertTrue(formats.contains("pptx"));
    }
    
    @Test
    public void testGetSupportedTargetFormats_Image() {
        Set<String> formats = fileTypeMapper.getSupportedTargetFormats(FileCategory.IMAGE);
        assertNotNull(formats);
        assertTrue(formats.contains("png"));
        assertTrue(formats.contains("jpg"));
        assertTrue(formats.contains("pdf"));
    }
    
    @Test
    public void testGetSupportedTargetFormats_Ofd() {
        Set<String> formats = fileTypeMapper.getSupportedTargetFormats(FileCategory.OFD);
        assertNotNull(formats);
        assertTrue(formats.contains("pdf"));
        assertTrue(formats.contains("png"));
        assertTrue(formats.contains("html"));
    }
    
    // ==================== 根据扩展名获取格式测试 ====================
    
    @Test
    public void testGetSupportedFormatsByExtension_Docx() {
        Set<String> sourceFormats = fileTypeMapper.getSupportedSourceFormatsByExtension("docx");
        Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormatsByExtension("docx");
        
        assertNotNull(sourceFormats);
        assertNotNull(targetFormats);
        assertTrue(sourceFormats.contains("docx"));
        assertTrue(targetFormats.contains("pdf"));
    }
    
    @Test
    public void testGetSupportedFormatsByExtension_Xlsx() {
        Set<String> sourceFormats = fileTypeMapper.getSupportedSourceFormatsByExtension("xlsx");
        Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormatsByExtension("xlsx");
        
        assertNotNull(sourceFormats);
        assertNotNull(targetFormats);
        assertTrue(sourceFormats.contains("xlsx"));
        assertTrue(targetFormats.contains("pdf"));
    }
    
    @Test
    public void testGetSupportedFormatsByExtension_Unknown() {
        Set<String> sourceFormats = fileTypeMapper.getSupportedSourceFormatsByExtension("unknown");
        Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormatsByExtension("unknown");
        
        assertNotNull(sourceFormats);
        assertNotNull(targetFormats);
        assertTrue(sourceFormats.isEmpty());
        assertTrue(targetFormats.isEmpty());
    }
    
    // ==================== 转换支持检查测试 ====================
    
    @Test
    public void testIsConversionSupported_DocumentToPdf() {
        assertTrue(fileTypeMapper.isConversionSupported(
            FileCategory.DOCUMENT, "docx", "pdf"));
        assertTrue(fileTypeMapper.isConversionSupported(
            FileCategory.DOCUMENT, "doc", "png"));
    }
    
    @Test
    public void testIsConversionSupported_SpreadsheetToPdf() {
        assertTrue(fileTypeMapper.isConversionSupported(
            FileCategory.SPREADSHEET, "xlsx", "pdf"));
        assertTrue(fileTypeMapper.isConversionSupported(
            FileCategory.SPREADSHEET, "csv", "xlsx"));
    }
    
    @Test
    public void testIsConversionSupported_PresentationToPdf() {
        assertTrue(fileTypeMapper.isConversionSupported(
            FileCategory.PRESENTATION, "pptx", "pdf"));
        assertTrue(fileTypeMapper.isConversionSupported(
            FileCategory.PRESENTATION, "ppt", "png"));
    }
    
    @Test
    public void testIsConversionSupported_ImageToPng() {
        assertTrue(fileTypeMapper.isConversionSupported(
            FileCategory.IMAGE, "tiff", "png"));
        assertTrue(fileTypeMapper.isConversionSupported(
            FileCategory.IMAGE, "jpg", "pdf"));
    }
    
    @Test
    public void testIsConversionSupported_OfdToPdf() {
        assertTrue(fileTypeMapper.isConversionSupported(
            FileCategory.OFD, "ofd", "pdf"));
        assertTrue(fileTypeMapper.isConversionSupported(
            FileCategory.OFD, "ofd", "png"));
    }
    
    @Test
    public void testIsConversionSupported_UnsupportedConversion() {
        assertFalse(fileTypeMapper.isConversionSupported(
            FileCategory.DOCUMENT, "docx", "mp4"));
        assertFalse(fileTypeMapper.isConversionSupported(
            FileCategory.IMAGE, "png", "docx"));
    }
    
    @Test
    public void testIsConversionSupported_NullParameters() {
        assertFalse(fileTypeMapper.isConversionSupported(null, "docx", "pdf"));
        assertFalse(fileTypeMapper.isConversionSupported(FileCategory.DOCUMENT, null, "pdf"));
        assertFalse(fileTypeMapper.isConversionSupported(FileCategory.DOCUMENT, "docx", null));
    }
    
    // ==================== 根据扩展名检查转换支持测试 ====================
    
    @Test
    public void testIsConversionSupportedByExtension() {
        assertTrue(fileTypeMapper.isConversionSupportedByExtension("docx", "docx", "pdf"));
        assertTrue(fileTypeMapper.isConversionSupportedByExtension("xlsx", "xlsx", "pdf"));
        assertTrue(fileTypeMapper.isConversionSupportedByExtension("pptx", "pptx", "pdf"));
        assertTrue(fileTypeMapper.isConversionSupportedByExtension("png", "png", "jpg"));
    }
    
    @Test
    public void testIsConversionSupportedByExtension_CaseInsensitive() {
        assertTrue(fileTypeMapper.isConversionSupportedByExtension("DOCX", "docx", "PDF"));
        assertTrue(fileTypeMapper.isConversionSupportedByExtension("XlSx", "XLSX", "pdf"));
    }
    
    // ==================== 源格式和目标格式独立检查测试 ====================
    
    @Test
    public void testIsSourceFormatSupported() {
        assertTrue(fileTypeMapper.isSourceFormatSupported(FileCategory.DOCUMENT, "docx"));
        assertTrue(fileTypeMapper.isSourceFormatSupported(FileCategory.SPREADSHEET, "xlsx"));
        assertTrue(fileTypeMapper.isSourceFormatSupported(FileCategory.PRESENTATION, "pptx"));
        assertTrue(fileTypeMapper.isSourceFormatSupported(FileCategory.IMAGE, "png"));
        assertFalse(fileTypeMapper.isSourceFormatSupported(FileCategory.DOCUMENT, "mp4"));
    }
    
    @Test
    public void testIsTargetFormatSupported() {
        assertTrue(fileTypeMapper.isTargetFormatSupported(FileCategory.DOCUMENT, "pdf"));
        assertTrue(fileTypeMapper.isTargetFormatSupported(FileCategory.SPREADSHEET, "xlsx"));
        assertTrue(fileTypeMapper.isTargetFormatSupported(FileCategory.PRESENTATION, "png"));
        assertTrue(fileTypeMapper.isTargetFormatSupported(FileCategory.IMAGE, "jpg"));
        assertFalse(fileTypeMapper.isTargetFormatSupported(FileCategory.DOCUMENT, "mp4"));
    }
    
    // ==================== 格式能力映射测试 ====================
    
    @Test
    public void testGetAllFormatCapabilities() {
        Map<FileCategory, FileTypeMapper.FormatCapability> capabilities = 
            fileTypeMapper.getAllFormatCapabilities();
        
        assertNotNull(capabilities);
        assertTrue(capabilities.size() > 0);
        
        // 验证文档类别
        assertTrue(capabilities.containsKey(FileCategory.DOCUMENT));
        FileTypeMapper.FormatCapability docCapability = capabilities.get(FileCategory.DOCUMENT);
        assertNotNull(docCapability);
        assertTrue(docCapability.getSourceFormats().contains("docx"));
        assertTrue(docCapability.getTargetFormats().contains("pdf"));
        
        // 验证表格类别
        assertTrue(capabilities.containsKey(FileCategory.SPREADSHEET));
        FileTypeMapper.FormatCapability excelCapability = capabilities.get(FileCategory.SPREADSHEET);
        assertNotNull(excelCapability);
        assertTrue(excelCapability.getSourceFormats().contains("xlsx"));
        assertTrue(excelCapability.getTargetFormats().contains("pdf"));
    }
    
    @Test
    public void testFormatCapability_Immutability() {
        Map<FileCategory, FileTypeMapper.FormatCapability> capabilities = 
            fileTypeMapper.getAllFormatCapabilities();
        
        FileTypeMapper.FormatCapability capability = capabilities.get(FileCategory.DOCUMENT);
        assertNotNull(capability);
        
        // 验证返回的集合是不可修改的
        assertThrows(UnsupportedOperationException.class, () -> {
            capability.getSourceFormats().add("test");
        });
        
        assertThrows(UnsupportedOperationException.class, () -> {
            capability.getTargetFormats().add("test");
        });
    }
    
    // ==================== 动态注册测试 ====================
    
    @Test
    public void testRegisterFormatCapability() {
        // 注册自定义格式能力
        fileTypeMapper.registerFormatCapability(
            FileCategory.IMAGE,
            java.util.Arrays.asList("webp", "avif"),
            java.util.Arrays.asList("webp", "avif")
        );
        
        // 验证注册成功
        Set<String> sourceFormats = fileTypeMapper.getSupportedSourceFormats(FileCategory.IMAGE);
        Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormats(FileCategory.IMAGE);
        
        assertTrue(sourceFormats.contains("webp"));
        assertTrue(sourceFormats.contains("avif"));
        assertTrue(targetFormats.contains("webp"));
        assertTrue(targetFormats.contains("avif"));
    }
    
    // ==================== 集成测试 ====================
    
    @Test
    public void testIntegration_CompleteWorkflow() {
        // 1. 通过扩展名识别类别
        FileCategory category = fileTypeMapper.getFileCategory("docx");
        assertEquals(FileCategory.DOCUMENT, category);
        
        // 2. 获取该类别支持的格式
        Set<String> sourceFormats = fileTypeMapper.getSupportedSourceFormats(category);
        Set<String> targetFormats = fileTypeMapper.getSupportedTargetFormats(category);
        
        assertTrue(sourceFormats.contains("docx"));
        assertTrue(targetFormats.contains("pdf"));
        
        // 3. 检查转换是否支持
        boolean supported = fileTypeMapper.isConversionSupported(category, "docx", "pdf");
        assertTrue(supported);
        
        // 4. 直接通过扩展名检查
        boolean supportedByExtension = fileTypeMapper.isConversionSupportedByExtension(
            "docx", "docx", "pdf");
        assertTrue(supportedByExtension);
    }
    
    @Test
    public void testGetSupportedSourceFormats_ReturnImmutableSet() {
        Set<String> formats = fileTypeMapper.getSupportedSourceFormats(FileCategory.DOCUMENT);
        
        assertThrows(UnsupportedOperationException.class, () -> {
            formats.add("test");
        });
    }
    
    @Test
    public void testGetSupportedTargetFormats_ReturnImmutableSet() {
        Set<String> formats = fileTypeMapper.getSupportedTargetFormats(FileCategory.DOCUMENT);
        
        assertThrows(UnsupportedOperationException.class, () -> {
            formats.add("test");
        });
    }
}
