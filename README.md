# Metamodel Generator (`metamodel-generator`)

[![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2025+-blue.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%20%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![JitPack](https://jitpack.io/v/Jiaul21/Spring-Boot-metamodel-generator.svg)](https://jitpack.io/#Jiaul21/Spring-Boot-metamodel-generator)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A high-performance, compile-time type-safe metamodel generator and annotation processor library for Java and Spring Boot applications.

`metamodel-generator` inspects classes and records annotated with `@EntityFields` during compilation and generates type-safe metamodel classes (e.g., `StudentFields`, `CourseFields`) with zero runtime reflection overhead.

---

## 🏗️ Architecture & Packages

The library is organized into three clean modules:

| Package | Purpose | Key Classes |
| :--- | :--- | :--- |
| `com.jiaul.metamodelgenerator.annotation` | Annotations triggering compile-time processing | [`EntityFields`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/annotation/EntityFields.java) |
| `com.jiaul.metamodelgenerator.metadata` | Runtime metamodel descriptors & properties | [`FieldProperty`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/metadata/FieldProperty.java) |
| `com.jiaul.metamodelgenerator.processor` | Compile-time Java annotation processor (SPI registered) | [`FieldsProcessor`](file:///d:/enterprise-system/spring-helper/metamodel-generator/src/main/java/com/jiaul/metamodelgenerator/processor/FieldsProcessor.java) |

---

## ✨ Features

- **Compile-Time Metamodel Generation**: Generates type-safe metamodel classes (e.g. `StudentFields`, `CourseFields`) during `javac` compilation.
- **Support for Classes & Records**: Seamlessly works on both traditional Java classes (with getters/inheritance/reflection fallback) and modern Java `record`s.
- **Deep Relation Path Chaining**: Build relation navigation paths with type-safe field completion (e.g. `StudentFields.courses.department.deptName`).
- **Bidirectional Cycle Protection**: Automatically breaks cyclic and recursive entity relations (e.g. `Student <-> Course` or self-referential `TreeNode <-> TreeNode`) at the cycle boundary, completely preventing `StackOverflowError` during class loading.
- **On-Demand Path Continuation**: For cases requiring traversal past cycle boundaries, on-demand methods (e.g. `StudentFields.courses.student().name`) are generated dynamically.
- **`CharSequence` Integration**: All generated path nodes and `FieldProperty` instances implement `CharSequence`, allowing direct string interpolation and concatenation in JPQL, HQL, or Spring Data Criteria queries without manual `.name()` calls.
- **Custom Prefix Factory**: Use `StudentFields.path("s")` to generate root-aliased paths such as `"s.courses.title"`.
- **Consistent Field Naming**: Generates exact field-matching properties (`id`, `name`, `course`, `courses`) with no redundant uppercase duplicates.
- **Field & Value Reflection Utilities**: Built-in `getPropertyValue(obj)`, `getFieldMap(obj)`, `ALL_FIELDS`, and `FIELD_MAP` for entity serialization, auditing, and dynamic query builders.
- **Self-Contained Shaded JAR**: JavaPoet is shaded directly into `metamodel-generator.jar`, guaranteeing that Maven's `<annotationProcessorPaths>` will never throw `NoClassDefFoundError`.
- **Standalone with Zero External Dependencies**: Does not depend on any application projects, keeping consumer classpaths clean.

---

## 📦 Installation

### Option 1: Via JitPack (Remote Consumption)

#### Maven (`pom.xml`)
```xml
<!-- 1. Add JitPack repository -->
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<!-- 2. Add dependency -->
<dependencies>
    <dependency>
        <groupId>com.github.Jiaul21</groupId>
        <artifactId>Spring-Boot-metamodel-generator</artifactId>
        <version>${version}</version>
    </dependency>
</dependencies>

<!-- 3. Add to annotationProcessorPaths in compiler plugin -->
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.14.0</version>
            <configuration>
                <annotationProcessorPaths>
                    <!-- If Lombok is used, include lombok first -->
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                    <path>
                        <groupId>com.github.Jiaul21</groupId>
                        <artifactId>Spring-Boot-metamodel-generator</artifactId>
                        <version>${version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

#### Gradle (Groovy - `build.gradle`)
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Jiaul21:Spring-Boot-metamodel-generator:<version>'
    annotationProcessor 'com.github.Jiaul21:Spring-Boot-metamodel-generator:<version>'
}
```

#### Gradle (Kotlin DSL - `build.gradle.kts`)
```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.Jiaul21:Spring-Boot-metamodel-generator:${version}")
    annotationProcessor("com.github.Jiaul21:Spring-Boot-metamodel-generator:${version}")
}
```

---

### Option 2: Local Development Cache (`~/.m2`)

If you build locally via `mvn clean install`:

#### Maven (`pom.xml`)
```xml
<dependencies>
    <dependency>
        <groupId>com.jiaul</groupId>
        <artifactId>metamodel-generator</artifactId>
        <version>${version}</version>
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
                    <!-- If using Lombok: -->
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                    <path>
                        <groupId>com.jiaul</groupId>
                        <artifactId>metamodel-generator</artifactId>
                        <version>0.0.1-SNAPSHOT</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

---

## 🚀 Usage Guide

### 1. Annotate Entities, DTOs, or Records

Apply `@EntityFields` on any entity, DTO, or record:

```java
import com.jiaul.metamodelgenerator.annotation.EntityFields;
import jakarta.persistence.*;
import java.util.List;

@Entity
@EntityFields
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private Integer age;

    @OneToMany(mappedBy = "student")
    private List<Course> courses;

    // Getters and setters...
}

@Entity
@EntityFields
public class Course {
    @Id
    private Long id;
    private String title;

    @ManyToOne
    private Department department;

    @ManyToOne
    private Student student; // Bidirectional relation!

    // Getters and setters...
}

@Entity
@EntityFields
public class Department {
    @Id
    private Long id;
    private String deptName;

    // Getters and setters...
}

// Modern Java Record support
@EntityFields
public record TeacherRecord(
    Long id,
    String name,
    String subject,
    Course course
) {}
```

---

### 2. Compile-Time Metamodel Generation

During compilation (`mvn compile`), `FieldsProcessor` generates type-safe helper classes in `target/generated-sources/annotations`:
- `StudentFields`
- `CourseFields`
- `DepartmentFields`
- `TeacherRecordFields`

---

### 3. Relation Path Chaining & Queries

Access fields and nested paths directly in your queries:

```java
// 1. Scalar field access
String idCol = StudentFields.id.name();     // "id"
String nameCol = StudentFields.name.name(); // "name"

// 2. Direct relation path chaining
String courseTitle = StudentFields.courses.title.name(); // "courses.title"

// 3. Deep relation chain: Student -> Course -> Department -> deptName
String dept = StudentFields.courses.department.deptName.name(); // "courses.department.deptName"

// 4. CharSequence direct interpolation in JPQL
String jpql = "SELECT s FROM Student s " +
              "JOIN s." + StudentFields.courses + " c " +
              "WHERE c." + CourseFields.title + " = :title";
// Evaluates directly to:
// "SELECT s FROM Student s JOIN s.courses c WHERE c.title = :title"

// 5. Custom root prefix
StudentFields.Path s = StudentFields.path("s");
String query = s.courses.department.deptName.name(); // "s.courses.department.deptName"
```

---

### 4. Bidirectional Cycle Safety

In bidirectional mappings (e.g. `Student <-> Course`):
- `StudentFields.courses.student` is safely set to `null` on the eager static instance, avoiding infinite loops or `StackOverflowError`.
- If dynamic backwards traversal is ever needed, call the generated on-demand method:
```java
// On-demand continuation past cycle boundaries
String path = StudentFields.courses.student().name.name(); // "courses.student.name"
```

---

### 5. Entity Value Extraction & Reflection Utilities

```java
Student student = new Student();
student.setId(10L);
student.setName("Alice");

// Extract Map<FieldProperty, Object>
Map<FieldProperty<?, ?>, Object> propValues = StudentFields.getPropertyValue(student);
Long id = (Long) propValues.get(StudentFields.id); // 10L

// Extract Map<String, Object>
Map<String, Object> fieldMap = StudentFields.getFieldMap(student);
String name = (String) fieldMap.get("name"); // "Alice"

// Dynamic property lookup
FieldProperty<?, ?> prop = StudentFields.getProperty("name");
Class<?> type = StudentFields.getType("name"); // String.class
```

---

## 🛠️ Building From Source

```bash
# Clean and run all unit tests
mvn clean test

# Build thin JAR, source JAR, Javadoc JAR, and install to local ~/.m2
mvn clean install
```
