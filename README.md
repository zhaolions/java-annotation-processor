# Java Annotation Processor

A complete Java annotation processor project for compile-time code generation using annotations and AST.

Features:
- @AutoBuilder: Generates a fluent Builder class for annotated classes
- @AutoMapper: Generates mapper classes between source and target types with matching fields
- JavaPoet-based generation
- Example app demonstrating usage

## Modules
- annotation-processor: Annotations and processors
- example-app: Example usage and demo

## Quick Start

1) Build and run the example
```bash
./gradlew :example-app:run
```

2) What gets generated
- For a class annotated with `@AutoBuilder`, e.g. `User`, a `UserBuilder` class is generated with fluent setters for each non-static field and a `build()` method.
- For a class annotated with `@AutoMapper(from = User.class)`, e.g. `UserDto`, a `UserToUserDtoMapper` class is generated with:
  - `public static UserDto map(User from)`
  - `public static User mapBack(UserDto target)`

3) Requirements and conventions
- AutoBuilder:
  - The target class must have a no-args constructor OR an accessible all-args constructor.
  - If there are public setters, the builder will use them; otherwise it will try direct field assignment when accessible.
- AutoMapper:
  - Place `@AutoMapper(from = SourceType.class)` on the target DTO (or target class).
  - Fields are mapped by identical names and assignment-compatible types.
  - If getters/setters exist, they will be preferred; otherwise, direct field access will be attempted when accessible.

4) Example
See `example-app` module for `User`, `UserDto`, and `Main`.

5) Notes
- Generated sources appear under `build/generated/sources/annotationProcessor/`.
- This project targets Java 8; adjust `build.gradle` if you need a newer version.