
package com.wat.bot.views.chat;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.wat.bot.Watsonx;
import com.wat.bot.model.Message;
import org.springframework.beans.factory.annotation.Autowired;


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@PageTitle("Chat")
@Route(value = "gtp-chat")
@RouteAlias(value = "")
@CssImport("../frontend/themes/watsonbot/styles.css")
public class ChatView extends VerticalLayout {

    private MessageList chat;
    private MessageInput input;

    @Autowired
    private Watsonx wx;
    private String USER_AVATAR = "https://api.dicebear.com/6.x/big-ears-neutral/svg?seed=Molly";
    private String AI_AVATAR = "https://api.dicebear.com/6.x/bottts/svg?seed=Sophie";
    private String SYSTEM_AVATAR = "https://api.dicebear.com/6.x/bottts/svg?seed=Sheba";

    public ChatView() {
        chat = new MessageList();
        input = new MessageInput();
        add(chat, input);

        this.setHorizontalComponentAlignment(Alignment.CENTER, chat, input);
        this.setPadding(true);
        this.setHeightFull();
        this.
        chat.setSizeFull();
        input.setWidthFull();
        chat.setMaxWidth("800px");
        input.setMaxWidth("800px");

        input.addSubmitListener(this::onSubmit);
    }

    private void onSubmit(MessageInput.SubmitEvent submitEvent) {
        List<MessageListItem> items = new ArrayList<>(chat.getItems());
        MessageListItem inputItem = new MessageListItem(submitEvent.getValue(), Instant.now(), formatName("user"), getAvatar("user"));
        items.add(inputItem);
        chat.setItems(items);

        wx.sendAsync(submitEvent.getValue()).whenComplete((messages, t) -> {
            getUI().get().access(() -> {
                chat.setItems(messages.stream().map(this::convertMessage).collect(Collectors.toList()));
            });
        });
    }

    private MessageListItem convertMessage(Message msg) {
        return new MessageListItem(msg.getContent(), msg.getTime(), formatName(msg.getRole()), getAvatar(msg.getRole()));
    }

    private String getAvatar(String role) {
        if ("assistant".equals(role)) {
            return AI_AVATAR;
        }
        if ("user".equals(role)) {
            return USER_AVATAR;
        }
        return SYSTEM_AVATAR;
    }

    private String formatName(String role) {
        return role != null && !role.isEmpty()? role.substring(0,1).toUpperCase()+role.substring(1): role;
    }
}
