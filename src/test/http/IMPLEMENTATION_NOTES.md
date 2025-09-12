# Implementation Notes: Vocal Message Feature

## Overview

This document describes the implementation of the vocal message feature in the Foundation-AI application and the creation of an HTTP file to test it.

## Implementation Process

1. **Understanding the API Endpoint**
   - Examined the `FnolResource.java` file to understand the endpoint structure
   - Identified that the endpoint accepts multipart form data with parameters:
     - `userMessage` (required)
     - `sessionId` (optional)
     - `files` (optional)
     - `userAudioMessage` (optional)

2. **Creating the HTTP File**
   - Created `fnol-chat-audio.http` in the `src/test/http` directory
   - Set up a multipart form request to the `/fnol/chat` endpoint
   - Included form parts for `userMessage` and `userAudioMessage`
   - Added comments explaining how to use the file

3. **Preparing for Audio File**
   - Created the `src/test/resources` directory to store the audio file
   - Provided instructions in the README.md for creating or obtaining an audio file with the message "the policy number is AUTHHR00026397"

4. **Documentation**
   - Created a README.md with detailed instructions for testing the vocal message feature
   - Included troubleshooting tips and expected response information

## Technical Details

The vocal message feature works as follows:

1. The client sends a multipart form request with an audio file to the `/fnol/chat` endpoint
2. The `FnolResource` class processes the request:
   - Saves the audio file to a temporary directory
   - Adds a special marker in the user message: `[AUDIO_MESSAGE] /path/to/file [/AUDIO_MESSAGE]`
3. The `FNOLAssistantAgent` processes the message:
   - Detects the audio message marker
   - Uses the `SpeechToTextAgent` to transcribe the audio file
   - Processes the transcribed text as part of the user's message
4. The response includes:
   - The session ID
   - The AI assistant's answer
   - Any final results (if available)

## Issues and Considerations

1. **Audio File Format**
   - The application expects audio files in WAV format
   - Other formats may not be properly transcribed

2. **Temporary Files**
   - Audio files are saved to temporary directories
   - These files may accumulate over time and should be cleaned up periodically

3. **Error Handling**
   - The application handles errors during audio file processing and returns appropriate error responses
   - Failed transcriptions return a "Failed to transcribe audio" message

4. **Testing Limitations**
   - The HTTP file requires a pre-existing audio file
   - IntelliJ's HTTP Client cannot create audio files dynamically

## Future Improvements

1. **Support for More Audio Formats**
   - Add support for MP3, M4A, and other common audio formats

2. **Streaming Audio**
   - Implement streaming audio transcription for real-time processing

3. **Cleanup Mechanism**
   - Add a scheduled task to clean up temporary audio files

4. **Enhanced Error Handling**
   - Provide more detailed error messages for different types of audio processing failures