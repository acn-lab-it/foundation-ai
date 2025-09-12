# Testing the Vocal Message Feature

This directory contains HTTP files that can be used in IntelliJ IDEA to test the API endpoints of the Foundation-AI application.

## Prerequisites

1. IntelliJ IDEA with the HTTP Client plugin installed
2. The Foundation-AI application running locally
3. An audio file containing the message "the policy number is AUTHHR00026397"

## Creating the Audio File

To create an audio file with the message "the policy number is AUTHHR00026397", you can use one of the following methods:

### Method 1: Use a Text-to-Speech Service

1. Go to a text-to-speech service like [Google Text-to-Speech](https://cloud.google.com/text-to-speech) or [Amazon Polly](https://aws.amazon.com/polly/)
2. Enter the text "the policy number is AUTHHR00026397"
3. Generate the audio and download it as a WAV file
4. Save the file as `policy_number_audio.wav` in the `src/test/resources` directory

### Method 2: Record Your Own Voice

1. Use a voice recording app on your computer or phone
2. Record yourself saying "the policy number is AUTHHR00026397"
3. Save the recording as a WAV file
4. Save the file as `policy_number_audio.wav` in the `src/test/resources` directory

## Running the HTTP Request

1. Make sure the Foundation-AI application is running locally
   ```
   cd /Users/francesco.stumpo/IdeaProjects/Allianz/bmp/foundation-ai
   ./mvnw quarkus:dev
   ```

2. Open the `fnol-chat-audio.http` file in IntelliJ IDEA

3. Click the green "Run" button next to the request

4. Check the response to see if the policy number was correctly extracted from the audio message

## Expected Response

The response should include:
- A sessionId
- An answer from the AI assistant acknowledging the policy number
- Possibly additional information about the policy if it exists in the system

## Troubleshooting

If you encounter issues:

1. Make sure the application is running on port 8080
2. Verify that the audio file is in the correct location and format
3. Check the application logs for any errors related to audio processing