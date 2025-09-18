package com.accenture.claims.ai.adapter.inbound.rest.chatv2;

import com.accenture.claims.ai.adapter.inbound.rest.GuardrailsContext;
import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.chatv2.tools.*;
import com.accenture.claims.ai.adapter.inbound.rest.dto.ChatForm;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.LanguageHelper;
import com.accenture.claims.ai.adapter.inbound.rest.helpers.SessionLanguageContext;
import com.accenture.claims.ai.application.tool.WelcomeTool;
import com.accenture.claims.ai.domain.model.*;
import com.accenture.claims.ai.domain.model.emailParsing.Contacts;
import com.accenture.claims.ai.domain.model.emailParsing.Reporter;
import com.accenture.claims.ai.domain.repository.PolicyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@jakarta.ws.rs.Path("/api/fnol/chat")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public class ChatV2Resource {

    @Inject
    SessionLanguageContext sessionLanguageContext;
    @Inject
    LanguageHelper languageHelper;
    @Inject
    GuardrailsContext guardrailsContext;
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;
    @Inject
    WelcomeTool welcomeTool;
    @Inject
    ChatV2DamageDetailsV2 chatV2DamageDetailsV2;
    // ChatV2 specific tools
    @Inject
    ChatV2WelcomeToolV2 chatV2WelcomeTool;
    @Inject
    ChatV2AdministrativeCheckToolV2 chatV2AdministrativeCheckTool;
    @Inject
    ChatV2WhatHappenedToolV2 chatV2WhatHappenedTool;
    @Inject
    ChatV2DateParserToolV2 chatV2DateParserTool;
    @Inject
    ChatV2MediaOcrAgentV2 chatV2MediaOcrAgent;
    @Inject
    ChatV2AddressToolV2 chatV2AddressTool;
    
    // New advanced tools
    @Inject
    ChatV2StructuredDataExtractorV2 chatV2StructuredDataExtractor;
    @Inject
    ChatV2MissingInfoHandlerV2 chatV2MissingInfoHandler;
    @Inject
    ChatV2CoverageVerifierV2 chatV2CoverageVerifier;
    @Inject
    ChatV2MediaHandlerV2 chatV2MediaHandler;
    @Inject
    PolicyRepository policyRepository;

    private static final ObjectMapper M = new ObjectMapper();

    public static class ChatResponseDto {
        public String sessionId;
        public String answer;
        public Object finalResult; // null se non è stato creato
        public String currentStep; // "welcome", "step1", "step2", "completed"
        public StepperData stepperData; // dati del stepper
        public String warning; // warning per il frontend (es. copertura danno)
        public boolean hideChat; // true se il frontend deve nascondere la chat e mostrare solo la scheda dettagli

        public ChatResponseDto(String sessionId, String answer, Object finalResult, String currentStep, StepperData stepperData) {
            this.sessionId = sessionId;
            this.answer = answer;
            this.finalResult = finalResult;
            this.currentStep = currentStep;
            this.stepperData = stepperData;
            this.hideChat = "completed".equals(currentStep);
        }

        public ChatResponseDto(String sessionId, String answer, Object finalResult, String currentStep, StepperData stepperData, String warning) {
            this.sessionId = sessionId;
            this.answer = answer;
            this.finalResult = finalResult;
            this.currentStep = currentStep;
            this.stepperData = stepperData;
            this.warning = warning;
            this.hideChat = "completed".equals(currentStep);
        }
    }

    public static class StepperData {
        public List<StepItem> stepList;
        public int currentState;

        public StepperData(List<StepItem> stepList, int currentState) {
            this.stepList = stepList;
            this.currentState = currentState;
        }
    }

    public static class StepItem {
        public String label;
        public boolean completed;

        public StepItem(String label, boolean completed) {
            this.label = label;
            this.completed = completed;
        }
    }

    @Data
    @AllArgsConstructor
    public static class UploadPolicyResponse {
        private Reporter reporter;
        private String policyNumber;
        private String createdAt;
        private String uploadedAt;
    }

    @Data
    public static class UploadPolicyPolicyholder {
        private UploadPolicyPersonalData personalData;
        private boolean marketingAgreement;
        private boolean isPolicyholderPayer;
    }

    @Data
    public static class UploadPolicyPersonalData {
        private String type;
        private UploadPolicyNaturalPerson naturalPerson;
        private UploadPolicyCompany company;
        private UploadPolicyContact contact;
        private UploadPolicyAddress address;
    }

    @Data
    public static class UploadPolicyNaturalPerson {
        private String firstName;
        private String lastName;
        private String idType;
        private String idNumber;
        private String birthdate;
        private String gender;
    }

    @Data
    public static class UploadPolicyCompany {
        // Campi vuoti, puoi aggiungere se servono
    }

    @Data
    public static class UploadPolicyContact {
        private String phone;
        private String email;
    }

    @Data
    public static class UploadPolicyAddress {
        private String streetName;
        private String streetNumber;
        private String zipCode;
        private String city;
        private String countryCode;
    }

    @Data
    public static class UploadPolicyHouseholdRelatedAttributes {
        private String role;
        private UploadPolicyQuoteConfiguration quoteConfiguration;
        private UploadPolicyProperty property;
    }

    @Data
    public static class UploadPolicyQuoteConfiguration {
        private UploadPolicySelection selection;
    }

    @Data
    public static class UploadPolicySelection {
        // Nessun campo definito
    }

    @Data
    public static class UploadPolicyProperty {
        private UploadPolicyPropertyAddress propertyAddress;
        private boolean shortTermRental;
    }

    @Data
    public static class UploadPolicyPropertyAddress {
        private String streetName;
        private String streetNumber;
        private String zipCode;
        private String city;
    }

    @Data
    public static class UploadPolicyTransaction {
        private UploadPolicyDetails policyDetails;
        private String policyStatus;
    }

    @Data
    public static class UploadPolicyDetails {
        private UploadPolicyHouseholdRelatedAttributes householdRelatedAttributes;
        private List<UploadPolicyPolicyholder> policyholders;
    }

    @Data
    public static class UploadPolicyRequest {
        @NotBlank
        String policyId;
        @NotBlank
        String creationTimestamp;
        @NotBlank
        String updateTimestamp;
        @NotBlank
        String productLineId;
        @NotNull
        UploadPolicyTransaction transaction;

    }


    @POST
    @jakarta.ws.rs.Path("/upload-document")
    @Consumes(MediaType.APPLICATION_JSON)
    @jakarta.enterprise.context.control.ActivateRequestContext
    public Response uploadPolicy(
            UploadPolicyRequest request,
            @HeaderParam("Accept-Language") String acceptLanguage) throws Exception {

        String policyId = request.getPolicyId();
        String creationTimestamp = request.getCreationTimestamp();
        String updateTimestamp = request.getUpdateTimestamp();
        String productLineId = request.getProductLineId();
        UploadPolicyTransaction transaction = request.getTransaction();
        Reporter reporter = new Reporter();
        reporter.setFirstName(transaction.policyDetails.policyholders.get(0).personalData.naturalPerson.firstName);
        reporter.setLastName(transaction.policyDetails.policyholders.get(0).personalData.naturalPerson.lastName);
        Contacts contacts = new Contacts();
        contacts.setMobile(transaction.policyDetails.policyholders.get(0).personalData.contact.phone);
        contacts.setEmail(transaction.policyDetails.policyholders.get(0).personalData.contact.email);
        reporter.setContacts(contacts);
        UploadPolicyResponse response = new UploadPolicyResponse(reporter, policyId, creationTimestamp, updateTimestamp);
        Policy policy = new Policy();
        // Status dalla transaction
        policy.setPolicyStatus(transaction.getPolicyStatus());
        // Fallback: usa policyId come policyNumber se non lo ricevi separatamente
        policy.setPolicyNumber(policyId);

        // ProductReference: se vuoi popolarlo, puoi mappare productLineId/prodotto/vers.
        // In assenza dei campi completi nel metodo, lo lasciamo null o compila con placeholder
        ProductReference pr = new ProductReference();
        pr.setVersion("0.3.0");
        pr.setName("Allianz BMP Generated Product");
        pr.setGroupNameApl("italia 1"); //TODO
        pr.setGroupName(productLineId.equals("MOTOR") ? "MOTOR" : "MULTIRISK");
        policy.setProductReference(pr);

        // InsuredProperty: mappa l’indirizzo dell’immobile (se presente nel payload)
        UploadPolicyPropertyAddress pa =
                Optional.ofNullable(transaction.getPolicyDetails())
                        .map(UploadPolicyDetails::getHouseholdRelatedAttributes)
                        .map(UploadPolicyHouseholdRelatedAttributes::getProperty)
                        .map(UploadPolicyProperty::getPropertyAddress)
                        .orElse(null);

        if (pa != null) {
            InsuredProperty insured = new InsuredProperty();
            Address addr = new Address();
            addr.setFullAddress(buildFullAddress(
                    pa.getStreetName(),
                    pa.getStreetNumber(),
                    pa.getZipCode(),
                    pa.getCity(),
                    null
            ));
            addr.setCity(pa.getCity());
            addr.setPostalCode(pa.getZipCode());

            addr.setCity(pa.getCity());
            // Se hai countryCode per la property lo puoi aggiungere; nel JSON dell’immobile non c’è
            insured.setAddress(addr);
            policy.setInsuredProperty(insured);
        }

        // PolicyHolders: mappa il primo (o tutti)
        List<PolicyHolder> holders = new ArrayList<>();
        if (transaction.getPolicyDetails() != null
                && transaction.getPolicyDetails().getPolicyholders() != null) {
            for (UploadPolicyPolicyholder uph : transaction.getPolicyDetails().getPolicyholders()) {
                PolicyHolder h = new PolicyHolder();
                if (uph.getPersonalData() != null) {
                    UploadPolicyPersonalData pd = uph.getPersonalData();
                    if (pd.getNaturalPerson() != null) {
                        UploadPolicyNaturalPerson np = pd.getNaturalPerson();
                        h.setFirstName(np.getFirstName());
                        h.setLastName(np.getLastName());
                        // birthdate: "yyyy-MM-dd"
                        h.setDateOfBirth(parseDateOrNull(np.getBirthdate()));
                        h.setGender(np.getGender());
                    }
                    if (pd.getAddress() != null) {
                        UploadPolicyAddress a = pd.getAddress();
                        Address ha = new Address();
                        ha.setFullAddress(buildFullAddress(
                                a.getStreetName(),
                                a.getStreetNumber(),
                                a.getZipCode(),
                                a.getCity(),
                                a.getCountryCode()
                        ));
                        ha.setCity(a.getCity());
                        ha.setPostalCode(a.getZipCode());
                        ha.setCountryCode(a.getCountryCode());
                        h.setAddress(ha);
                    }

                    if (pd.getContact() != null) {
                        UploadPolicyContact c = pd.getContact();
                        List<ContactChannel> channels = new ArrayList<>();
                        if (c.getEmail() != null && !c.getEmail().isBlank()) {
                            ContactChannel email = new ContactChannel();
                            email.setCommunicationType("EMAIL");
                            email.setCommunicationDetails(c.getEmail());
                            channels.add(email);
                        }
                        if (c.getPhone() != null && !c.getPhone().isBlank()) {
                            ContactChannel phone = new ContactChannel();
                            phone.setCommunicationType("MOBILE");
                            phone.setCommunicationDetails(c.getPhone());
                            channels.add(phone);
                        }
                        h.setContactChannels(channels);
                    }
                }
                h.setPolicyHolderPayer(uph.isPolicyholderPayer());
                // Se ti serve mappare marketingAgreement/roles/customerId, fallo qui
                holders.add(h);
            }
        }
        policy.setPolicyHolders(holders);

        policyRepository.put(policy);

        return Response.ok(response).build();
    }

    private static String buildFullAddress(String streetName, String streetNumber, String zip, String city, String countryCode) {
        StringBuilder sb = new StringBuilder();
        if (streetName != null && !streetName.isBlank()) {
            sb.append(streetName.trim());
        }
        if (streetNumber != null && !streetNumber.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(streetNumber.trim());
        }
        if (zip != null && !zip.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(zip.trim());
        }
        if (city != null && !city.isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(city.trim());
        }
        if (countryCode != null && !countryCode.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(countryCode.trim());
        }
        return sb.toString();
    }


    // Helpers locali per parsing date
    private static java.util.Date parseDateOrNull(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.isBlank()) return null;
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(yyyyMMdd);
            return java.util.Date.from(d.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant());
        } catch (Exception e) {
            return null;
        }
    }

    private static java.util.Date parseDateTimeOrNull(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return java.util.Date.from(java.time.Instant.parse(iso));
        } catch (Exception e) {
            // fallback OffsetDateTime (es: ±hh:mm)
            try {
                return java.util.Date.from(java.time.OffsetDateTime.parse(iso).toInstant());
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    @POST
    @jakarta.ws.rs.Path("/welcome")
    @jakarta.enterprise.context.control.ActivateRequestContext
    public Response welcome(@BeanParam ChatForm form, @HeaderParam("Accept-Language") String acceptLanguage) throws Exception {

        if (form == null || form.policyNumber == null || form.emailAddress == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"policyNumber e emailAddress obbligatori\"}").build();
        }

        String sessionId = UUID.randomUUID().toString();
        
        try {
            // Usa la logica identica a WelcomeTool ma con il nostro tool duplicato
            ChatResponseDto response = chatV2WelcomeTool.welcomeMsg(
                form.policyNumber, 
                form.emailAddress, 
                acceptLanguage, 
                sessionId
            );
            
            // Crea stepperData per il welcome
            StepperData stepperData = createStepperData("welcome");
            ChatResponseDto finalResponse = new ChatResponseDto(
                response.sessionId, 
                response.answer, 
                response.finalResult, 
                response.currentStep, 
                stepperData
            );
            
            return Response.ok(finalResponse).build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @POST
    @jakarta.ws.rs.Path("/step")
    @jakarta.enterprise.context.control.ActivateRequestContext
    public Response step(@BeanParam ChatForm form, @HeaderParam("Accept-Language") String acceptLanguage) throws Exception {

        if (form == null || form.sessionId == null || form.userMessage == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"sessionId e userMessage obbligatori\"}").build();
        }

        String sessionId = form.sessionId;
        String userMessage = form.userMessage;

        // Gestione eventuali file
        if (form.files != null && !form.files.isEmpty()) {
            try {
                Path tmpDir = Files.createTempDirectory("chatv2-media-");
                List<String> paths = new ArrayList<>();
                for (FileUpload fu : form.files) {
                    Path dst = tmpDir.resolve(fu.fileName());
                    Files.copy(fu.uploadedFile(), dst);
                    paths.add(dst.toString());
                }
                userMessage += "\n\n[MEDIA_FILES]\n" +
                        String.join("\n", paths) +
                        "\n[/MEDIA_FILES]";
            } catch (IOException e) {
                return Response.serverError()
                        .entity("{\"error\":\"upload_failure\"}")
                        .build();
            }
        }

        // Gestione messaggio vocale
        if (form.userAudioMessage != null) {
            try {
                Path tmpDir = Files.createTempDirectory("chatv2-audio-");
                Path dst = tmpDir.resolve(form.userAudioMessage.fileName());
                Files.copy(form.userAudioMessage.uploadedFile(), dst);

                userMessage += "[AUDIO_MESSAGE]\n" + dst + "\n[/AUDIO_MESSAGE]";
            } catch (IOException e) {
                return Response.serverError()
                        .entity("{\"error\":\"audio_upload_failure\"}")
                        .build();
            }
        }

        // Recupero la lingua e il main prompt
        LanguageHelper.PromptResult promptResult =
                languageHelper.getPromptWithLanguage(acceptLanguage, "superAgent.mainPrompt");

        // Inietto la sessionId corrente nel prompt
        String systemPrompt = languageHelper.applyVariables(promptResult.prompt, Map.of("sessionId", sessionId));

        // Imposto la lingua di sessione
        sessionLanguageContext.setLanguage(sessionId, promptResult.language);

        // Imposto il contesto per i guardrails
        guardrailsContext.setSessionId(sessionId);
        guardrailsContext.setSystemPrompt(systemPrompt);

        // Determina lo step corrente e processa il messaggio
        String currentStep = determineCurrentStep(sessionId);
        ChatResponseDto response = processStep(sessionId, currentStep, userMessage, acceptLanguage);

        return Response.ok(response).build();
    }

    private String determineCurrentStep(String sessionId) {
        ObjectNode fo = finalOutputJSONStore.get("final_output", sessionId);
        if (fo == null) {
            return "welcome";
        }

        // Verifica step 1 usando i dati salvati
        List<String> missingStep1Fields = checkMissingStep1FieldsFromStored(sessionId);
        if (!missingStep1Fields.isEmpty()) {
            return "step1";
        }

        // Verifica step 2 usando i dati salvati
        List<String> missingStep2Fields = checkMissingStep2FieldsFromStored(sessionId);
        if (!missingStep2Fields.isEmpty()) {
            return "step2";
        }

        // Step 3 (documenti) non ha controlli - passa direttamente al completamento
        return "completed";
    }

    private ChatResponseDto processStep(String sessionId, String currentStep, String userMessage, String acceptLanguage) {
        switch (currentStep) {
            case "step1":
                return processStep1(sessionId, userMessage, acceptLanguage);
            case "step2":
                return processStep2(sessionId, userMessage, acceptLanguage);
            case "completed":
                return processCompleted(sessionId, userMessage, acceptLanguage);
            default:
                StepperData stepperData = createStepperData("error");
                return new ChatResponseDto(sessionId, "Errore: step non riconosciuto", null, "error", stepperData);
        }
    }

    private ChatResponseDto processStep1(String sessionId, String userMessage, String acceptLanguage) {
        // STEP 1: Verificare "dove" e "quando"
        try {
            // Estrai structured data dal messaggio
            Map<String, Object> extractedData = extractStep1Data(sessionId, userMessage, acceptLanguage);
            
            // Salva SEMPRE i dati estratti (anche parziali)
            saveStep1Data(sessionId, extractedData);
            
            // Verifica se abbiamo tutte le info necessarie (dai dati salvati, non solo estratti)
            List<String> missingFields = checkMissingStep1FieldsFromStored(sessionId);
            
            if (!missingFields.isEmpty()) {
                // Chiedi info mancanti con il nuovo handler
                String missingInfoMessage = chatV2MissingInfoHandler.generateMissingInfoRequestV2(
                    sessionId, "step1", getStoredStep1Data(sessionId), acceptLanguage);
                StepperData stepperData = createStepperData("step1");
                return new ChatResponseDto(sessionId, missingInfoMessage, null, "step1", stepperData);
            }
            
            // Verifica amministrativa completa
            String policyNumber = getPolicyNumber(sessionId);
            if (policyNumber == null) {
                StepperData stepperData = createStepperData("step1");
                return new ChatResponseDto(sessionId, 
                    "Errore: numero di polizza non trovato.", 
                    null, "step1_error", stepperData);
            }
            
            // Verifica amministrativa: controlla solo se la data è nel periodo di copertura
            boolean adminCheckPassed = chatV2AdministrativeCheckTool.checkPolicyV2(sessionId, policyNumber);
            
            if (!adminCheckPassed) {
                StepperData stepperData = createStepperData("step1");
                return new ChatResponseDto(sessionId, 
                    "Data incidente non coperta dalla polizza. La verifica amministrativa non va bene.", 
                    null, "step1_error", stepperData);
            }

            // Passa al step 2
            String nextStepMessage = generateStep2WelcomeMessage(acceptLanguage);
            StepperData stepperData = createStepperData("step2");
            return new ChatResponseDto(sessionId, nextStepMessage, null, "step2", stepperData);

        } catch (Exception e) {
            StepperData stepperData = createStepperData("step1");
            return new ChatResponseDto(sessionId, 
                "Errore durante l'elaborazione: " + e.getMessage(), 
                null, "step1_error", stepperData);
        }
    }

    private ChatResponseDto processStep2(String sessionId, String userMessage, String acceptLanguage) {
        // STEP 2: Verificare "cosa"
        try {
            // Estrai structured data per categorizzazione
            Map<String, Object> extractedData = extractStep2Data(sessionId, userMessage, acceptLanguage);
            
            // Salva SEMPRE i dati estratti (anche parziali)
            saveStep2Data(sessionId, extractedData);

            chatV2DamageDetailsV2.runOcr(sessionId, userMessage);

            // Processa eventuali media con ChatV2MediaOcrAgentV2 PRIMA della validazione
            System.out.println("DEBUG: Checking for media files in extractedData: " + extractedData.containsKey("mediaFiles"));
            if (extractedData.containsKey("mediaFiles")) {
                @SuppressWarnings("unchecked")
                List<String> mediaFiles = (List<String>) extractedData.get("mediaFiles");
                System.out.println("DEBUG: Found media files, processing with OCR: " + mediaFiles);
                processMediaFilesWithOCR(sessionId, mediaFiles, userMessage);
            } else {
                System.out.println("DEBUG: No media files found in extractedData");
            }
            
            // Verifica se abbiamo tutte le info necessarie (dai dati salvati, non solo estratti)
            List<String> missingFields = checkMissingStep2FieldsFromStored(sessionId);
            
            if (!missingFields.isEmpty()) {
                // Chiedi info mancanti con il nuovo handler
                String missingInfoMessage = chatV2MissingInfoHandler.generateMissingInfoRequestV2(
                    sessionId, "step2", getStoredStep2Data(sessionId), acceptLanguage);
                StepperData stepperData = createStepperData("step2");
                return new ChatResponseDto(sessionId, missingInfoMessage, null, "step2", stepperData);
            }

            // Verifica copertura del tipo di danno - ora solo warning, non bloccante
            String coverageWarning = null;
            String policyNumber = getPolicyNumber(sessionId);
            if (policyNumber != null) {
                ObjectNode finalOutput = finalOutputJSONStore.get("final_output", sessionId);
                String incidentDate = finalOutput.path("incidentDate").asText();
                String damageType = finalOutput.path("whatHappenedCode").asText("UNKNOWN");
                
                Map<String, Object> coverageResult = chatV2CoverageVerifier.verifyPolicyCoverageV2(
                    sessionId, policyNumber, incidentDate, damageType);
                
                boolean coveragePassed = (Boolean) coverageResult.getOrDefault("covered", false);
                
                if (!coveragePassed) {
                    coverageWarning = (String) coverageResult.getOrDefault("reason", "Tipo di danno non coperto dalla polizza.");
                }
            }

            // Processo completato - costruisci output finale
            ObjectNode finalResult = chatV2MediaHandler.buildFinalOutputV2(sessionId);
            String completionMessage = chatV2MissingInfoHandler.generateCompletionMessageV2(sessionId, acceptLanguage, coverageWarning);
            StepperData stepperData = createStepperData("completed");
            
            if (coverageWarning != null) {
                // Genera warning con LLM
                String warningMessage = chatV2MissingInfoHandler.generateWarningMessageV2(sessionId, acceptLanguage, coverageWarning);
                return new ChatResponseDto(sessionId, completionMessage, finalResult, "completed", stepperData, warningMessage);
            } else {
                return new ChatResponseDto(sessionId, completionMessage, finalResult, "completed", stepperData);
            }

        } catch (Exception e) {
            StepperData stepperData = createStepperData("step2");
            return new ChatResponseDto(sessionId, 
                "Errore durante l'elaborazione: " + e.getMessage(), 
                null, "step2_error", stepperData);
        }
    }

    private ChatResponseDto processCompleted(String sessionId, String userMessage, String acceptLanguage) {
        try {
            ObjectNode finalResult = finalOutputJSONStore.get("final_output", sessionId);
            if (finalResult == null) {
                StepperData stepperData = createStepperData("error");
                return new ChatResponseDto(sessionId, 
                    "Errore: dati non trovati per la sessione.", 
                    null, "error", stepperData);
            }
            
            // Controlla se ci sono media aggiuntivi da processare
            List<String> mediaFiles = extractMediaFiles(userMessage);
            if (!mediaFiles.isEmpty()) {
                System.out.println("DEBUG: Processing additional media files in completed state: " + mediaFiles);
                processMediaFilesWithOCR(sessionId, mediaFiles, acceptLanguage);
                
                // Ricostruisci il risultato finale con i nuovi media
                finalResult = chatV2MediaHandler.buildFinalOutputV2(sessionId);
            }
            
            // Rimuovi _internals prima di restituire il risultato
            ObjectNode publicResult = finalResult.deepCopy();
            publicResult.remove("_internals");
            
            StepperData stepperData = createStepperData("completed");
            String message = mediaFiles.isEmpty() ? 
                chatV2MissingInfoHandler.generateCompletionMessageV2(sessionId, acceptLanguage, null) :
                chatV2MissingInfoHandler.generateCompletionMessageV2(sessionId, acceptLanguage, null);
            
            return new ChatResponseDto(sessionId, message, publicResult, "completed", stepperData);
        } catch (Exception e) {
            StepperData stepperData = createStepperData("error");
            return new ChatResponseDto(sessionId, 
                "Errore durante il recupero dei dati: " + e.getMessage(), 
                null, "error", stepperData);
        }
    }

    // Metodi di supporto per l'estrazione e validazione dei dati
    private Map<String, Object> extractStep1Data(String sessionId, String userMessage, String acceptLanguage) {
        // Usa il nuovo structured data extractor
        return chatV2StructuredDataExtractor.extractStep1DataV2(sessionId, userMessage);
    }

    private Map<String, Object> extractStep2Data(String sessionId, String userMessage, String acceptLanguage) {
        // Usa il nuovo structured data extractor
        Map<String, Object> data = chatV2StructuredDataExtractor.extractStep2DataV2(sessionId, userMessage);
        
        // Aggiungi categorizzazione whatHappened
        try {
            String whatHappenedResult = chatV2WhatHappenedTool.classifyAndSaveV2(sessionId, userMessage, getCurrentLocation(sessionId));
            data.put("whatHappenedResult", whatHappenedResult);
        } catch (Exception e) {
            data.put("whatHappenedResult", "{\"whatHappenedCode\":\"UNKNOWN\",\"whatHappenedContext\":\"UNKNOWN\",\"claimClassGroup\":\"UNKNOWN\",\"confidence\":0.0}");
        }
        
        // Estrai media files se presenti
        List<String> mediaFiles = extractMediaFiles(userMessage);
        System.out.println("DEBUG: extractStep2Data - mediaFiles found: " + mediaFiles);
        if (!mediaFiles.isEmpty()) {
            data.put("mediaFiles", mediaFiles);
            System.out.println("DEBUG: extractStep2Data - added mediaFiles to data");
        }
        
        System.out.println("DEBUG: extractStep2Data - final data keys: " + data.keySet());
        return data;
    }

    private List<String> checkMissingStep1Fields(Map<String, Object> data) {
        Map<String, Object> validation = chatV2MissingInfoHandler.validateStepDataV2("", "step1", data);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) validation.get("missingFields");
        return missing;
    }

    private List<String> checkMissingStep1FieldsFromStored(String sessionId) {
        Map<String, Object> storedData = getStoredStep1Data(sessionId);
        return checkMissingStep1Fields(storedData);
    }

    private Map<String, Object> getStoredStep1Data(String sessionId) {
        System.out.println("=== DEBUG getStoredStep1Data START ===");
        System.out.println("SessionId: " + sessionId);
        
        ObjectNode finalOutput = finalOutputJSONStore.get("final_output", sessionId);
        Map<String, Object> data = new HashMap<>();
        
        if (finalOutput != null) {
            System.out.println("DEBUG: finalOutput found");
            // Gestisci data e ora separatamente da _internals.date
            boolean hasDate = false;
            boolean hasTime = false;
            String date = null;
            String time = null;
            
            if (finalOutput.has("_internals") && !finalOutput.get("_internals").isNull()) {
                ObjectNode internals = (ObjectNode) finalOutput.get("_internals");
                if (internals.has("date") && !internals.get("date").isNull()) {
                    ObjectNode dateInfo = (ObjectNode) internals.get("date");
                    hasDate = dateInfo.has("hasDate") && dateInfo.get("hasDate").asBoolean();
                    hasTime = dateInfo.has("hasTime") && dateInfo.get("hasTime").asBoolean();
                    
                    if (dateInfo.has("date")) {
                        date = dateInfo.get("date").asText();
                    }
                    if (dateInfo.has("time")) {
                        time = dateInfo.get("time").asText();
                    }
                }
            }
            
            // Fallback: se non abbiamo _internals.date, usa incidentDate
            if (!hasDate && !hasTime && finalOutput.has("incidentDate") && !finalOutput.get("incidentDate").isNull()) {
                String incidentDate = finalOutput.get("incidentDate").asText();
                data.put("incidentDate", incidentDate);
                
                if (incidentDate.contains("T")) {
                    hasDate = true;
                    hasTime = true;
                } else if (incidentDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    hasDate = true;
                } else if (incidentDate.matches("\\d{2}:\\d{2}:\\d{2}")) {
                    hasTime = true;
                    time = incidentDate;
                }
            }
            
            data.put("hasDate", hasDate);
            data.put("hasTime", hasTime);
            if (date != null) {
                data.put("date", date);
            }
            if (time != null) {
                data.put("time", time);
                data.put("incidentTime", time);
            }
            
            // Costruisci incidentDate finale se abbiamo entrambi
            if (hasDate && hasTime && date != null && time != null) {
                data.put("incidentDate", date + "T" + time + "Z");
            } else if (hasDate && date != null) {
                data.put("incidentDate", date + "T00:00:00Z");
            } else if (hasTime && time != null) {
                // NON desumere la data per time-only input
                data.put("incidentDate", time);
                System.out.println("DEBUG: Time-only input, NOT inferring date: " + time);
            }
            
            // Gestisci indirizzo separatamente per via e città
            if (finalOutput.has("incidentLocation") && !finalOutput.get("incidentLocation").isNull()) {
                String incidentLocation = finalOutput.get("incidentLocation").asText();
                data.put("incidentLocation", incidentLocation);
                
                // Controlla se abbiamo già le parti separate salvate in _internals.addressInfo
                boolean hasStreet = false;
                boolean hasCity = false;
                boolean hasHouseNumber = false;
                boolean hasPostalCode = false;
                boolean hasState = false;
                String street = null;
                String city = null;
                String houseNumber = null;
                String postalCode = null;
                String state = null;
                
                if (finalOutput.has("_internals") && !finalOutput.get("_internals").isNull()) {
                    ObjectNode internals = (ObjectNode) finalOutput.get("_internals");
                    if (internals.has("addressInfo") && !internals.get("addressInfo").isNull()) {
                        ObjectNode addressInfo = (ObjectNode) internals.get("addressInfo");
                        hasStreet = addressInfo.has("hasStreet") && addressInfo.get("hasStreet").asBoolean();
                        hasCity = addressInfo.has("hasCity") && addressInfo.get("hasCity").asBoolean();
                        hasHouseNumber = addressInfo.has("hasHouseNumber") && addressInfo.get("hasHouseNumber").asBoolean();
                        hasPostalCode = addressInfo.has("hasPostalCode") && addressInfo.get("hasPostalCode").asBoolean();
                        hasState = addressInfo.has("hasState") && addressInfo.get("hasState").asBoolean();
                        
                        System.out.println("DEBUG: Address flags from _internals.addressInfo:");
                        System.out.println("  hasStreet: " + hasStreet);
                        System.out.println("  hasCity: " + hasCity);
                        System.out.println("  hasHouseNumber: " + hasHouseNumber);
                        System.out.println("  hasPostalCode: " + hasPostalCode);
                        System.out.println("  hasState: " + hasState);
                        
                        if (hasStreet && addressInfo.has("street")) {
                            street = addressInfo.get("street").asText();
                        }
                        if (hasCity && addressInfo.has("city")) {
                            city = addressInfo.get("city").asText();
                        }
                        if (hasHouseNumber && addressInfo.has("houseNumber")) {
                            houseNumber = addressInfo.get("houseNumber").asText();
                        }
                        if (hasPostalCode && addressInfo.has("postalCode")) {
                            postalCode = addressInfo.get("postalCode").asText();
                        }
                        if (hasState && addressInfo.has("state")) {
                            state = addressInfo.get("state").asText();
                        }
                        
                    }
                }
                
                // Usa sempre i dati salvati in _internals.addressInfo se disponibili
                data.put("hasStreet", hasStreet);
                data.put("hasCity", hasCity);
                data.put("hasHouseNumber", hasHouseNumber);
                data.put("hasPostalCode", hasPostalCode);
                data.put("hasState", hasState);
                data.put("hasAddress", hasStreet || hasCity || hasHouseNumber || hasPostalCode || hasState);
                
                if (hasStreet && street != null) {
                    data.put("street", street);
                }
                if (hasCity && city != null) {
                    data.put("city", city);
                }
                if (hasHouseNumber && houseNumber != null) {
                    data.put("houseNumber", houseNumber);
                }
                if (hasPostalCode && postalCode != null) {
                    data.put("postalCode", postalCode);
                }
                if (hasState && state != null) {
                    data.put("state", state);
                }
                
                // Se mancano dati essenziali, prova a processare l'indirizzo esistente come fallback
                if (!hasStreet && !hasCity && !hasHouseNumber) {
                    Map<String, Object> addressParts = processExistingAddress(sessionId, incidentLocation);
                    data.putAll(addressParts);
                }
            } else {
                data.put("hasAddress", false);
                data.put("hasStreet", false);
                data.put("hasCity", false);
                data.put("hasHouseNumber", false);
                data.put("hasPostalCode", false);
                data.put("hasState", false);
            }
            
            if (finalOutput.has("administrativeCheck") && !finalOutput.get("administrativeCheck").isNull()) {
                data.put("administrativeCheck", finalOutput.get("administrativeCheck"));
            }
        }
        
        
        return data;
    }

    private List<String> checkMissingStep2Fields(Map<String, Object> data) {
        Map<String, Object> validation = chatV2MissingInfoHandler.validateStepDataV2("", "step2", data);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) validation.get("missingFields");
        return missing;
    }

    private List<String> checkMissingStep2FieldsFromStored(String sessionId) {
        Map<String, Object> storedData = getStoredStep2Data(sessionId);
        return checkMissingStep2Fields(storedData);
    }

    private Map<String, Object> getStoredStep2Data(String sessionId) {
        ObjectNode finalOutput = finalOutputJSONStore.get("final_output", sessionId);
        Map<String, Object> data = new HashMap<>();
        
        if (finalOutput != null) {
            if (finalOutput.has("whatHappenedCode") && !finalOutput.get("whatHappenedCode").isNull()) {
                data.put("whatHappenedCode", finalOutput.get("whatHappenedCode").asText());
            }
            if (finalOutput.has("whatHappenedContext") && !finalOutput.get("whatHappenedContext").isNull()) {
                data.put("whatHappenedContext", finalOutput.get("whatHappenedContext").asText());
            }
            if (finalOutput.has("damageDetails") && !finalOutput.get("damageDetails").isNull()) {
                data.put("damageDetails", finalOutput.get("damageDetails").asText());
            }
            if (finalOutput.has("circumstances") && !finalOutput.get("circumstances").isNull()) {
                data.put("circumstances", finalOutput.get("circumstances"));
            }
            if (finalOutput.has("imagesUploaded") && !finalOutput.get("imagesUploaded").isNull()) {
                data.put("imagesUploaded", finalOutput.get("imagesUploaded"));
            }
        }
        
        return data;
    }

    private StepperData createStepperData(String currentStep) {
        List<StepItem> stepList = new ArrayList<>();
        
        // Step 1: Quando
        boolean step1Completed = "step2".equals(currentStep) || "completed".equals(currentStep);
        stepList.add(new StepItem("quando", step1Completed));
        
        // Step 2: Cosa Successo
        boolean step2Completed = "completed".equals(currentStep);
        stepList.add(new StepItem("cosaSuccesso", step2Completed));
        
        // Step 3: Documenti (sempre presente ma senza logica)
        boolean step3Completed = "completed".equals(currentStep);
        stepList.add(new StepItem("documenti", step3Completed));
        
        // Current state: indice dello step corrente (0-based)
        // Se siamo in step1 ma step1 non è completato, siamo ancora al primo step (indice 0)
        // Se siamo in step1 e step1 è completato, siamo al secondo step (indice 1)
        int currentState;
        if ("welcome".equals(currentStep)) {
            currentState = 0; // Primo step
        } else if ("step1".equals(currentStep)) {
            currentState = step1Completed ? 1 : 0; // Se completato, vai al prossimo, altrimenti rimani al primo
        } else if ("step2".equals(currentStep)) {
            currentState = step2Completed ? 2 : 1; // Se completato, vai al prossimo, altrimenti rimani al secondo
        } else if ("completed".equals(currentStep)) {
            currentState = 2; // Tutti completati, siamo all'ultimo step
        } else {
            currentState = 0; // Default al primo step
        }
        
        return new StepperData(stepList, currentState);
    }

    private void saveStep1Data(String sessionId, Map<String, Object> data) {
        System.out.println("=== DEBUG saveStep1Data START ===");
        System.out.println("SessionId: " + sessionId);
        System.out.println("Input data: " + data);
        
        ObjectNode patch = M.createObjectNode();
        
        // Gestisci data e ora separatamente
        boolean hasDateInfo = data.containsKey("hasDate") || data.containsKey("hasTime") || 
                             data.containsKey("incidentDate") || data.containsKey("incidentTime");
        // In step 1, aggiorniamo SEMPRE tutti i campi di indirizzo
        boolean hasAddressInfo = true; // Sempre true in step 1
        
        System.out.println("hasDateInfo: " + hasDateInfo);
        System.out.println("hasAddressInfo: " + hasAddressInfo);
        
        if (hasDateInfo || hasAddressInfo) {
            ObjectNode internals = M.createObjectNode();
            
            // Gestisci data e ora in _internals.date - APPROCCIO INCREMENTALE
            if (hasDateInfo) {
                ObjectNode dateInfo = M.createObjectNode();
                
                // Prima copia tutto quello che esiste
                ObjectNode existingDateInfo = null;
                if (finalOutputJSONStore.get("final_output", sessionId) != null) {
                    ObjectNode existingOutput = finalOutputJSONStore.get("final_output", sessionId);
                    if (existingOutput.has("_internals") && !existingOutput.get("_internals").isNull()) {
                        ObjectNode existingInternals = (ObjectNode) existingOutput.get("_internals");
                        if (existingInternals.has("date") && !existingInternals.get("date").isNull()) {
                            existingDateInfo = (ObjectNode) existingInternals.get("date");
                            // Copia tutti i dati esistenti
                            if (existingDateInfo.has("hasDate")) {
                                dateInfo.put("hasDate", existingDateInfo.get("hasDate").asBoolean());
                            }
                            if (existingDateInfo.has("hasTime")) {
                                dateInfo.put("hasTime", existingDateInfo.get("hasTime").asBoolean());
                            }
                            if (existingDateInfo.has("date")) {
                                dateInfo.put("date", existingDateInfo.get("date").asText());
                            }
                            if (existingDateInfo.has("time")) {
                                dateInfo.put("time", existingDateInfo.get("time").asText());
                            }
                            if (existingDateInfo.has("incidentDate")) {
                                dateInfo.put("incidentDate", existingDateInfo.get("incidentDate").asText());
                            }
                        }
                    }
                }
                
                // Poi aggiorna SOLO quello che l'AI ha trovato - APPROCCIO INCREMENTALE
                if (data.containsKey("hasDate")) {
                    dateInfo.put("hasDate", (Boolean) data.get("hasDate"));
                    System.out.println("DEBUG: hasDate UPDATED from AI: " + data.get("hasDate"));
                }
                if (data.containsKey("hasTime")) {
                    dateInfo.put("hasTime", (Boolean) data.get("hasTime"));
                    System.out.println("DEBUG: hasTime UPDATED from AI: " + data.get("hasTime"));
                }
                if (data.containsKey("incidentDate")) {
                    String incidentDate = (String) data.get("incidentDate");
                    dateInfo.put("incidentDate", incidentDate);
                    
                    // Se è una data completa ISO, estrai data e ora separate
                    if (incidentDate != null && incidentDate.contains("T")) {
                        String[] parts = incidentDate.split("T");
                        if (parts.length == 2) {
                            dateInfo.put("date", parts[0]);
                            dateInfo.put("time", parts[1].replace("Z", ""));
                        }
                    } else if (incidentDate != null && incidentDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        // Solo data
                        dateInfo.put("date", incidentDate);
                    } else if (incidentDate != null && incidentDate.matches("\\d{2}:\\d{2}:\\d{2}")) {
                        // Solo ora
                        dateInfo.put("time", incidentDate);
                    }
                    System.out.println("DEBUG: incidentDate UPDATED from AI: " + incidentDate);
                }
                if (data.containsKey("incidentTime")) {
                    dateInfo.put("time", (String) data.get("incidentTime"));
                    System.out.println("DEBUG: incidentTime UPDATED from AI: " + data.get("incidentTime"));
                }
                
                internals.set("date", dateInfo);
            }
            
            // Gestisci indirizzo in _internals.addressInfo
            // Aggiorna sempre i dati di indirizzo, anche se non ci sono nuovi dati
            if (hasAddressInfo || hasDateInfo) {
                ObjectNode addressInfo = M.createObjectNode();
                
                // Recupera i dati esistenti per mantenerli
                ObjectNode existingAddressInfo = null;
                if (finalOutputJSONStore.get("final_output", sessionId) != null) {
                    ObjectNode existingOutput = finalOutputJSONStore.get("final_output", sessionId);
                    if (existingOutput.has("_internals") && !existingOutput.get("_internals").isNull()) {
                        ObjectNode existingInternals = (ObjectNode) existingOutput.get("_internals");
                        if (existingInternals.has("addressInfo") && !existingInternals.get("addressInfo").isNull()) {
                            existingAddressInfo = (ObjectNode) existingInternals.get("addressInfo");
                            System.out.println("DEBUG: Found existing addressInfo: " + existingAddressInfo);
                        }
                    }
                }
                System.out.println("DEBUG: existingAddressInfo is null: " + (existingAddressInfo == null));
                
                // APPROCCIO INCREMENTALE: aggiorna SOLO i campi che l'AI ha estratto
                // Prima copia tutto quello che esiste
                if (existingAddressInfo != null) {
                    if (existingAddressInfo.has("street")) {
                        addressInfo.put("street", existingAddressInfo.get("street").asText());
                    }
                    if (existingAddressInfo.has("city")) {
                        addressInfo.put("city", existingAddressInfo.get("city").asText());
                    }
                    if (existingAddressInfo.has("postalCode")) {
                        addressInfo.put("postalCode", existingAddressInfo.get("postalCode").asText());
                    }
                    if (existingAddressInfo.has("state")) {
                        addressInfo.put("state", existingAddressInfo.get("state").asText());
                    }
                    if (existingAddressInfo.has("houseNumber")) {
                        addressInfo.put("houseNumber", existingAddressInfo.get("houseNumber").asText());
                    }
                }
                
                // Poi aggiorna SOLO quello che l'AI ha trovato
                if (data.containsKey("street")) {
                    addressInfo.put("street", (String) data.get("street"));
                }
                if (data.containsKey("city")) {
                    addressInfo.put("city", (String) data.get("city"));
                }
                if (data.containsKey("postalCode")) {
                    addressInfo.put("postalCode", (String) data.get("postalCode"));
                }
                if (data.containsKey("state")) {
                    addressInfo.put("state", (String) data.get("state"));
                }
                if (data.containsKey("houseNumber")) {
                    addressInfo.put("houseNumber", (String) data.get("houseNumber"));
                }
                
                // APPROCCIO INCREMENTALE: aggiorna SOLO i flag che l'AI ha estratto
                // Prima copia tutti i flag esistenti
                if (existingAddressInfo != null) {
                    if (existingAddressInfo.has("hasStreet")) {
                        addressInfo.put("hasStreet", existingAddressInfo.get("hasStreet").asBoolean());
                    }
                    if (existingAddressInfo.has("hasCity")) {
                        addressInfo.put("hasCity", existingAddressInfo.get("hasCity").asBoolean());
                    }
                    if (existingAddressInfo.has("hasPostalCode")) {
                        addressInfo.put("hasPostalCode", existingAddressInfo.get("hasPostalCode").asBoolean());
                    }
                    if (existingAddressInfo.has("hasState")) {
                        addressInfo.put("hasState", existingAddressInfo.get("hasState").asBoolean());
                    }
                    if (existingAddressInfo.has("hasHouseNumber")) {
                        addressInfo.put("hasHouseNumber", existingAddressInfo.get("hasHouseNumber").asBoolean());
                    }
                }
                
                // Poi aggiorna SOLO i flag che l'AI ha trovato
                if (data.containsKey("hasStreet")) {
                    addressInfo.put("hasStreet", (Boolean) data.get("hasStreet"));
                    System.out.println("DEBUG: hasStreet UPDATED from AI: " + data.get("hasStreet"));
                }
                if (data.containsKey("hasCity")) {
                    addressInfo.put("hasCity", (Boolean) data.get("hasCity"));
                    System.out.println("DEBUG: hasCity UPDATED from AI: " + data.get("hasCity"));
                }
                if (data.containsKey("hasPostalCode")) {
                    addressInfo.put("hasPostalCode", (Boolean) data.get("hasPostalCode"));
                    System.out.println("DEBUG: hasPostalCode UPDATED from AI: " + data.get("hasPostalCode"));
                }
                if (data.containsKey("hasState")) {
                    addressInfo.put("hasState", (Boolean) data.get("hasState"));
                    System.out.println("DEBUG: hasState UPDATED from AI: " + data.get("hasState"));
                }
                if (data.containsKey("hasHouseNumber")) {
                    addressInfo.put("hasHouseNumber", (Boolean) data.get("hasHouseNumber"));
                    System.out.println("DEBUG: hasHouseNumber UPDATED from AI: " + data.get("hasHouseNumber"));
                }
                
                internals.set("addressInfo", addressInfo);
                System.out.println("DEBUG: Final addressInfo to save: " + addressInfo);
            }
            
            patch.set("_internals", internals);
        }
        
        // Costruisci incidentDate finale combinando data e ora da _internals
        String finalIncidentDate = buildFinalIncidentDate(sessionId, data);
        if (finalIncidentDate != null) {
            patch.put("incidentDate", finalIncidentDate);
        }
        
        // Costruisci incidentLocation finale combinando parti separate da _internals.addressInfo
        String finalIncidentLocation = buildFinalIncidentLocation(sessionId, data);
        if (finalIncidentLocation != null) {
            patch.put("incidentLocation", finalIncidentLocation);
        } else if (data.containsKey("incidentLocation")) {
            // Fallback: usa incidentLocation se non riusciamo a costruirlo
            patch.put("incidentLocation", (String) data.get("incidentLocation"));
        }
        
        finalOutputJSONStore.put("final_output", sessionId, null, patch);
    }

    private void saveStep2Data(String sessionId, Map<String, Object> data) {
        ObjectNode patch = M.createObjectNode();
        if (data.containsKey("whatHappenedResult")) {
            // Parse the JSON result and extract fields
            try {
                Object whatHappenedResult = data.get("whatHappenedResult");
                System.out.println("DEBUG: whatHappenedResult type: " + whatHappenedResult.getClass().getSimpleName());
                System.out.println("DEBUG: whatHappenedResult value: " + whatHappenedResult);
                
                ObjectNode whatHappened;
                
                if (whatHappenedResult instanceof String) {
                    // Se è una stringa, parsala
                    System.out.println("DEBUG: Parsing as String");
                    whatHappened = (ObjectNode) M.readTree((String) whatHappenedResult);
                } else if (whatHappenedResult instanceof ObjectNode) {
                    // Se è già un ObjectNode, usalo direttamente
                    System.out.println("DEBUG: Using as ObjectNode directly");
                    whatHappened = (ObjectNode) whatHappenedResult;
                } else {
                    // Fallback: prova a convertire in stringa e poi parsare
                    System.out.println("DEBUG: Converting to string and parsing");
                    whatHappened = (ObjectNode) M.readTree(whatHappenedResult.toString());
                }
                
                patch.put("whatHappenedCode", whatHappened.path("whatHappenedCode").asText());
                patch.put("whatHappenedContext", whatHappened.path("whatHappenedContext").asText());
                System.out.println("DEBUG: Successfully parsed whatHappenedResult");
            } catch (Exception e) {
                System.err.println("Error parsing whatHappenedResult: " + e.getMessage());
                e.printStackTrace();
                // Handle parsing error
            }
        }
        if (data.containsKey("damageDetails")) {
            patch.put("damageDetails", (String) data.get("damageDetails"));
        }
        if (data.containsKey("circumstances")) {
            // Crea oggetto circumstances con struttura corretta
            ObjectNode circumstances = M.createObjectNode();
            circumstances.put("details", (String) data.get("circumstances"));
            circumstances.put("notes", (String) data.get("circumstances"));
            patch.set("circumstances", circumstances);
        }
        finalOutputJSONStore.put("final_output", sessionId, null, patch);
    }


    private String getPolicyNumber(String sessionId) {
        ObjectNode fo = finalOutputJSONStore.get("final_output", sessionId);
        return fo != null ? fo.path("policyNumber").asText() : null;
    }

    private String getCurrentLocation(String sessionId) {
        ObjectNode fo = finalOutputJSONStore.get("final_output", sessionId);
        return fo != null ? fo.path("incidentLocation").asText() : null;
    }


    private void processMediaFilesWithOCR(String sessionId, List<String> mediaFiles, String userMessage) {
        System.out.println("DEBUG: ===== processMediaFilesWithOCR CALLED =====");
        System.out.println("DEBUG: SessionId: " + sessionId);
        System.out.println("DEBUG: MediaFiles: " + mediaFiles);
        System.out.println("DEBUG: UserMessage: " + userMessage);
        try {
            System.out.println("DEBUG: Processing media files with OCR: " + mediaFiles);
            
            ObjectNode finalOutput = finalOutputJSONStore.get("final_output", sessionId);
            if (finalOutput == null) {
                finalOutput = M.createObjectNode();
            }
            
            // Crea array imagesUploaded
            ArrayNode imagesUploaded = M.createArrayNode();
            StringBuilder damageDetailsBuilder = new StringBuilder();
            List<String> mediaDescriptions = new ArrayList<>();
            
            // Processa ogni file con OCR
            for (String mediaFile : mediaFiles) {
                System.out.println("DEBUG: Processing media file: " + mediaFile);
                
                // Chiama ChatV2MediaOcrAgentV2 per processare il file
                String result = null;
                try {
                    result = chatV2MediaOcrAgent.processMediaV2(sessionId, mediaFile);
                    System.out.println("DEBUG: OCR result for " + mediaFile + ": " + result);
                    System.out.println("DEBUG: OCR result type: " + (result != null ? result.getClass().getSimpleName() : "null"));
                } catch (Exception e) {
                    System.err.println("ERROR: Failed to process media " + mediaFile + ": " + e.getMessage());
                    e.printStackTrace();
                    continue;
                }
                
                // Estrai informazioni dal risultato OCR
                try {
                    // Controlla se il risultato è un JSON valido
                    if (result == null || result.trim().isEmpty()) {
                        System.out.println("DEBUG: Empty OCR result, skipping");
                        continue;
                    }
                    
                    ObjectNode analysisResult = (ObjectNode) M.readTree(result);
                    String damageCategory = analysisResult.path("damageCategory").asText("NONE");
                    String damagedEntity = analysisResult.path("damagedEntity").asText("NONE");
                    String eventType = analysisResult.path("eventType").asText("UNKNOWN");
                    double confidence = analysisResult.path("confidence").asDouble(0.0);
                    String description = analysisResult.path("description").asText("Media analysis");
                    
                    // Crea oggetto media per imagesUploaded
                    ObjectNode mediaItem = M.createObjectNode();
                    mediaItem.put("mediaName", mediaFile);
                    mediaItem.put("mediaDescription", damagedEntity + " - " + eventType);
                    mediaItem.put("mediaType", getMediaType(mediaFile));
                    imagesUploaded.add(mediaItem);
                    
                    // Aggiungi a damageDetails se non è NONE
                    if (!"NONE".equals(damageCategory)) {
                        if (damageDetailsBuilder.length() > 0) {
                            damageDetailsBuilder.append(", ");
                        }
                        damageDetailsBuilder.append(damagedEntity).append(" (conf. ").append(String.format("%.2f", confidence)).append(")");
                    }
                    
                    // Aggiungi descrizione per circumstances
                    mediaDescriptions.add(description);
                    
                } catch (Exception e) {
                    System.err.println("Error parsing OCR result for " + mediaFile + ": " + e.getMessage());
                    // Fallback: aggiungi media item base
                    ObjectNode mediaItem = M.createObjectNode();
                    mediaItem.put("mediaName", mediaFile);
                    mediaItem.put("mediaDescription", "Media analysis");
                    mediaItem.put("mediaType", getMediaType(mediaFile));
                    imagesUploaded.add(mediaItem);
                }
            }
            
            // Salva imagesUploaded
            if (imagesUploaded.size() > 0) {
                finalOutput.set("imagesUploaded", imagesUploaded);
            }
            
            // Salva damageDetails
            if (damageDetailsBuilder.length() > 0) {
                finalOutput.put("damageDetails", damageDetailsBuilder.toString());
            }
            
            // Crea circumstances combinando messaggio utente e risultati media
            createCircumstancesFromMedia(sessionId, userMessage, mediaDescriptions, finalOutput);
            
            // Aggiorna final output
            finalOutputJSONStore.put("final_output", sessionId, null, finalOutput);
            
        } catch (Exception e) {
            System.err.println("Error processing media files: " + e.getMessage());
        }
    }
    
    private String getMediaType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        if (List.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff", "heic", "heif", "svg").contains(extension)) {
            return "image";
        } else if (List.of("mp4", "mov", "m4v", "avi", "mkv", "webm", "mpeg", "mpg", "3gp", "3gpp", "wmv").contains(extension)) {
            return "video";
        } else {
            return "document";
        }
    }
    
    private void createCircumstancesFromMedia(String sessionId, String userMessage, List<String> mediaDescriptions, ObjectNode finalOutput) {
        try {
            // Combina messaggio utente e descrizioni media per creare circumstances
            StringBuilder circumstancesBuilder = new StringBuilder();
            
            // Aggiungi titolo per l'evento (da whatHappenedContext se disponibile)
            if (finalOutput.has("whatHappenedContext")) {
                circumstancesBuilder.append(finalOutput.get("whatHappenedContext").asText());
            } else {
                circumstancesBuilder.append("Incidente assicurativo");
            }
            
            circumstancesBuilder.append("\n\n");
            
            // Aggiungi recap combinando messaggio utente e info estratte da media
            circumstancesBuilder.append("Descrizione: ").append(userMessage).append("\n");
            
            if (!mediaDescriptions.isEmpty()) {
                circumstancesBuilder.append("Analisi media: ");
                for (int i = 0; i < mediaDescriptions.size(); i++) {
                    if (i > 0) {
                        circumstancesBuilder.append("; ");
                    }
                    circumstancesBuilder.append(mediaDescriptions.get(i));
                }
            }
            
            // Crea oggetto circumstances
            ObjectNode circumstances = M.createObjectNode();
            circumstances.put("details", finalOutput.path("whatHappenedContext").asText("Incidente assicurativo"));
            circumstances.put("notes", circumstancesBuilder.toString());
            
            finalOutput.set("circumstances", circumstances);
            
        } catch (Exception e) {
            System.err.println("Error creating circumstances from media: " + e.getMessage());
        }
    }

    private List<String> extractMediaFiles(String userMessage) {
        List<String> mediaFiles = new ArrayList<>();
        
        // Cerca pattern [MEDIA_FILES]...[/MEDIA_FILES]
        if (userMessage.contains("[MEDIA_FILES]") && userMessage.contains("[/MEDIA_FILES]")) {
            String mediaSection = userMessage.substring(
                userMessage.indexOf("[MEDIA_FILES]") + 13,
                userMessage.indexOf("[/MEDIA_FILES]")
            );
            
            String[] lines = mediaSection.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    mediaFiles.add(trimmed);
                }
            }
        } else {
            // Cerca file allegati direttamente nel messaggio (pattern: filename.ext + size)
            String[] lines = userMessage.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                // Pattern: filename.ext seguito da size (es. "burnt-wall.jpg" seguito da "35.05 KB")
                if (trimmed.matches(".*\\.(jpg|jpeg|png|gif|mp4|avi|mov|pdf|doc|docx)$")) {
                    mediaFiles.add(trimmed);
                }
            }
        }
        
        System.out.println("DEBUG: extractMediaFiles found: " + mediaFiles);
        return mediaFiles;
    }

    private Map<String, Object> processExistingAddress(String sessionId, String incidentLocation) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Usa i tool per estrarre le parti separate dall'indirizzo esistente
            String street = chatV2AddressTool.extractStreetOnlyV2(sessionId, incidentLocation);
            String city = chatV2AddressTool.extractCityOnlyV2(sessionId, incidentLocation);
            
            boolean hasStreet = street != null && !street.trim().isEmpty();
            boolean hasCity = city != null && !city.trim().isEmpty();
            
            result.put("hasStreet", hasStreet);
            result.put("hasCity", hasCity);
            result.put("hasAddress", hasStreet || hasCity);
            
            if (hasStreet) {
                result.put("street", street);
            }
            if (hasCity) {
                result.put("city", city);
            }
            
            // Salva le parti separate in _internals.addressInfo per future validazioni
            if (hasStreet || hasCity) {
                ObjectNode patch = M.createObjectNode();
                ObjectNode internals = M.createObjectNode();
                ObjectNode addressInfo = M.createObjectNode();
                
                if (hasStreet) {
                    addressInfo.put("street", street);
                    addressInfo.put("hasStreet", true);
                }
                if (hasCity) {
                    addressInfo.put("city", city);
                    addressInfo.put("hasCity", true);
                }
                
                internals.set("addressInfo", addressInfo);
                patch.set("_internals", internals);
                finalOutputJSONStore.put("final_output", sessionId, null, patch);
            }
            
        } catch (Exception e) {
            // Fallback: considera l'indirizzo come completo se non riusciamo a processarlo
            result.put("hasAddress", true);
            result.put("hasStreet", true);
            result.put("hasCity", true);
            result.put("street", incidentLocation);
            result.put("city", incidentLocation);
        }
        
        return result;
    }

    private String buildFinalIncidentDate(String sessionId, Map<String, Object> data) {
        System.out.println("=== DEBUG buildFinalIncidentDate START ===");
        System.out.println("SessionId: " + sessionId);
        System.out.println("Input data: " + data);
        
        try {
            // Recupera i dati esistenti da _internals.date
            ObjectNode finalOutput = finalOutputJSONStore.get("final_output", sessionId);
            String existingDate = null;
            String existingTime = null;
            
            if (finalOutput != null && finalOutput.has("_internals") && !finalOutput.get("_internals").isNull()) {
                ObjectNode internals = (ObjectNode) finalOutput.get("_internals");
                if (internals.has("date") && !internals.get("date").isNull()) {
                    ObjectNode dateInfo = (ObjectNode) internals.get("date");
                    if (dateInfo.has("date")) {
                        existingDate = dateInfo.get("date").asText();
                    }
                    if (dateInfo.has("time")) {
                        existingTime = dateInfo.get("time").asText();
                    }
                }
            }
            
            // Aggiorna con i nuovi dati se presenti
            String newDate = null;
            String newTime = null;
            
            if (data.containsKey("incidentDate")) {
                String incidentDate = (String) data.get("incidentDate");
                if (incidentDate != null && incidentDate.contains("T")) {
                    // Data completa ISO
                    String[] parts = incidentDate.split("T");
                    if (parts.length == 2) {
                        newDate = parts[0];
                        newTime = parts[1].replace("Z", "");
                    }
                } else if (incidentDate != null && incidentDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    // Solo data
                    newDate = incidentDate;
                } else if (incidentDate != null && incidentDate.matches("\\d{2}:\\d{2}:\\d{2}")) {
                    // Solo ora
                    newTime = incidentDate;
                }
            }
            
            if (data.containsKey("incidentTime")) {
                newTime = (String) data.get("incidentTime");
            }
            
            // Usa i nuovi dati se disponibili, altrimenti quelli esistenti
            String finalDate = newDate != null ? newDate : existingDate;
            String finalTime = newTime != null ? newTime : existingTime;
            
            // Costruisci la data finale - NON INFERIRE MAI ORE NON COMUNICATE
            String result = null;
            if (finalDate != null && finalTime != null) {
                result = finalDate + "T" + finalTime + "Z";
                System.out.println("DEBUG: Combined date+time: " + result);
            } else if (finalDate != null) {
                // Solo data - NON aggiungere ora fittizia
                result = finalDate;
                System.out.println("DEBUG: Date only (NO time inferred): " + result);
            } else if (finalTime != null) {
                // Solo ora - NON desumere la data
                result = finalTime;
                System.out.println("DEBUG: Time only (NO date inferred): " + result);
            }
            
            System.out.println("DEBUG: Final incidentDate result: " + result);
            return result;
            
        } catch (Exception e) {
            System.err.println("Error building final incident date: " + e.getMessage());
            return null;
        }
    }

    private String buildFinalIncidentLocation(String sessionId, Map<String, Object> data) {
        try {
            // Recupera i dati esistenti da _internals.addressInfo
            ObjectNode finalOutput = finalOutputJSONStore.get("final_output", sessionId);
            Map<String, String> existingParts = new HashMap<>();
            
            if (finalOutput != null && finalOutput.has("_internals") && !finalOutput.get("_internals").isNull()) {
                ObjectNode internals = (ObjectNode) finalOutput.get("_internals");
                if (internals.has("addressInfo") && !internals.get("addressInfo").isNull()) {
                    ObjectNode addressInfo = (ObjectNode) internals.get("addressInfo");
                    
                    if (addressInfo.has("street")) {
                        existingParts.put("street", addressInfo.get("street").asText());
                    }
                    if (addressInfo.has("houseNumber")) {
                        existingParts.put("houseNumber", addressInfo.get("houseNumber").asText());
                    }
                    if (addressInfo.has("city")) {
                        existingParts.put("city", addressInfo.get("city").asText());
                    }
                    if (addressInfo.has("postalCode")) {
                        existingParts.put("postalCode", addressInfo.get("postalCode").asText());
                    }
                    if (addressInfo.has("state")) {
                        existingParts.put("state", addressInfo.get("state").asText());
                    }
                }
            }
            
            // Aggiorna con i nuovi dati se presenti, altrimenti mantieni quelli esistenti
            if (data.containsKey("street")) {
                existingParts.put("street", (String) data.get("street"));
            }
            if (data.containsKey("houseNumber")) {
                existingParts.put("houseNumber", (String) data.get("houseNumber"));
            }
            if (data.containsKey("city")) {
                existingParts.put("city", (String) data.get("city"));
            }
            if (data.containsKey("postalCode")) {
                existingParts.put("postalCode", (String) data.get("postalCode"));
            }
            if (data.containsKey("state")) {
                existingParts.put("state", (String) data.get("state"));
            }
            
            // Se non ci sono nuovi dati, mantieni quelli esistenti per la costruzione dell'indirizzo
            // Questo è importante per quando l'utente fornisce solo data/ora senza indirizzo
            
            // Costruisci l'indirizzo standardizzato
            return buildStandardizedAddress(existingParts);
            
        } catch (Exception e) {
            System.err.println("Error building final incident location: " + e.getMessage());
            return null;
        }
    }

    private String buildStandardizedAddress(Map<String, String> parts) {
        StringBuilder address = new StringBuilder();
        
        // Via (senza numero civico, che viene aggiunto separatamente)
        if (parts.containsKey("street") && !parts.get("street").isEmpty()) {
            String street = parts.get("street");
            // Rimuovi eventuali numeri dalla street per evitare duplicazioni
            street = street.replaceAll("\\s+\\d+\\s*$", "").trim();
            address.append(street);
        }
        
        // Numero civico separato
        if (parts.containsKey("houseNumber") && !parts.get("houseNumber").isEmpty()) {
            if (address.length() > 0) {
                address.append(" ");
            }
            address.append(parts.get("houseNumber"));
        }
        
        // Città
        if (parts.containsKey("city") && !parts.get("city").isEmpty()) {
            if (address.length() > 0) {
                address.append(", ");
            }
            address.append(parts.get("city"));
        }
        
        // CAP
        if (parts.containsKey("postalCode") && !parts.get("postalCode").isEmpty()) {
            if (address.length() > 0) {
                address.append(" ");
            }
            address.append(parts.get("postalCode"));
        }
        
        // Stato/Provincia
        if (parts.containsKey("state") && !parts.get("state").isEmpty()) {
            if (address.length() > 0) {
                address.append(", ");
            }
            address.append(parts.get("state"));
        }
        
        
        return address.length() > 0 ? address.toString() : null;
    }

    private String generateStep2WelcomeMessage(String acceptLanguage) {
        // @todo: tradurre con genAI
        switch (acceptLanguage != null ? acceptLanguage.toLowerCase() : "it") {
            case "en":
                return "Perfect! I've collected the information about where and when the incident happened. " +
                       "Now I need to know what happened. Can you describe the incident in detail? " +
                       "If you have photos or videos, you can attach them to help me better understand the situation.";
            case "de":
                return "Perfekt! Ich habe die Informationen darüber gesammelt, wo und wann der Vorfall passiert ist. " +
                       "Jetzt muss ich wissen, was passiert ist. Können Sie mir den Vorfall im Detail beschreiben? " +
                       "Wenn Sie Fotos oder Videos haben, können Sie diese anhängen, um mir zu helfen, die Situation besser zu verstehen.";
            case "it":
            default:
                return "Perfetto! Ho raccolto le informazioni su dove e quando è successo l'incidente. " +
                       "Ora ho bisogno di sapere cosa è successo. Puoi descrivermi l'incidente in dettaglio? " +
                       "Se hai foto o video, puoi allegarli per aiutarmi a capire meglio la situazione.";
        }
    }

}
