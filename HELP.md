# Metamodel Generator - Help & Developer Handbook

This guide provides answers to common questions, version notation guidance, IDE configuration walkthroughs, and troubleshooting solutions for developers using [`metamodel-generator`](README.md).

---

## 📌 Frequently Asked Questions (FAQ)

### 1. What does `metamodel-generator` do?
When you annotate a Java class or `record` with [`@ModelFields`](src/main/java/com/jiaul/metamodelgenerator/annotation/ModelFields.java), the compile-time annotation processor generates a companion type-safe metamodel class (e.g. `Student` $\rightarrow$ `StudentFields`) during standard `javac` compilation.

Key capabilities provided:
- **Type-safe field constants**: `StudentFields.name.name()`
- **Deep relation navigation**: `StudentFields.courses.department.deptName.name()`
- **`CharSequence` compatibility**: Direct string concatenation in JPQL/HQL queries without calling `.name()`
- **Complete `FIELD_MAP` registry**: Dynamic metadata inspection for all scalar and relation properties
- **Fast reflection-free extraction**: `StudentFields.toMap(student)`, `StudentFields.toNonNullMap(student)`, `StudentFields.toValues(student)`, and `StudentFields.toNonNullValues(student)` using compile-time getter dispatch
- **Granular Generation Control**: Use `fields`, `maps`, and `lists` flags to generate only what you need.

### 2. How is it different from `hibernate-jpamodelgen`?
- **Zero runtime JPA dependency**: Works with standard POJOs, DTOs, and Java `record`s even without JPA/Hibernate on your classpath.
- **Record support**: Full first-class support for modern Java `record`s.
- **Deep relation chaining**: Enables multi-hop relation navigation (e.g., `CourseFields.department.deptName`).
- **`CharSequence` support**: Can be embedded directly into query strings without extra boilerplate.
- **Utility Methods**: Reflection-free map and value list extraction built right into the generated metamodel.

### 3. Does it support Java Records?
**Yes, 100%.** Records annotated with [`@ModelFields`](src/main/java/com/jiaul/metamodelgenerator/annotation/ModelFields.java) receive identical metamodel generation, relation chaining, `FIELD_MAP` coverage, and reflection-free map/list extraction (dispatching directly to record component accessors like `record.name()`).

### 4. Does it use reflection at runtime?
**No.** All metamodel structures and getter dispatches are generated strictly at compile time. It uses **zero runtime reflection** (`java.lang.reflect.Field`), ensuring maximum performance and complete compatibility with **GraalVM Native Images**.

### 5. How do I generate only Map or List utilities for DTOs without generating metamodel fields?
Set `fields = false` and enable `maps` or `lists`:
```java
@ModelFields(fields = false, maps = true, lists = true)
public class StudentDto {
    // ...
}
```
When `fields = false`, all static `ModelField` constants, `ROOT`, `FIELD_MAP`, and the nested `Path` class are completely omitted, generating only `toMap()`, `toNonNullMap()`, `toValues()`, and `toNonNullValues()`.

---

## 🏷️ Version Notation & Examples

