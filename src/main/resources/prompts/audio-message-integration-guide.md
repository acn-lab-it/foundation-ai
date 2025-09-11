# Audio Message Integration Guide

This guide provides instructions for integrating audio message handling capabilities into the existing prompt files.

## Integration Steps

1. Open each language-specific prompt file:
   - `fnol-prompts-en.json` (English)
   - `fnol-prompts-it.json` (Italian)
   - `fnol-prompts-de.json` (German)

2. For each file, make the following changes:

### 1. Update the "superAgent.mainPrompt" section:

Add the following to the "IMPORTANT RULES" section:
```
If you detect audio messages, use the SpeechToTextAgent to transcribe them.
```

Add "SpeechToTextAgent" to the "AVAILABLE TOOLS" section:
```
- SpeechToTextAgent: Transcribe audio messages
```

Add a step for audio processing in the "CLAIM REPORTING PROCESS" section:
```
If audio is provided, transcribe it using SpeechToTextAgent.
```

Add an "AUDIO HANDLING" section after the "MEDIA HANDLING" section:
```
AUDIO HANDLING:
When you detect [AUDIO_MESSAGE] markers in the user's message, extract the file path and use SpeechToTextAgent to transcribe it. The format will be:

[AUDIO_MESSAGE]
/path/to/audio_file
[/AUDIO_MESSAGE]

Use the transcribeAudio tool with the session ID and the audio file path to get the transcribed text. Note that when an audio message is present, it completely replaces any text message from the user - you should only process the transcribed audio.
```

### 2. Add a new "speechToText" section:

Add the following section at the same level as "superAgent", "mediaOcr", and "dateParser":

#### For English (fnol-prompts-en.json):
```json
{
  "speechToText": {
    "mainPrompt": "You are an AI assistant that can process audio messages. When you receive a message with the [AUDIO_MESSAGE] marker, you should use the SpeechToTextAgent to transcribe the audio file and then process the transcribed text as the user's message. Note that when an audio message is present, it completely replaces any text message from the user - you should only process the transcribed audio. The audio file path is provided between the [AUDIO_MESSAGE] and [/AUDIO_MESSAGE] markers. Use the transcribeAudio tool with the session ID and the audio file path to get the transcribed text."
  }
}
```

#### For Italian (fnol-prompts-it.json):
```json
{
  "speechToText": {
    "mainPrompt": "Sei un assistente AI che può elaborare messaggi vocali. Quando ricevi un messaggio con il marker [AUDIO_MESSAGE], dovresti utilizzare lo SpeechToTextAgent per trascrivere il file audio e poi elaborare il testo trascritto come il messaggio dell'utente. Nota che quando è presente un messaggio audio, questo sostituisce completamente qualsiasi messaggio di testo dell'utente - dovresti elaborare solo l'audio trascritto. Il percorso del file audio è fornito tra i marker [AUDIO_MESSAGE] e [/AUDIO_MESSAGE]. Utilizza lo strumento transcribeAudio con l'ID sessione e il percorso del file audio per ottenere il testo trascritto."
  }
}
```

#### For German (fnol-prompts-de.json):
```json
{
  "speechToText": {
    "mainPrompt": "Du bist ein KI-Assistent, der Audionachrichten verarbeiten kann. Wenn du eine Nachricht mit der Markierung [AUDIO_MESSAGE] erhältst, solltest du den SpeechToTextAgent verwenden, um die Audiodatei zu transkribieren und dann den transkribierten Text als die Nachricht des Benutzers verarbeiten. Beachte, dass wenn eine Audionachricht vorhanden ist, diese jede Textnachricht des Benutzers vollständig ersetzt - du solltest nur das transkribierte Audio verarbeiten. Der Pfad zur Audiodatei wird zwischen den Markierungen [AUDIO_MESSAGE] und [/AUDIO_MESSAGE] angegeben. Verwende das Tool transcribeAudio mit der Sitzungs-ID und dem Pfad zur Audiodatei, um den transkribierten Text zu erhalten."
  }
}
```

## Example JSON Structure

After integration, each prompt file should have a structure similar to this:

```json
{
  "superAgent": {
    "mainPrompt": "... existing content with audio handling additions ..."
  },
  "mediaOcr": {
    "mainPrompt": "... existing content ..."
  },
  "dateParser": {
    "mainPrompt": "... existing content ..."
  },
  "speechToText": {
    "mainPrompt": "... language-specific audio handling prompt ..."
  }
}
```

## Verification

After making these changes, verify that:
1. The JSON syntax is valid in each file
2. The application can load the prompt files without errors
3. The audio message handling functionality works as expected