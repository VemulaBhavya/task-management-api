package com.ikaansh.tasks;

public record Task(
        long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        String due_date,
        String created_at
) {
}