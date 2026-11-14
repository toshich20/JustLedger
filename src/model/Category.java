package model;

public class Category {

    private final String categoryName;
    private final OperationType operationType;
    private Double limit;

    public Category (String categoryName, OperationType operationType, Double limit) {
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("У категории должно быть хоть какое-то имя");
        } else {
            this.categoryName = categoryName.trim();
        }

        if (operationType == null) {
            throw new IllegalArgumentException("Некорректный тип операции");
        } else {
            this.operationType = operationType;
        }

        setLimit(limit);
    }

    public Category (String categoryName, OperationType operationType) { //Не передали лимит
        this(categoryName, operationType, null);
    }


    //Геттеры
    public String getCategoryName() {
        return categoryName;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public Double getLimit() {
        return limit;
    }


    //Сеттер для лимита
    public void setLimit(Double limit) {
        if (operationType == OperationType.INCOME && limit != null) {
            throw new IllegalArgumentException("Нельзя задать лимит для доходов, не останавливайтесь в обогащении!!!");
        }
        if (limit != null && limit < 0) {
            throw new IllegalArgumentException("Лимит не может быть отрицательным");
        }
        this.limit = limit;
    }

    public boolean hasLimit() {
        return limit != null;
    }
}
