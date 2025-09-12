# Telegram Access Control Implementation

## Overview

This document summarizes the implementation of access control for the Telegram bot integration. The access control feature allows you to restrict which users can interact with the bot by specifying a list of allowed chat IDs.

## Changes Made

### 1. Configuration Property

Added a new configuration property in `application.properties` to store the list of allowed chat IDs:

```properties
# Telegram access control
# Comma-separated list of chat IDs that are allowed to use the bot
# If empty, all chat IDs are allowed
telegram.bot.allowed-chat-ids=
```

### 2. Configuration Class Update

Updated the `TelegramBotConfig` class to:
- Parse the comma-separated list of allowed chat IDs
- Store the allowed chat IDs in a Set for efficient lookup
- Provide a method to check if a chat ID is allowed

Key additions:
```java
@ConfigProperty(name = "telegram.bot.allowed-chat-ids", defaultValue = "")
String allowedChatIdsString;

private Set<Long> allowedChatIds;

@PostConstruct
public void init() {
    if (allowedChatIdsString == null || allowedChatIdsString.trim().isEmpty()) {
        // If no chat IDs are specified, initialize as an empty set
        allowedChatIds = Collections.emptySet();
    } else {
        // Parse the comma-separated list of chat IDs
        allowedChatIds = Arrays.stream(allowedChatIdsString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }
}

public boolean isChatIdAllowed(Long chatId) {
    // If no chat IDs are specified (empty set), all chat IDs are allowed
    return allowedChatIds.isEmpty() || allowedChatIds.contains(chatId);
}
```

### 3. Webhook Mode Implementation

Updated the `TelegramResource` class to check if a chat ID is allowed before processing the message:

```java
// Extract chat ID for response
Long chatId = update.message.chat.id;

// Check if the chat ID is allowed
if (!telegramBotConfig.isChatIdAllowed(chatId)) {
    // Return access denied response
    TelegramResponse accessDeniedResponse = TelegramResponse.createSendMessageResponse(
        chatId, "Access denied. You are not authorized to use this bot.");
    return Response.ok(accessDeniedResponse).build();
}
```

### 4. Long Polling Mode Implementation

Updated the `TelegramBotLongPolling` class to check if a chat ID is allowed before processing the message:

```java
Message message = update.getMessage();
Long chatId = message.getChatId();

// Check if the chat ID is allowed
if (!config.isChatIdAllowed(chatId)) {
    // Send access denied message
    sendErrorMessage(chatId, "Access denied. You are not authorized to use this bot.");
    return;
}
```

### 5. Documentation Update

Updated the `README-TELEGRAM-INTEGRATION.md` file to:
- Include the new configuration property in the setup instructions
- Add a new section explaining the access control feature
- Provide instructions for finding Telegram chat IDs

## How It Works

1. When the application starts, it parses the comma-separated list of allowed chat IDs from the configuration property.
2. When a message is received (either via webhook or long polling), the bot extracts the chat ID from the message.
3. The bot checks if the chat ID is in the list of allowed chat IDs.
4. If the chat ID is allowed (or if no chat IDs are specified), the bot processes the message normally.
5. If the chat ID is not allowed, the bot sends an "Access denied" message and stops processing the message.

## Testing the Implementation

To test the access control feature:

1. Configure the allowed chat IDs in `application.properties`:
   ```properties
   telegram.bot.allowed-chat-ids=123456789,987654321
   ```
   Replace the example chat IDs with your actual chat ID and perhaps another test chat ID.

2. Start the application:
   ```
   ./mvnw quarkus:dev
   ```

3. Test with an allowed chat ID:
   - Send a message to the bot from a chat with an allowed chat ID
   - Verify that the bot processes the message and responds normally

4. Test with a non-allowed chat ID:
   - Send a message to the bot from a chat with a non-allowed chat ID
   - Verify that the bot responds with an "Access denied" message

5. Test with an empty allowed chat IDs list:
   - Remove all chat IDs from the configuration property
   - Restart the application
   - Verify that the bot processes messages from any chat ID

## Finding Your Chat ID

To find your Telegram chat ID:

1. Start a conversation with the [@userinfobot](https://t.me/userinfobot) on Telegram
2. The bot will reply with your chat ID and other information
3. For group chats, you can use [@RawDataBot](https://t.me/RawDataBot) or other similar bots

## Conclusion

The access control feature provides a simple but effective way to restrict access to the Telegram bot. By specifying a list of allowed chat IDs, you can ensure that only authorized users can interact with the bot. This is particularly useful for bots that handle sensitive information or provide access to restricted functionality.

The implementation is designed to be backward compatible, so if no chat IDs are specified, the bot will continue to work as before, allowing all users to interact with it.