# Calling the FNOL API with Audio Messages

## Summary of Implementation

I've prepared everything needed to call the real API with the audio file:

1. **Verified the Audio File**: Confirmed that `policy_ai_converted.wav` exists in the `src/test/resources` folder.

2. **Updated the HTTP File**: Modified `fnol-chat-audio.http` to use the correct audio file name and path.

3. **Added Detailed Instructions**: Enhanced the HTTP file with step-by-step instructions on:
   - How to start the application
   - How to execute the HTTP request
   - What to expect in the response
   - Troubleshooting tips

## Next Steps

To call the real API with the audio file:

1. **Start the Application**:
   ```bash
   cd /Users/francesco.stumpo/IdeaProjects/Allianz/bmp/foundation-ai
   ./mvnw quarkus:dev
   ```

2. **Wait for Full Startup**: Look for a message like "Listening on: http://localhost:8080" in the console output.

3. **Execute the HTTP Request**: Open `src/test/http/fnol-chat-audio.http` in IntelliJ and click the green "Run" button next to the request.

4. **Review the Response**: The response should contain:
   - A session ID
   - The AI assistant's response acknowledging the policy number
   - Possibly additional information about the policy if it exists in the system

## Technical Details

The HTTP request is configured to:
- Send a multipart form to the `/fnol/chat` endpoint
- Include a text message: "Hello, I need to check my policy"
- Include the audio file `policy_ai_converted.wav` from the `src/test/resources` folder
- Set the Accept-Language header to "en" for English responses

The application processes the audio file using:
1. The `FnolResource` class to handle the HTTP request
2. The `SpeechToTextAgent` to transcribe the audio
3. The `FNOLAssistantAgent` to process the transcribed text

## Troubleshooting

If you encounter issues:
- Make sure the application is running on port 8080
- Check the application logs for any errors
- Verify that the audio file exists at the correct path
- Ensure the audio file is in a format supported by the Azure Speech-to-Text API (WAV is recommended)