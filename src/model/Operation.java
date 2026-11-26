package model;

import java.time.LocalDateTime;

public record Operation(
        OperationType operationType,
        double amount,
        String categoryName,
        LocalDateTime timeStamp,
        String comment
) {
    public Operation {
        if (operationType == null) {
            throw new IllegalArgumentException("Тип операции не может быть null");
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Сумма операции должна быть положительным конечным числом");
        }
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("Категория не может быть пустой");
        }

        categoryName = categoryName.trim();
        if (timeStamp == null) {
            timeStamp = LocalDateTime.now();
        }
    }

    // Конструктор без даты и комментария
    public Operation(OperationType operationType, double amount, String categoryName) {
        this(operationType, amount, categoryName, LocalDateTime.now(), null);
    }

    // Конструктор с комментарием, но без даты
    public Operation(OperationType operationType, double amount, String categoryName, String comment) {
        this(operationType, amount, categoryName, LocalDateTime.now(), comment);
    }
}