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
package com.basemetas.fileview.preview.service.password;

import net.lingala.zip4j.ZipFile;
import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.basemetas.fileview.preview.config.FileFormatConfig;
import com.basemetas.fileview.preview.utils.EnvironmentUtils;
import com.basemetas.fileview.preview.utils.FileUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.RecordFactoryInputStream;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.hssf.record.crypto.Biff8EncryptionKey;
import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.poifs.filesystem.OfficeXmlFileException;

/**
 * 文件密码验证服务
 * 
 * 提供文件加密状态检查和密码验证功能
 * 支持格式：
 * - 压缩包：ZIP、RAR、7Z
 * - Office文档：DOCX、XLSX、PPTX（OOXML格式）、DOC、XLS、PPT（OLE2格式）
 * - PDF文档
 * 
 * @author 夫子
 */
@Service
public class FilePasswordValidator {

    private static final Logger logger = LoggerFactory.getLogger(FilePasswordValidator.class);

    // native 调用全局锁,减少并发调用导致的崩溃（参考转换模块）
    private static final Object NATIVE_LOCK = new Object();

    @Autowired
    private FileUtils fileUtils;

    @Autowired
    private FileFormatConfig fileFormatConfig;

    /**
     * 密码验证结果
     */
    public static class PasswordValidationResult {
        private boolean encrypted;
        private Boolean passwordCorrect; // null表示未提供密码或未测试
        private String archiveFormat;
        private String errorMessage;

        public static PasswordValidationResult notEncrypted(String format) {
            PasswordValidationResult result = new PasswordValidationResult();
            result.encrypted = false;
            result.archiveFormat = format;
            return result;
        }

        public static PasswordValidationResult passwordRequired(String format) {
            PasswordValidationResult result = new PasswordValidationResult();
            result.encrypted = true;
            result.passwordCorrect = false;
            result.archiveFormat = format;
            result.errorMessage = "压缩包已加密，需要提供密码";
            return result;
        }

        public static PasswordValidationResult passwordCorrect(String format) {
            PasswordValidationResult result = new PasswordValidationResult();
            result.encrypted = true;
            result.passwordCorrect = true;
            result.archiveFormat = format;
            return result;
        }

        public static PasswordValidationResult passwordIncorrect(String format, String message) {
            PasswordValidationResult result = new PasswordValidationResult();
            result.encrypted = true;
            result.passwordCorrect = false;
            result.archiveFormat = format;
            result.errorMessage = message != null ? message : "密码错误";
            return result;
        }

        /**
         * 仅标记为加密，但无法确定密码是否正确（用于旧式加密只做加密检测的场景）
         */
        public static PasswordValidationResult encryptedUnknown(String format) {
            PasswordValidationResult result = new PasswordValidationResult();
            result.encrypted = true;
            result.passwordCorrect = null;
            result.archiveFormat = format;
            return result;
        }

        public static PasswordValidationResult error(String format, String message) {
            PasswordValidationResult result = new PasswordValidationResult();
            result.archiveFormat = format;
            result.errorMessage = message;
            return result;
        }

        // Getters
        public boolean isEncrypted() {
            return encrypted;
        }

        public Boolean isPasswordCorrect() {
            return passwordCorrect;
        }

        public String getArchiveFormat() {
            return archiveFormat;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * 验证文件密码（支持压缩包、Office文档、PDF）
     * 
     * @param filePath 文件路径
     * @param password 密码（可为null，用于检查是否加密）
     * @return 验证结果
     */
    public PasswordValidationResult validatePassword(String filePath, String password) {
        return validatePassword(filePath, password, null);
    }

    /**
     * 验证文件密码（支持压缩包、Office文档、PDF）
     * 
     * @param filePath      文件路径
     * @param password      密码（可为null，用于检查是否加密）
     * @param fileExtension 文件扩展名（可选，如果为null则从文件名自动检测）
     * @return 验证结果
     */
    public PasswordValidationResult validatePassword(String filePath, String password, String fileExtension) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return PasswordValidationResult.error("unknown", "文件路径不能为空");
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return PasswordValidationResult.error("unknown", "文件不存在");
        }

        // 检测文件格式
        String format = fileExtension != null ? fileExtension.toLowerCase()
                : fileUtils.detectFileFormat(file.getName());
        logger.debug("检测文件格式: {} - {}", file.getName(), format);

        switch (format.toLowerCase()) {
            // 压缩包格式
            case "zip":
            case "jar":
            case "war":
            case "ear":
                return validateZipPassword(file, password, format);
            case "rar":
                return validateRarPassword(file, password, format);
            case "7z":
                return validate7zPassword(file, password, format);
            case "tar":
            case "tar.gz":
            case "tgz":
                // TAR类格式不支持密码加密
                return PasswordValidationResult.notEncrypted(format);

            // Office OOXML 格式（基于ZIP）
            case "docx":
            case "xlsx":
            case "pptx":
            case "docm":
            case "xlsm":
            case "pptm":
                return validateOoxmlPassword(file, password, format);

            // Office 二进制格式（OLE2）
            case "doc":
            case "xls":
            case "ppt":
                // WPS 格式（基于OLE2结构）
            case "wps": // WPS文字（类似DOC）
            case "wpt": // WPS模板
            case "et": // WPS表格（类似XLS）
            case "ett": // WPS表格模板
            case "dps": // WPS演示（类似PPT）
            case "dpt": // WPS演示模板
                return validateOle2Password(file, password, format);

            // PDF 格式
            // case "pdf":
            // return validatePdfPassword(file, password, format);

            default:
                return PasswordValidationResult.error(format, "不支持的文件格式密码检测");
        }
    }

