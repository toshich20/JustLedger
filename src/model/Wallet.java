package model;

import java.util.*;

public class Wallet {
    private double balance;
    private final List<Operation> operationList;
    private final Map<String,Category> categoryMap;

    public Wallet () {
        this.balance = 0.0;
        this.operationList = new ArrayList<>();
        this.categoryMap = new LinkedHashMap<>();
    }

    //Геттеры
    public double getBalance() {
        return balance;
    }

    public List<Operation> getOperationList() {
        return List.copyOf(operationList);
    }

    public Map<String, Category> getCategoryMap() {
        return Collections.unmodifiableMap(categoryMap);
    }

    //Основные методы
    public void addOperation(Operation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("Операция не может быть null");
        }

        operationList.add(operation);

        switch (operation.operationType()) {
            case INCOME -> balance += operation.amount();
            case EXPENSE -> balance -= operation.amount();
        }
    }

    public void addCategory(Category category) {
        if (category == null) {
            throw new IllegalArgumentException("Категория не может быть null");
        }
        String key = category.getCategoryName().toLowerCase();
        if (categoryMap.containsKey(key)) {
            throw new IllegalArgumentException("Категория '" + category.getCategoryName() + "' уже существует");
        }
        categoryMap.put(key, category);
    }

    public Optional<Category> getCategory(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(categoryMap.get(name.trim().toLowerCase()));
    }

    public boolean hasCategory(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return categoryMap.containsKey(name.trim().toLowerCase());
    }

    public boolean removeCategory(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        return categoryMap.remove(name.trim().toLowerCase()) != null;
    }


}
