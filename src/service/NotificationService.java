package service;

import model.Category;
import model.Operation;
import model.OperationType;
import model.Wallet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NotificationService {

    private static final double WARNING_THRESHOLD = 0.8;

    public List<String> checkAfterExpense(Wallet wallet, String categoryName) {
        if (wallet == null) {
            throw new IllegalArgumentException("Кошелёк не может быть null");
        }

        List<String> notifications = new ArrayList<>();

        // Проверка бюджета по конкретной категории
        if (categoryName != null && !categoryName.isBlank()) {
            Optional<Category> categoryOpt = wallet.getCategory(categoryName);
            if (categoryOpt.isPresent()) {
                Category category = categoryOpt.get();
                Double limit = category.getLimit();
                if (limit != null) {
                    double spent = calculateCategoryExpense(wallet, category.getCategoryName());
                    if (spent > limit) {
                        double overspent = spent - limit;
                        notifications.add(String.format(
                                "Внимание! Превышен бюджет по категории '%s': потрачено %.2f из %.2f (перерасход %.2f).",
                                category.getCategoryName(), spent, limit, overspent
                        ));
                    } else if (spent >= limit * WARNING_THRESHOLD) {
                        double percent = (spent / limit) * 100;
                        notifications.add(String.format(
                                "Предупреждение: по категории '%s' израсходовано %.0f%% бюджета (%.2f из %.2f).",
                                category.getCategoryName(), percent, spent, limit
                        ));
                    }
                }
            }
        }

        // Проверка соотношения общих расходов и доходов
        double totalIncome = calculateTotalByType(wallet, OperationType.INCOME);
        double totalExpense = calculateTotalByType(wallet, OperationType.EXPENSE);
        if (totalExpense > totalIncome) {
            notifications.add(String.format(
                    "Внимание! Общие расходы (%.2f) превысили общие доходы (%.2f).",
                    totalExpense, totalIncome
            ));
        }

        // Проверка нулевого или отрицательного баланса
        double balance = wallet.getBalance();
        if (balance < 0) {
            notifications.add(String.format(
                    "Критично: баланс кошелька отрицательный (%.2f).", balance
            ));
        } else if (balance == 0) {
            notifications.add("Внимание: баланс кошелька равен нулю.");
        }

        return notifications;
    }

    private double calculateTotalByType(Wallet wallet, OperationType type) {
        return wallet.getOperationList().stream()
                .filter(op -> op.operationType() == type)
                .mapToDouble(Operation::amount)
                .sum();
    }

    private double calculateCategoryExpense(Wallet wallet, String categoryName) {
        return wallet.getOperationList().stream()
                .filter(op -> op.operationType() == OperationType.EXPENSE)
                .filter(op -> op.categoryName().equalsIgnoreCase(categoryName))
                .mapToDouble(Operation::amount)
                .sum();
    }
}