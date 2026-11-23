import cli.ConsoleCli;
import repository.FileWalletStorage;
import repository.InMemoryUserRepository;
import repository.UserRepository;
import repository.WalletStorage;
import service.AuthService;
import service.FinanceService;
import service.NotificationService;
import service.ReportService;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        UserRepository userRepository = new InMemoryUserRepository();
        WalletStorage walletStorage = new FileWalletStorage(Path.of("data"));

        AuthService authService = new AuthService(userRepository, walletStorage);
        NotificationService notificationService = new NotificationService();
        FinanceService financeService = new FinanceService(authService, userRepository, walletStorage, notificationService);
        ReportService reportService = new ReportService(authService);

        ConsoleCli cli = new ConsoleCli(authService, financeService, reportService);
        cli.run();
    }
}