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

import net.sf.sevenzipjbinding.IArchiveOpenCallback;
import net.sf.sevenzipjbinding.ICryptoGetTextPassword;
import net.sf.sevenzipjbinding.SevenZipException;

/**
 * SevenZip 密码回调实现
 * 用于处理加密的 7z 和 RAR 文件
 */
public class PasswordOpenCallback implements IArchiveOpenCallback, ICryptoGetTextPassword {
    
    private final String password;
    
    public PasswordOpenCallback(String password) {
        this.password = password;
    }
    
    @Override
    public String cryptoGetTextPassword() throws SevenZipException {
        return password;
    }
    
    @Override
    public void setCompleted(Long files, Long bytes) throws SevenZipException {
        // 可选：记录进度
    }
    
    @Override
    public void setTotal(Long files, Long bytes) throws SevenZipException {
        // 可选：记录总大小
    }
}
