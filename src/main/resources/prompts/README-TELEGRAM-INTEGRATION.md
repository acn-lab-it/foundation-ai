# Telegram Integration Guide

This document explains how to integrate the FNOL Assistant with Telegram.

## Overview

The FNOL Assistant can now be accessed through Telegram, allowing users to interact with the assistant using the Telegram messaging platform. The integration supports:

- Text messages
- Voice messages
- Photos
- Documents

## How It Works

The integration works by exposing a webhook endpoint that Telegram can call when a user sends a message to your bot. The endpoint processes the message and uses the FNOLAssistantAgent to generate a response, which is then sent back to the user through Telegram.

## Setup

To set up the Telegram integration, follow these steps:

1. Create a Telegram bot using BotFather (https://t.me/botfather)
2. Get your bot token from BotFather
3. Configure the application properties:
   ```properties
   # Telegram bot token and username
   telegram.bot.token=YOUR_BOT_TOKEN_HERE
   telegram.bot.username=YOUR_BOT_USERNAME
   
   # Telegram configuration mode (webhook or long_polling)
   # Use webhook for production and long_polling for local development
   telegram.bot.mode=webhook
   
   # Telegram webhook configuration (used when mode=webhook)
   telegram.webhook.url=https://your-server-url.com
   telegram.webhook.path=/telegram/webhook
   
   # Telegram access control
   # Comma-separated list of chat IDs that are allowed to use the bot
   # If empty, all chat IDs are allowed
   telegram.bot.allowed-chat-ids=123456789,987654321
   ```
   Replace `YOUR_BOT_TOKEN_HERE` with your bot token, `YOUR_BOT_USERNAME` with your bot username (without the @ symbol), and `https://your-server-url.com` with the URL of your server. Replace the example chat IDs with the actual chat IDs you want to allow.

### Operation Modes

The Telegram integration supports two modes of operation:

1. **Webhook Mode (Production)**: In this mode, Telegram sends updates to a webhook URL that you specify. This requires a publicly accessible HTTPS URL, making it suitable for production environments.

2. **Long Polling Mode (Local Development)**: In this mode, the application actively polls Telegram's API for updates. This doesn't require a publicly accessible URL, making it ideal for local development and testing.

The mode is controlled by the `telegram.bot.mode` property in `application.properties`. Set it to `webhook` for production or `long_polling` for local development.

### Access Control

The Telegram integration includes an access control feature that allows you to restrict which users can interact with the bot. This is useful for:

1. **Security**: Ensuring that only authorized users can access sensitive information
2. **Testing**: Limiting access during testing or development phases
3. **Resource Management**: Preventing unauthorized usage that could consume resources

#### How Access Control Works

The access control is based on Telegram chat IDs. Each Telegram user or group has a unique chat ID. The bot checks if the chat ID of an incoming message is in the list of allowed chat IDs before processing the message.

To configure access control:

1. Add the chat IDs you want to allow to the `telegram.bot.allowed-chat-ids` property in `application.properties`:
   ```properties
   telegram.bot.allowed-chat-ids=123456789,987654321
   ```

2. If the property is empty, all chat IDs are allowed (no restrictions):
   ```properties
   telegram.bot.allowed-chat-ids=
   ```

When a user who is not in the allowed list tries to interact with the bot, they will receive an "Access denied" message and their request will not be processed.

#### Finding Your Chat ID

To find your Telegram chat ID:

1. Start a conversation with the [@userinfobot](https://t.me/userinfobot) on Telegram
2. The bot will reply with your chat ID and other information
3. For group chats, you can use [@RawDataBot](https://t.me/RawDataBot) or other similar bots

### Automatic Webhook Registration

When in webhook mode, the application automatically registers the webhook URL with Telegram's API on startup. This is handled by the `TelegramBotService` component, which:

1. Reads the configuration from application.properties
2. Constructs the full webhook URL by combining `telegram.webhook.url` and `telegram.webhook.path`
3. Sends a request to Telegram's API to register the webhook

You can verify the webhook registration by checking the application logs during startup or by accessing the webhook information through Telegram's API:
```
https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getWebhookInfo
```

## Webhook Endpoint

The webhook endpoint is available at:

```
POST /telegram/webhook
```

This endpoint accepts JSON payloads from Telegram and returns JSON responses that Telegram can use to send messages back to the user.

## Session Management

The integration maintains conversation context by using a session ID based on the Telegram chat ID. This allows the assistant to remember previous interactions with the user within the same chat.

## Language Support

The integration supports multiple languages by:

1. Detecting the user's language preference from the Telegram user object
2. Using the appropriate language-specific prompts for the assistant
3. Maintaining language context across the conversation

## Media Handling

The integration supports various types of media:

- **Voice Messages**: Processed as audio messages for speech-to-text conversion
- **Photos**: Processed as media files for analysis
- **Documents**: Processed as media files for analysis

## Implementation Details

The integration is implemented in the `TelegramResource` class, which:

1. Receives Telegram updates via the webhook endpoint
2. Extracts message content (text, voice, photos, documents)
3. Processes the message using the FNOLAssistantAgent
4. Returns a response to Telegram

The integration uses the same underlying FNOLAssistantAgent as the web interface, ensuring consistent behavior across platforms.

## Limitations

- The current implementation does not include actual file download logic from Telegram. In a production environment, you would need to implement this using Telegram's getFile API.
- The integration does not support all Telegram message types (e.g., location, contact, etc.).
- The integration does not support Telegram-specific features like inline keyboards or buttons.

## Testing

### Testing in Production (Webhook Mode)

To test the Telegram integration in webhook mode:

1. Set up a Telegram bot for testing using BotFather
2. Configure the application properties for webhook mode:
   ```properties
   telegram.bot.mode=webhook
   telegram.webhook.url=https://your-server-url.com
   ```
3. Deploy the application to a server with a publicly accessible HTTPS URL
4. Send different types of messages to the bot:
   - Text messages
   - Voice messages
   - Photos
   - Documents
5. Verify that the bot responds appropriately to each message type
6. Test conversation context by having a multi-turn conversation
7. Test language support by using Telegram clients with different language settings

### Testing Locally (Long Polling Mode)

To test the Telegram integration locally using long polling:

1. Set up a Telegram bot for testing using BotFather
2. Configure the application properties for long polling mode:
   ```properties
   telegram.bot.mode=long_polling
   telegram.bot.username=your_bot_username
   ```
3. Start the application locally:
   ```
   ./mvnw quarkus:dev
   ```
4. The application will start in long polling mode and begin polling Telegram's API for updates
5. Send different types of messages to the bot on Telegram
6. The application will receive the updates, process them, and send responses back to you
7. Check the application logs for debugging information

Long polling mode is ideal for local development because:
- It doesn't require a publicly accessible URL
- It doesn't require setting up tunneling services like ngrok
- It's easier to debug as everything happens locally
- It provides a faster development cycle

## Future Enhancements

Possible future enhancements include:

- Support for more Telegram message types
- Support for Telegram-specific features like inline keyboards and buttons
- Improved error handling and logging
- Support for group chats and channels
- Automated testing with mock Telegram updates