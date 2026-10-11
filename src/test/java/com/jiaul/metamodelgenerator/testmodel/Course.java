package com.jiaul.metamodelgenerator.testmodel;

import com.jiaul.metamodelgenerator.annotation.EntityFields;

@EntityFields
public class Course {
    private Long id;
    private String title;
    private Department department;
    private Student student;

    public Course() {}

    public Course(Long id, String title, Department department, Student student) {
        this.id = id;
        this.title = title;
        this.department = department;
        this.student = student;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }
}
