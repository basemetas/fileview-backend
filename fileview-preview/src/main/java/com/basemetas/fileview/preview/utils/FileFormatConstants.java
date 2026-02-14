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
package com.basemetas.fileview.preview.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件格式常量定义
 * 统一管理各类文件格式的分类和判断逻辑
 */
public class FileFormatConstants {

    // ==================== Office OOXML 格式（基于ZIP）====================
    
    /** OOXML 格式集合 */
    private static final Set<String> OOXML_FORMATS = new HashSet<>(Arrays.asList(
        "docx", "xlsx", "pptx",
        "docm", "xlsm", "pptm"
    ));

    // ==================== Office OLE2 二进制格式 ====================
    
    /** OLE2 格式集合 */
    private static final Set<String> OLE2_FORMATS = new HashSet<>(Arrays.asList(
        "doc", "xls", "ppt"
    ));

    // ==================== WPS Office 格式（基于OLE2）====================
    
    /** WPS 文字格式 */
    private static final Set<String> WPS_WORD_FORMATS = new HashSet<>(Arrays.asList(
        "wps", "wpt"
    ));

    /** WPS 表格格式 */
    private static final Set<String> WPS_EXCEL_FORMATS = new HashSet<>(Arrays.asList(
        "et", "ett"
    ));

    /** WPS 演示格式 */
    private static final Set<String> WPS_PPT_FORMATS = new HashSet<>(Arrays.asList(
        "dps", "dpt"
    ));

    /** WPS 所有格式 */
    private static final Set<String> WPS_ALL_FORMATS = new HashSet<>();
    static {
        WPS_ALL_FORMATS.addAll(WPS_WORD_FORMATS);
        WPS_ALL_FORMATS.addAll(WPS_EXCEL_FORMATS);
        WPS_ALL_FORMATS.addAll(WPS_PPT_FORMATS);
    }

    // ==================== Office 文档组合分类 ====================
    
    /** 所有 Office 文档格式（OOXML + OLE2 + WPS） */
    private static final Set<String> ALL_OFFICE_FORMATS = new HashSet<>();
    static {
        ALL_OFFICE_FORMATS.addAll(OOXML_FORMATS);
        ALL_OFFICE_FORMATS.addAll(OLE2_FORMATS);
        ALL_OFFICE_FORMATS.addAll(WPS_ALL_FORMATS);
    }

    /** 旧式加密格式（无法直接验证密码，需转换引擎处理） */
    private static final Set<String> LEGACY_ENCRYPTED_FORMATS = new HashSet<>();
    static {
        LEGACY_ENCRYPTED_FORMATS.add("doc");
        LEGACY_ENCRYPTED_FORMATS.add("ppt");
        LEGACY_ENCRYPTED_FORMATS.addAll(WPS_ALL_FORMATS);
    }

    /** 压缩包格式 */
    private static final Set<String> ARCHIVE_FORMATS = new HashSet<>(Arrays.asList(
        "zip", "rar", "7z", "tar", "tar.gz", "tgz", "jar", "war", "ear"
    ));

    // ==================== 公共判断方法 ====================

    /**
     * 判断是否为 OOXML 格式
     */
    public static boolean isOoxmlFormat(String format) {
        return format != null && OOXML_FORMATS.contains(format.toLowerCase());
    }

    /**
     * 判断是否为 OLE2 格式（不含 WPS）
     */
    public static boolean isOle2Format(String format) {
        return format != null && OLE2_FORMATS.contains(format.toLowerCase());
    }

    /**
     * 判断是否为 WPS 格式
     */
    public static boolean isWpsFormat(String format) {
        return format != null && WPS_ALL_FORMATS.contains(format.toLowerCase());
    }

    /**
     * 判断是否为 WPS 文字格式
     */
    public static boolean isWpsWordFormat(String format) {
        return format != null && WPS_WORD_FORMATS.contains(format.toLowerCase());
    }

    /**
     * 判断是否为 WPS 表格格式
     */
    public static boolean isWpsExcelFormat(String format) {
        return format != null && WPS_EXCEL_FORMATS.contains(format.toLowerCase());
    }

    /**
     * 判断是否为 WPS 演示格式
     */
    public static boolean isWpsPptFormat(String format) {
        return format != null && WPS_PPT_FORMATS.contains(format.toLowerCase());
    }

    /**
     * 判断是否为 Office 文档（包括 OOXML、OLE2、WPS）
     */
    public static boolean isOfficeDocument(String format) {
        return format != null && ALL_OFFICE_FORMATS.contains(format.toLowerCase());
    }

    /**
     * 判断是否为旧式加密格式（无法直接验证密码）
     * 包括：doc、ppt 和所有 WPS 格式
     */
    public static boolean isLegacyEncryptedFormat(String format) {
        return format != null && LEGACY_ENCRYPTED_FORMATS.contains(format.toLowerCase());
    }

    /**
     * 判断是否为压缩包格式
     */
    public static boolean isArchiveFormat(String format) {
        return format != null && ARCHIVE_FORMATS.contains(format.toLowerCase());
    }

    /**
     * 获取文件类型描述（用于日志）
     */
    public static String getFileTypeDescription(String format) {
        if (format == null) {
            return "Unknown";
        }
        String lower = format.toLowerCase();
        if ("doc".equals(lower)) return "Word";
        if ("ppt".equals(lower)) return "PPT";
        if ("xls".equals(lower)) return "Excel";
        if (WPS_WORD_FORMATS.contains(lower)) return "WPS文字";
        if (WPS_EXCEL_FORMATS.contains(lower)) return "WPS表格";
        if (WPS_PPT_FORMATS.contains(lower)) return "WPS演示";
        if (OOXML_FORMATS.contains(lower)) return "Office";
        return format.toUpperCase();
    }
}
