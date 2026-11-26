package service;

import model.Operation;
import model.OperationType;
import model.Wallet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class FinanceCalculator {

    private FinanceCalculator() {
    }

    public static double totalByType(Wallet wallet, OperationType type) {
        return totalByType(wallet.getOperationList(), type);
    }

    public static double totalByType(List<Operation> operations, OperationType type) {
        return operations.stream()
                .filter(op -> op.operationType() == type)
                .mapToDouble(Operation::amount)
                .sum();
    }

    public static double byCategory(Wallet wallet, OperationType type, String categoryName) {
        return wallet.getOperationList().stream()
                .filter(op -> op.operationType() == type)
                .filter(op -> op.categoryName().equalsIgnoreCase(categoryName))
                .mapToDouble(Operation::amount)
                .sum();
    }

    public static Map<String, Double> groupSumByCategory(List<Operation> operations, OperationType type) {
        return operations.stream()
                .filter(op -> op.operationType() == type)
                .collect(Collectors.groupingBy(
                        Operation::categoryName,
                        LinkedHashMap::new,
                        Collectors.summingDouble(Operation::amount)
                ));
    }
}
