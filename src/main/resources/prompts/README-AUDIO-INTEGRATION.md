# Audio Message Integration for Foundation-AI

This directory contains files to help you integrate audio message handling capabilities into the existing prompt files.

## Files Overview

1. **Template Files**:
   - `speechToText-en.json`: English template for audio message handling
   - `speechToText-it.json`: Italian template for audio message handling
   - `speechToText-de.json`: German template for audio message handling

2. **Integration Guide**:
   - `audio-message-integration-guide.md`: Detailed instructions for integrating audio message handling

## Integration Steps

### 1. Add the "speechToText" Section to Each Language File

For each language file (`fnol-prompts-en.json`, `fnol-prompts-it.json`, `fnol-prompts-de.json`), add the "speechToText" section from the corresponding template file:

1. Open the language-specific prompt file (e.g., `fnol-prompts-en.json`)
2. Open the corresponding template file (e.g., `speechToText-en.json`)
3. Copy the "speechToText" section from the template file
4. Paste it into the language-specific prompt file at the same level as "superAgent", "mediaOcr", and "dateParser"
5. Make sure to add a comma after the previous section if needed

Example:
```json
{
  "superAgent": {
    "mainPrompt": "..."
  },
  "mediaOcr": {
    "mainPrompt": "..."
  },
  "dateParser": {
    "mainPrompt": "..."
  },
  "speechToText": {
    "mainPrompt": "..."
  }
}
```

### 2. Update the "superAgent.mainPrompt" Section

For each language file, update the "superAgent.mainPrompt" section to include audio message handling instructions. Follow the detailed instructions in the `audio-message-integration-guide.md` file.

Key additions to make:

1. Add to the "IMPORTANT RULES" section:
   ```
   If you detect audio messages, use the SpeechToTextAgent to transcribe them.
   ```

2. Add "SpeechToTextAgent" to the "AVAILABLE TOOLS" section:
   ```
   - SpeechToTextAgent: Transcribe audio messages
   ```

3. Add a step for audio processing in the "CLAIM REPORTING PROCESS" section:
   ```
   If audio is provided, transcribe it using SpeechToTextAgent.
   ```

4. Add an "AUDIO HANDLING" section after the "MEDIA HANDLING" section:
   ```
   AUDIO HANDLING:
   When you detect [AUDIO_MESSAGE] markers in the user's message, extract the file path and use SpeechToTextAgent to transcribe it. The format will be:

   [AUDIO_MESSAGE]
   /path/to/audio_file
   [/AUDIO_MESSAGE]

   Use the transcribeAudio tool with the session ID and the audio file path to get the transcribed text, then process it as a normal text message.
   ```

## Verification

After making these changes, verify that:
1. The JSON syntax is valid in each file
2. The application can load the prompt files without errors
3. The audio message handling functionality works as expected

## Troubleshooting

If you encounter issues:
1. Check the JSON syntax for errors (missing commas, brackets, etc.)
2. Verify that the "speechToText" section is at the same level as other top-level sections
3. Make sure the "superAgent.mainPrompt" updates are properly integrated into the existing content
4. Restart the application to ensure it loads the updated prompt files