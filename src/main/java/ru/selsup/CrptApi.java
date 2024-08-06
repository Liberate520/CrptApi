package ru.selsup;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final HttpClient httpClient;
    private final Gson gson;
    private final Lock lock;
    private final Condition condition;
    private final long intervalMillis;
    private final int requestLimit;
    private int requestCount;
    private long nextResetTime;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.lock = new ReentrantLock();
        this.condition = lock.newCondition();
        this.intervalMillis = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;
        this.requestCount = 0;
        this.nextResetTime = System.currentTimeMillis() + intervalMillis;
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        lock.lock();
        try {
            while (true) {
                long currentTime = System.currentTimeMillis();
                if (currentTime >= nextResetTime) {
                    requestCount = 0;
                    nextResetTime = currentTime + intervalMillis;
                }

                if (requestCount < requestLimit) {
                    requestCount++;
                    break;
                }

                long waitTime = nextResetTime - currentTime;
                if (waitTime > 0) {
                    condition.await(waitTime, TimeUnit.MILLISECONDS);
                }
            }

            String jsonRequest = gson.toJson(document);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // Handle response as needed
        } finally {
            condition.signalAll();
            lock.unlock();
        }
    }
}

class Document {
    // Поля документа должны соответствовать JSON структуре API
    public Description description;
    public String doc_id;
    public String doc_status;
    public String doc_type;
    public boolean importRequest;
    public String owner_inn;
    public String participant_inn;
    public String producer_inn;
    public String production_date;
    public String production_type;
    public Product[] products;
    public String reg_date;
    public String reg_number;
}

class Description {
    public String participantInn;
}

class Product {
    public String certificate_document;
    public String certificate_document_date;
    public String certificate_document_number;
    public String owner_inn;
    public String producer_inn;
    public String production_date;
    public String tnved_code;
    public String uit_code;
    public String uitu_code;
}
