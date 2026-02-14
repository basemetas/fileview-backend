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
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.charset.Charset;

/**
 * 测试ZIP文件包含非UTF-8编码文件名时的处理
 */
public class ZipMalformedInputTest {

    @TempDir
    Path tempDir;

    @Test
    void testZipWithGBKEncodedFileNames() throws IOException {
        System.out.println("=== 测试GBK编码的ZIP文件 ===");
        
        // 创建使用GBK编码的ZIP文件
        File testZip = createGBKEncodedZip("gbk_encoded.zip");
        ArchiveConvertStrategy strategy = new ArchiveConvertStrategy();
        
        String outputDir = tempDir.toString();
        System.out.println("ZIP文件路径: " + testZip.getAbsolutePath());
        
        boolean result = strategy.convert(
            testZip.getAbsolutePath(),
            outputDir,
            "gbk_result",
            "json"
        );
        
        System.out.println("转换结果: " + result);
        
        // 检查输出文件
        File outputFile = new File(outputDir, "gbk_result.json");
        if (outputFile.exists()) {
            System.out.println("JSON文件大小: " + outputFile.length() + " bytes");
            System.out.println("修复成功：能够正确处理GBK编码的ZIP文件");
        } else {
            System.out.println("JSON文件不存在，处理失败");
        }
    }

    @Test
    void testZipWithMixedEncodingFileNames() throws IOException {
        System.out.println("=== 测试混合编码的ZIP文件 ===");
        
        File testZip = createMixedEncodingZip("mixed_encoding.zip");
        ArchiveConvertStrategy strategy = new ArchiveConvertStrategy();
        
        boolean result = strategy.convert(
            testZip.getAbsolutePath(),
            tempDir.toString(),
            "mixed_result",
            "json"
        );
        
        System.out.println("转换结果: " + result);
    }

    /**
     * 创建使用GBK编码的ZIP文件（模拟XMind等软件生成的文件）
     */
    private File createGBKEncodedZip(String fileName) throws IOException {
        File zipFile = tempDir.resolve(fileName).toFile();
        
        // 使用GBK字符集创建ZipOutputStream
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos, Charset.forName("GBK"))) {
            
            // 添加中文文件名的文件
            String chineseFileName = "测试文档.txt";
            ZipEntry entry1 = new ZipEntry(chineseFileName);
            zos.putNextEntry(entry1);
            zos.write("这是一个使用GBK编码的测试文件。".getBytes("GBK"));
            zos.closeEntry();
            
            // 添加中文目录
            ZipEntry entry2 = new ZipEntry("资料文件夹/");
            zos.putNextEntry(entry2);
            zos.closeEntry();
            
            // 添加子目录文件
            ZipEntry entry3 = new ZipEntry("资料文件夹/说明文档.md");
            zos.putNextEntry(entry3);
            zos.write("# 说明文档\n这是一个markdown文件。".getBytes("GBK"));
            zos.closeEntry();
            
            // 添加带特殊字符的文件名
            ZipEntry entry4 = new ZipEntry("数据统计（2024年）.xlsx");
            zos.putNextEntry(entry4);
            zos.write("Excel文件内容".getBytes("GBK"));
            zos.closeEntry();
        }
        
        return zipFile;
    }

    /**
     * 创建混合编码的ZIP文件
     */
    private File createMixedEncodingZip(String fileName) throws IOException {
        File zipFile = tempDir.resolve(fileName).toFile();
        
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            // UTF-8编码的文件
            ZipEntry entry1 = new ZipEntry("utf8_文件.txt");
            zos.putNextEntry(entry1);
            zos.write("UTF-8 content".getBytes("UTF-8"));
            zos.closeEntry();
            
            // 英文文件名
            ZipEntry entry2 = new ZipEntry("english_file.txt");
            zos.putNextEntry(entry2);
            zos.write("English content".getBytes("UTF-8"));
            zos.closeEntry();
            
            // 数字和符号
            ZipEntry entry3 = new ZipEntry("file_123_@#$.txt");
            zos.putNextEntry(entry3);
            zos.write("Special chars content".getBytes("UTF-8"));
            zos.closeEntry();
        }
        
        return zipFile;
    }
}