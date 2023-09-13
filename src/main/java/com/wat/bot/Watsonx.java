package com.wat.bot;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.assistant.v2.Assistant;
import com.ibm.watson.assistant.v2.model.*;
import com.wat.bot.exception.UserCredentialsException;
import com.wat.bot.model.Message;
import com.wat.bot.service.BankingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;


@Component
@Scope("session")
public class Watsonx {
    @Value("${watsonx.url}")
    private String waUrl;
    @Value("${watsonx.assistant.id}")
    private String assistantId;

    private final String apiKey;
    private final List<Message> messages = new ArrayList<>();
    private SessionResponse sessionResponse;
    private  Assistant assistant;
    private static final String version = "2021-08-18";

    private String watsonLastMessage;

    private String username;
    private String password;


    private String action;
    private String fromAccount;
    private String toAccount;

    private boolean isManageInvalidCredentials;
    @Autowired
    private BankingService bs;

    public Watsonx(@Value("${watsonx.apikey}")String apiKey, RestTemplateBuilder builder) {
        this.apiKey = apiKey;
        this.watsonLastMessage = "";
        this.username = "";
        this.password = "";
        this.fromAccount = "";
        this.toAccount = "";
        this.action = "";
        this.isManageInvalidCredentials = false;
    }

    public CompletableFuture<List<Message>> sendAsync(String userInput) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return send(userInput);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Message> send(String userInput) {
        getWASessionResponse();
        boolean isValidCredentials = true;
        String watson = "watson";
        if(this.watsonLastMessage != null && !this.watsonLastMessage.isEmpty()){
            if(this.isManageInvalidCredentials){
                manageInvalidCredentials(userInput);
                return  this.messages;
            }
            userInput = getUserResponse(userInput);
            getBankingSystemInput(this.watsonLastMessage, userInput);
            if(this.watsonLastMessage.contains("enter your password")) {
                if (this.username != null && !this.username.isEmpty() && this.password != null && !this.password.isEmpty()) {
                    try {
                        isValidCredentials = bs.checkUserCredentials(this.username, this.password);
                    }catch(UserCredentialsException e){
                        this.username = "";
                        this.password = "";
                        isValidCredentials = false;
                    }
                }
            }
        }

        if(isValidCredentials) {
            this.messages.add(new Message("user", userInput, Instant.now()));
            try {
                MessageInput input = new MessageInput.Builder().text(userInput).build();
                MessageOptions messageOptions = new MessageOptions.Builder().assistantId(assistantId)
                        .sessionId(sessionResponse.getSessionId()).input(input).build();
                MessageResponse messageResponse = assistant.message(messageOptions).execute().getResult();

                String waMsg = getWatsonMessage(messageResponse);
                if (this.watsonLastMessage.contains("You are now logged out")) {
                    resetBankingSystemInput();
                }
                this.messages.add(new Message(watson, waMsg));

            } catch (Exception e) {
                messages.add(new Message("system", e.getMessage(), Instant.now()));
            }
        }else{
            this.isManageInvalidCredentials = true;
            this.messages.add(new Message("watson", "Invalid credentials, please enter your username again"));
            this.watsonLastMessage = "Enter your username please";
        }
        return messages;
    }

private String getWatsonMessage(  MessageResponse messageResponse ){
    String output = "output";
    String generic = "generic";
    String text = "text";
    String options = "options";

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    JsonNode waResponseNode = objectMapper.valueToTree(messageResponse);
    StringBuilder waBuilderMsg = new StringBuilder();
    AtomicInteger index = new AtomicInteger(1);
    if(waResponseNode.has(output) && waResponseNode.get(output) != null
            && waResponseNode.get(output).has(generic) && waResponseNode.get(output).get(generic) != null) {
        waResponseNode.get(output).get(generic).forEach(node -> {
            if(node.has(text) && node.get(text) != null && !node.get(text).asText().isEmpty()) {
                String msg = node.get(text).asText();
                if(!msg.equals("null")) {
                    waBuilderMsg.append(msg + "\n");
                }
            }
            if(node.has(options) && node.get(options) != null) {
                node.get(options).forEach(o -> {
                    if (isThereAnyOptions(o))
                        waBuilderMsg.append("\t" + index.getAndIncrement() + ") "
                                + o.get("value").get("input").get(text).asText() + "\n");
                });
            }
        });
    }
    this.watsonLastMessage = waBuilderMsg.toString();
    return  waBuilderMsg.toString();
}

