package cli;

import model.Operation;
import model.OperationType;
import service.AuthService;
import service.FinanceService;
import service.ReportService;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ConsoleCli {

    private static final DateTimeFormatter DATE_INPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AuthService authService;
    private final FinanceService financeService;
    private final ReportService reportService;
    private final Scanner scanner;

    public ConsoleCli(AuthService authService,
                      FinanceService financeService,
                      ReportService reportService) {
        this.authService = authService;
        this.financeService = financeService;
        this.reportService = reportService;
        this.scanner = new Scanner(System.in);
    }

    public void run() {
        System.out.println("Добро пожаловать в JustLedger — систему управления личными финансами!");
        boolean running = true;

        while (running) {
            try {
                if (authService.getCurrentUser().isEmpty()) {
                    running = handleGuestMenu();
                } else {
                    running = handleUserMenu();
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("Ошибка: " + e.getMessage());
            } catch (NoSuchElementException e) {
                System.out.println("Ввод завершён. Выход из программы.");
                running = false;
            } catch (Exception e) {
                System.out.println("Непредвиденная ошибка: " + e.getMessage());
            }
        }

        System.out.println("Работа программы завершена. До свидания!");
    }

    private boolean handleGuestMenu() {
        System.out.println("\n=== Меню входа ===");
        System.out.println("1. Регистрация");
        System.out.println("2. Вход в аккаунт");
        System.out.println("help. Справка");
        System.out.println("0. Выход из программы");
        System.out.print("Выберите действие: ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> register();
            case "2" -> login();
            case "help", "h" -> printHelp();
            case "0" -> {
                return false;
            }
            default -> System.out.println("Неизвестная команда. Введите 1, 2, help или 0.");
        }
        return true;
    }

    private boolean handleUserMenu() {
        String currentLogin = authService.getCurrentUser().orElseThrow().getLogin();
        System.out.println("\n=== Личный кабинет (" + currentLogin + ") ===");
        System.out.println("1. Добавить доход");
        System.out.println("2. Добавить расход");
        System.out.println("3. Создать категорию / задать бюджет");
        System.out.println("4. Изменить лимит категории");
        System.out.println("5. Удалить категорию");
        System.out.println("6. Показать финансовый отчёт");
        System.out.println("7. Подсчёт по выбранным категориям");
        System.out.println("8. Операции за период");
        System.out.println("9. Перевод другому пользователю");
        System.out.println("10. Экспорт / Импорт данных");
        System.out.println("11. Выйти из аккаунта");
        System.out.println("12. Справка (help)");
        System.out.println("0. Выход из программы");
        System.out.print("Выберите действие: ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> addIncome();
            case "2" -> addExpense();
            case "3" -> createCategory();
            case "4" -> updateCategoryLimit();
            case "5" -> removeCategory();
            case "6" -> System.out.println("\n" + reportService.generateSummaryReport());
            case "7" -> showTotalsByCategories();
            case "8" -> showOperationsByPeriod();
            case "9" -> transfer();
            case "10" -> handleExportImportMenu();
            case "12", "help", "h" -> printHelp();
            case "11" -> {
                authService.logout();
                System.out.println("Вы вышли из аккаунта. Данные кошелька сохранены.");
            }
            case "0" -> {
                authService.logout();
                System.out.println("Данные кошелька сохранены.");
                return false;
            }
            default -> System.out.println("Неизвестная команда. Попробуйте снова.");
        }
        return true;
    }

    private void register() {
        System.out.print("Введите новый логин: ");
        String login = scanner.nextLine();
        System.out.print("Введите пароль: ");
        String password = scanner.nextLine();

        authService.register(login, password);
        System.out.println("Регистрация прошла успешно! Теперь вы можете войти.");
    }

    private void login() {
        System.out.print("Введите логин: ");
        String login = scanner.nextLine();
        System.out.print("Введите пароль: ");
        String password = scanner.nextLine();

        authService.login(login, password);
        System.out.println("Вход выполнен успешно!");
    }

    private void addIncome() {
        System.out.print("Категория дохода: ");
        String category = scanner.nextLine();
        double amount = readDouble("Сумма дохода: ");
        System.out.print("Комментарий (необязательно, Enter чтобы пропустить): ");
        String comment = scanner.nextLine();

        financeService.addIncome(category, amount, comment.isBlank() ? null : comment);
        System.out.println("Доход успешно добавлен.");
    }

    private void addExpense() {
        System.out.print("Категория расхода: ");
        String category = scanner.nextLine();
        double amount = readDouble("Сумма расхода: ");
        System.out.print("Комментарий (необязательно, Enter чтобы пропустить): ");
        String comment = scanner.nextLine();

        List<String> alerts = financeService.addExpense(category, amount, comment.isBlank() ? null : comment);
        System.out.println("Расход успешно добавлен.");
        printAlerts(alerts);
    }

    private void createCategory() {
        System.out.print("Название новой категории: ");
        String name = scanner.nextLine();
        System.out.print("Тип категории (1 - Доход, 2 - Расход): ");
        String typeChoice = scanner.nextLine().trim();

        OperationType type = switch (typeChoice) {
            case "1" -> OperationType.INCOME;
            case "2" -> OperationType.EXPENSE;
            default -> throw new IllegalArgumentException("Выберите 1 (Доход) или 2 (Расход)");
        };

        Double limit = null;
        if (type == OperationType.EXPENSE) {
            System.out.print("Лимит бюджета (Enter, если без лимита): ");
            String limitInput = scanner.nextLine().trim();
            if (!limitInput.isEmpty()) {
                limit = parseDouble(limitInput);
            }
        }

        financeService.createCategory(name, type, limit);
        System.out.println("Категория '" + name.trim() + "' сохранена.");
    }

    private void updateCategoryLimit() {
        System.out.print("Название категории расходов: ");
        String name = scanner.nextLine();
        System.out.print("Новый лимит (Enter, чтобы снять лимит): ");
        String limitInput = scanner.nextLine().trim();

        Double newLimit = limitInput.isEmpty() ? null : parseDouble(limitInput);
        financeService.updateCategoryLimit(name, newLimit);
        System.out.println("Лимит категории обновлён.");
    }

    private void removeCategory() {
        System.out.print("Название категории для удаления: ");
        String name = scanner.nextLine();
        financeService.removeCategory(name);
        System.out.println("Категория удалена.");
    }

    private void showTotalsByCategories() {
        System.out.print("Тип операций (1 - расходы, 2 - доходы): ");
        String typeChoice = scanner.nextLine().trim();
        OperationType type = switch (typeChoice) {
            case "1" -> OperationType.EXPENSE;
            case "2" -> OperationType.INCOME;
            default -> throw new IllegalArgumentException("Выберите 1 (Расходы) или 2 (Доходы)");
        };

        System.out.print("Введите названия категорий через запятую: ");
        String line = scanner.nextLine();
        List<String> categories = Arrays.stream(line.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        Map<String, Double> result = type == OperationType.EXPENSE
                ? financeService.getExpensesByCategories(categories)
                : financeService.getIncomesByCategories(categories);
        double total = 0;
        System.out.println("\n" + (type == OperationType.EXPENSE ? "Расходы" : "Доходы")
                + " по выбранным категориям:");
        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<String, Double> entry : result.entrySet()) {
            rows.add(List.of(entry.getKey(), String.format("%.2f", entry.getValue())));
            total += entry.getValue();
        }
        printTable(List.of("Категория", "Сумма"), rows);
        System.out.printf("Итого по выбранным категориям: %.2f%n", total);
    }

    private void showOperationsByPeriod() {
        LocalDate fromDate = readDate("Начальная дата (ГГГГ-ММ-ДД): ");
        LocalDate toDate = readDate("Конечная дата (ГГГГ-ММ-ДД): ");

        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.atTime(LocalTime.MAX);

        List<Operation> operations = financeService.getOperationsByPeriod(from, to);
        if (operations.isEmpty()) {
            System.out.println("За указанный период операций не найдено.");
            return;
        }

        System.out.println("\nОперации за период:");
        List<List<String>> rows = new ArrayList<>();
        for (Operation op : operations) {
            String sign = op.operationType() == OperationType.INCOME ? "+" : "-";
            rows.add(List.of(
                    op.timeStamp().toLocalDate().toString(),
                    op.operationType() == OperationType.INCOME ? "Доход" : "Расход",
                    op.categoryName(),
                    String.format("%s%.2f", sign, op.amount()),
                    op.comment() == null ? "" : op.comment()
            ));
        }
        printTable(List.of("Дата", "Тип", "Категория", "Сумма", "Комментарий"), rows);
    }

    private void transfer() {
        System.out.print("Логин получателя: ");
        String recipient = scanner.nextLine();
        double amount = readDouble("Сумма перевода: ");

        List<String> alerts = financeService.transfer(recipient, amount);
        System.out.println("Перевод выполнен успешно.");
        printAlerts(alerts);
    }

    private void handleExportImportMenu() {
        System.out.println("\n--- Экспорт и импорт ---");
        System.out.println("1. Сохранить сводный отчёт в текстовый файл (.txt)");
        System.out.println("2. Экспортировать операции в CSV");
        System.out.println("3. Импортировать операции из CSV");
        System.out.print("Выберите действие: ");

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1" -> {
                System.out.print("Путь к файлу (например, reports/summary.txt): ");
                Path path = Path.of(scanner.nextLine().trim());
                reportService.exportReportToTextFile(path);
                System.out.println("Отчёт успешно сохранён в " + path);
            }
            case "2" -> {
                System.out.print("Путь к CSV-файлу (например, reports/operations.csv): ");
                Path path = Path.of(scanner.nextLine().trim());
                reportService.exportOperationsToCsv(path);
                System.out.println("Операции экспортированы в " + path);
            }
            case "3" -> {
                System.out.print("Путь к CSV-файлу для импорта: ");
                Path path = Path.of(scanner.nextLine().trim());
                int count = reportService.importOperationsFromCsv(path);
                System.out.println("Успешно импортировано операций: " + count);
            }
            default -> System.out.println("Неизвестная команда.");
        }
    }

    private void printHelp() {
        System.out.println("\n=== Справка JustLedger ===");
        System.out.println("Команды выбираются номером в меню; help или h показывают эту справку.");
        System.out.println("Пример работы:");
        System.out.println("  1) Регистрация: 1 -> логин -> пароль");
        System.out.println("  2) Вход: 2 -> логин -> пароль");
        System.out.println("  3) Доход: 1 -> Зарплата -> 20000 -> Enter");
        System.out.println("  4) Расход: 2 -> Еда -> 300 -> Enter");
        System.out.println("  5) Бюджет: 3 -> Еда -> 2 -> 4000");
        System.out.println("  6) Отчёт: 6; подсчёт по категориям: 7; перевод: 9");
        System.out.println("  7) Выход: 0 (данные кошелька сохраняются)");
    }

    private void printTable(List<String> headers, List<List<String>> rows) {
        int[] widths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            widths[i] = headers.get(i).length();
        }
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }

        StringBuilder border = new StringBuilder();
        for (int width : widths) {
            border.append("+").append("-".repeat(width + 2));
        }
        border.append("+");

        System.out.println(border);
        printTableRow(headers, widths);
        System.out.println(border);
        for (List<String> row : rows) {
            printTableRow(row, widths);
        }
        System.out.println(border);
    }

    private void printTableRow(List<String> values, int[] widths) {
        StringBuilder line = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            String value = i < values.size() ? values.get(i) : "";
            line.append(" ").append(String.format("%-" + widths[i] + "s", value)).append(" |");
        }
        System.out.println(line);
    }

    private void printAlerts(List<String> alerts) {
        if (!alerts.isEmpty()) {
            System.out.println();
            for (String alert : alerts) {
                System.out.println("[!] " + alert);
            }
        }
    }

    private double readDouble(String prompt) {
        System.out.print(prompt);
        return parseDouble(scanner.nextLine().trim());
    }

    private double parseDouble(String input) {
        try {
            double value = Double.parseDouble(input.replace(",", "."));
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("Значение должно быть конечным");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ожидалось конечное числовое значение, получено: '" + input + "'");
        }
    }

    private LocalDate readDate(String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        try {
            return LocalDate.parse(input, DATE_INPUT_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Неверный формат даты. Используйте ГГГГ-ММ-ДД (например, 2026-11-23)");
        }
    }
}