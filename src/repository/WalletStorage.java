package repository;

import model.Wallet;

import java.util.Optional;

public interface WalletStorage {
    void save(String userLogin, Wallet wallet);
    Optional<Wallet> load(String userLogin);
}
