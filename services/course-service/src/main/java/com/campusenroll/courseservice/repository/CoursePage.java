package com.campusenroll.courseservice.repository;

import java.util.List;

import com.campusenroll.courseservice.domain.Course;

public record CoursePage(List<Course> items, long totalElements) {

    public CoursePage {
        items = List.copyOf(items);
    }
}
