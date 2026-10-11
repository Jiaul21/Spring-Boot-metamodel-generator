package com.jiaul.metamodelgenerator.testmodel;

import com.jiaul.metamodelgenerator.annotation.EntityFields;

@EntityFields
public record TeacherRecord(
        Long id,
        String name,
        String subject,
        Course course
) {
}
