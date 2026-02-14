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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 简单测试中文字符在JSON输出中的正确显示
 */
public class ChineseJsonTest {

    @TempDir
    Path tempDir;

    @Test
    void testChineseCharactersNotDisplayedAsQuestionMarks() throws IOException {
        System.out.println("=== 测试中文字符在JSON中不显示为问号 ===");
        
        // 创建包含中文文件名的ZIP文件
        File testZip = createSimpleChineseZip("测试文件.zip");
        ArchiveConvertStrategy strategy = new ArchiveConvertStrategy();
        
        String outputDir = tempDir.toString();
        System.out.println("ZIP文件: " + testZip.getName());
        
        // 执行转换
        boolean result = strategy.convert(
            testZip.getAbsolutePath(),
            outputDir,
            "result",
            "json"
        );
        
        assertTrue(result, "转换应该成功");
        
        // 读取JSON文件内容
        File outputFile = new File(outputDir, "result.json");
        assertTrue(outputFile.exists(), "JSON文件应该存在");
        
        String jsonContent = Files.readString(outputFile.toPath(), StandardCharsets.UTF_8);
        System.out.println("JSON内容预览:");
        System.out.println(jsonContent.substring(0, Math.min(500, jsonContent.length())));
        
        // 验证包含中文字符
        assertTrue(jsonContent.contains("中文"), "JSON应该包含中文字符");
        assertTrue(jsonContent.contains("测试"), "JSON应该包含测试字符");
        
        // 验证没有问号乱码
        assertFalse(jsonContent.contains("??????"), "不应该有连续问号");
        assertFalse(jsonContent.contains("????"), "不应该有问号乱码");
        
        // 计算问号数量，应该很少或没有
        long questionMarkCount = jsonContent.chars().filter(ch -> ch == '?').count();
        System.out.println("问号数量: " + questionMarkCount);
        
        // 允许少量问号（可能来自其他地方），但不应该有大量问号
        assertTrue(questionMarkCount < 10, "问号数量应该很少，不应该有大量乱码问号");
        
        System.out.println("✅ 测试通过：JSON文件正确显示中文字符，无明显乱码问题");
    }

    private File createSimpleChineseZip(String fileName) throws IOException {
        File zipFile = tempDir.resolve(fileName).toFile();
        
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            
            // 简单的中文文件
            ZipEntry entry1 = new ZipEntry("中文文档.txt");
            zos.putNextEntry(entry1);
            zos.write("简单的中文内容".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            
            // 中文目录
            ZipEntry entry2 = new ZipEntry("测试目录/");
            zos.putNextEntry(entry2);
            zos.closeEntry();
            
            // 英文文件（对比）
            ZipEntry entry3 = new ZipEntry("english.txt");
            zos.putNextEntry(entry3);
            zos.write("English content".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        
        return zipFile;
    }
}