    private void manageInvalidCredentials(String userInput){
        this.username = userInput;
        this.watsonLastMessage = "Please, enter your password again";
        this.messages.add(new Message("watson", "Please, enter your password again"));
        this.isManageInvalidCredentials = false;
    }
 private void getWASessionResponse(){
     if( this.sessionResponse == null) {
         IamAuthenticator authenticator = new IamAuthenticator.Builder().apikey(apiKey).build();
         assistant = new Assistant(version, authenticator);
         assistant.setServiceUrl(waUrl);
         CreateSessionOptions options = new CreateSessionOptions.Builder(assistantId).build();
         assistant.createSession(options);
         this.sessionResponse = assistant.createSession(options).execute().getResult();
     }
 }

/** This method retrieves the info needed to call a banking service input**/
private void getBankingSystemInput(String waMsg, String userMsg){
    extractAuthInputFromAIUserChat(waMsg, userMsg);
    extractTransferInputFromAIUserChat(waMsg, userMsg);
    extractPrintingInputFromAIUserChat(waMsg, userMsg);
}
    private void resetBankingSystemInput(){
        this.username = "";
        this.fromAccount = "";
        this.action = "";
        this.watsonLastMessage = "";
        this.password = "";
        this.sessionResponse = null;
    }

private void extractAuthInputFromAIUserChat(String waMsg, String userMsg){
    if(!waMsg.isEmpty()) {
        if (waMsg.contains("Enter your username please")) {
            this.username = userMsg;
            this.action = "authentication";
        }
        if (waMsg.contains("enter your password")) {
            this.password = userMsg;
        }
    }
}

    private void extractTransferInputFromAIUserChat(String waMsg, String userMsg){
        if(!waMsg.isEmpty() && waMsg.contains("You would like to transfer money. Is that correct") && !userMsg.isEmpty()
                && userMsg.trim().equalsIgnoreCase("yes")){
            this.action = "transfer";
        }
        if(waMsg.contains("From which account, please")){
            this.fromAccount = userMsg;
        }
        if(waMsg.contains("You would like to transfer money from your") && !userMsg.isEmpty()
                && userMsg.trim().equalsIgnoreCase("no")){
            this.fromAccount = "";
        }
        if(waMsg.contains("You would like to transfer from saving into checking instead") && !userMsg.isEmpty()
                && userMsg.trim().equalsIgnoreCase("yes")){
            this.fromAccount = "saving";
        }
    }

    private void extractPrintingInputFromAIUserChat(String waMsg, String userMsg){
        if(!waMsg.isEmpty() && waMsg.contains("You would like your account balance to be printed") && !userMsg.isEmpty()
                && userMsg.trim().equalsIgnoreCase("yes")){
            this.action = "balance";
        }
        if(waMsg.contains("Select account, please")){
            this.fromAccount = userMsg.trim();
        }
        if(waMsg.contains(this.fromAccount) && !userMsg.isEmpty()
                && userMsg.trim().equalsIgnoreCase("no")){

            if(this.fromAccount.equalsIgnoreCase("checking")){
                this.fromAccount = "saving";
            }else if(this.fromAccount.equalsIgnoreCase("saving")) {
                this.fromAccount = "checking";
            }
        }
        if(waMsg.contains("account balance to be printed instead.") && !userMsg.isEmpty()
                && userMsg.trim().equalsIgnoreCase("no")){
            this.fromAccount = "";
        }
    }

private String getUserResponse(String userResponse){
        //user has typed no/yes instead od 1/2
    if(userResponse.equalsIgnoreCase("no") || userResponse.equalsIgnoreCase("yes")){
        return userResponse;
    }

    String[] tokens = this.watsonLastMessage.split("\n");
    String userNewInput = userResponse;
    for(String s: tokens){
        if((userResponse.startsWith("1") || userResponse.startsWith("2")) && s.contains(userResponse)){
            userNewInput = s.substring(3);
        }
    }
     return userNewInput;
}
    private boolean isThereAnyOptions(JsonNode options ){
        String value = "value";
        String input = "input";
        String text = "text";

       if(!options.has(value) || options.get(value) == null ){
           return false;
       }
        if(!options.get(value).has(input) || options.get(value).get(input) == null){
            return false;
        }
        if(!options.get(value).get(input).has(text) || options.get(value).get(input).get(text) == null
                || options.get(value).get(input).get(text).asText().isEmpty()){
            return false;
        }
        return true;
    }

    }
