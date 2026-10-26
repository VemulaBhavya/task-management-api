package com.ikaansh.tasks;

import java.util.List;

public record TaskPage(List<Task> items, int page, int size, long total) {
}