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
 * 校验字符串中不包含路径遍历相关字符的自定义注解。
 *
 * 主要用于限制 fileId 等参数，防止出现 ".."、路径分隔符等路径遍历尝试。
 */
@Documented
@Constraint(validatedBy = NoPathTraversal.NoPathTraversalValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoPathTraversal {

    String message() default "不允许包含路径遍历相关字符";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 具体的校验逻辑实现。
     */
    class NoPathTraversalValidator implements ConstraintValidator<NoPathTraversal, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isEmpty()) {
                // 交给其他注解（如 @NotBlank）处理空值
                return true;
            }

            // 拒绝包含路径遍历相关片段
            if (value.contains("..")) {
                return false;
            }

            // 拒绝包含任何路径分隔符
            if (value.contains("/") || value.contains("\\")) {
                return false;
            }

            return true;
        }
    }
}
