package repository;

import model.Category;
import model.Operation;
import model.OperationType;
import model.Wallet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FileWalletStorage implements WalletStorage {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String DELIMITER = ";";

    private final Path storageDirectory;

    public FileWalletStorage(Path storageDirectory) {
        if (storageDirectory == null) {
            throw new IllegalArgumentException("Директория хранилища не может быть null");
        }
        this.storageDirectory = storageDirectory;
    }

    @Override
    public void save(String login, Wallet wallet) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Логин не может быть пустым");
        }
        if (wallet == null) {
            throw new IllegalArgumentException("Кошелёк не может быть null");
        }

        try {
            Files.createDirectories(storageDirectory);
            Path filePath = resolveUserFile(login);

            List<String> lines = new ArrayList<>();

            for (Category category : wallet.getCategoryMap().values()) {
                String limitValue = category.getLimit() == null ? "" : String.valueOf(category.getLimit());
                lines.add(String.join(DELIMITER,
                        "CATEGORY",
                        escape(category.getCategoryName()),
                        category.getOperationType().name(),
                        limitValue
                ));
            }

            for (Operation op : wallet.getOperationList()) {
                String commentValue = op.comment() == null ? "" : escape(op.comment());
                lines.add(String.join(DELIMITER,
                        "OPERATION",
                        op.operationType().name(),
                        String.valueOf(op.amount()),
                        escape(op.categoryName()),
                        op.timeStamp().format(DATE_FORMATTER),
                        commentValue
                ));
            }

            Files.write(filePath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка сохранения кошелька пользователя " + login + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Wallet> load(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }

        Path filePath = resolveUserFile(login);
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            Wallet wallet = new Wallet();

            for (String rawLine : lines) {
                if (rawLine.isBlank()) {
                    continue;
                }
                String[] parts = rawLine.split(DELIMITER, -1);
                String recordType = parts[0];

                if ("CATEGORY".equals(recordType) && parts.length >= 4) {
                    String name = unescape(parts[1]);
                    OperationType type = OperationType.valueOf(parts[2]);
                    Double limit = parts[3].isBlank() ? null : Double.parseDouble(parts[3]);
                    wallet.addCategory(new Category(name, type, limit));
                } else if ("OPERATION".equals(recordType) && parts.length >= 6) {
                    OperationType type = OperationType.valueOf(parts[1]);
                    double amount = Double.parseDouble(parts[2]);
                    String categoryName = unescape(parts[3]);
                    LocalDateTime timeStamp = LocalDateTime.parse(parts[4], DATE_FORMATTER);
                    String comment = parts[5].isBlank() ? null : unescape(parts[5]);

                    if (wallet.getCategory(categoryName).isEmpty()) {
                        wallet.addCategory(new Category(categoryName, type, null));
                    }
                    wallet.addOperation(new Operation(type, amount, categoryName, timeStamp, comment));
                }
            }

            return Optional.of(wallet);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка загрузки кошелька пользователя " + login + ": " + e.getMessage(), e);
        }
    }

    private Path resolveUserFile(String login) {
        String safeFileName = login.trim().toLowerCase().replaceAll("[^a-zа-яё0-9_-]", "_") + ".txt";
        return storageDirectory.resolve(safeFileName);
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace(DELIMITER, "\\s")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String unescape(String text) {
        return text.replace("\\n", "\n")
                .replace("\\s", DELIMITER)
                .replace("\\\\", "\\");
    }
}
