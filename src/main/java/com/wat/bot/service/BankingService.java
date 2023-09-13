package com.wat.bot.service;

import com.wat.bot.exception.UserCredentialsException;
import com.wat.bot.model.User;

public interface BankingService {
    public boolean checkUserCredentials(String username, String password) throws UserCredentialsException;

    public User getCurrentUser();
}
