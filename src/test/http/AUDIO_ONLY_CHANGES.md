# Audio-Only Message Processing Changes

This document describes the changes made to handle only the audio file and not the user message when an audio file is received.

## Changes Made

### 1. FnolResource.java

Modified the audio message handling code to replace the user's text message with just the audio message marker when an audio file is present:

```java
// Se c'è un messaggio vocale, ignora il messaggio testuale dell'utente
// e usa solo il messaggio vocale
userMessage = "[AUDIO_MESSAGE]\n" + dst.toString() + "\n[/AUDIO_MESSAGE]";
```

Instead of appending the audio message marker to the user's text message, we now replace the user's text message with just the audio message marker. This ensures that when an audio file is present, only the audio file is processed, not the user's text message.

### 2. Prompt Files

Updated all the prompt files to clarify that when an audio message is present, it completely replaces any text message from the user:

#### English (speechToText-en.json and speech-to-text-prompts.json)

Added:
```
Note that when an audio message is present, it completely replaces any text message from the user - you should only process the transcribed audio.
```

#### Italian (speechToText-it.json and speech-to-text-prompts.json)

Added:
```
Nota che quando è presente un messaggio audio, questo sostituisce completamente qualsiasi messaggio di testo dell'utente - dovresti elaborare solo l'audio trascritto.
```

#### German (speechToText-de.json and speech-to-text-prompts.json)

Added:
```
Beachte, dass wenn eine Audionachricht vorhanden ist, diese jede Textnachricht des Benutzers vollständig ersetzt - du solltest nur das transkribierte Audio verarbeiten.
```

## Testing Instructions

To test these changes:

1. Start the application:
   ```
   cd /Users/francesco.stumpo/IdeaProjects/Allianz/bmp/foundation-ai
   ./mvnw quarkus:dev
   ```

2. Use the HTTP file to send a request with both a text message and an audio file:
   ```
   # Open the HTTP file in IntelliJ
   src/test/http/fnol-chat-audio.http
   
   # Click the green "Run" button next to the request
   ```

3. Verify that:
   - The response contains only the transcribed audio message
   - The original text message ("Hello, I need to check my policy") is ignored
   - The AI processes only the audio content ("the policy number is AUTHHR00026397")

## Expected Behavior

- When a request contains both a text message and an audio file, only the audio file should be processed
- The AI should transcribe the audio file and process the transcribed text as the user's message
- The original text message should be completely ignored

## Troubleshooting

If the changes don't work as expected:

1. Check the application logs for any errors
2. Verify that the audio file is being correctly uploaded and processed
3. Ensure that the prompt files are being correctly loaded by the application
4. Check that the FnolResource.java changes are being applied correctly