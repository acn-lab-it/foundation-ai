# Telegram Long Polling Implementation

## Overview

This document explains the implementation of long polling for the Telegram bot integration, which is primarily used for local development and testing.

## Webhook vs. Long Polling

The Telegram bot integration supports two modes of operation:

1. **Webhook Mode (Production)**: In this mode, Telegram sends updates to a webhook URL that you specify. This requires a publicly accessible HTTPS URL, making it suitable for production environments but challenging for local development.

2. **Long Polling Mode (Local Development)**: In this mode, the application actively polls Telegram's API for updates. This doesn't require a publicly accessible URL, making it ideal for local development and testing.

## Configuration

The mode of operation is controlled by the `telegram.bot.mode` property in `application.properties`:

```properties
# Telegram configuration mode (webhook or long_polling)
# Use webhook for production and long_polling for local development
telegram.bot.mode=webhook
```

To switch to long polling mode for local development, change this to:

```properties
telegram.bot.mode=long_polling
```

Additionally, you need to set the bot username for long polling mode:

```properties
telegram.bot.username=your_bot_username
```

Replace `your_bot_username` with the username of your Telegram bot (without the @ symbol).

## How Long Polling Works

When the application starts in long polling mode:

1. The `TelegramBotService` initializes a `TelegramBotLongPolling` instance
2. The bot is registered with Telegram's API using the `TelegramBotsApi`
3. The bot starts polling Telegram's API for updates
4. When an update is received, the `onUpdateReceived` method is called
5. The update is processed and a response is sent back to the user

## Implementation Details

The long polling implementation consists of the following components:

1. **TelegramBotConfig**: Configuration class that reads properties from `application.properties`
2. **TelegramBotLongPolling**: Implementation of a Telegram bot using the long polling mechanism
3. **TelegramBotService**: Service that initializes and manages Telegram bots based on configuration

### TelegramBotConfig

This class reads configuration values from `application.properties` and provides methods to access them:

- `getBotToken()`: Gets the Telegram bot token
- `getBotUsername()`: Gets the Telegram bot username
- `getBotMode()`: Gets the Telegram bot mode (webhook or long_polling)
- `isWebhookMode()`: Checks if the bot is in webhook mode
- `isLongPollingMode()`: Checks if the bot is in long polling mode

### TelegramBotLongPolling

This class extends `TelegramLongPollingBot` from the Telegram Bot API library and implements the long polling mechanism:

- It receives updates from Telegram via the `onUpdateReceived` method
- It processes different types of messages (text, voice, photo, document)
- It integrates with the `FNOLAssistantAgent` to process messages
- It sends responses back to the user

### TelegramBotService

This service initializes and manages Telegram bots based on configuration:

- It checks the bot mode from the configuration
- It initializes the webhook approach if in webhook mode
- It initializes the long polling approach if in long polling mode
- It provides a method to get webhook information for debugging purposes

## How to Test Locally

To test the Telegram bot locally using long polling:

1. Update `application.properties`:
   ```properties
   telegram.bot.mode=long_polling
   telegram.bot.username=your_bot_username
   ```

2. Start the application:
   ```
   ./mvnw quarkus:dev
   ```

3. The application will start in long polling mode and begin polling Telegram's API for updates

4. Send a message to your bot on Telegram

5. The application will receive the update, process it, and send a response back to you

6. Check the application logs for debugging information

## Advantages of Long Polling for Local Development

- No need for a publicly accessible URL
- No need to set up tunneling services like ngrok
- Easier to debug as everything happens locally
- Faster development cycle as you don't need to restart the tunnel when restarting the application

## Limitations of Long Polling

- Not suitable for production environments with high load
- May not be as responsive as webhook mode
- Requires keeping a connection open, which may not be ideal for all hosting environments

## Switching Between Modes

You can easily switch between webhook and long polling modes by changing the `telegram.bot.mode` property in `application.properties`. This allows you to use long polling for local development and webhook for production.

## Conclusion

The long polling implementation provides a convenient way to test the Telegram bot integration locally without the need for a publicly accessible URL. This makes development and testing much easier, while still allowing the use of webhook mode in production environments.