package com.wat.bot.service;

import com.wat.bot.exception.UserCredentialsException;
import com.wat.bot.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class BankingServiceImpl implements BankingService{
    private final RestTemplate rest;
    @Value("${banking.sys.url}")
    private String bankSysUrl;

    private User user;

    public BankingServiceImpl( RestTemplateBuilder builder) {
        this.rest=builder.build();
        this.user = new User();
    }

    @Override
    public boolean checkUserCredentials(String username, String password) throws UserCredentialsException {
        ResponseEntity<User> resp = null;
        boolean isValidCred = false;
        try {
            if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                String bankingSysCompleteUrl = bankSysUrl + username + "/" + password;
                resp = rest.getForEntity(bankingSysCompleteUrl, User.class);

                if(resp != null && resp.getBody() != null && resp.getBody().getPassword() != null && resp.getBody().getPassword().equalsIgnoreCase(password)){
                    this.user = resp.getBody();
                    return true;
                }
            }
            return false;
        }catch(Exception e){
            if(resp != null && resp.getStatusCode().value() == 404){
                throw new UserCredentialsException("Invalid credentials");
            }else{
                return false;
            }

        }
    }

    public User getCurrentUser(){
        return this.user;
    }
}
