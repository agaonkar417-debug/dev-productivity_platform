package com.devflow.web;

import java.util.List;

/** Exceptions that the API turns into clear HTTP error responses (see ApiExceptionHandler). */
public final class ApiErrors {

    private ApiErrors() {
    }

    /** HTTP 404 */
    public static class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }

    /** HTTP 400 with a list of specific problems the user can fix. */
    public static class BadInput extends RuntimeException {
        private final List<String> problems;

        public BadInput(String message, List<String> problems) {
            super(message);
            this.problems = List.copyOf(problems);
        }

        public BadInput(String message) {
            this(message, List.of());
        }

        public List<String> getProblems() {
            return problems;
        }
    }

    /** HTTP 409 */
    public static class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }
    }
}
