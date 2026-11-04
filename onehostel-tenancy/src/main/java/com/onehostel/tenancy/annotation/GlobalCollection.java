package com.onehostel.tenancy.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a document class out of tenant scoping: platform-wide data (countries, fee-head catalogue)
 * or records looked up before a tenant is known (users at login, OTP challenges).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GlobalCollection {

    /** Why this collection is not tenant-scoped. Required so every exemption is explained. */
    String reason();
}
