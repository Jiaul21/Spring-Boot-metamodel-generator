package com.jiaul.metamodelgenerator;

import com.jiaul.metamodelgenerator.metadata.FieldProperty;
import com.jiaul.metamodelgenerator.testmodel.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldsProcessorTest {

    @Test
    @DisplayName("Should generate scalar static properties for Entity class")
    void testScalarProperties() {
        assertThat(StudentFields.id.name()).isEqualTo("id");
        assertThat(StudentFields.id.getName()).isEqualTo("id");
        assertThat(StudentFields.id.type()).isEqualTo(Long.class);
        assertThat(StudentFields.id.getType()).isEqualTo(Long.class);

        assertThat(StudentFields.name.name()).isEqualTo("name");
        assertThat(StudentFields.name.type()).isEqualTo(String.class);

        assertThat(StudentFields.age.name()).isEqualTo("age");
        assertThat(StudentFields.age.type()).isEqualTo(Integer.class);

        assertThat(StudentFields.tags.name()).isEqualTo("tags");
        assertThat(StudentFields.tags.type()).isEqualTo(List.class);
        assertThat(StudentFields.tags.elementType()).isEqualTo(String.class);
        assertThat(StudentFields.tags.getElementType()).isEqualTo(String.class);
    }

    @Test
    @DisplayName("Should support relation chaining for single and collection references")
    void testRelationChaining() {
        // Single relation: Student -> Course
        assertThat(StudentFields.course.title.name()).isEqualTo("course.title");

        // Collection relation: Student -> Courses
        assertThat(StudentFields.courses.title.name()).isEqualTo("courses.title");

        // Deep chain: Student -> Course -> Department -> deptName
        assertThat(StudentFields.courses.department.deptName.name()).isEqualTo("courses.department.deptName");
        assertThat(StudentFields.course.department.deptName.name()).isEqualTo("course.department.deptName");
    }

    @Test
    @DisplayName("Should break bidirectional cyclic references without StackOverflowError")
    void testBidirectionalCycleProtection() {
        // Student -> Course -> Student (cycle broken on eager static reference)
        assertThat(StudentFields.courses.student).isNull();
        assertThat(StudentFields.course.student).isNull();

        // Course -> Student -> Course (cycle broken on eager static reference)
        assertThat(CourseFields.student.course).isNull();
        assertThat(CourseFields.student.courses).isNull();

        // On-demand method invocation allows manual continuation if needed
        assertThat(StudentFields.courses.student().name.name()).isEqualTo("courses.student.name");
        assertThat(CourseFields.student.courses().title.name()).isEqualTo("student.courses.title");
    }

    @Test
    @DisplayName("Should break self-referential recursive cycles without StackOverflowError")
    void testSelfReferentialCycleProtection() {
        // Depth 1 is accessible
        assertThat(TreeNodeFields.parent.name.name()).isEqualTo("parent.name");
        assertThat(TreeNodeFields.children.name.name()).isEqualTo("children.name");

        // Depth 2 cycle is safely broken to null
        assertThat(TreeNodeFields.parent.parent).isNull();
        assertThat(TreeNodeFields.parent.children).isNull();
        assertThat(TreeNodeFields.children.parent).isNull();
        assertThat(TreeNodeFields.children.children).isNull();

        // On-demand navigation method allows deeper explicit chains
        assertThat(TreeNodeFields.parent.parent().name.name()).isEqualTo("parent.parent.name");
    }

    @Test
    @DisplayName("Should support Java Record metamodels with relation chaining")
    void testRecordMetamodel() {
        assertThat(TeacherRecordFields.id.name()).isEqualTo("id");
        assertThat(TeacherRecordFields.name.name()).isEqualTo("name");
        assertThat(TeacherRecordFields.subject.name()).isEqualTo("subject");

        // Chaining on Record
        assertThat(TeacherRecordFields.course.title.name()).isEqualTo("course.title");
        assertThat(TeacherRecordFields.course.department.deptName.name()).isEqualTo("course.department.deptName");
    }

    @Test
    @DisplayName("Should support custom prefix via path() factory")
    void testCustomPrefix() {
        StudentFields.Path s = StudentFields.path("s");
        assertThat(s.name.name()).isEqualTo("s.name");
        assertThat(s.courses.title.name()).isEqualTo("s.courses.title");
        assertThat(s.courses.department.deptName.name()).isEqualTo("s.courses.department.deptName");
        assertThat(s.path()).isEqualTo("s");
        assertThat(s.getPath()).isEqualTo("s");
    }

    @Test
    @DisplayName("Should implement CharSequence for direct string concatenation and JPQL")
    void testCharSequenceAndToString() {
        assertThat(StudentFields.courses.toString()).isEqualTo("courses");
        assertThat(StudentFields.courses.department.toString()).isEqualTo("courses.department");
        assertThat(StudentFields.courses.title.toString()).isEqualTo("courses.title");

        // String concatenation test
        String jpql = "SELECT s FROM Student s JOIN s." + StudentFields.courses + " c WHERE c." + CourseFields.title + " = :title";
        assertThat(jpql).isEqualTo("SELECT s FROM Student s JOIN s.courses c WHERE c.title = :title");

        String deepJpql = "s." + StudentFields.courses.department.deptName;
        assertThat(deepJpql).isEqualTo("s.courses.department.deptName");

        // CharSequence length and charAt
        assertThat(StudentFields.courses.length()).isEqualTo(7);
        assertThat(StudentFields.courses.charAt(0)).isEqualTo('c');
        assertThat(StudentFields.courses.subSequence(0, 4).toString()).isEqualTo("cour");
    }

    @Test
    @DisplayName("Should support ALL_FIELDS, FIELD_MAP, getProperty, and getType")
    void testMetadataInspection() {
        assertThat(StudentFields.ALL_FIELDS).isNotEmpty();
        assertThat(StudentFields.FIELD_MAP).containsKey("id").containsKey("name").containsKey("age");

        FieldProperty<?, ?> nameProp = StudentFields.getProperty("name");
        assertThat(nameProp.name()).isEqualTo("name");
        assertThat(StudentFields.getType("name")).isEqualTo(String.class);

        assertThatThrownBy(() -> StudentFields.getProperty("nonExistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown field: nonExistent");
    }

    @Test
    @DisplayName("Should extract property values and field map from Entity class")
    void testEntityValuesExtraction() {
        Department dept = new Department();
        dept.setId(10L);
        dept.setDeptName("Computer Science");

        Student student = new Student();
        student.setId(1L);
        student.setName("Alice");
        student.setAge(20);

        Map<FieldProperty<?, ?>, Object> propValues = StudentFields.getPropertyValue(student);
        assertThat(propValues.get(StudentFields.id)).isEqualTo(1L);
        assertThat(propValues.get(StudentFields.name)).isEqualTo("Alice");
        assertThat(propValues.get(StudentFields.age)).isEqualTo(20);

        Map<String, Object> fieldMap = StudentFields.getFieldMap(student);
        assertThat(fieldMap.get("id")).isEqualTo(1L);
        assertThat(fieldMap.get("name")).isEqualTo("Alice");
        assertThat(fieldMap.get("age")).isEqualTo(20);

        // Null safe
        assertThat(StudentFields.getPropertyValue(null)).isEmpty();
        assertThat(StudentFields.getFieldMap(null)).isEmpty();
    }

    @Test
    @DisplayName("Should extract property values and field map from Java Record")
    void testRecordValuesExtraction() {
        TeacherRecord teacher = new TeacherRecord(101L, "Dr. Smith", "Mathematics", null);

        Map<FieldProperty<?, ?>, Object> propValues = TeacherRecordFields.getPropertyValue(teacher);
        assertThat(propValues.get(TeacherRecordFields.id)).isEqualTo(101L);
        assertThat(propValues.get(TeacherRecordFields.name)).isEqualTo("Dr. Smith");
        assertThat(propValues.get(TeacherRecordFields.subject)).isEqualTo("Mathematics");

        Map<String, Object> fieldMap = TeacherRecordFields.getFieldMap(teacher);
        assertThat(fieldMap.get("id")).isEqualTo(101L);
        assertThat(fieldMap.get("name")).isEqualTo("Dr. Smith");
        assertThat(fieldMap.get("subject")).isEqualTo("Mathematics");
    }

    @Test
    @DisplayName("Should generate metamodel and extract values from Entity without getters")
    void testNoGetterEntityExtraction() {
        assertThat(NoGetterEntityFields.id.name()).isEqualTo("id");
        assertThat(NoGetterEntityFields.title.name()).isEqualTo("title");

        NoGetterEntity entity = new NoGetterEntity(50L, "Sample Title");
        Map<FieldProperty<?, ?>, Object> propValues = NoGetterEntityFields.getPropertyValue(entity);
        assertThat(propValues.get(NoGetterEntityFields.id)).isEqualTo(50L);
        assertThat(propValues.get(NoGetterEntityFields.title)).isEqualTo("Sample Title");

        Map<String, Object> fieldMap = NoGetterEntityFields.getFieldMap(entity);
        assertThat(fieldMap.get("id")).isEqualTo(50L);
        assertThat(fieldMap.get("title")).isEqualTo("Sample Title");
    }
}
