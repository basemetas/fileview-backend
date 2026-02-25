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
package com.basemetas.fileview.convert.strategy.impl;

import com.basemetas.fileview.convert.strategy.model.ArchiveInfo;
import com.basemetas.fileview.convert.strategy.model.ArchiveEntryInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通用压缩文件转换策略单元测试
 */
class UniversalArchiveConvertStrategyTest {
    
    private ArchiveConvertStrategy universalArchiveStrategy;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        universalArchiveStrategy = new ArchiveConvertStrategy();
    }
    
    @Test
    void testGetSupportedFileType() {
        assertEquals("archive", universalArchiveStrategy.getSupportedFormats());
    }
    
    @Test
    void testGetSupportedFormats() {
        assertNotNull(universalArchiveStrategy.getSupportedFormats());
        assertTrue(universalArchiveStrategy.getSupportedFormats().contains("zip"));
        assertTrue(universalArchiveStrategy.getSupportedFormats().contains("jar"));
        assertTrue(universalArchiveStrategy.getSupportedFormats().contains("tar"));
        assertTrue(universalArchiveStrategy.getSupportedFormats().contains("tar.gz"));
        assertTrue(universalArchiveStrategy.getSupportedFormats().contains("rar"));
        assertTrue(universalArchiveStrategy.getSupportedFormats().contains("7z"));
    }
    
    @Test
    void testConvertZipToJson() throws IOException {
        // 创建测试ZIP文件
        File testZip = createTestZipFile("test.zip");
        
        // 设置输出目录
        String outputDir = tempDir.toString();
        
        // 执行转换
        boolean result = universalArchiveStrategy.convert(
            testZip.getAbsolutePath(),
            outputDir,
            "test_zip_archive",
            "json"
        );
        
        // 验证结果
        assertTrue(result, "ZIP转换应该成功");
        
        // 检查输出文件是否存在
        File outputFile = new File(outputDir, "test_zip_archive.json");
        assertTrue(outputFile.exists(), "输出JSON文件应该存在");
        assertTrue(outputFile.length() > 0, "输出JSON文件不应为空");
    }
    
    @Test
    void testConvertJarToJson() throws IOException {
        // 创建测试JAR文件
        File testJar = createTestZipFile("test.jar");
        
        // 设置输出目录
        String outputDir = tempDir.toString();
        
        // 执行转换
        boolean result = universalArchiveStrategy.convert(
            testJar.getAbsolutePath(),
            outputDir,
            "test_jar_archive",
            "json"
        );
        
        // 验证结果
        assertTrue(result, "JAR转换应该成功");
        
        // 检查输出文件是否存在
        File outputFile = new File(outputDir, "test_jar_archive.json");
        assertTrue(outputFile.exists(), "输出JSON文件应该存在");
    }
    
    @Test
    void testConvertNonExistentFile() {
        String nonExistentFile = tempDir.resolve("nonexistent.zip").toString();
        String outputDir = tempDir.toString();
        
        boolean result = universalArchiveStrategy.convert(
            nonExistentFile,
            outputDir,
            "test",
            "json"
        );
        
        assertFalse(result, "转换不存在的文件应该失败");
    }
    
    @Test
    void testConvertUnsupportedFormat() throws IOException {
        // 创建一个文本文件而不是压缩文件
        File textFile = tempDir.resolve("test.txt").toFile();
        boolean fileCreated = textFile.createNewFile();
        assertTrue(fileCreated, "测试文件应该成功创建");
        
        String outputDir = tempDir.toString();
        
        boolean result = universalArchiveStrategy.convert(
            textFile.getAbsolutePath(),
            outputDir,
            "test",
            "json"
        );
        
        assertFalse(result, "转换不支持的格式应该失败");
    }
    
    @Test
    void testArchiveInfoClass() {
        ArchiveInfo info = new ArchiveInfo();
        
        // 测试基本属性
        info.setFileName("test.zip");
        info.setFileSize(1024);
        info.setTotalEntries(10);
        info.setFileCount(8);
        info.setDirectoryCount(2);
        info.setCompressionRatio(0.6);
        
        assertEquals("test.zip", info.getFileName());
        assertEquals(1024, info.getFileSize());
        assertEquals(10, info.getTotalEntries());
        assertEquals(8, info.getFileCount());
        assertEquals(2, info.getDirectoryCount());
        assertEquals(0.6, info.getCompressionRatio(), 0.01);
        
        // 测试toString方法
        String infoString = info.toString();
        assertNotNull(infoString);
        assertTrue(infoString.contains("test.zip"));
    }
    
    @Test
    void testArchiveEntryInfoClass() {
        ArchiveEntryInfo entry = new ArchiveEntryInfo();
        
        // 测试基本属性
        entry.setName("src/main/java/Test.java");
        entry.setSize(2048);
        entry.setCompressedSize(1024);
        entry.setDirectory(false);
        entry.setFileType("Java Source");
        entry.setMethod("DEFLATED");
        entry.setCrc(123456789L);
        
        assertEquals("src/main/java/Test.java", entry.getName());
        assertEquals(2048, entry.getSize());
        assertEquals(1024, entry.getCompressedSize());
        assertFalse(entry.isDirectory());
        assertEquals("Java Source", entry.getFileType());
        assertEquals("DEFLATED", entry.getMethod());
        assertEquals(123456789L, entry.getCrc());
        
        // 测试计算压缩比
        assertEquals(0.5, entry.getCompressionRatio(), 0.01);
        
        // 测试获取文件扩展名
        assertEquals("java", entry.getFileExtension());
        
        // 测试获取简单文件名
        assertEquals("Test.java", entry.getSimpleName());
        
        // 测试格式化大小
        assertNotNull(entry.getFormattedSize());
        assertNotNull(entry.getFormattedCompressedSize());
        
        // 测试toString方法
        String entryString = entry.toString();
        assertNotNull(entryString);
        assertTrue(entryString.contains("Test.java"));
    }
    
    @Test
    void testConvertWithDefaultParameters() throws IOException {
        // 创建测试ZIP文件
        File testZip = createTestZipFile("test_default.zip");
        
        // 设置输出目录
        String outputDir = tempDir.toString();
        
        // 使用默认参数执行转换
        boolean result = universalArchiveStrategy.convert(
            testZip.getAbsolutePath(),
            outputDir
        );
        
        // 验证结果
        assertTrue(result, "使用默认参数的转换应该成功");
        
        // 检查输出文件是否存在（默认文件名）
        File outputFile = new File(outputDir, "archive_content.json");
        assertTrue(outputFile.exists(), "默认输出JSON文件应该存在");
    }
    
    /**
     * 创建测试用的ZIP文件
     */
    private File createTestZipFile(String fileName) throws IOException {
        File zipFile = tempDir.resolve(fileName).toFile();
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // 添加一个目录
            ZipEntry dirEntry = new ZipEntry("test-dir/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();
            
            // 添加一个文本文件
            ZipEntry fileEntry = new ZipEntry("test-dir/readme.txt");
            zos.putNextEntry(fileEntry);
            zos.write("This is a test file content for universal archive converter.".getBytes());
            zos.closeEntry();
            
            // 添加一个Java文件
            ZipEntry javaEntry = new ZipEntry("src/main/java/com/example/Test.java");
            zos.putNextEntry(javaEntry);
            String javaContent = "package com.example;\n\n" +
                               "public class Test {\n" +
                               "    public static void main(String[] args) {\n" +
                               "        System.out.println(\"Hello Universal Archive!\");\n" +
                               "    }\n" +
                               "}";
            zos.write(javaContent.getBytes());
            zos.closeEntry();
            
            // 添加一个JSON配置文件
            ZipEntry jsonEntry = new ZipEntry("config/application.json");
            zos.putNextEntry(jsonEntry);
            String jsonContent = "{\n" +
                               "  \"name\": \"Universal Archive Test\",\n" +
                               "  \"version\": \"1.0.0\",\n" +
                               "  \"supportedFormats\": [\"ZIP\", \"JAR\", \"TAR\", \"RAR\", \"7Z\"]\n" +
                               "}";
            zos.write(jsonContent.getBytes());
            zos.closeEntry();
            
            // 添加一个空的子目录
            ZipEntry subDirEntry = new ZipEntry("test-dir/subdir/");
            zos.putNextEntry(subDirEntry);
            zos.closeEntry();
        }
        
        return zipFile;
    }
}