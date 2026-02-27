package com.basemetas.fileview.preview.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验文件路径安全性的自定义注解。
 * 
 * 与 @NoPathTraversal 的区别：
 * - @NoPathTraversal：用于 fileId、fileName 等标识符，完全禁止路径分隔符
 * - @SecurePath：用于 srcRelativePath、downloadTargetPath 等路径字段，允许正常路径分隔符，仅禁止路径遍历
 * 
 * 防护目标：
 * - 禁止相对路径向上遍历（..）
 * - 允许正常的绝对路径和相对路径（包含 / 或 \）
 * 
 * @author 夫子
 */
@Documented
@Constraint(validatedBy = SecurePath.SecurePathValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SecurePath {

    String message() default "路径包含不安全的遍历字符";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 具体的校验逻辑实现。
     */
    class SecurePathValidator implements ConstraintValidator<SecurePath, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isEmpty()) {
                // 交给其他注解（如 @NotBlank）处理空值
                return true;
            }

            // 禁止包含路径遍历符号（相对路径向上）
            // 注意：这里使用严格匹配，避免误判正常文件名中的两个点（如 file..backup.txt）
            if (value.contains("..")) {
                // 进一步检查是否为路径遍历模式
                if (value.contains("../") || value.contains("..\\") || 
                    value.startsWith("..") || value.endsWith("..")) {
                    return false;
                }
            }

            return true;
        }
    }
}
