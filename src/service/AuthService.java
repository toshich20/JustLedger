package service;

import model.User;
import model.Wallet;
import repository.UserRepository;
import repository.WalletStorage;

import java.util.Optional;

public class AuthService {
    private final UserRepository userRepository;
    private final WalletStorage walletStorage;
    private User currentUser;


    public void register(String login, String password) {
        if (userRepository.existsByLogin(login)) {
            throw new IllegalArgumentException("Пользователь с таким логином уже существует");
        }
        User user = new User(login, password, new Wallet());
        userRepository.save(user);
    }

    public void login(String login, String password) {
        User user = userRepository.findByLogin(login)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь с таким логином не найден"));

        if (!user.checkPassword(password)) {
            throw new IllegalArgumentException("Неверный пароль, попробуйте еще раз");
        }

        Wallet wallet = walletStorage.load(user.getLogin()).orElseGet(Wallet::new);
        user.setWallet(wallet);

        this.currentUser = user;
    }

    public void logout() {
        if (currentUser != null) {
            walletStorage.save(currentUser.getLogin(), currentUser.getWallet());
            this.currentUser = null;
        }
    }

    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public AuthService(UserRepository userRepository, WalletStorage walletStorage) {
        if (userRepository == null) {
            throw new IllegalArgumentException("UserRepository не может быть null");
        }
        if (walletStorage == null) {
            throw new IllegalArgumentException("WalletStorage не может быть null");
        }
        this.userRepository = userRepository;
        this.walletStorage = walletStorage;
    }
}
