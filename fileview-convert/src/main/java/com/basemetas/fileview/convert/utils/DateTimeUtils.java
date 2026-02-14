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
package com.basemetas.fileview.convert.utils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期时间工具类
 * 提供UTC格式的时间处理功能
 */
public class DateTimeUtils {
    
    /**
     * 将Date对象转换为UTC格式的字符串
     * 格式: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
     * 
     * @param date Date对象
     * @return UTC格式的时间字符串
     */
    public static String toUTCString(Date date) {
        if (date == null) {
            return null;
        }
        
        try {
            Instant instant = date.toInstant();
            return DateTimeFormatter.ISO_INSTANT.format(instant);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 将毫秒时间戳转换为UTC格式的字符串
     * 
     * @param timestamp 毫秒时间戳
     * @return UTC格式的时间字符串
     */
    public static String toUTCString(long timestamp) {
        try {
            Instant instant = Instant.ofEpochMilli(timestamp);
            return DateTimeFormatter.ISO_INSTANT.format(instant);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 将Date对象转换为UTC时区的Date对象
     * 
     * @param date Date对象
     * @return UTC时区的Date对象
     */
    public static Date toUTCDate(Date date) {
        if (date == null) {
            return null;
        }
        
        try {
            Instant instant = date.toInstant().atOffset(ZoneOffset.UTC).toInstant();
            return Date.from(instant);
        } catch (Exception e) {
            return date;
        }
    }
}