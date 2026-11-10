# Changelog

All notable changes to the **Metamodel Generator** library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- **`fields` toggle in `@ModelFields`**: Added `boolean fields() default true;` to selectively enable or disable metamodel field constants and nested `Path` class generation.
- **Pure Utility DTO Mode**: When `fields = false`, generated classes omit `ROOT`, static `ModelField` properties, `FIELD_MAP`, and the nested `Path` class, providing zero-overhead compile-time `toMap` / `toValues` helpers.
- **Cross-Model Path Safety**: Enhanced `FieldExtractor` to verify target model configuration. Referencing a model configured with `fields = false` safely falls back to a terminal `ModelField` instead of attempting to link to a non-existent `Path` class.
- **Lombok Introspection**: Hardened getter resolution in `FieldExtractor` to inspect `@Getter`, `@Data`, and `@Value` annotations on classes and individual fields.
- **Documentation**: Added comprehensive developer handbook [`HELP.md`](HELP.md) and official Apache 2.0 [`LICENSE`](LICENSE).

### Changed
- **Renamed `FieldProperty` → `ModelField`**: Modernized metadata class naming to reflect universal support across JPA entities, DTOs, and Java Records.
- **Unified Configuration**: Consolidated metamodel configuration under `@ModelFields(fields = ..., maps = ..., lists = ...)`.

### Removed
- **Removed `@EntityFields`**: Deprecated annotation removed in favor of unified `@ModelFields`.
- **Removed `toNonNullFieldMap`**: Replaced by unified `toNonNullMap()` API.
- **Removed Unused Dependencies**: Stripped unused `spring-web` and `jackson-annotations` dependencies from `pom.xml`.
- **Removed Dead Resources**: Deleted unused Spring Initializr `application.yaml`.

---

## [0.0.1] - 2026-10-15

### Added
- Initial release of the compile-time annotation processor.
- AST hierarchy traversal supporting class inheritance and Java 17+ Records.
- Type-safe relation path navigation (`EntityFields.ROOT.relation.subProperty`).
- Compile-time static metamodel generation without runtime reflection.
- Built-in `toMap()` and `toValues()` entity-to-collection converters.
- Automated deployment workflow via GitHub Actions to GitHub Packages.

