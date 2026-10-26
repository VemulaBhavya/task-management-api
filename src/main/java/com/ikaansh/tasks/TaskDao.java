package com.ikaansh.tasks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TaskDao {
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public TaskDao(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public void initialize() throws SQLException, IOException {
        try (InputStream schema = TaskDao.class.getResourceAsStream("/schema.sql")) {
            if (schema == null) {
                throw new IOException("schema.sql was not found on the classpath");
            }
            String[] statements = new String(schema.readAllBytes(), StandardCharsets.UTF_8).split(";");
            try (Connection connection = connect(); Statement statement = connection.createStatement()) {
                for (String sql : statements) {
                    if (!sql.isBlank()) {
                        statement.execute(sql.trim());
                    }
                }
            }
        }
    }

    public Task create(String title, String description, TaskStatus status,
                       TaskPriority priority, LocalDate dueDate) throws SQLException {
        String sql = "INSERT INTO tasks (title, description, status, priority, due_date) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setString(3, status.name());
            statement.setString(4, priority.name());
            setDate(statement, 5, dueDate);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Creating a task did not return an ID");
                }
                return findById(keys.getLong(1)).orElseThrow(
                        () -> new SQLException("Created task could not be loaded"));
            }
        }
    }

    public Optional<Task> findById(long id) throws SQLException {
        String sql = "SELECT id, title, description, status, priority, due_date, created_at FROM tasks WHERE id = ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapTask(result)) : Optional.empty();
            }
        }
    }

    public TaskPage findAll(TaskFilters filters) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (filters.status() != null) {
            where.append(" AND status = ?");
            parameters.add(filters.status().name());
        }
        if (filters.priority() != null) {
            where.append(" AND priority = ?");
            parameters.add(filters.priority().name());
        }
        if (filters.dueDateFrom() != null) {
            where.append(" AND due_date >= ?");
            parameters.add(Date.valueOf(filters.dueDateFrom()));
        }
        if (filters.dueDateTo() != null) {
            where.append(" AND due_date <= ?");
            parameters.add(Date.valueOf(filters.dueDateTo()));
        }

        long total;
        try (Connection connection = connect()) {
            try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM tasks" + where)) {
                bind(count, parameters);
                try (ResultSet result = count.executeQuery()) {
                    result.next();
                    total = result.getLong(1);
                }
            }
            String sql = "SELECT id, title, description, status, priority, due_date, created_at FROM tasks"
                    + where + " ORDER BY id LIMIT ? OFFSET ?";
            List<Task> tasks = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, parameters);
                statement.setInt(parameters.size() + 1, filters.size());
                statement.setLong(parameters.size() + 2, filters.offset());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        tasks.add(mapTask(result));
                    }
                }
            }
            return new TaskPage(tasks, filters.page(), filters.size(), total);
        }
    }

    public Optional<Task> update(long id, String title, String description, TaskStatus status,
                                 TaskPriority priority, LocalDate dueDate) throws SQLException {
        String sql = "UPDATE tasks SET title = ?, description = ?, status = ?, priority = ?, due_date = ? WHERE id = ?";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            statement.setString(2, description);
            statement.setString(3, status.name());
            statement.setString(4, priority.name());
            setDate(statement, 5, dueDate);
            statement.setLong(6, id);
            if (statement.executeUpdate() == 0) {
                return Optional.empty();
            }
        }
        return findById(id);
    }

    public boolean delete(long id) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private static void setDate(PreparedStatement statement, int index, LocalDate dueDate) throws SQLException {
        if (dueDate == null) {
            statement.setNull(index, java.sql.Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(dueDate));
        }
    }

    private static void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }

    private static Task mapTask(ResultSet result) throws SQLException {
        Date dueDate = result.getDate("due_date");
        Timestamp createdAt = result.getTimestamp("created_at");
        LocalDate dueDateValue = dueDate == null ? null : dueDate.toLocalDate();
        LocalDateTime createdAtValue = createdAt.toLocalDateTime();
        return new Task(
                result.getLong("id"),
                result.getString("title"),
                result.getString("description"),
                TaskStatus.valueOf(result.getString("status")),
                TaskPriority.valueOf(result.getString("priority")),
                dueDateValue == null ? null : dueDateValue.toString(),
                createdAtValue.toString()
        );
    }
}