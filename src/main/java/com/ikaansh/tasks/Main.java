package com.ikaansh.tasks;

import io.javalin.Javalin;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String jdbcUrl = env("DB_URL", "jdbc:h2:file:./tasks-db");
        String username = env("DB_USERNAME", "sa");
        String password = env("DB_PASSWORD", "");
        int port = Integer.parseInt(env("PORT", "7070"));

        TaskDao dao = new TaskDao(jdbcUrl, username, password);
        dao.initialize();
        Javalin app = new TaskApi(dao).createServer();
        app.start(port);
        System.out.println("Task API listening at http://localhost:" + port);
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}