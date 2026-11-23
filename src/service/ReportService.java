package service;

import model.Category;
import model.Operation;
import model.OperationType;
import model.User;
import model.Wallet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CSV_HEADER = "Type;Category;Amount;DateTime;Comment";

    private final AuthService authService;

    public ReportService(AuthService authService) {
        if (authService == null) {
            throw new IllegalArgumentException("AuthService не может быть null");
        }
        this.authService = authService;
    }

    public String generateSummaryReport() {
        User user = getCurrentUser();
        Wallet wallet = user.getWallet();
        List<Operation> operations = wallet.getOperationList();

        double totalIncome = calculateTotalByType(operations, OperationType.INCOME);
        double totalExpense = calculateTotalByType(operations, OperationType.EXPENSE);

        Map<String, Double> incomeByCategory = groupSumByCategory(operations, OperationType.INCOME);
        Map<String, Double> expenseByCategory = groupSumByCategory(operations, OperationType.EXPENSE);

        StringBuilder sb = new StringBuilder();
        sb.append("=== Финансовый отчёт: ").append(user.getLogin()).append(" ===\n");
        sb.append(String.format("Общий доход:  %.2f\n", totalIncome));
        sb.append(String.format("Общий расход: %.2f\n", totalExpense));
        sb.append(String.format("Баланс:       %.2f\n\n", wallet.getBalance()));

        sb.append("Доходы по категориям:\n");
        if (incomeByCategory.isEmpty()) {
            sb.append("  (нет данных)\n");
        } else {
            incomeByCategory.forEach((cat, sum) ->
                    sb.append(String.format("  - %s: %.2f\n", cat, sum)));
        }

        sb.append("\nБюджет по категориям расходов:\n");
        if (expenseByCategory.isEmpty()) {
            sb.append("  (нет данных)\n");
        } else {
            for (Map.Entry<String, Double> entry : expenseByCategory.entrySet()) {
                String catName = entry.getKey();
                double spent = entry.getValue();
                Double limit = wallet.getCategory(catName)
                        .map(Category::getLimit)
                        .orElse(null);

                if (limit != null) {
                    double remaining = limit - spent;
                    sb.append(String.format("  - %s: потрачено %.2f | лимит %.2f | остаток %.2f\n",
                            catName, spent, limit, remaining));
                } else {
                    sb.append(String.format("  - %s: потрачено %.2f | лимит не задан\n",
                            catName, spent));
                }
            }
        }

        return sb.toString();
    }

    public void exportReportToTextFile(Path filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("Путь к файлу не может быть null");
        }
        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            Files.writeString(filePath, generateSummaryReport(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при сохранении текстового отчёта: " + e.getMessage(), e);
        }
    }

    public void exportOperationsToCsv(Path filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("Путь к файлу не может быть null");
        }
        Wallet wallet = getCurrentUser().getWallet();
        List<String> lines = new ArrayList<>();
        lines.add(CSV_HEADER);

        for (Operation op : wallet.getOperationList()) {
            String comment = op.comment() == null ? "" : op.comment().replace(";", ",");
            String line = String.join(";",
                    op.operationType().name(),
                    op.categoryName(),
                    String.valueOf(op.amount()),
                    op.timeStamp().format(DATE_FORMATTER),
                    comment
            );
            lines.add(line);
        }

        try {
            if (filePath.getParent() != null) {
                Files.createDirectories(filePath.getParent());
            }
            Files.write(filePath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при экспорте операций в CSV: " + e.getMessage(), e);
        }
    }

    public int importOperationsFromCsv(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            throw new IllegalArgumentException("Файл для импорта не найден");
        }
        Wallet wallet = getCurrentUser().getWallet();
        int importedCount = 0;

        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || (i == 0 && line.equalsIgnoreCase(CSV_HEADER))) {
                    continue;
                }

                String[] parts = line.split(";", -1);
                if (parts.length < 4) {
                    throw new IllegalArgumentException("Некорректный формат строки CSV №" + (i + 1));
                }

                OperationType type = OperationType.valueOf(parts[0].trim());
                String categoryName = parts[1].trim();
                double amount = Double.parseDouble(parts[2].trim());
                LocalDateTime timeStamp = LocalDateTime.parse(parts[3].trim(), DATE_FORMATTER);
                String comment = parts.length > 4 && !parts[4].isBlank() ? parts[4].trim() : null;

                if (wallet.getCategory(categoryName).isEmpty()) {
                    wallet.addCategory(new Category(categoryName, type, null));
                }

                wallet.addOperation(new Operation(type, amount, categoryName, timeStamp, comment));
                importedCount++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при чтении CSV-файла: " + e.getMessage(), e);
        }

        return importedCount;
    }

    private User getCurrentUser() {
        return authService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Операция недоступна: выполните вход в систему"));
    }

    private double calculateTotalByType(List<Operation> operations, OperationType type) {
        return operations.stream()
                .filter(op -> op.operationType() == type)
                .mapToDouble(Operation::amount)
                .sum();
    }

    private Map<String, Double> groupSumByCategory(List<Operation> operations, OperationType type) {
        return operations.stream()
                .filter(op -> op.operationType() == type)
                .collect(Collectors.groupingBy(
                        Operation::categoryName,
                        LinkedHashMap::new,
                        Collectors.summingDouble(Operation::amount)
                ));
    }
}