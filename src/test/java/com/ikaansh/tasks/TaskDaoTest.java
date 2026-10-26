package com.ikaansh.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDaoTest {
    private TaskDao dao;

    @BeforeEach
    void setUp() throws Exception {
        String databaseName = "tasks_" + System.nanoTime();
        dao = new TaskDao("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1", "sa", "");
        dao.initialize();
    }

    @Test
    void createsFindsUpdatesAndDeletesTasks() throws Exception {
        Task created = dao.create("Write tests", "DAO coverage", TaskStatus.IN_PROGRESS,
                TaskPriority.HIGH, LocalDate.parse("2026-11-01"));

        Optional<Task> found = dao.findById(created.id());
        assertTrue(found.isPresent());
        assertEquals("Write tests", found.get().title());
        assertEquals("DAO coverage", found.get().description());
        assertEquals(TaskStatus.IN_PROGRESS, found.get().status());
        assertEquals(TaskPriority.HIGH, found.get().priority());
        assertEquals("2026-11-01", found.get().due_date());
        assertTrue(found.get().created_at() != null && !found.get().created_at().isBlank());

        Task updated = dao.update(created.id(), "Review tests", null, TaskStatus.DONE,
                TaskPriority.LOW, null).orElseThrow();
        assertEquals("Review tests", updated.title());
        assertEquals(TaskStatus.DONE, updated.status());
        assertEquals(null, updated.due_date());

        assertTrue(dao.delete(created.id()));
        assertFalse(dao.findById(created.id()).isPresent());
        assertFalse(dao.delete(created.id()));
    }

    @Test
    void filtersAndPaginatesTasks() throws Exception {
        dao.create("First", null, TaskStatus.TODO, TaskPriority.LOW, LocalDate.parse("2026-11-02"));
        dao.create("Second", null, TaskStatus.DONE, TaskPriority.HIGH, LocalDate.parse("2026-11-10"));
        dao.create("Third", null, TaskStatus.DONE, TaskPriority.HIGH, LocalDate.parse("2026-11-20"));

        TaskFilters filters = new TaskFilters(TaskStatus.DONE, TaskPriority.HIGH,
                "2026-11-05", "2026-11-15", 1, 1);
        TaskPage page = dao.findAll(filters);

        assertEquals(1, page.total());
        assertEquals(1, page.items().size());
        assertEquals("Second", page.items().get(0).title());
        assertEquals(1, page.page());
        assertEquals(1, page.size());
    }
}