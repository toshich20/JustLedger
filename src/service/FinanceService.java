package service;

import model.Category;
import model.Operation;
import model.OperationType;
import model.User;
import model.Wallet;
import repository.UserRepository;
import repository.WalletStorage;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FinanceService {

    private static final String TRANSFER_CATEGORY = "Переводы";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final WalletStorage walletStorage;
    private final NotificationService notificationService;

    public FinanceService(AuthService authService,
                          UserRepository userRepository,
                          WalletStorage walletStorage,
                          NotificationService notificationService) {
        if (authService == null) {
            throw new IllegalArgumentException("AuthService не может быть null");
        }
        if (userRepository == null) {
            throw new IllegalArgumentException("UserRepository не может быть null");
        }
        if (walletStorage == null) {
            throw new IllegalArgumentException("WalletStorage не может быть null");
        }
        if (notificationService == null) {
            throw new IllegalArgumentException("NotificationService не может быть null");
        }
        this.authService = authService;
        this.userRepository = userRepository;
        this.walletStorage = walletStorage;
        this.notificationService = notificationService;
    }

    public void addIncome(String categoryName, double amount) {
        addIncome(categoryName, amount, null);
    }

    public void addIncome(String categoryName, double amount, String comment) {
        validateAmount(amount);
        Wallet wallet = getCurrentWallet();
        Category category = getOrCreateCategory(wallet, categoryName, OperationType.INCOME);

        Operation operation = new Operation(
                OperationType.INCOME,
                amount,
                category.getCategoryName(),
                comment
        );
        wallet.addOperation(operation);
    }

    public List<String> addExpense(String categoryName, double amount) {
        return addExpense(categoryName, amount, null);
    }

    public List<String> addExpense(String categoryName, double amount, String comment) {
        validateAmount(amount);
        Wallet wallet = getCurrentWallet();
        Category category = getOrCreateCategory(wallet, categoryName, OperationType.EXPENSE);

        Operation operation = new Operation(
                OperationType.EXPENSE,
                amount,
                category.getCategoryName(),
                comment
        );
        wallet.addOperation(operation);

        return notificationService.checkAfterExpense(wallet, category.getCategoryName());
    }

    public void createCategory(String name, OperationType type, Double limit) {
        Wallet wallet = getCurrentWallet();
        Category existing = wallet.getCategory(name).orElse(null);

        if (existing != null) {
            if (existing.getOperationType() != type) {
                throw new IllegalArgumentException(
                        "Категория '" + existing.getCategoryName() + "' относится к другому типу операций"
                );
            }
            if (limit != null) {
                existing.setLimit(limit);
            }
            return;
        }

        Category category = new Category(name, type, limit);
        wallet.addCategory(category);
    }

    public void updateCategoryLimit(String categoryName, Double newLimit) {
        Wallet wallet = getCurrentWallet();
        Category category = wallet.getCategory(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("Категория '" + categoryName + "' не найдена"));

        if (category.getOperationType() != OperationType.EXPENSE) {
            throw new IllegalArgumentException("Лимит бюджета можно установить только для категории расходов");
        }
        category.setLimit(newLimit);
    }

    public void removeCategory(String categoryName) {
        Wallet wallet = getCurrentWallet();
        if (!wallet.removeCategory(categoryName)) {
            throw new IllegalArgumentException("Категория '" + categoryName + "' не найдена");
        }
    }

    public double getTotalIncome() {
        return calculateTotalByType(getCurrentWallet(), OperationType.INCOME);
    }

    public double getTotalExpense() {
        return calculateTotalByType(getCurrentWallet(), OperationType.EXPENSE);
    }

    public double getBalance() {
        return getCurrentWallet().getBalance();
    }

    public Map<String, Double> getExpensesByCategories(List<String> categoryNames) {
        return getTotalsByCategories(OperationType.EXPENSE, categoryNames);
    }

    public Map<String, Double> getIncomesByCategories(List<String> categoryNames) {
        return getTotalsByCategories(OperationType.INCOME, categoryNames);
    }

    private Map<String, Double> getTotalsByCategories(OperationType type, List<String> categoryNames) {
        if (categoryNames == null || categoryNames.isEmpty()) {
            throw new IllegalArgumentException("Список категорий для подсчёта не может быть пустым");
        }
        Wallet wallet = getCurrentWallet();
        List<String> missingCategories = new ArrayList<>();
        Map<String, Double> result = new LinkedHashMap<>();

        for (String rawName : categoryNames) {
            if (rawName == null || rawName.isBlank()) {
                continue;
            }
            Category category = wallet.getCategory(rawName).orElse(null);
            if (category == null) {
                missingCategories.add(rawName.trim());
            } else {
                double total = wallet.getOperationList().stream()
                        .filter(op -> op.operationType() == type)
                        .filter(op -> op.categoryName().equalsIgnoreCase(category.getCategoryName()))
                        .mapToDouble(Operation::amount)
                        .sum();
                result.put(category.getCategoryName(), total);
            }
        }

        if (!missingCategories.isEmpty()) {
            throw new IllegalArgumentException("Не найдены категории: " + String.join(", ", missingCategories));
        }

        return result;
    }

    public Double getRemainingBudget(String categoryName) {
        Wallet wallet = getCurrentWallet();
        Category category = wallet.getCategory(categoryName)
                .orElseThrow(() -> new IllegalArgumentException("Категория '" + categoryName + "' не найдена"));

        Double limit = category.getLimit();
        if (limit == null) {
            return null;
        }
        double spent = calculateCategoryExpense(wallet, category.getCategoryName());
        return limit - spent;
    }

    public List<Operation> getOperationsByPeriod(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Некорректный диапазон дат");
        }
        return getCurrentWallet().getOperationList().stream()
                .filter(op -> !op.timeStamp().isBefore(from) && !op.timeStamp().isAfter(to))
                .toList();
    }

    public List<String> transfer(String recipientLogin, double amount) {
        validateAmount(amount);
        if (recipientLogin == null || recipientLogin.isBlank()) {
            throw new IllegalArgumentException("Логин получателя не может быть пустым");
        }

        User sender = getCurrentUser();
        User recipient = userRepository.findByLogin(recipientLogin)
                .orElseThrow(() -> new IllegalArgumentException("Получатель с логином '" + recipientLogin + "' не найден"));

        if (sender.getLogin().equalsIgnoreCase(recipient.getLogin())) {
            throw new IllegalArgumentException("Нельзя перевести средства самому себе");
        }

        LocalDateTime now = LocalDateTime.now();

        Wallet senderWallet = sender.getWallet();
        Category senderCategory = getOrCreateCategory(senderWallet, TRANSFER_CATEGORY, OperationType.EXPENSE);
        senderWallet.addOperation(new Operation(
                OperationType.EXPENSE,
                amount,
                senderCategory.getCategoryName(),
                now,
                "Перевод пользователю " + recipient.getLogin()
        ));

        Wallet recipientWallet = recipient.getWallet();
        Category recipientCategory = getOrCreateCategory(recipientWallet, TRANSFER_CATEGORY, OperationType.INCOME);
        recipientWallet.addOperation(new Operation(
                OperationType.INCOME,
                amount,
                recipientCategory.getCategoryName(),
                now,
                "Перевод от пользователя " + sender.getLogin()
        ));

        walletStorage.save(recipient.getLogin(), recipientWallet);

        return notificationService.checkAfterExpense(senderWallet, senderCategory.getCategoryName());
    }

    private User getCurrentUser() {
        return authService.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Операция недоступна: выполните вход в систему"));
    }

    private Wallet getCurrentWallet() {
        return getCurrentUser().getWallet();
    }

    private Category getOrCreateCategory(Wallet wallet, String categoryName, OperationType expectedType) {
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("Название категории не может быть пустым");
        }
        String trimmedName = categoryName.trim();
        Category existing = wallet.getCategory(trimmedName).orElse(null);

        if (existing != null) {
            if (existing.getOperationType() != expectedType) {
                throw new IllegalArgumentException(
                        "Категория '" + existing.getCategoryName() + "' относится к другому типу операций"
                );
            }
            return existing;
        }

        Category created = new Category(trimmedName, expectedType, null);
        wallet.addCategory(created);
        return created;
    }

    private void validateAmount(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Сумма должна быть положительным конечным числом");
        }
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