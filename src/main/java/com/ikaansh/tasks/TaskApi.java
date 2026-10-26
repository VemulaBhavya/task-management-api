package com.ikaansh.tasks;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public final class TaskApi {
    private static final Gson GSON = new Gson();
    private final TaskDao dao;

    public TaskApi(TaskDao dao) {
        this.dao = dao;
    }

    public Javalin createServer() {
        Javalin app = Javalin.create(config -> config.staticFiles.add("/public"));
        app.get("/tasks", this::listTasks);
        app.get("/tasks/{id}", this::getTask);
        app.post("/tasks", this::createTask);
        app.put("/tasks/{id}", this::updateTask);
        app.delete("/tasks/{id}", this::deleteTask);
        app.error(404, context -> sendJson(context, 404, new ApiError("not_found", "Route not found")));
        app.exception(BadRequestException.class, (exception, context) ->
            sendJson(context, 400, new ApiError("bad_request", exception.getMessage())));
        app.exception(Exception.class, (exception, context) -> {
            exception.printStackTrace();
            sendJson(context, 500, new ApiError("internal_server_error", "An unexpected error occurred"));
        });
        return app;
    }

    private void listTasks(Context context) throws SQLException {
        TaskFilters filters = parseFilters(context);
        sendJson(context, 200, dao.findAll(filters));
    }

    private void getTask(Context context) throws SQLException {
        long id = parseId(context.pathParam("id"));
        Optional<Task> task = dao.findById(id);
        if (task.isEmpty()) {
            sendJson(context, 404, new ApiError("not_found", "Task " + id + " was not found"));
            return;
        }
        sendJson(context, 200, task.get());
    }

    private void createTask(Context context) throws SQLException {
        TaskInput input = parseTask(context);
        Task task = dao.create(input.title(), input.description(), input.status(), input.priority(), input.dueDate());
        context.header("Location", "/tasks/" + task.id());
        sendJson(context, 201, task);
    }

    private void updateTask(Context context) throws SQLException {
        long id = parseId(context.pathParam("id"));
        TaskInput input = parseTask(context);
        Optional<Task> task = dao.update(id, input.title(), input.description(), input.status(),
                input.priority(), input.dueDate());
        if (task.isEmpty()) {
            sendJson(context, 404, new ApiError("not_found", "Task " + id + " was not found"));
            return;
        }
        sendJson(context, 200, task.get());
    }

    private void deleteTask(Context context) throws SQLException {
        long id = parseId(context.pathParam("id"));
        if (!dao.delete(id)) {
            sendJson(context, 404, new ApiError("not_found", "Task " + id + " was not found"));
            return;
        }
        context.status(204);
    }

    private static TaskInput parseTask(Context context) {
        TaskRequest request;
        try {
            request = GSON.fromJson(context.body(), TaskRequest.class);
        } catch (JsonParseException exception) {
            throw new BadRequestException("Request body must be valid JSON");
        }
        if (request == null) {
            throw new BadRequestException("Request body must be a JSON object");
        }
        String title = request.title == null ? "" : request.title.trim();
        if (title.isEmpty()) {
            throw new BadRequestException("title is required and must not be blank");
        }
        if (title.length() > 255) {
            throw new BadRequestException("title must be 255 characters or fewer");
        }
        TaskStatus status = parseEnum(request.status, TaskStatus.class, "status", TaskStatus.TODO);
        TaskPriority priority = parseEnum(request.priority, TaskPriority.class, "priority", TaskPriority.MEDIUM);
        LocalDate dueDate = parseDate(request.due_date, "due_date");
        return new TaskInput(title, request.description, status, priority, dueDate);
    }

    private static TaskFilters parseFilters(Context context) {
        TaskStatus status = parseEnum(context.queryParam("status"), TaskStatus.class, "status", null);
        TaskPriority priority = parseEnum(context.queryParam("priority"), TaskPriority.class, "priority", null);
        LocalDate from = parseDate(context.queryParam("due_date_from"), "due_date_from");
        LocalDate to = parseDate(context.queryParam("due_date_to"), "due_date_to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("due_date_from must be on or before due_date_to");
        }
        int page = parsePositiveInt(context.queryParam("page"), "page", 1);
        int size = parsePositiveInt(context.queryParam("size"), "size", 20);
        if (size > 100) {
            throw new BadRequestException("size must be 100 or fewer");
        }
        return new TaskFilters(status, priority, from == null ? null : from.toString(),
                to == null ? null : to.toString(), page, size);
    }

    private static <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, String field, T defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(field + " must be one of: " + String.join(", ",
                    java.util.Arrays.stream(enumType.getEnumConstants()).map(Enum::name).toList()));
        }
    }

    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(field + " must use YYYY-MM-DD format");
        }
    }

    private static int parsePositiveInt(String value, String field, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BadRequestException(field + " must be a positive integer");
        }
    }

    private static long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id < 1) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Task ID must be a positive integer");
        }
    }

    private static void sendJson(Context context, int status, Object value) {
        context.status(status).contentType("application/json").result(GSON.toJson(value));
    }

    private record TaskInput(String title, String description, TaskStatus status,
                             TaskPriority priority, LocalDate dueDate) {
    }

    private static final class BadRequestException extends RuntimeException {
        private BadRequestException(String message) {
            super(message);
        }
    }
}