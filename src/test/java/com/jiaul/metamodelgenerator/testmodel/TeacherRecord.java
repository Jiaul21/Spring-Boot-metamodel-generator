package com.jiaul.metamodelgenerator.testmodel;

import com.jiaul.metamodelgenerator.annotation.ModelFields;

@ModelFields(fields = true, maps = true, lists = true)
public record TeacherRecord(
        Long id,
        String name,
        String subject,
        Course course
) {}
