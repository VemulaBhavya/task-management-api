package com.ikaansh.tasks;

public record TaskFilters(
        TaskStatus status,
        TaskPriority priority,
        String dueDateFrom,
        String dueDateTo,
        int page,
        int size
) {
    public long offset() {
        return ((long) page - 1) * size;
    }
}