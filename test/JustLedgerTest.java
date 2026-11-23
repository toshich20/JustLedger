import model.Operation;
import model.OperationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.FileWalletStorage;
import repository.InMemoryUserRepository;
import repository.UserRepository;
import repository.WalletStorage;
import service.AuthService;
import service.FinanceService;
import service.NotificationService;
import service.ReportService;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JustLedgerTest {

    @TempDir
    Path tempDir;

    private UserRepository userRepository;
    private WalletStorage walletStorage;
    private AuthService authService;
    private FinanceService financeService;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        walletStorage = new FileWalletStorage(tempDir);
        authService = new AuthService(userRepository, walletStorage);
        NotificationService notificationService = new NotificationService();
        financeService = new FinanceService(authService, userRepository, walletStorage, notificationService);
        reportService = new ReportService(authService);

        authService.register("anton", "12345");
        authService.login("anton", "12345");
    }

    @Test
    void testIncomeExpenseAndBalanceCalculation() {
        financeService.addIncome("Зарплата", 100000);
        financeService.addIncome("Фриланс", 25000);
        financeService.addExpense("Продукты", 15000);
        financeService.addExpense("Транспорт", 5000);

        assertEquals(125000.0, financeService.getTotalIncome(), 0.001);
        assertEquals(20000.0, financeService.getTotalExpense(), 0.001);
        assertEquals(105000.0, financeService.getBalance(), 0.001);
    }

    @Test
    void testBudgetThresholdsAndNotifications() {
        financeService.addIncome("Зарплата", 50000);
        financeService.createCategory("Кафе", OperationType.EXPENSE, 10000.0);

        List<String> noAlerts = financeService.addExpense("Кафе", 5000);
        assertTrue(noAlerts.isEmpty());
        assertEquals(5000.0, financeService.getRemainingBudget("Кафе"), 0.001);

        List<String> warningAlerts = financeService.addExpense("Кафе", 3000);
        assertEquals(1, warningAlerts.size());
        assertTrue(warningAlerts.get(0).contains("80%"));

        List<String> exceededAlerts = financeService.addExpense("Кафе", 3000);
        assertEquals(1, exceededAlerts.size());
        assertTrue(exceededAlerts.get(0).contains("Превышен бюджет"));
        assertEquals(-1000.0, financeService.getRemainingBudget("Кафе"), 0.001);
    }

    @Test
    void testExpensesExceedIncomeAndZeroBalanceAlerts() {
        financeService.addIncome("Подарок", 5000);

        List<String> alerts = financeService.addExpense("Техника", 6000);
        assertEquals(2, alerts.size());
        assertTrue(alerts.stream().anyMatch(a -> a.contains("превысили общие доходы")));
        assertTrue(alerts.stream().anyMatch(a -> a.contains("отрицательный")));
    }

    @Test
    void testExpensesByMultipleCategoriesAndValidation() {
        financeService.addIncome("Зарплата", 50000);
        financeService.addExpense("Еда", 4000);
        financeService.addExpense("Связь", 1000);
        financeService.addExpense("Кино", 2000);

        Map<String, Double> stats = financeService.getExpensesByCategories(List.of("Еда", "Связь"));
        assertEquals(4000.0, stats.get("Еда"), 0.001);
        assertEquals(1000.0, stats.get("Связь"), 0.001);

        assertThrows(IllegalArgumentException.class, () ->
                financeService.getExpensesByCategories(List.of("Еда", "Несуществующая")));
    }

    @Test
    void testTransferBetweenUsers() {
        authService.register("polina", "qwerty");

        financeService.addIncome("Зарплата", 30000);
        financeService.transfer("polina", 7500);

        assertEquals(22500.0, financeService.getBalance(), 0.001);

        authService.logout();
        authService.login("polina", "qwerty");

        assertEquals(7500.0, financeService.getBalance(), 0.001);
        assertEquals(7500.0, financeService.getTotalIncome(), 0.001);
    }

    @Test
    void testPersistenceSaveAndLoadOnLogoutLogin() {
        financeService.addIncome("Зарплата", 40000);
        financeService.createCategory("Аренда", OperationType.EXPENSE, 25000.0);
        financeService.addExpense("Аренда", 25000, "За ноябрь");

        authService.logout();
        authService.login("anton", "12345");

        assertEquals(15000.0, financeService.getBalance(), 0.001);
        assertEquals(0.0, financeService.getRemainingBudget("Аренда"), 0.001);

        List<Operation> ops = financeService.getOperationsByPeriod(
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );
        assertEquals(2, ops.size());
    }

    @Test
    void testReportGenerationAndCsvExportImport() {
        financeService.addIncome("Зарплата", 60000);
        financeService.createCategory("Еда", OperationType.EXPENSE, 15000.0);
        financeService.addExpense("Еда", 5000);

        String report = reportService.generateSummaryReport();
        assertTrue(report.contains("anton"));
        assertTrue(report.contains("60000"));
        assertTrue(report.contains("остаток 10000"));

        Path csvFile = tempDir.resolve("export.csv");
        reportService.exportOperationsToCsv(csvFile);

        authService.register("backup_user", "1111");
        authService.logout();
        authService.login("backup_user", "1111");

        int imported = reportService.importOperationsFromCsv(csvFile);
        assertEquals(2, imported);
        assertEquals(55000.0, financeService.getBalance(), 0.001);
    }
}