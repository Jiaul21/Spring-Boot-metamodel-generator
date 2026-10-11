package com.jiaul.metamodelgenerator.testmodel;

import com.jiaul.metamodelgenerator.annotation.EntityFields;
import java.util.List;
import java.util.Map;

@EntityFields
public class Student {
    private Long id;
    private String name;
    private Integer age;
    private List<String> tags;
    private Map<String, Integer> scores;
    private Course course;
    private List<Course> courses;

    public Student() {}

    public Student(Long id, String name, Integer age, List<String> tags, Map<String, Integer> scores, Course course, List<Course> courses) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.tags = tags;
        this.scores = scores;
        this.course = course;
        this.courses = courses;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public Map<String, Integer> getScores() { return scores; }
    public void setScores(Map<String, Integer> scores) { this.scores = scores; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public List<Course> getCourses() { return courses; }
    public void setCourses(List<Course> courses) { this.courses = courses; }
}
