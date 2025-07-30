package com.accenture.claims.ai.application.tool;

import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.FinalOutputJSONStore;
import com.accenture.claims.ai.adapter.inbound.rest.chatStorage.PolicySelectionFlagStore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class PolicyFinderTool {

    @Inject MongoClient mongo;
    @Inject
    FinalOutputJSONStore finalOutputJSONStore;
    @Inject
    PolicySelectionFlagStore flagStore;

    @ConfigProperty(name = "quarkus.mongodb.database", defaultValue = "local_db")
    String dbName;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MongoCollection<Document> coll() {
        return mongo.getDatabase(dbName).getCollection("policy");
    }

    @PostConstruct
    void ensureIndexes() {
        // Indici utili (idempotenti)
        coll().createIndex(new Document("policyNumber", 1));
        coll().createIndex(new Document("policyHolders.lastName", 1).append("policyHolders.firstName", 1));
        coll().createIndex(new Document("policyHolders.contactChannels.communicationDetails", 1),
                new IndexOptions().name("contactDetails_idx"));
    }

    /**
     * Fuzzy search per trovare il policy holder.
     * Ritorna JSON:
     * {
     *   "matchStatus": "EXACT" | "SIMILAR" | "NONE",
     *   "holder": { "firstName":"...", "lastName":"...", "customerId":"...", "emails":["..."], "mobiles":["..."] }  // solo per EXACT o SIMILAR
     * }
     */
    @Tool(
            name = "fuzzySearch",
            value = "fuzzySearch: Cerca il policy holder con fuzzy match su nome/cognome; usa email/cellulare se disponibili. Parametri: sessionId, firstName, lastName, email?, mobile?"
    )
    public String fuzzySearch(String sessionId, String firstName, String lastName, @Nullable String email, @Nullable String mobile) {
        String fn = safe(firstName);
        String ln = safe(lastName);
        String normFn = normalize(fn);
        String normLn = normalize(ln);

        // Candidati dal DB (regex su prefissi o contatti)
        List<Document> candidatePolicies = fetchCandidatePolicies(fn, ln, email, mobile, 200);

        // Estrai candidati holder (deduplica per customerId, altrimenti nome+cognome normalizzati)
        Map<String, Holder> holders = new LinkedHashMap<>();
        for (Document pol : candidatePolicies) {
            List<Document> phs = (List<Document>) pol.getOrDefault("policyHolders", List.of());
            for (Document h : phs) {
                Holder holder = toHolder(h);
                if (holder == null) continue;
                String key = holder.customerId != null ? "id:" + holder.customerId
                        : "nm:" + holder.normFirst + "|" + holder.normLast;
                holders.putIfAbsent(key, holder);
            }
        }

        if (holders.isEmpty()) {
            return json(Map.of("matchStatus", "NONE"));
        }

        // Valuta distanze
        Holder exact = null;
        Holder best = null;
        int bestScore = Integer.MAX_VALUE;

        for (Holder h : holders.values()) {
            int d1 = levenshtein(normFn, h.normFirst);
            int d2 = levenshtein(normLn, h.normLast);
            int score = d1 + d2;

            if (d1 == 0 && d2 == 0) {
                exact = h; break;
            }
            if (score < bestScore) {
                bestScore = score;
                best = h;
            }
        }

        if (exact != null) {
            return json(Map.of(
                    "matchStatus", "EXACT",
                    "holder", exact.toPublicMap()
            ));
        }

        // soglie semplici: nomi corti <= 4 → tollera 1, altrimenti 2
        int lenAvg = (normFn.length() + normLn.length()) / 2;
        int threshold = lenAvg <= 4 ? 1 : 2;
        if (best != null && bestScore <= threshold) {
            return json(Map.of(
                    "matchStatus", "SIMILAR",
                    "holder", best.toPublicMap()
            ));
        }

        return json(Map.of("matchStatus", "NONE"));
    }

    // -------------------- TOOL 2: RETRIEVE POLICIES --------------------


    /**
     * Recupera le polizze per il holder indicato (match case-insensitive; se email/mobile presenti li usa).
     * Ritorna JSON:
     * {
     *   "resultType": "LIST" | "SINGLE" | "NONE",
     *   "items": [
     *     { "policyNumber":"...", "policyStatus":"...", "productReference":{ "name":"...", "groupName":"...", "code":"..." }, "beginDate":"...", "endDate":"..." }
     *   ]
     * }
     */
    @Tool(
            name = "retrievePolicy",
            value = "retrievePolicy: Recupera polizze associate a firstName/lastName (email/mobile opzionali). Parametri: sessionId, firstName, lastName, email?, mobile?"
    )
    public String retrievePolicy(String sessionId, String firstName, String lastName,@Nullable String email,@Nullable String mobile) {
        String fn = safe(firstName);
        String ln = safe(lastName);
        List<Document> docs = fetchPoliciesForHolder(fn, ln, email, mobile, 200);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Document d : docs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("policyNumber", d.getString("policyNumber"));
            m.put("policyStatus", d.getString("policyStatus"));

            Map<String, Object> product = new LinkedHashMap<>();
            Document pr = (Document) d.get("productReference");
            if (pr != null) {
                product.put("name", pr.getString("name"));
                product.put("groupName", pr.getString("groupName"));
                product.put("code", pr.getString("code"));
            }
            m.put("productReference", product);

            m.put("beginDate", toIso((Date) d.get("beginDate")));
            m.put("endDate", toIso((Date) d.get("endDate")));
            items.add(m);
        }

        String resultType = items.isEmpty() ? "NONE" : (items.size() == 1 ? "SINGLE" : "LIST");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultType", resultType);
        out.put("items",      items);
        out.put("_sysHint",
                """
                ⚠️ ISTRUZIONE OBBLIGATORIA ⚠️
                DOPO che l'agente conferma o sceglie una polizza, e PRIMA di passare allo STEP 3:
                1. DEVI ASSOLUTAMENTE chiamare il tool setSelectedPolicy
                2. Usa esattamente questi parametri:
                   - sessionId: lo stesso sessionId usato per retrievePolicy
                   - policyNumber: il numero della polizza scelta/confermata
                
                Esempio:
                setSelectedPolicy(sessionId, "MTRHHR00026398")
                
                ⚠️ QUESTO PASSAGGIO È OBBLIGATORIO E NON OPZIONALE ⚠️
                Il sistema bloccherà qualsiasi tentativo di procedere allo STEP 3 senza prima chiamare setSelectedPolicy.
                """
        );
        
        // Aggiungi anche un campo specifico per il numero di polizza per renderlo più evidente
        out.put("_mandatoryAction", "setSelectedPolicy");
        flagStore.setPending(sessionId, true);
        return json(out);
    }

    /**
     * PolicyFinder.setSelectedPolicy
     * Seleziona la polizza definitiva e la salva nella collection (final_output di default).
     * Parametri: sessionId, policyNumber, collection? (opzionale, default "final_output")
     *
     * @return  { "status":"OK" }  oppure { "error":"not_found" }
     */
    @Tool(
            value = """
            setSelectedPolicy: salva nel FINAL_OUTPUT la polizza selezionata.
            Parametri:
              sessionId     string   // id sessione corrente
              policyNumber  string   // numero polizza scelta
            """,
            name = "setSelectedPolicy"
    )
    public String setSelectedPolicy(String sessionId,
                                    String policyNumber) {
        
        System.out.println("========== setSelectedPolicy CALLED ==========");
        System.out.println("sessionId: " + sessionId);
        System.out.println("policyNumber: " + policyNumber);
        System.out.println("==============================================");

        String collection = "final_output";

        // 1. fetch singola polizza
        Document pol = coll().find(Filters.eq("policyNumber", policyNumber)).first();
        if (pol == null) {
            return json(Map.of("error", "not_found"));
        }

        // 2. estrai campi necessari
        String status = safe(pol.getString("policyStatus"));

        // • holder principale = il primo in lista
        List<Document> holders = (List<Document>) pol.getOrDefault("policyHolders", List.of());
        Document h = holders.isEmpty() ? new Document() : holders.getFirst();

        String hFirst = safe(h.getString("firstName"));
        String hLast  = safe(h.getString("lastName"));

        String email  = null;
        String mobile = null;
        List<Document> channels = (List<Document>) h.getOrDefault("contactChannels", List.of());
        for (Document c : channels) {
            String type = safe(c.getString("communicationType")).toUpperCase(Locale.ROOT);
            String val  = safe(c.getString("communicationDetails"));
            if (email == null && "EMAIL".equals(type)  && !val.isBlank()) email  = val;
            if (mobile == null && "MOBILE".equals(type) && !val.isBlank()) mobile = val;
        }

        // 3. costruisci patch JSON
        ObjectNode patch = MAPPER.createObjectNode();
        patch.put("policyNumber", policyNumber);
        patch.put("policyStatus", status);

        ObjectNode contacts = MAPPER.createObjectNode();
        if (email  != null) contacts.put("email" , email);
        if (mobile != null) contacts.put("mobile", mobile);

        ObjectNode reporter = MAPPER.createObjectNode();
        if (!hFirst.isBlank()) reporter.put("firstName", hFirst);
        if (!hLast .isBlank()) reporter.put("lastName" , hLast);
        if (contacts.size()   > 0)      reporter.set("contacts", contacts);

        if (reporter.size() > 0) patch.set("reporter", reporter);

        // 4. salva / merge
        finalOutputJSONStore.put(collection, sessionId, "", patch);
        flagStore.setPending(sessionId, false);
        return json(Map.of("status", "OK"));
    }

    // -------------------- Helpers DB --------------------

    /** Se ho email/mobile provo match su contatti; altrimenti uso regex su prefissi nome/cognome. */
    private List<Document> fetchCandidatePolicies(String firstName, String lastName, String email, String mobile, int limit) {
        List<Bson> filters = new ArrayList<>();

        if (notBlank(email)) {
            filters.add(Filters.elemMatch("policyHolders.contactChannels",
                    Filters.and(
                            Filters.regex("communicationDetails", "^" + Pattern.quote(email) + "$", "i"),
                            Filters.eq("communicationType", "EMAIL")
                    )));
        }
        if (notBlank(mobile)) {
            String digits = digitsOnly(mobile);
            if (!digits.isEmpty()) {
                String mobileRegex = Arrays.stream(digits.split(""))
                        .collect(Collectors.joining("\\D*"));
                filters.add(Filters.elemMatch("policyHolders.contactChannels",
                        Filters.and(
                                Filters.regex("communicationDetails", mobileRegex, "i"),
                                Filters.eq("communicationType", "MOBILE")
                        )));
            }
        }

        if (filters.isEmpty()) {
            // Nessun contatto → restringi con prefissi (2 lettere) case-insensitive
            String p1 = prefix(firstName, 2);
            String p2 = prefix(lastName, 2);
            if (!p1.isEmpty()) {
                filters.add(Filters.regex("policyHolders.firstName", "^" + Pattern.quote(p1), "i"));
            }
            if (!p2.isEmpty()) {
                filters.add(Filters.regex("policyHolders.lastName", "^" + Pattern.quote(p2), "i"));
            }
        }

        Bson match = filters.isEmpty() ? Filters.exists("_id") : Filters.and(filters);
        FindIterable<Document> it = coll().find(match).limit(limit);
        List<Document> out = new ArrayList<>();
        for (Document d : it) out.add(d);
        return out;
    }

    /** Per recupero polizze: privilegia email/mobile, altrimenti match esatto case-insensitive su nome+cognome. */
    private List<Document> fetchPoliciesForHolder(String firstName, String lastName, String email, String mobile, int limit) {
        List<Bson> filters = new ArrayList<>();

        if (notBlank(email)) {
            filters.add(Filters.elemMatch("policyHolders.contactChannels",
                    Filters.and(
                            Filters.regex("communicationDetails", "^" + Pattern.quote(email) + "$", "i"),
                            Filters.eq("communicationType", "EMAIL")
                    )));
        }
        if (notBlank(mobile)) {
            String digits = digitsOnly(mobile);
            if (!digits.isEmpty()) {
                String mobileRegex = Arrays.stream(digits.split("")).collect(Collectors.joining("\\D*"));
                filters.add(Filters.elemMatch("policyHolders.contactChannels",
                        Filters.and(
                                Filters.regex("communicationDetails", mobileRegex, "i"),
                                Filters.eq("communicationType", "MOBILE")
                        )));
            }
        }

        if (filters.isEmpty()) {
            if (notBlank(firstName)) {
                filters.add(Filters.regex("policyHolders.firstName", "^" + Pattern.quote(firstName) + "$", "i"));
            }
            if (notBlank(lastName)) {
                filters.add(Filters.regex("policyHolders.lastName", "^" + Pattern.quote(lastName) + "$", "i"));
            }
        }

        Bson match = filters.isEmpty() ? Filters.exists("_id") : Filters.and(filters);
        FindIterable<Document> it = coll().find(match).limit(limit);
        List<Document> out = new ArrayList<>();
        for (Document d : it) out.add(d);
        return out;
    }

    // -------------------- Helpers model --------------------

    private static class Holder {
        String first;
        String last;
        String normFirst;
        String normLast;
        String customerId;
        List<String> emails = new ArrayList<>();
        List<String> mobiles = new ArrayList<>();

        Map<String, Object> toPublicMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("firstName", first);
            m.put("lastName", last);
            if (customerId != null) m.put("customerId", customerId);
            if (!emails.isEmpty()) m.put("emails", emails);
            if (!mobiles.isEmpty()) m.put("mobiles", mobiles);
            return m;
        }
    }

    private Holder toHolder(Document h) {
        if (h == null) return null;
        Holder holder = new Holder();
        holder.first = safe(h.getString("firstName"));
        holder.last = safe(h.getString("lastName"));
        holder.normFirst = normalize(holder.first);
        holder.normLast = normalize(holder.last);
        holder.customerId = h.getString("customerId");

        List<Document> channels = (List<Document>) h.getOrDefault("contactChannels", List.of());
        for (Document c : channels) {
            String t = safe(c.getString("communicationType"));
            String v = safe(c.getString("communicationDetails"));
            if (t.equalsIgnoreCase("EMAIL") && !v.isBlank()) holder.emails.add(v);
            if (t.equalsIgnoreCase("MOBILE") && !v.isBlank()) holder.mobiles.add(v);
        }
        return holder;
    }

    private static String toIso(Date d) {
        if (d == null) return null;
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(d.getTime()).atOffset(ZoneOffset.UTC));
    }

    // -------------------- String utils & fuzzy --------------------

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private static String prefix(String s, int n) {
        s = safe(s);
        return s.length() <= n ? s : s.substring(0, n);
    }

    /** Normalizza per confronto: minuscolo, rimuove accenti e caratteri non A-Z. */
    private static String normalize(String s) {
        String lower = safe(s).toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        String noAccents = decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return noAccents.replaceAll("[^a-z]", "");
    }

    /** Mantiene solo cifre. */
    private static String digitsOnly(String s) {
        return safe(s).replaceAll("\\D+", "");
    }

    /** Distanza di Levenshtein. */
    private static int levenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[m];
    }

    private static String json(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization error", e);
        }
    }
}
