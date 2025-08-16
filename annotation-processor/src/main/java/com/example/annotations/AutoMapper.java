package com.example.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Annotate the target class (e.g., DTO) to generate a mapper from the source class to this target.
 * Example: @AutoMapper(from = User.class) on UserDto will generate UserToUserDtoMapper.
 */
@Retention(SOURCE)
@Target(TYPE)
public @interface AutoMapper {
    Class<?> from();
}