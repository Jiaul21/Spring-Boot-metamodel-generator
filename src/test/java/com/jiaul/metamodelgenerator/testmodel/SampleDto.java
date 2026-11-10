package com.jiaul.metamodelgenerator.testmodel;

import com.jiaul.metamodelgenerator.annotation.ModelFields;

@ModelFields(fields = false, maps = true, lists = true)
public class SampleDto {
    private Long id;
    private String code;
    private Department department;

    public SampleDto() {}

    public SampleDto(Long id, String code, Department department) {
        this.id = id;
        this.code = code;
        this.department = department;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
}

