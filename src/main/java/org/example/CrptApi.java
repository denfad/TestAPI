package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.*;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    //лимит на колличества запросов
    private final int requestLimit;
    //период обнуления лимитов
    private final long delay;
    //момент времени, когда обнуляется лимит
    private volatile long resetTime;
    //текущее колличество отправленных запросов
    private volatile int requestsCount = 0;
    //сериализация
    private ObjectMapper objectMapper = new ObjectMapper();
    //HTTP клиент
    private OkHttpClient client = new OkHttpClient();
    //Период повторения попыток сделать запрос к API в случае неудачного предыдущего запроса
    private final int RETRY_DELAY = 200;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.delay = timeUnit.toChronoUnit().getDuration().toMillis();
        this.resetTime = System.currentTimeMillis() + delay;
    }

    //метод, создающий очередь из запросов
    private synchronized void waitExecution() {
        //если число запросов не превышает лимит, нам не важно обнулился ли уже лимит
        if(requestsCount >= requestLimit) {
            long sleepTime = resetTime - System.currentTimeMillis();
            //если время обнуления лимитов еще не настало, то дожидаемся его
            if(sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            //обнуляем лимит
            requestsCount = 0;
            //устанавливаем новое время обнуления
            resetTime = resetTime + delay;
            System.out.println("Reset limit");
        }
        //разрешаем запрос
        System.out.println("Make request №" + requestsCount);
        ++requestsCount;
    }

    //запрос на создание документа ввода в оборот товара
    public String createDocument(Document document, String token) throws IOException {
        waitExecution();
        String json = objectMapper.writeValueAsString(document);
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
        Request request = new Request.Builder()
                .url("https://ismp.crpt.ru/api/v3/lk/documents/create")
                .post(body)
                .addHeader("Authorization", "Bearer "+token)
                .build();
        Response response = makeRequest(request);
        return response.body().string();
    }

    private Response makeRequest(Request request) throws IOException {
        Response response = null;
        for(int i = 0; i < 3; ++i) {
            response = client.newCall(request).execute();
            //если 5ХХ ошибка, то делаем повторные 2 попытки сделать запрос
            if(response.code() % 100 == 5) {
                System.out.println("RETRY");
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                break;
            }
        }
        return response;
    }

    public static class Document {
        public Description description;
        public String docId;
        public String docStatus;
        public String docType;
        public boolean importRequest;
        public String ownerInn;
        public String participantInn;
        public String producerInn;
        public Date productionDate;
        public String productionType;
        public List<Product> products;
        public Date regDate;
        public String regNumber;

        public static class Description {
            public String participantInn;
        }

        public static class Product {
            public String certificateDocument;
            public Date certificateDocumentDate;
            public String certificateDocumentNumber;
            public String ownerInn;
            public String producerInn;
            public Date productionDate;
            public String tnvedCode;
            public String uitCode;
            public String uituCode;
        }
    }
}
