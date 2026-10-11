package com.jiaul.metamodelgenerator.testmodel;

import com.jiaul.metamodelgenerator.annotation.EntityFields;

@EntityFields
public class NoGetterEntity {
    private Long id;
    private String title;

    public NoGetterEntity() {}

    public NoGetterEntity(Long id, String title) {
        this.id = id;
        this.title = title;
    }
}
