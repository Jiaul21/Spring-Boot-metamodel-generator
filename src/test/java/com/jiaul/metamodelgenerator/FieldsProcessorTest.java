package com.jiaul.metamodelgenerator;

import com.jiaul.metamodelgenerator.metadata.ModelField;
import com.jiaul.metamodelgenerator.testmodel.Course;
import com.jiaul.metamodelgenerator.testmodel.CourseFields;
import com.jiaul.metamodelgenerator.testmodel.Department;
import com.jiaul.metamodelgenerator.testmodel.NoGetterEntity;
import com.jiaul.metamodelgenerator.testmodel.NoGetterEntityFields;
import com.jiaul.metamodelgenerator.testmodel.SampleDto;
import com.jiaul.metamodelgenerator.testmodel.SampleDtoFields;
import com.jiaul.metamodelgenerator.testmodel.Student;
import com.jiaul.metamodelgenerator.testmodel.StudentFields;
import com.jiaul.metamodelgenerator.testmodel.TeacherRecord;
import com.jiaul.metamodelgenerator.testmodel.TeacherRecordFields;
import com.jiaul.metamodelgenerator.testmodel.TreeNodeFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldsProcessorTest {

    @Test
    @DisplayName("Should generate scalar static ModelField properties for Entity class")
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

        // Record FIELD_MAP, getProperty, and property descriptors
        assertThat(TeacherRecordFields.FIELD_MAP).containsKeys("id", "name", "subject", "course");
        assertThat(TeacherRecordFields.getProperty("course").type()).isEqualTo(Course.class);
        assertThat(TeacherRecordFields.course.property.name()).isEqualTo("course");
        assertThat(TeacherRecordFields.course.asProperty().type()).isEqualTo(Course.class);
        assertThat(TeacherRecordFields.asProperty().type()).isEqualTo(TeacherRecord.class);
    }

    @Test
    @DisplayName("Should support custom prefix via path() factory")
    void testCustomPrefix() {
        StudentFields.Path s = StudentFields.path("s");
        assertThat(s.name.name()).isEqualTo("s.name");
        assertThat(s.courses.title.name()).isEqualTo("s.courses.title");
        assertThat(s.courses.department.deptName.name()).isEqualTo("s.courses.department.deptName");
        assertThat(s.path()).isEqualTo("s");
    }

    @Test
    @DisplayName("Should implement CharSequence for direct string concatenation and JPQL")
    void testCharSequenceAndToString() {
        assertThat(StudentFields.courses.toString()).isEqualTo("courses");
        assertThat(StudentFields.courses.department.toString()).isEqualTo("courses.department");
        assertThat(StudentFields.courses.title.toString()).isEqualTo("courses.title");

        // String concatenation test
        String jpql = "SELECT s FROM Student s JOIN s." + StudentFields.courses + " c WHERE c." + CourseFields.title
                + " = :title";
        assertThat(jpql).isEqualTo("SELECT s FROM Student s JOIN s.courses c WHERE c.title = :title");

        String deepJpql = "s." + StudentFields.courses.department.deptName;
        assertThat(deepJpql).isEqualTo("s.courses.department.deptName");

        // CharSequence length and charAt
        assertThat(StudentFields.courses.length()).isEqualTo(7);
        assertThat(StudentFields.courses.charAt(0)).isEqualTo('c');
        assertThat(StudentFields.courses.subSequence(0, 4).toString()).isEqualTo("cour");
    }

    @Test
    @DisplayName("Should support FIELD_MAP, getProperty, and asProperty for relations")
    void testMetadataInspection() {
        // Scalar and relation fields all in FIELD_MAP
        assertThat(StudentFields.FIELD_MAP).containsKeys("id", "name", "age", "tags", "scores", "course", "courses");
        assertThat(CourseFields.FIELD_MAP).containsKeys("id", "title", "department", "student");

        // Scalar metadata
        ModelField<?, ?> nameProp = StudentFields.getProperty("name");
        assertThat(nameProp.name()).isEqualTo("name");
        assertThat(nameProp.type()).isEqualTo(String.class);

        // Relation metadata in CourseFields
        ModelField<?, ?> deptProp = CourseFields.getProperty("department");
        assertThat(deptProp.name()).isEqualTo("department");
        assertThat(deptProp.type()).isEqualTo(Department.class);
        assertThat(deptProp.elementType()).isNull();

        // Direct relation property access
        assertThat(CourseFields.department.property).isNotNull();
        assertThat(CourseFields.department.property.name()).isEqualTo("department");
        assertThat(CourseFields.department.property.type()).isEqualTo(Department.class);
        assertThat(CourseFields.department.asProperty().name()).isEqualTo("department");

        // Collection relation metadata in StudentFields
        ModelField<?, ?> coursesProp = StudentFields.getProperty("courses");
        assertThat(coursesProp.name()).isEqualTo("courses");
        assertThat(coursesProp.type()).isEqualTo(List.class);
        assertThat(coursesProp.elementType()).isEqualTo(Course.class);

        // Direct relation navigation still works seamlessly
        assertThat(CourseFields.department.deptName.name()).isEqualTo("department.deptName");
        assertThat(StudentFields.courses.title.name()).isEqualTo("courses.title");

        // Root asProperty
        assertThat(CourseFields.asProperty().name()).isEqualTo("");
        assertThat(CourseFields.asProperty().type()).isEqualTo(Course.class);

        assertThatThrownBy(() -> StudentFields.getProperty("nonExistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown field: nonExistent");
    }

    @Test
    @DisplayName("Should extract full map and non-null map from Entity class")
    void testEntityMapExtraction() {
        Student student = new Student();
        student.setId(1L);
        student.setName("Alice");
        student.setAge(20);

        // toNonNullMap (excludes null fields like 'course', 'scores')
        Map<String, Object> nonNullMap = StudentFields.toNonNullMap(student);
        assertThat(nonNullMap.get("id")).isEqualTo(1L);
        assertThat(nonNullMap.get("name")).isEqualTo("Alice");
        assertThat(nonNullMap.get("age")).isEqualTo(20);
        assertThat(nonNullMap).doesNotContainKey("course");
        assertThat(nonNullMap).doesNotContainKey("scores");

        // toMap (includes null fields)
        Map<String, Object> fullMap = StudentFields.toMap(student);
        assertThat(fullMap.get("id")).isEqualTo(1L);
        assertThat(fullMap.get("name")).isEqualTo("Alice");
        assertThat(fullMap).containsKey("course");
        assertThat(fullMap.get("course")).isNull();

        // Null safe
        assertThat(StudentFields.toMap(null)).isEmpty();
        assertThat(StudentFields.toNonNullMap(null)).isEmpty();
    }

    @Test
    @DisplayName("Should extract full values list and non-null values list from Entity class")
    void testEntityListExtraction() {
        Student student = new Student();
        student.setId(1L);
        student.setName("Alice");
        student.setAge(20);

        // toNonNullValues only contains non-null values
        List<Object> nonNullValues = StudentFields.toNonNullValues(student);
        assertThat(nonNullValues).containsExactly(1L, "Alice", 20);

        // toValues contains all values in field order including nulls
        List<Object> allValues = StudentFields.toValues(student);
        assertThat(allValues.get(0)).isEqualTo(1L);
        assertThat(allValues.get(1)).isEqualTo("Alice");
        assertThat(allValues.get(2)).isEqualTo(20);
        assertThat(allValues).containsNull();
        assertThat(allValues.get(3)).isNull();

        // Null safe
        assertThat(StudentFields.toValues(null)).isEmpty();
        assertThat(StudentFields.toNonNullValues(null)).isEmpty();
    }

    @Test
    @DisplayName("Should omit all ModelField properties and Path class when fields = false")
    void testFieldsDisabledOnDto() {
        // SampleDto has fields = false, maps = true, lists = true
        // 1. Verify that maps and lists are generated
        SampleDto dto = new SampleDto(10L, "D-10", null);
        Map<String, Object> map = SampleDtoFields.toMap(dto);
        assertThat(map.get("id")).isEqualTo(10L);
        assertThat(map.get("code")).isEqualTo("D-10");
        assertThat(map.get("department")).isNull();

        List<Object> values = SampleDtoFields.toValues(dto);
        assertThat(values.get(0)).isEqualTo(10L);
        assertThat(values.get(1)).isEqualTo("D-10");
        assertThat(values.get(2)).isNull();

        // 2. Verify that NO Path inner class is generated
        assertThat(SampleDtoFields.class.getDeclaredClasses()).isEmpty();

        // 3. Verify that NO static ModelField constants or FIELD_MAP are generated
        assertThat(SampleDtoFields.class.getDeclaredFields()).isEmpty();
    }

    @Test
    @DisplayName("Should extract maps and lists from Java Record")
    void testRecordValuesExtraction() {
        TeacherRecord teacher = new TeacherRecord(101L, "Dr. Smith", "Mathematics", null);

        Map<String, Object> nonNullMap = TeacherRecordFields.toNonNullMap(teacher);
        assertThat(nonNullMap.get("id")).isEqualTo(101L);
        assertThat(nonNullMap.get("name")).isEqualTo("Dr. Smith");
        assertThat(nonNullMap.get("subject")).isEqualTo("Mathematics");
        assertThat(nonNullMap).doesNotContainKey("course");

        Map<String, Object> fullMap = TeacherRecordFields.toMap(teacher);
        assertThat(fullMap.get("id")).isEqualTo(101L);
        assertThat(fullMap).containsKey("course");
        assertThat(fullMap.get("course")).isNull();

        List<Object> values = TeacherRecordFields.toValues(teacher);
        assertThat(values.get(0)).isEqualTo(101L);
        assertThat(values.get(1)).isEqualTo("Dr. Smith");
        assertThat(values.get(2)).isEqualTo("Mathematics");
        assertThat(values.get(3)).isNull();
    }

    @Test
    @DisplayName("Should handle Entity without getters safely")
    void testNoGetterEntityExtraction() {
        assertThat(NoGetterEntityFields.id.name()).isEqualTo("id");
        assertThat(NoGetterEntityFields.title.name()).isEqualTo("title");

        NoGetterEntity entity = new NoGetterEntity(50L, "Sample Title");
        Map<String, Object> fieldMap = NoGetterEntityFields.toNonNullMap(entity);
        assertThat(fieldMap).isEmpty();
    }
}
