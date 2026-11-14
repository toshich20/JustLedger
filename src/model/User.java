package model;

public class User {
    private final String login;
    private String password;
    private final Wallet wallet;

    public User (String login, String password, Wallet wallet) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Логин не может быть пустым");
        }
        this.login = login.trim();

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Пароль  не может быть пустым");
        }
        this.password = password;

        if (wallet == null) {
            this.wallet = new Wallet();
        } else {
            this.wallet = wallet;
        }
    }

    public User (String login, String password) {
        this(login, password, null);
    }

    public String getLogin() {
        return login;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public boolean checkPassword(String rawPassword) {
        return password.equals(rawPassword);
    }
}