This library uses [Semantic Versioning (SemVer)](https://semver.org/):

```
MAJOR.MINOR.PATCH
```

### Version Notation Patterns

| Type | Notation Pattern | Example | Usage Context |
| :--- | :--- | :--- | :--- |
| **Stable Release** | `MAJOR.MINOR.PATCH` | `1.0.0`, `1.2.0` | Recommended for production builds |
| **JitPack Tag** | `vMAJOR.MINOR.PATCH` | `v1.0.0` | Git release tag reference on JitPack |
| **Development Snapshot** | `MAJOR.MINOR.PATCH-SNAPSHOT` | `0.0.1-SNAPSHOT` | Local Maven cache (`~/.m2`) |
| **Branch Snapshot** | `<branch>-SNAPSHOT` | `main-SNAPSHOT` | JitPack bleeding-edge tracking |

### Example: Setting the Version in Maven (`pom.xml`)

```xml
<properties>
    <!-- Version Notation: Replace with your chosen release version or snapshot -->
    <!-- Example values: 1.0.0, 1.1.0, or 0.0.1-SNAPSHOT -->
    <metamodel-generator.version>VERSION_EXAMPLE</metamodel-generator.version>
</properties>

<dependencies>
    <dependency>
        <groupId>com.github.Jiaul21</groupId>
        <artifactId>Spring-Boot-metamodel-generator</artifactId>
        <version>${metamodel-generator.version}</version>
    </dependency>
</dependencies>
```

### Example: Setting the Version in Gradle (`build.gradle`)

```groovy
ext {
    // Version Notation Example: '1.0.0' or 'v1.0.0'
    metamodelGeneratorVersion = 'VERSION_EXAMPLE'
}

dependencies {
    implementation "com.github.Jiaul21:Spring-Boot-metamodel-generator:${metamodelGeneratorVersion}"
    annotationProcessor "com.github.Jiaul21:Spring-Boot-metamodel-generator:${metamodelGeneratorVersion}"
}
```

---

## ⚙️ IDE Setup & Configuration

Because `metamodel-generator` executes during compilation, your IDE must have **Annotation Processing enabled**.

### IntelliJ IDEA
1. Open **Settings / Preferences** (`Ctrl + Alt + S` on Windows/Linux, `Cmd + ,` on macOS).
2. Go to **Build, Execution, Deployment** $\rightarrow$ **Compiler** $\rightarrow$ **Annotation Processors**.
3. Check the box **Enable annotation processing**.
4. Select **Store generated sources relative to** $\rightarrow$ **Module content root**.
5. Click **Apply** and **OK**.
6. Trigger a build via **Build** $\rightarrow$ **Rebuild Project**.

> [!TIP]
> If generated metamodels are highlighted in red in the editor, right-click `target/generated-sources/annotations` in the Project Explorer and select **Mark Directory as** $\rightarrow$ **Generated Sources Root**.

### Eclipse
1. Right-click your project $\rightarrow$ **Properties**.
2. Navigate to **Java Compiler** $\rightarrow$ **Annotation Processing**.
3. Check **Enable project specific settings** and **Enable annotation processing**.
4. Set the generated source directory to `target/generated-sources/annotations`.
5. Click **Apply and Close**, then run **Project** $\rightarrow$ **Clean...**.

### Visual Studio Code (VS Code)
1. Ensure the **Extension Pack for Java** (or **Language Support for Java™ by Red Hat**) is installed.
2. Open the Command Palette (`Ctrl + Shift + P`) and run:
   ```
   Java: Clean Java Language Server Workspace
   ```
3. Restart VS Code and compile using the terminal (`mvn compile` or `./gradlew compileJava`).

---

## ⚠️ Important: Lombok Coexistence & Processor Ordering

If your project uses **Project Lombok** (`@Getter`, `@Setter`, `@Data`), Lombok **must be declared first** in Maven's `<annotationProcessorPaths>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.14.0</version>
    <configuration>
        <annotationProcessorPaths>
            <!-- 1. Lombok MUST be listed first to generate getters/setters -->
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <!-- 2. metamodel-generator is listed second to read the getters -->
            <path>
                <groupId>com.github.Jiaul21</groupId>
                <artifactId>Spring-Boot-metamodel-generator</artifactId>
                <version>${metamodel-generator.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

> [!NOTE]
> Even if Lombok has not yet run in an early round, `metamodel-generator` contains fallback introspection for Lombok `@Getter`, `@Data`, and `@Value` annotations to correctly derive standard getter names without failing.

---

## 🤝 Ecosystem Synergy with `jpa-helper`

`metamodel-generator` and [`jpa-helper`](https://github.com/Jiaul21/Spring-Boot-jpa-helper) form a complementary stack:
- **`metamodel-generator`**: Analyzes classes at compile time, creating typed descriptors (`ModelField`) implementing `CharSequence` and zero-reflection map/list extractors.
- **`jpa-helper`**: Consumes `CharSequence` descriptors to execute dynamic multi-level `LEFT JOIN FETCH` queries, Cartesian-product-safe collection joins, and high-performance CASE-WHEN bulk batch updates.
- For complete real-world examples, see Section 8 of the [jpa-helper README](https://github.com/Jiaul21/Spring-Boot-jpa-helper).

---

## 🔍 Troubleshooting Common Issues

### Issue 1: "Cannot find symbol `StudentFields`"
- **Cause**: The project has not been compiled yet, or the generated sources folder is not registered in the IDE classpath.
- **Solution**:
  1. Run `mvn compile` (or `./gradlew compileJava`) in your terminal.
  2. Verify that `target/generated-sources/annotations/StudentFields.java` exists.
  3. Ensure annotation processing is enabled in your IDE (see IDE Setup above).

### Issue 2: "StackOverflowError or infinite loops in cyclic relations"
- **Cause**: Bidirectional entity relations (e.g., `Student <-> Course` or recursive trees `TreeNode <-> TreeNode`).
- **Solution**: `metamodel-generator` automatically breaks cycles at depth 1 by setting cyclic references to `null` during static class loading.
  If you need to traverse past the cycle boundary in a specific query, invoke the on-demand navigation method:
  ```java
  // On-demand method continuation past cycle boundary:
  String path = StudentFields.courses.student().name.name(); // "courses.student.name"
  ```

### Issue 3: "Field values are missing from `toMap` or `toNonNullMap`"
- **Cause**: The entity field does not have a standard getter (e.g. `getName()`) or record component accessor (e.g. `name()`).
- **Solution**:
  1. Add standard getters or use Lombok `@Getter`.
  2. Verify that Lombok is ordered before `metamodel-generator` in `<annotationProcessorPaths>`.

---

## 📜 Version History & Migrations

For breaking changes, migration instructions, and a full version history, refer to [CHANGELOG.md](CHANGELOG.md).

