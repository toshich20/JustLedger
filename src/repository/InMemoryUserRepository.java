package repository;

import model.User;

import java.util.*;

public class InMemoryUserRepository implements UserRepository{
    private final Map<String, User> users = new HashMap<>();


    @Override
    public void save(User user) {
        if(user == null) {
            throw new IllegalArgumentException("Пользоыватель не может быть null");
        }
        String key = normalizeLogin(user.getLogin());
        if (users.containsKey(key)) {
            throw new IllegalArgumentException("Пользователь с логином '" + user.getLogin() + "' уже существует");
        }
        users.put(key, user);
    }

    @Override
    public Optional<User> findByLogin(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(users.get(normalizeLogin(login)));
    }

    @Override
    public boolean existsByLogin(String login) {
        if (login == null || login.isBlank()) {
            return false;
        }
        return users.containsKey(normalizeLogin(login));
    }

    @Override
    public Collection<User> findAll() {
        return Collections.unmodifiableCollection(users.values());
    }

    private String normalizeLogin(String login) {
        return login.trim().toLowerCase();
    }
}
