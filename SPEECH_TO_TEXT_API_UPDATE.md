# Speech-to-Text API Update

## Overview

This document describes the changes made to the `SpeechToTextService` class to update it to use the newer version of the Azure Speech-to-Text API as specified in the issue description.

## Changes Made

### 1. Updated API Endpoint and Request Format

The `convertAudioToText` method has been updated to use the new API endpoint and request format:

- **Old API Endpoint**: `https://{region}.api.cognitive.microsoft.com/language/speech/transcription`
- **New API Endpoint**: `https://{resource-name}-eastus2.cognitiveservices.azure.com/speechtotext/transcriptions:transcribe?api-version=2024-11-15`

The request format has been changed from a JSON payload with base64-encoded audio data to a multipart/form-data request with separate parts for the audio file and definition parameters.

### 2. Improved Response Parsing

The `extractTranscribedText` method has been enhanced to:

- Handle both old and new API response formats
- Support both "DisplayText" and "displayText" field names in the new API response
- Include detailed logging for better debugging
- Implement more robust error handling

### 3. Added Logging

Logging has been added throughout the service to help with debugging and monitoring:

- Request and response logging
- Error logging with detailed information
- Success logging with extracted text

### 4. Updated Tests

The test class `SpeechToTextServiceTest` has been updated to:

- Test the old API response format
- Test the new API response format with "DisplayText" field
- Test the new API response format with "displayText" field (lowercase variant)
- Maintain existing error handling tests

## Testing Instructions

To verify that the changes work correctly, run the tests using:

```bash
./mvnw test -Dtest=SpeechToTextServiceTest
```

All tests should pass, indicating that:

1. The service can handle the old API response format (backward compatibility)
2. The service can handle the new API response format with both "DisplayText" and "displayText" field names
3. The service properly handles error responses and malformed responses

## Manual Testing

To manually test the service with a real audio file:

1. Ensure you have a valid Azure Speech-to-Text API key and resource name configured in `application.properties`
2. Create a test audio file (WAV format recommended)
3. Use the following code to test the service:

```java
SpeechToTextService service = new SpeechToTextService();
// Inject dependencies or set them manually for testing
String transcribedText = service.convertAudioToText("/path/to/audio/file.wav");
System.out.println("Transcribed text: " + transcribedText);
```

## Potential Issues and Considerations

1. **Region Configuration**: The new API endpoint uses a different region format. Make sure the `azure.speech.resource-name` property is correctly set in `application.properties`.

2. **API Key**: The new API uses the `Ocp-Apim-Subscription-Key` header instead of an API key query parameter. Ensure the `azure.speech.api-key` property is correctly set.

3. **Response Format**: The actual response format from the new API might differ slightly from our assumptions. Monitor the logs for any parsing errors and adjust the `extractTranscribedText` method if needed.

4. **Audio Format**: The new API might have different requirements for audio formats. WAV format is recommended for best compatibility.

## Future Improvements

1. Use a proper JSON parsing library (like Jackson) instead of string manipulation for more robust response parsing.

2. Add more configuration options for the API, such as language selection, profanity filtering, etc.

3. Implement retry logic for transient errors.

4. Add more comprehensive logging and monitoring.