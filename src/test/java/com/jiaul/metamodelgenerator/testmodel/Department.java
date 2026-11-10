package com.jiaul.metamodelgenerator.testmodel;

import com.jiaul.metamodelgenerator.annotation.ModelFields;

@ModelFields
public class Department {
    private Long id;
    private String deptName;

    public Department() {}

    public Department(Long id, String deptName) {
        this.id = id;
        this.deptName = deptName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
}
