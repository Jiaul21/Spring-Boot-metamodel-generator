# Metamodel Generator (`metamodel-generator`)

[![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2025+-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![JitPack](https://jitpack.io/v/Jiaul21/Spring-Boot-metamodel-generator.svg)](https://jitpack.io/#Jiaul21/Spring-Boot-metamodel-generator)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A high-performance, compile-time annotation processor and type-safe metamodel generator for Java and Spring Boot applications.

`metamodel-generator` inspects classes and records annotated with [`@ModelFields`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/annotation/ModelFields.java) during compilation (`javac`) and generates clean, type-safe metamodel classes (e.g., `StudentFields`, `CourseFields`, `TeacherRecordFields`) with **zero runtime reflection**.

---

## 📑 Table of Contents

- [Why Metamodel Generator?](#-why-metamodel-generator)
- [Architecture & Modules](#-architecture--modules)
- [Key Features](#-key-features)
- [Version Notation & Release Strategy](#-version-notation--release-strategy)
- [Installation & Dependency Setup](#-installation--dependency-setup)
  - [Maven (`pom.xml`) - Remote JitPack](#1-maven-pomxml---remote-jitpack)
  - [Gradle (Groovy DSL) - Remote JitPack](#2-gradle-groovy-dsl---remote-jitpack)
  - [Gradle (Kotlin DSL) - Remote JitPack](#3-gradle-kotlin-dsl---remote-jitpack)
  - [Local Development Setup (`~/.m2`)](#4-local-development-setup-m2)
- [Quick Start in 3 Steps](#-quick-start-in-3-steps)
- [Annotation Configuration Options (`@ModelFields`)](#-annotation-configuration-options-modelfields)
- [Comprehensive Usage & Real-World Examples](#-comprehensive-usage--real-world-examples)
  - [1. Type-Safe JPQL & HQL Queries (CharSequence Support)](#1-type-safe-jpql--hql-queries-charsequence-support)
  - [2. Deep Relation Path Chaining (Classes & Records)](#2-deep-relation-path-chaining-classes--records)
  - [3. Spring Data JPA Specifications (Criteria API)](#3-spring-data-jpa-specifications-criteria-api)
  - [4. Spring Data Dynamic Sorting & Pagination](#4-spring-data-dynamic-sorting--pagination)
  - [5. Reflection-Free Map Extraction (`toMap`, `toNonNullMap`)](#5-reflection-free-map-extraction-tomap-tononnullmap)
  - [6. Ordered Field Value List Extraction (`toValues`, `toNonNullValues`)](#6-ordered-field-value-list-extraction-tovalues-tononnullvalues)
  - [7. Dynamic Metadata Inspection (`FIELD_MAP`, `getProperty`)](#7-dynamic-metadata-inspection-field_map-getproperty)
  - [8. Direct Relation & Root Descriptors (`.property`, `.asProperty()`)](#8-direct-relation--root-descriptors-property-asproperty)
  - [9. Query Root Aliasing (`path("alias")`)](#9-query-root-aliasing-pathalias)
  - [10. Bidirectional & Recursive Cycle Safety](#10-bidirectional--recursive-cycle-safety)
- [IDE Configuration](#-ide-configuration)
- [Building From Source](#-building-from-source)
- [License](#-license)

---

## 💡 Why Metamodel Generator?

When writing JPA queries, Spring Data Specifications, or dynamic filters, developers often rely on raw string literals:
```java
// ❌ Dangerous: silent typos fail at runtime during production queries!
cb.equal(root.get("stduentName"), "Alice");
```

With `metamodel-generator`:
```java
// ✅ 100% Type-safe: compile-time checked, auto-completable, and safe under IDE refactoring!
cb.equal(root.get(StudentFields.name.name()), "Alice");
```

### How does it compare to standard JPA Metamodel generators?

| Capability | Standard JPA Metamodel (Hibernate) | `metamodel-generator` |
| :--- | :--- | :--- |
| **Runtime Dependency** | Requires full Hibernate / JPA runtime | **Zero dependencies** (pure Java processor) |
| **Java Records Support** | Limited or non-existent | **Full first-class support** for Java `record`s |
| **`CharSequence` Interpolation** | No (must use Criteria API) | **Yes** (direct string concatenation in JPQL/HQL) |
| **Reflection-Free Map Extraction** | Not available | **Built-in** (`toMap`, `toNonNullMap`) |
| **Deep Relation Chaining** | Requires multiple joins | **Yes** (`StudentFields.courses.department.deptName`) |
| **Cycle & Recursion Safety** | Vulnerable to recursion | **Yes** (automatic cycle breaking at depth 1) |

---

## 🏗️ Architecture & Modules

The library is organized into three clean packages:

| Package | Purpose | Key Classes |
| :--- | :--- | :--- |
| `com.jiaul.metamodelgenerator.annotation` | Annotations triggering compile-time processing | [`@ModelFields`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/annotation/ModelFields.java) |
| `com.jiaul.metamodelgenerator.metadata` | Runtime property descriptors & metadata | [`ModelField<T, E>`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/metadata/ModelField.java) |
| `com.jiaul.metamodelgenerator.processor` | Compile-time annotation processor & code generators | [`FieldsProcessor`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/processor/FieldsProcessor.java), [`MetamodelClassGenerator`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/processor/MetamodelClassGenerator.java), [`PathClassGenerator`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/processor/PathClassGenerator.java), [`FieldExtractor`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/processor/FieldExtractor.java) |

---

## ✨ Key Features

- **Compile-Time Generation**: Metamodels (e.g. `StudentFields`, `CourseFields`) are generated during `javac` compilation.
- **Full Class, DTO & Record Parity**: Works seamlessly on traditional Java classes, DTOs, and modern Java `record`s.
- **Configurable Generation (`fields`, `maps`, `lists`)**: Generate only what you need to keep bytecode slim and focused. When `fields = false`, all static properties, `ROOT`, and `Path` classes are completely omitted for pure conversion DTOs.
- **Deep Relation Chaining**: Build multi-level navigation paths with IDE auto-completion (e.g. `StudentFields.courses.department.deptName`).
- **Complete `FIELD_MAP` Coverage**: `FIELD_MAP` indexes all fields—both scalar properties and relation navigation targets.
- **Direct Relation Metadata**: Relation paths expose their own metadata descriptor via `.property` and `.asProperty()`.
- **Reflection-Free Map Extraction**: `toMap(obj)` and `toNonNullMap(obj)` extract fields into an ordered `Map<String, Object>` without runtime reflection.
- **Reflection-Free Value List Extraction**: `toValues(obj)` and `toNonNullValues(obj)` extract ordered `List<Object>` of field values for uniform bulk operations.
- **Bidirectional Cycle Protection**: Automatically breaks cyclic and recursive entity relations (e.g. `Student <-> Course` or self-referential `TreeNode <-> TreeNode`) at depth 1 to prevent `StackOverflowError`.
- **On-Demand Path Continuation**: When traversal past cycle boundaries is needed, on-demand methods (e.g. `TreeNodeFields.parent.parent().name`) are generated dynamically.
- **`CharSequence` Integration**: All generated path nodes and [`ModelField`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/metadata/ModelField.java) instances implement `CharSequence`, allowing direct string interpolation in JPQL, HQL, or Spring Data Criteria queries without manual `.name()` calls.
- **Query Root Aliasing**: Use `StudentFields.path("s")` to generate prefixed paths like `"s.courses.title"`.
- **Encapsulated State**: Internal path state is private (`private final String _path`) with a public `path()` accessor.
- **Self-Contained Shaded JAR**: JavaPoet is shaded directly into the JAR, preventing classpath conflicts in `<annotationProcessorPaths>`.

---

## 🏷️ Version Notation & Release Strategy

This library adheres to [Semantic Versioning (SemVer)](https://semver.org/):

```
MAJOR.MINOR.PATCH
```

- **`MAJOR`**: Incompatible API changes or breaking metamodel signature modifications.
- **`MINOR`**: Backwards-compatible new features (e.g., new metadata accessors, processor improvements).
- **`PATCH`**: Backwards-compatible bug fixes and internal optimizations.

### Version Notation Reference

| Release Type | Version Notation Format | Example Value | Description |
| :--- | :--- | :--- | :--- |
| **Stable Release** | `MAJOR.MINOR.PATCH` | `1.0.0`, `1.2.0` | Recommended for production environments. |
| **Git Release Tag** | `vMAJOR.MINOR.PATCH` | `v1.0.0`, `v1.2.0` | Used when referencing releases via JitPack. |
| **Development Snapshot** | `MAJOR.MINOR.PATCH-SNAPSHOT` | `1.0.0-SNAPSHOT` | Used in local `~/.m2` development builds. |
| **Bleeding-Edge Branch** | `<branch-name>-SNAPSHOT` | `main-SNAPSHOT` | Tracks latest unreleased commits from the main branch on JitPack. |

> [!NOTE]
> In all dependency examples below, replace placeholder version notations such as `${metamodel-generator.version}` with your desired version (e.g., `1.0.0` or `v1.0.0`).

---

## 📦 Installation & Dependency Setup

### 1. Maven (`pom.xml`) - Remote JitPack

```xml
<project>
    <!-- 1. Define version property -->
    <properties>
        <!-- Version notation example: 1.0.0, 1.1.0, or v1.0.0 -->
        <metamodel-generator.version>VERSION_EXAMPLE</metamodel-generator.version>
    </properties>

    <!-- 2. Add JitPack repository -->
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>

    <dependencies>
        <!-- 3. Add compile and runtime dependency -->
        <dependency>
            <groupId>com.github.Jiaul21</groupId>
            <artifactId>Spring-Boot-metamodel-generator</artifactId>
            <version>${metamodel-generator.version}</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- 4. Configure compiler annotation processor -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
                <configuration>
                    <release>17</release> <!-- Supported: 17, 21, 25+ -->
                    <annotationProcessorPaths>
                        <!-- NOTE: If Lombok is used, Lombok MUST be declared before metamodel-generator -->
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                        <!-- Metamodel Generator annotation processor -->
                        <path>
                            <groupId>com.github.Jiaul21</groupId>
                            <artifactId>Spring-Boot-metamodel-generator</artifactId>
                            <version>${metamodel-generator.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

### 2. Gradle (Groovy DSL) - Remote JitPack

In your `build.gradle`:

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

ext {
    // Version notation example: '1.0.0', '1.1.0', or 'v1.0.0'
    metamodelGeneratorVersion = 'VERSION_EXAMPLE'
}

dependencies {
    // Compile-time & runtime descriptors
    implementation "com.github.Jiaul21:Spring-Boot-metamodel-generator:${metamodelGeneratorVersion}"
    
    // Annotation processor
    annotationProcessor "com.github.Jiaul21:Spring-Boot-metamodel-generator:${metamodelGeneratorVersion}"
}
```

---

### 3. Gradle (Kotlin DSL) - Remote JitPack

In your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

// Version notation example: "1.0.0", "1.1.0", or "v1.0.0"
val metamodelGeneratorVersion = "VERSION_EXAMPLE"

dependencies {
    implementation("com.github.Jiaul21:Spring-Boot-metamodel-generator:$metamodelGeneratorVersion")
    annotationProcessor("com.github.Jiaul21:Spring-Boot-metamodel-generator:$metamodelGeneratorVersion")
}
```

---

### 4. Local Development Setup (`~/.m2`)

If you clone and build the library locally using `mvn clean install`:

```xml
<properties>
    <!-- Snapshot version notation example: 0.0.1-SNAPSHOT or 1.0.0-SNAPSHOT -->
    <metamodel-generator.version>LOCAL_SNAPSHOT_VERSION</metamodel-generator.version>
</properties>

<dependencies>
    <dependency>
        <groupId>com.jiaul</groupId>
        <artifactId>metamodel-generator</artifactId>
        <version>${metamodel-generator.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.14.0</version>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>com.jiaul</groupId>
                        <artifactId>metamodel-generator</artifactId>
                        <version>${metamodel-generator.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## 🚀 Quick Start in 3 Steps

### Step 1: Annotate your Entities, DTOs, or Records
Add [`@ModelFields`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/annotation/ModelFields.java) to any class or record:

```java
import com.jiaul.metamodelgenerator.annotation.ModelFields;
import jakarta.persistence.*;
import java.util.List;

// 1. Standard Java Entity Class
@Entity
@ModelFields(fields = true, maps = true, lists = true)
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private Integer age;

    @OneToMany(mappedBy = "student")
    private List<Course> courses;

    // Getters and Setters...
}

@Entity
@ModelFields
public class Course {
    @Id
    private Long id;
    private String title;

    @ManyToOne
    private Department department;

    @ManyToOne
    private Student student; // Bidirectional relation

    // Getters and Setters...
}

@Entity
@ModelFields
public class Department {
    @Id
    private Long id;
    private String deptName;

    // Getters and Setters...
}

// 2. Modern Java Record (First-Class Support)
@ModelFields
public record TeacherRecord(
    Long id,
    String name,
    String subject,
    Course course
) {}
```

### Step 2: Compile your project
Run standard compilation:
```bash
mvn compile
# or
./gradlew compileJava
```
During compilation, [`FieldsProcessor`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/processor/FieldsProcessor.java) generates the following metamodel classes in your generated sources directory:
- `StudentFields.java`
- `CourseFields.java`
- `DepartmentFields.java`
- `TeacherRecordFields.java`

### Step 3: Use the generated metamodels in your application
```java
// Type-safe field navigation:
String courseDeptPath = StudentFields.courses.department.deptName.name();
// Returns: "courses.department.deptName"
```

---

## ⚙️ Annotation Configuration Options (`@ModelFields`)

The [`@ModelFields`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/annotation/ModelFields.java) annotation provides three granular flags to customize metamodel generation per class:

```java
@ModelFields(
    fields = true,  // default: true
    maps = false,    // default: false
    lists = false   // default: false
)
```

| Property | Default | Description | Generated Methods / Features |
| :--- | :--- | :--- | :--- |
| `fields` | `true` | Enables metamodel properties (`ModelField`), `FIELD_MAP`, and the nested `Path` class for relation chaining. | Generates static `ModelField` constants and `Path` navigation (e.g. `StudentFields.department.deptName`). When set to `false`, **all `ModelField` constants, `ROOT`, and the `Path` class are completely omitted**, generating only map or list conversion methods for pure DTOs. |
| `maps` | `false` | Generates reflection-free map conversion methods. | Generates `toMap(obj)` (includes `null` values) and `toNonNullMap(obj)` (excludes `null` values). |
| `lists` | `false` | Generates reflection-free value list extraction methods. | Generates `toValues(obj)` (includes `null` values in field declaration order) and `toNonNullValues(obj)` (excludes `null` values). Ideal for uniform batch updates. |

---

## 📖 Comprehensive Usage & Real-World Examples

### 1. Type-Safe JPQL & HQL Queries (CharSequence Support)

All generated field paths and [`ModelField`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/metadata/ModelField.java) instances implement `java.lang.CharSequence`. This enables direct string concatenation in queries without requiring repetitive `.name()` or `.toString()` calls:

```java
// Direct string interpolation with zero boilerplate:
String jpql = "SELECT s FROM Student s " +
              "JOIN s." + StudentFields.courses + " c " +
              "WHERE c." + CourseFields.title + " = :title " +
              "AND s." + StudentFields.age + " >= :minAge";

// Evaluates directly to:
// "SELECT s FROM Student s JOIN s.courses c WHERE c.title = :title AND s.age >= :minAge"

TypedQuery<Student> query = entityManager.createQuery(jpql, Student.class);
query.setParameter("title", "Distributed Systems");
query.setParameter("minAge", 20);
List<Student> students = query.getResultList();
```

---

### 2. Deep Relation Path Chaining (Classes & Records)

Build nested relation paths with full IDE auto-complete across standard classes and Java `record`s:

```java
// Standard Entity Class: Student -> courses -> department -> deptName
String deptPath = StudentFields.courses.department.deptName.name();
// Result: "courses.department.deptName"

// Modern Java Record: TeacherRecord -> course -> department -> deptName
String recordDeptPath = TeacherRecordFields.course.department.deptName.name();
// Result: "course.department.deptName"

// Calling toString() produces identical path string:
assertThat(StudentFields.courses.department.deptName.toString())
    .isEqualTo("courses.department.deptName");
```

---

### 3. Spring Data JPA Specifications (Criteria API)

Build type-safe, refactoring-proof dynamic queries with `org.springframework.data.jpa.domain.Specification`:

```java
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;

public class StudentSpecifications {

    // Simple scalar property filter
    public static Specification<Student> hasName(String name) {
        return (root, query, cb) ->
            cb.equal(root.get(StudentFields.name.name()), name);
    }

    // Age threshold filter
    public static Specification<Student> isOlderThan(int age) {
        return (root, query, cb) ->
            cb.greaterThanOrEqualTo(root.get(StudentFields.age.name()), age);
    }

    // Relation join query
    public static Specification<Student> enrolledInCourse(String courseTitle) {
        return (root, query, cb) -> {
            Join<Student, Course> courses = root.join(StudentFields.courses.name());
            return cb.equal(courses.get(CourseFields.title.name()), courseTitle);
        };
    }
}
```

```java
// Usage in Spring Data Repository:
List<Student> results = studentRepository.findAll(
    Specification.where(StudentSpecifications.hasName("Alice"))
                 .and(StudentSpecifications.enrolledInCourse("Computer Science"))
);
```

---

### 4. Spring Data Dynamic Sorting & Pagination

Avoid hardcoded sorting column strings when creating `PageRequest` or `Sort` objects:

```java
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

// Sort by student name ascending:
Sort sortByName = Sort.by(StudentFields.name.name()).ascending();

// Sort by deep relation path:
Sort sortByDept = Sort.by(StudentFields.courses.department.deptName.name()).descending();

// Pass directly into PageRequest:
PageRequest pageable = PageRequest.of(0, 20, sortByName.and(sortByDept));
```

---

### 5. Reflection-Free Map Extraction (`toMap`, `toNonNullMap`)

When `maps = true` is set on `@ModelFields`, the generator produces reflection-free map conversion methods. These methods are generated at compile time using direct getter dispatches, performing **zero runtime reflection** (`java.lang.reflect.Field`) for maximum performance and 100% **GraalVM Native Image** compatibility:

- **`toMap(obj)`**: Extracts all fields into an ordered `Map<String, Object>`, retaining `null` values.
- **`toNonNullMap(obj)`**: Extracts only fields whose values are non-null (ideal for partial PATCH updates, audit logging, or diffing).

```java
// --- Entity Class Example ---
Student student = new Student();
student.setId(101L);
student.setName("Alice");
// student.age is null

// 1. Full Map (including nulls)
Map<String, Object> fullMap = StudentFields.toMap(student);
// Result:
// {
//   "id": 101L,
//   "name": "Alice",
//   "age": null,
//   "courses": null
// }

// 2. Non-Null Map (compact for PATCH / partial updates)
Map<String, Object> nonNullMap = StudentFields.toNonNullMap(student);
// Result:
// {
//   "id": 101L,
//   "name": "Alice"
// }
// (null fields omitted)

// --- Java Record Example ---
TeacherRecord teacher = new TeacherRecord(202L, "Dr. Smith", "Physics", null);

Map<String, Object> recordFields = TeacherRecordFields.toNonNullMap(teacher);
// Result: { "id": 202L, "name": "Dr. Smith", "subject": "Physics" }
```

---

### 6. Ordered Field Value List Extraction (`toValues`, `toNonNullValues`)

When `lists = true` is set on `@ModelFields`, the generator produces reflection-free value list extraction methods in field declaration order:

- **`toValues(obj)`**: Extracts all field values in declaration order including `null`s (ideal for batch INSERT / bulk uniform operations).
- **`toNonNullValues(obj)`**: Extracts only non-null values in declaration order.

```java
Student student = new Student();
student.setId(101L);
student.setName("Alice");
// student.age is null

// 1. All values in declaration order:
List<Object> allValues = StudentFields.toValues(student);
// Result: [101L, "Alice", null, null]

// 2. Only non-null values:
List<Object> nonNullValues = StudentFields.toNonNullValues(student);
// Result: [101L, "Alice"]
```

---

### 7. Dynamic Metadata Inspection (`FIELD_MAP`, `getProperty`)

`FIELD_MAP` contains comprehensive descriptors for **all properties**—both scalar columns and relation navigation targets:

```java
// 1. Check whether a field exists dynamically:
boolean hasAge = StudentFields.FIELD_MAP.containsKey("age"); // true
boolean hasCourses = StudentFields.FIELD_MAP.containsKey("courses"); // true

// 2. Inspect scalar metadata:
ModelField<?, ?> nameProp = StudentFields.getProperty("name");
String nameFieldName = nameProp.name();          // "name"
Class<?> nameType   = nameProp.type();          // String.class

// 3. Inspect relation metadata from FIELD_MAP:
ModelField<?, ?> deptProp = CourseFields.getProperty("department");
Class<?> deptType = deptProp.type();            // Department.class

// 4. Inspect collection relations:
ModelField<?, ?> coursesProp = StudentFields.getProperty("courses");
Class<?> colType  = coursesProp.type();        // List.class
Class<?> elemType = coursesProp.elementType(); // Course.class
```

---

### 8. Direct Relation & Root Descriptors (`.property`, `.asProperty()`)

Every relation path node exposes its own [`ModelField`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/metadata/ModelField.java) descriptor directly via `.property` and `.asProperty()`:

```java
// Direct relation descriptor access on path:
ModelField<Department, Void> deptDesc1 = CourseFields.department.property;
ModelField<Department, Void> deptDesc2 = CourseFields.department.asProperty();

assertThat(deptDesc1.name()).isEqualTo("department");
assertThat(deptDesc1.type()).isEqualTo(Department.class);

// Works identically on Java Records:
ModelField<Course, Void> recordCourseDesc = TeacherRecordFields.course.property;
assertThat(recordCourseDesc.name()).isEqualTo("course");
assertThat(recordCourseDesc.type()).isEqualTo(Course.class);

// Root entity descriptors:
ModelField<Course, Void> rootCourse = CourseFields.asProperty();
ModelField<TeacherRecord, Void> rootTeacher = TeacherRecordFields.asProperty();
```

---

### 9. Query Root Aliasing (`path("alias")`)

When writing complex queries involving multiple instances or aliases of the same table, use `.path("alias")` to generate root-prefixed paths:

```java
// Create path builder aliased with "s"
StudentFields.Path s = StudentFields.path("s");

String namePath   = s.name.name();                  // "s.name"
String coursePath = s.courses.title.name();          // "s.courses.title"
String deptPath   = s.courses.department.deptName.name(); // "s.courses.department.deptName"
String rootAlias  = s.path();                       // "s"
```

---

### 10. Bidirectional & Recursive Cycle Safety

In bidirectional relations (`Student <-> Course`) or recursive structures (`TreeNode <-> TreeNode`), circular references are safely broken at cycle boundaries at depth 1 during eager static initialization:

```java
// Eager cycle is broken to null to prevent StackOverflowError:
assertThat(CourseFields.student.courses.student).isNull();

// When you explicitly need deeper traversal in a specific query, call on-demand methods:
String extendedPath = CourseFields.student.courses.student().name.name();
// Evaluates to: "student.courses.student.name"
```

---

## 🛠️ IDE Configuration

Because `metamodel-generator` runs at compile time, ensure your IDE has **Annotation Processing enabled**:
- **IntelliJ IDEA**: Settings $\rightarrow$ Build, Execution, Deployment $\rightarrow$ Compiler $\rightarrow$ Annotation Processors $\rightarrow$ Enable annotation processing.
- **Eclipse / VS Code**: See detailed setup steps in our [HELP.md](file:///d:/enterprise-system/spring-helper/metamodel-generator/HELP.md).

---

## 🔨 Building From Source

```bash
# Clone the repository
git clone https://github.com/Jiaul21/Spring-Boot-metamodel-generator.git
cd Spring-Boot-metamodel-generator

# Clean and run all unit tests
mvn clean test

# Build thin JAR, source JAR, Javadoc JAR, and install to local ~/.m2
mvn clean install
```

---

## 📄 License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