    /**
     * 🚀 轻量级加密检测（只检测是否加密，不验证密码）
     * 
     * 性能优化：用于首次预览场景，快速判断文件是否需要密码
     * 如果检测到加密，直接返回 PASSWORD_REQUIRED，不尝试验证密码
     * 
     * @param filePath      文件路径
     * @param fileExtension 文件扩展名
     * @return 检测结果（只包含是否加密，不包含密码正确性）
     */
    public PasswordValidationResult quickDetectEncryption(String filePath, String fileExtension) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return PasswordValidationResult.error("unknown", "文件路径不能为空");
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            return PasswordValidationResult.error("unknown", "文件不存在");
        }

        String format = fileExtension != null ? fileExtension.toLowerCase()
                : fileUtils.detectFileFormat(file.getName());
        logger.debug("🚀 轻量检测文件加密状态: {} - {}", file.getName(), format);

        // 传入null密码，只检测是否加密
        return validatePassword(filePath, null, fileExtension);
    }

    /**
     * 验证ZIP格式密码
     */
    private PasswordValidationResult validateZipPassword(File archiveFile, String password, String format) {
        try {
            try (ZipFile zipFile = new ZipFile(archiveFile)) {
                // 检查是否加密
                if (!zipFile.isEncrypted()) {
                    logger.debug("ZIP文件未加密: {}", archiveFile.getName());
                    return PasswordValidationResult.notEncrypted(format);
                }

                // 已加密但未提供密码
                if (password == null || password.trim().isEmpty()) {
                    logger.debug("ZIP文件已加密但未提供密码: {}", archiveFile.getName());
                    return PasswordValidationResult.passwordRequired(format);
                }

                // 设置密码并尝试读取第一个文件
                zipFile.setPassword(password.toCharArray());

                // 获取第一个非目录条目进行验证
                for (Object fhObj : zipFile.getFileHeaders()) {
                    net.lingala.zip4j.model.FileHeader fileHeader = (net.lingala.zip4j.model.FileHeader) fhObj;
                    if (!fileHeader.isDirectory()) {
                        try (java.io.InputStream is = zipFile.getInputStream(fileHeader)) {
                            // 尝试读取少量数据（1KB）以验证密码
                            byte[] buffer = new byte[1024];
                            int read = is.read(buffer);
                            if (read > 0) {
                                logger.debug("ZIP密码验证成功: {}", archiveFile.getName());
                                return PasswordValidationResult.passwordCorrect(format);
                            }
                        }
                        break; // 只测试第一个文件
                    }
                }
            }

            // 如果没有非目录文件，认为密码正确
            logger.debug("ZIP文件无可验证条目，假定密码正确: {}", archiveFile.getName());
            return PasswordValidationResult.passwordCorrect(format);

        } catch (net.lingala.zip4j.exception.ZipException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.toLowerCase().contains("wrong password") ||
                    errorMsg.toLowerCase().contains("incorrect password"))) {
                logger.warn("ZIP密码错误: {}", archiveFile.getName());
                return PasswordValidationResult.passwordIncorrect(format, "密码错误");
            }
            logger.error("ZIP文件处理异常: {}", archiveFile.getName(), e);
            return PasswordValidationResult.error(format, "ZIP文件处理失败: " + errorMsg);
        } catch (Exception e) {
            logger.error("ZIP密码验证异常: {}", archiveFile.getName(), e);
            return PasswordValidationResult.error(format, "密码验证失败: " + e.getMessage());
        }
    }

    /**
     * 验证RAR格式密码
     */
    private PasswordValidationResult validateRarPassword(File archiveFile, String password, String format) {
        return validate7zBasedPassword(archiveFile, password, format, ArchiveFormat.RAR);
    }

    /**
     * 验证7Z格式密码
     */
    private PasswordValidationResult validate7zPassword(File archiveFile, String password, String format) {
        return validate7zBasedPassword(archiveFile, password, format, ArchiveFormat.SEVEN_ZIP);
    }

    /**
     * 使用SevenZipJBinding验证密码（通用方法）
     * 参考 ArchiveExtractService 的 WSL2 安全策略
     * - WSL2环境：使用外部7z命令检测加密状态（避免native崩溃）
     * - 非WSL2：使用native库验证（加全局锁保护）
     */
    private PasswordValidationResult validate7zBasedPassword(File archiveFile, String password,
            String format, ArchiveFormat archiveFormat) {
        // Step 0: WSL2环境检测（参考 ArchiveExtractService）
        boolean runningInWsl = EnvironmentUtils.isWslEnvironment();
        if (runningInWsl) {
            logger.warn("🔒 WSL2环境检测到，使用外部7z命令检测加密状态（避免native崩溃）- File: {}", archiveFile.getName());
            // WSL2环境下使用外部7z命令检测是否加密
            return validate7zWithExternalCommand(archiveFile, password, format);
        }

        // Step 1: 非WSL2环境，使用native库验证
        // ⚠️ 安全检查：防止native崩溃
        logger.debug("准备验证{}文件密码 - File: {}, Size: {} bytes", format.toUpperCase(), archiveFile.getName(),
                archiveFile.length());

        // 文件大小检查
        if (archiveFile.length() == 0) {
            logger.warn("{}文件为空 - File: {}", format.toUpperCase(), archiveFile.getName());
            return PasswordValidationResult.error(format, "文件为空或损坏");
        }

        // 验证7z/RAR文件头魔数和版本号
        if (archiveFormat == ArchiveFormat.SEVEN_ZIP) {
            try (RandomAccessFile rafCheck = new RandomAccessFile(archiveFile, "r")) {
                if (rafCheck.length() < 32) { // 7z文件头至少32字节
                    return PasswordValidationResult.error(format, "7z文件过小，可能已损坏");
                }

                byte[] header = new byte[32];
                rafCheck.readFully(header);

                // 验证魔数 (0x377ABCAF271C)
                if (!(header[0] == 0x37 && header[1] == 0x7A && header[2] == (byte) 0xBC &&
                        header[3] == (byte) 0xAF && header[4] == 0x27 && header[5] == 0x1C)) {
                    return PasswordValidationResult.error(format, "7z文件魔数不匹配，可能已损坏");
                }

                // 验证版本号（字节6-7）
                // 根据7z规范：Major Version当前为0，Minor Version为2或4
                int majorVersion = header[6] & 0xFF;
                int minorVersion = header[7] & 0xFF;

                // Major Version必须为0（7z格式从1999年至今一直是0）
                if (majorVersion != 0) {
                    logger.warn("7z文件Major Version异常 - File: {}, Version: {}.{} (期望: 0.x)",
                            archiveFile.getName(), majorVersion, minorVersion);
                    return PasswordValidationResult.error(format, "7z文件版本不支持（Major Version应为0）");
                }

                // Minor Version应在合理范围内（当前已知版本为0-4，保守允许到20）
                if (minorVersion > 20) {
                    logger.warn("7z文件Minor Version过高 - File: {}, Version: {}.{}",
                            archiveFile.getName(), majorVersion, minorVersion);
                    return PasswordValidationResult.error(format, "7z文件Minor Version异常，可能已损坏");
                }

                logger.debug("7z文件格式验证通过 - File: {}, Version: {}.{}",
                        archiveFile.getName(), majorVersion, minorVersion);
            } catch (Exception e) {
                logger.error("7z文件格式验证失败 - File: {}", archiveFile.getName(), e);
                return PasswordValidationResult.error(format, "文件读取失败: " + e.getMessage());
            }
        }

        try (RandomAccessFile raf = new RandomAccessFile(archiveFile, "r")) {

            // 创建密码回调
            IArchiveOpenCallback openCallback = (password != null && !password.trim().isEmpty())
                    ? new PasswordOpenCallback(password)
                    : null;

            IInArchive inArchive;
            try {
                // 🔑 关键修复：使用三参数重载，显式指定归档格式，防止WSL2环境下SIGSEGV崩溃
                // 🔒 参考转换模块：使用全局锁串行化 native 调用
                synchronized (NATIVE_LOCK) {
                    inArchive = SevenZip.openInArchive(archiveFormat,
                            new RandomAccessFileInStream(raf),
                            openCallback);
                }
            } catch (Throwable nativeError) {
                // ⚠️ 捕获native层错误（包括SIGSEGV等致命错误的Java包装异常）
                logger.error("❌ SevenZipJBinding native库解析失败 - File: {}, Error: {}",
                        archiveFile.getName(), nativeError.getMessage(), nativeError);
                return PasswordValidationResult.error(format,
                        "该7z文件包含SevenZipJBinding库无法安全处理的特殊结构。" +
                                "建议使用其他解压工具。Native错误: " + nativeError.getMessage());
            }

            try {
                int numberOfItems = inArchive.getNumberOfItems();
                boolean hasEncryptedItem = false;

                // 检查是否有加密条目
                for (int i = 0; i < numberOfItems; i++) {
                    Boolean isEncrypted = (Boolean) inArchive.getProperty(i, PropID.ENCRYPTED);
                    if (Boolean.TRUE.equals(isEncrypted)) {
                        hasEncryptedItem = true;

                        // 如果有加密条目但未提供密码
                        if (password == null || password.trim().isEmpty()) {
                            logger.debug("{}文件已加密但未提供密码: {}", format.toUpperCase(), archiveFile.getName());
                            return PasswordValidationResult.passwordRequired(format);
                        }

                        // 尝试提取第一个加密的非目录文件以验证密码
                        Boolean isFolder = (Boolean) inArchive.getProperty(i, PropID.IS_FOLDER);
                        if (!Boolean.TRUE.equals(isFolder)) {
                            try {
                                // 使用内存提取验证密码
                                inArchive.extractSlow(i, new ISequentialOutStream() {
                                    private int totalRead = 0;

                                    @Override
                                    public int write(byte[] data) throws SevenZipException {
                                        totalRead += data.length;
                                        // 只读取1KB用于验证
                                        if (totalRead >= 1024) {
                                            return 0; // 停止读取
                                        }
                                        return data.length;
                                    }
                                });

                                logger.debug("{}密码验证成功: {}", format.toUpperCase(), archiveFile.getName());
                                return PasswordValidationResult.passwordCorrect(format);

                            } catch (SevenZipException e) {
                                String errorMsg = e.getMessage();
                                if (errorMsg != null && (errorMsg.toLowerCase().contains("password") ||
                                        errorMsg.toLowerCase().contains("wrong"))) {
                                    logger.warn("{}密码错误: {}", format.toUpperCase(), archiveFile.getName());
                                    return PasswordValidationResult.passwordIncorrect(format, "密码错误");
                                }
                                throw e;
                            }
                        }
                    }
                }

                // 如果没有加密条目
                if (!hasEncryptedItem) {
                    logger.debug("{}文件未加密: {}", format.toUpperCase(), archiveFile.getName());
                    return PasswordValidationResult.notEncrypted(format);
                }

                // 有加密条目但都是目录，假定密码正确
                if (password != null && !password.trim().isEmpty()) {
                    logger.debug("{}文件无可验证条目，假定密码正确: {}", format.toUpperCase(), archiveFile.getName());
                    return PasswordValidationResult.passwordCorrect(format);
                }

                return PasswordValidationResult.passwordRequired(format);

            } finally {
                inArchive.close();
            }

        } catch (SevenZipException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.toLowerCase().contains("password") ||
                    errorMsg.toLowerCase().contains("encrypted") ||
                    errorMsg.toLowerCase().contains("wrong"))) {
                if (password == null || password.trim().isEmpty()) {
                    logger.debug("{}文件已加密，需要密码: {}", format.toUpperCase(), archiveFile.getName());
                    return PasswordValidationResult.passwordRequired(format);
                } else {
                    logger.warn("{}密码错误: {}", format.toUpperCase(), archiveFile.getName());
                    return PasswordValidationResult.passwordIncorrect(format, "密码错误");
                }
            }
            logger.error("{}文件处理异常: {}", format.toUpperCase(), archiveFile.getName(), e);
            return PasswordValidationResult.error(format, format.toUpperCase() + "文件处理失败: " + errorMsg);
        } catch (Exception e) {
            logger.error("{}密码验证异常: {}", format.toUpperCase(), archiveFile.getName(), e);
            return PasswordValidationResult.error(format, "密码验证失败: " + e.getMessage());
        }
    }

    /**
     * 验证 OOXML 格式密码(docx/xlsx/pptx)
     * 
     * 核心标准:
     * - 未加密的OOXML = ZIP格式
     * - 加密的OOXML = OLE2容器 + EncryptionInfo + EncryptedPackage
     * 
     * 原理:
     * 1. 先判断是否是ZIP → 是ZIP则必定未加密(微软约定)
     * 2. 不是ZIP → 尝试OLE2检测,判断是否为加密OOXML
     * 3. 在OLE2容器中查找EncryptionInfo和EncryptedPackage两个entry
     * 
     * 容错处理:
     * - ZIP检测失败不代表加密,需进一步用OLE2检测
     * - OLE2检测失败说明文件损坏
     */
    private PasswordValidationResult validateOoxmlPassword(File file, String password, String format) {
        logger.debug("开始检测{}文件加密状态 - 文件: {}, 大小: {} bytes",
                format.toUpperCase(), file.getName(), file.length());

        // --------------- STEP 0:优先检测 ZIP(OOXML 普通文件) ---------------
        boolean isZip = false;
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(file)) {
            isZip = true;
        } catch (java.util.zip.ZipException ignore) {
            isZip = false;
        } catch (IOException ioe) {
            logger.error("读取文件时发生 I/O 异常: {}", file.getName(), ioe);
            return PasswordValidationResult.notEncrypted(format);
        }

        // --------------- STEP 1:如果是 ZIP → 必定未加密(微软约定) ---------------
        if (isZip) {
            logger.info("🔓 {}文件是 ZIP/OOXML 格式,不含加密容器,认定为未加密 - 文件: {}",
                    format.toUpperCase(), file.getName());
            return PasswordValidationResult.notEncrypted(format);
        }

        // --------------- STEP 2:不是 ZIP → 尝试按加密 OOXML(OLE2)处理 ---------------
        try (POIFSFileSystem fs = new POIFSFileSystem(
                file, true)) {

            DirectoryNode root = fs.getRoot();

            boolean hasEncryptionInfo = root.hasEntry("EncryptionInfo");
            boolean hasEncryptedPackage = root.hasEntry("EncryptedPackage");

            if (!hasEncryptionInfo || !hasEncryptedPackage) {
                logger.info("OLE2 文件中未找到 OOXML 加密条目,非加密 OOXML - 文件: {}", file.getName());
                return PasswordValidationResult.notEncrypted(format);
            }

            // 找到加密标志
            logger.info("🔒 检测到加密的 {} 文档(OLE2容器 + 加密条目) - 文件: {}",
                    format.toUpperCase(), file.getName());

            // 密码为空 → 需要密码
            if (password == null || password.trim().isEmpty()) {
                return PasswordValidationResult.passwordRequired(format);
            }

            // 使用 POI 轻量级验证密码
            return validateOle2PasswordInternal(file, password, format, fs);

        } catch (IllegalArgumentException e) {
            // 🔑 POI 5.4.0 新增：ZIP 重复条目检测
            // 当文件包含重复路径的 ZIP 条目时，POI 会抛出 IllegalArgumentException
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.toLowerCase().contains("duplicate") ||
                    errorMsg.toLowerCase().contains("duplicated"))) {
                logger.warn("⚠️ {}文件包含重复ZIP条目（可能是恶意文件）- 文件: {}, 错误: {}",
                        format.toUpperCase(), file.getName(), errorMsg);
                return PasswordValidationResult.error(format, "文件格式异常：包含重复ZIP条目");
            }
            // 其他 IllegalArgumentException，继续抛出
            logger.error("{}文件参数异常 - 文件: {}, 错误: {}",
                    format.toUpperCase(), file.getName(), errorMsg, e);
            return PasswordValidationResult.error(format, "文件格式异常: " + errorMsg);
        } catch (IOException e) {
            // OLE2读取失败,文件可能损坏或特殊加密格式
            // 🚨 关键修复：既然不是ZIP，应该保守认为可能是加密文件
            logger.warn("⚠️ {}文件既非ZIP也OLE2读取失败，保守认为加密 - 文件: {}, 错误: {}",
                    format.toUpperCase(), file.getName(), e.getMessage());
            return PasswordValidationResult.passwordRequired(format);
        } catch (Exception e) {
            // 🚨 关键修复：包括IndexOutOfBoundsException等异常
            // 既然不是ZIP，且POI读取失败，应该保守认为加密文件
            logger.warn("⚠️ {}文件OLE2解析异常，保守认为加密 - 文件: {}, 错误: {}",
                    format.toUpperCase(), file.getName(), e.getMessage());
            return PasswordValidationResult.passwordRequired(format);
        }
    }

    /**
     * 验证 OLE2 格式密码（doc/xls/ppt）
     * 
     * 原理：
     * 1. 直接尝试构造POIFSFileSystem判断OLE2格式（失败则说明不是OLE2文件）
     * 2. 分类处理多种加密方式：
     * - ECMA-376加密（现代Office）：通过EncryptionInfo+Decryptor验证
     * - Excel XOR加密（Excel 97-2003）：EncryptionInfo不存在，需特殊处理
     * - RC4加密（旧版Office）：EncryptionInfo可能不在默认位置
     * 3. 容错处理：
     * - IOException 说明不是OLE2格式或文件损坏
     * - EncryptedDocumentException 说明文件已加密，应尝试验证密码
     * - 损坏文件返回错误状态，不要误判为未加密
     * 
     * 注意：此方法仅处理文件级加密，不处理Excel工作表保护等非加密功能
     */
    private PasswordValidationResult validateOle2Password(File file, String password, String format) {
        logger.debug("开始检测OLE2文件加密状态 - 文件: {}, 格式: {}, 大小: {} bytes",
                file.getName(), format.toUpperCase(), file.length());

        // 步骤1：检查文件头判断是否为OLE2格式
        // POI 5.2.3版本直接尝试构造POIFSFileSystem，如果失败则说明不是OLE2格式
        try (POIFSFileSystem fs = new POIFSFileSystem(
                file)) {

            return validateOle2PasswordInternal(file, password, format, fs);

        } catch (org.apache.poi.poifs.filesystem.OfficeXmlFileException e) {
            // 🔄 文件实际为 OOXML 格式（扩展名与内容不符），自动切换到 OOXML 验证
            logger.info("⚠️ 文件扩展名为 .{} 但实际为 OOXML 格式，自动切换验证逻辑 - 文件: {}",
                    format, file.getName());
            return validateOoxmlPassword(file, password, format);
            
        } catch (IOException e) {
            // 🚨 POIFSFileSystem构造失败，说明不是OLE2格式或文件损坏
            logger.warn("{}文件不是OLE2格式或文件损坏 - 文件: {}, 错误: {}",
                    format.toUpperCase(), file.getName(), e.getMessage());
            return PasswordValidationResult.error(format,
                    "文件格式不正确或损坏: " + e.getMessage());
        } catch (Exception e) {
            logger.warn("{}文件密码验证异常 - 文件: {}, 错误: {}", 
                    format.toUpperCase(), file.getName(), e.getMessage());
            return PasswordValidationResult.error(format, "密码验证失败: " + e.getMessage());
        }
    }

    /**
     * 验证OLE2密码的核心逻辑（已打开POIFSFileSystem）
     * 此方法被validateOle2Password和validateOoxmlPassword复用
     */
    private PasswordValidationResult validateOle2PasswordInternal(
            File file, String password, String format,
            POIFSFileSystem fs) {
        try {
            EncryptionInfo info = new EncryptionInfo(fs);

            logger.debug("{}文件检测到ECMA-376加密 - 文件: {}", format.toUpperCase(), file.getName());

            if (password == null || password.trim().isEmpty()) {
                return PasswordValidationResult.passwordRequired(format);
            }

            // 使用 Apache POI 轻量级验证密码
            Decryptor decryptor = Decryptor.getInstance(info);

            boolean passwordCorrect = decryptor.verifyPassword(password);

            if (passwordCorrect) {
                logger.info("✅ {}密码验证成功 - 文件: {}", format.toUpperCase(), file.getName());
                return PasswordValidationResult.passwordCorrect(format);
            } else {
                logger.warn("❌ {}密码错误 - 文件: {}", format.toUpperCase(), file.getName());
                return PasswordValidationResult.passwordIncorrect(format, "密码错误");
            }

        } catch (EncryptedDocumentException e) {
            // 🔑 关键修复：EncryptedDocumentException说明文件已加密，但可能是特殊加密类型
            // 应继续尝试验证密码，而不是直接返回passwordRequired
            logger.debug("{}文件检测到加密文档异常（可能是特殊加密类型） - 文件: {}, 错误: {}",
                    format.toUpperCase(), file.getName(), e.getMessage());

            if (password == null || password.trim().isEmpty()) {
                return PasswordValidationResult.passwordRequired(format);
            }

            // 尝试使用Decryptor验证密码（某些加密类型虽然EncryptionInfo失败，但Decryptor仍能用）
            try {
                // 重新构造EncryptionInfo并验证
                EncryptionInfo info = new EncryptionInfo(fs);
                Decryptor decryptor = Decryptor.getInstance(info);

                boolean passwordCorrect = decryptor.verifyPassword(password);
                if (passwordCorrect) {
                    logger.info("✅ {}密码验证成功（特殊加密类型） - 文件: {}",
                            format.toUpperCase(), file.getName());
                    return PasswordValidationResult.passwordCorrect(format);
                } else {
                    logger.warn("❌ {}密码错误 - 文件: {}", format.toUpperCase(), file.getName());
                    return PasswordValidationResult.passwordIncorrect(format, "密码错误");
                }
            } catch (Exception retryEx) {
                logger.debug("重试Decryptor验证失败: {}", retryEx.getMessage());
                // 无法验证，但文件确实已加密
                return PasswordValidationResult.passwordRequired(format);
            }

        } catch (Exception e) {
            // 🚨 关键修复：不能因为EncryptionInfo构造失败就判定为未加密
            // 可能原因：
            // 1. Excel XOR加密（EncryptionInfo不存在）
            // 2. 旧式RC4加密（EncryptionInfo不在默认位置）
            // 3. 文件损坏但仍是加密文件
            logger.debug("{}文件EncryptionInfo构造失败，检查是否为旧式加密 - 文件: {}, 错误: {}",
                    format.toUpperCase(), file.getName(), e.getMessage());

            // 步骤3：尝试检测Excel旧式加密（XOR/RC4）
            if ("xls".equalsIgnoreCase(format)) {
                return handleExcelLegacyEncryption(file, password, format, fs);
            }

            // 步骤4：尝试检测Word/PPT/WPS旧式加密（统一使用ExtractorFactory）
            if (fileFormatConfig.isOle2Format(format) || fileFormatConfig.isWpsFormat(format)) {
                String fileType = fileFormatConfig.getFileTypeDescription(format);
                return validateOle2PasswordWithExtractorFactory(file, password, format, fileType);
            }

            // 其他非Office文件，无法检测加密信息
            logger.warn("⚠️ {}文件无法检测加密信息，交由转换引擎处理 - 文件: {}",
                    format.toUpperCase(), file.getName());
            return PasswordValidationResult.notEncrypted(format);
        }
    }

    /**
     * 使用 ExtractorFactory 统一验证 OLE2 文件密码（DOC/XLS/PPT/WPS 通用）
     * 
     * @param file     文件对象
     * @param password 密码（可为null）
     * @param format   文件格式
     * @param fileType 文件类型描述（用于日志）
     * @return 密码验证结果
     */
    private PasswordValidationResult validateOle2PasswordWithExtractorFactory(
            File file, String password, String format, String fileType) {

        try {
            // 无密码：检测是否加密
            if (password == null || password.trim().isEmpty()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ExtractorFactory.createExtractor(fis);
                    logger.debug("{}文件未加密（ExtractorFactory可直接打开） - 文件: {}", fileType, file.getName());
                    return PasswordValidationResult.notEncrypted(format);
                } catch (org.apache.poi.EncryptedDocumentException | IOException e) {
                    logger.info("🔒 ExtractorFactory 检测到加密 {} 文档 - 文件: {}", fileType, file.getName());
                    return PasswordValidationResult.passwordRequired(format);
                }
            }

            // 有密码：验证密码正确性
            try (FileInputStream fis = new FileInputStream(file)) {
                Biff8EncryptionKey.setCurrentUserPassword(password);
                ExtractorFactory.createExtractor(fis);
                logger.info("✅ {}旧式加密密码验证成功（ExtractorFactory） - 文件: {}", fileType, file.getName());
                return PasswordValidationResult.passwordCorrect(format);
            } catch (Exception e) {
                logger.warn("❌ {}旧式加密密码验证失败（ExtractorFactory） - 文件: {}", fileType, file.getName());
                return PasswordValidationResult.passwordIncorrect(format, "密码错误");
            } finally {
                Biff8EncryptionKey.setCurrentUserPassword(null);
            }

        } catch (Exception e) {
            logger.debug("{}旧式加密检测失败: {} - {}", fileType, file.getName(), e.getMessage());
            return PasswordValidationResult.notEncrypted(format);
        }
    }

    /**
     * 处理Excel旧式加密（XOR/RC4）- 优化版本
     * 
     * 🚀 性能优化：使用RecordInputStream只读取头部记录，检测FilePass记录（sid=0x002F）
     * 避免完整解析整个工作簿，将检测时间从500ms+降至20-50ms
     * 
     * Excel 97-2003可能使用XOR或RC4加密，EncryptionInfo不存在或不在默认位置
     * FilePass记录位于Biff流的BOF记录之后，通常在前10个记录内
     */
    private PasswordValidationResult handleExcelLegacyEncryption(
            File file, String password, String format,
            org.apache.poi.poifs.filesystem.POIFSFileSystem fs) {

        logger.debug("快速检测Excel旧式加密（FilePass记录） - 文件: {}", file.getName());

        // 🔑 优化：只读取头部记录，检测FilePass（加密标志）
        try {
            DocumentInputStream dis = fs.getRoot().createDocumentInputStream("Workbook");
            try {
                RecordFactoryInputStream ris = new RecordFactoryInputStream(dis, false);

                // 只检查前15个记录（BOF、FilePass通常在最前面）
                for (int i = 0; i < 15; i++) {
                    Record record = ris.nextRecord();
                    if (record == null) {
                        break;
                    }

                    // FilePassRecord sid = 0x002F (47)，表示文件已加密
                    if (record.getSid() == 0x002F) {
                        logger.info("🔒 Excel检测到旧式加密（FilePass记录） - 文件: {}", file.getName());
                     
                        if (password == null || password.trim().isEmpty()) {
                            return PasswordValidationResult.passwordRequired(format);
                        }
                        // 有密码则验证
                        return verifyLegacyExcelPassword(file, password, format);
                    }
                }
           
                // 未找到FilePass，文件未加密
                logger.debug("Excel文件未加密（无FilePass记录） - 文件: {}", file.getName());
                return PasswordValidationResult.notEncrypted(format);

            } finally {
                try {
                    dis.close();
                } catch (Exception ignore) {
                }
            }
        } catch (FileNotFoundException e) {
            // 可能是"Book"而不是"Workbook"（Excel 5.0/95）
            logger.debug("未找到Workbook流，尝试Book流（Excel 5.0/95） - 文件: {}", file.getName());
            return handleExcel95Legacy(file, password, format, fs);
        } catch (Exception e) {
            logger.debug("快速检测失败，回退完整解析: {} - {}", file.getName(), e.getMessage());
            return fallbackFullParse(file, password, format, fs);
        }
    }

    /**
     * 验证旧式Excel加密密码
     */
    private PasswordValidationResult verifyLegacyExcelPassword(File file, String password, String format) {
        try (org.apache.poi.poifs.filesystem.POIFSFileSystem fsRetry = new org.apache.poi.poifs.filesystem.POIFSFileSystem(
                file)) {

            // 设置密码
            org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword(password);

            try (org.apache.poi.hssf.usermodel.HSSFWorkbook workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook(
                    fsRetry)) {
                // 成功打开，密码正确
                logger.info("✅ Excel旧式加密密码验证成功 - 文件: {}", file.getName());
                return PasswordValidationResult.passwordCorrect(format);
            } finally {
                // 清除密码
                org.apache.poi.hssf.record.crypto.Biff8EncryptionKey.setCurrentUserPassword(null);
            }
        } catch (org.apache.poi.EncryptedDocumentException e) {
            logger.warn("❌ Excel旧式加密密码错误 - 文件: {}", file.getName());
            return PasswordValidationResult.passwordIncorrect(format, "密码错误");
        } catch (Exception e) {
            logger.error("Excel旧式加密密码验证异常: {}", e.getMessage());
            return PasswordValidationResult.passwordRequired(format);
        }
    }

    /**
     * 处理Excel 5.0/95旧格式（Book流）
     */
    private PasswordValidationResult handleExcel95Legacy(
            File file, String password, String format,
            org.apache.poi.poifs.filesystem.POIFSFileSystem fs) {
        try {
            DocumentInputStream dis = fs.getRoot().createDocumentInputStream("Book");
            try {
                RecordFactoryInputStream ris = new RecordFactoryInputStream(dis, false);
                for (int i = 0; i < 15; i++) {
                    Record record = ris.nextRecord();
                    if (record == null) {
                        break;
                    }

                    if (record.getSid() == 0x002F) {
                        logger.info("🔒 Excel 5.0/95检测到加密 - 文件: {}", file.getName());
                   
                        if (password == null || password.trim().isEmpty()) {
                            return PasswordValidationResult.passwordRequired(format);
                        }
                        return verifyLegacyExcelPassword(file, password, format);
                    }
                }
           
                logger.debug("Excel 5.0/95文件未加密 - 文件: {}", file.getName());
                return PasswordValidationResult.notEncrypted(format);
            } finally {
                try {
                    dis.close();
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            logger.debug("Excel 5.0/95检测失败: {}", e.getMessage());
            return PasswordValidationResult.notEncrypted(format);
        }
    }

    /**
     * 回退到完整解析（兼容性保障）
     */
    private PasswordValidationResult fallbackFullParse(
            File file, String password, String format,
            org.apache.poi.poifs.filesystem.POIFSFileSystem fs) {
        try {
            try (org.apache.poi.hssf.usermodel.HSSFWorkbook workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook(
                    fs)) {
                logger.debug("Excel文件未加密（回退完整解析） - 文件: {}", file.getName());
                return PasswordValidationResult.notEncrypted(format);
            }
        } catch (org.apache.poi.EncryptedDocumentException e) {
            logger.info("🔒 Excel文件加密（回退检测） - 文件: {}", file.getName());
            if (password == null || password.trim().isEmpty()) {
                return PasswordValidationResult.passwordRequired(format);
            }
            return verifyLegacyExcelPassword(file, password, format);
        } catch (Exception e) {
            logger.debug("回退解析失败: {}", e.getMessage());
            return PasswordValidationResult.notEncrypted(format);
        }
    }

    /**
     * 使用外部7z命令检测文件是否加密（WSL2环境专用）
     * 
     * @param archiveFile 压缩文件
     * @param password    密码（可为null）
     * @param format      文件格式
     * @return 密码验证结果
     */
    private PasswordValidationResult validate7zWithExternalCommand(File archiveFile, String password, String format) {
        try {
            // 检查外部7z命令是否可用
            Process checkProcess = new ProcessBuilder("7z", "--help")
                    .redirectErrorStream(true)
                    .start();

            int checkExitCode = checkProcess.waitFor();
            if (checkExitCode != 0) {
                logger.error("❌ WSL2环境下外部7z命令不可用 - File: {}", archiveFile.getName());
                // 无法检测，保守起见假定未加密
                return PasswordValidationResult.notEncrypted(format);
            }

            // 使用7z l命令列出文件，检查是否需要密码
            ProcessBuilder pb = new ProcessBuilder(
                    "7z", "l",
                    "-slt", // 显示详细信息
                    archiveFile.getAbsolutePath());

            if (password != null && !password.trim().isEmpty()) {
                pb.command().add("-p" + password); // 提供密码
            } else {
                pb.command().add("-p"); // 空密码
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            String outputStr = output.toString();

            logger.debug("📋 7z命令输出 - File: {}, ExitCode: {}, Output: {}",
                    archiveFile.getName(), exitCode, outputStr);

            // 分析输出和退出码
            if (exitCode == 0) {
                // 成功列出，检查是否有Encrypted属性
                if (outputStr.contains("Encrypted = +")) {
                    // 文件加密
                    if (password == null || password.trim().isEmpty()) {
                        logger.debug("🔒 外部7z检测到文件加密但未提供密码 - File: {}", archiveFile.getName());
                        return PasswordValidationResult.passwordRequired(format);
                    } else {
                        logger.debug("✅ 外部7z检测到文件加密且密码正确 - File: {}", archiveFile.getName());
                        return PasswordValidationResult.passwordCorrect(format);
                    }
                } else {
                    // 文件未加密
                    logger.debug("🔓 外部7z检测到文件未加密 - File: {}", archiveFile.getName());
                    return PasswordValidationResult.notEncrypted(format);
                }
            } else if (exitCode == 2) {
                // 退出码2通常表示密码错误或需要密码
                if (outputStr.toLowerCase().contains("wrong password") ||
                        outputStr.toLowerCase().contains("can not open") ||
                        outputStr.toLowerCase().contains("encrypted")) {

                    if (password == null || password.trim().isEmpty()) {
                        logger.debug("🔒 外部7z检测到需要密码 - File: {}", archiveFile.getName());
                        return PasswordValidationResult.passwordRequired(format);
                    } else {
                        logger.warn("❌ 外部7z检测到密码错误 - File: {}", archiveFile.getName());
                        return PasswordValidationResult.passwordIncorrect(format, "密码错误");
                    }
                }
                // 其他错误
                logger.error("❌ 外部7z命令执行失败 - File: {}, ExitCode: {}", archiveFile.getName(), exitCode);
                return PasswordValidationResult.error(format, "文件检测失败");
            } else {
                // 其他退出码
                logger.error("❌ 外部7z命令返回异常退出码 - File: {}, ExitCode: {}", archiveFile.getName(), exitCode);
                // 保守起见，假定未加密
                return PasswordValidationResult.notEncrypted(format);
            }

        } catch (Exception e) {
            logger.error("💥 外部7z命令执行异常 - File: {}", archiveFile.getName(), e);
            // 无法检测，保守起见假定未加密
            return PasswordValidationResult.notEncrypted(format);
        }
    }

}
