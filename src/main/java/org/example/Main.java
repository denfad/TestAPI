package org.example;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5);

        //создаём 100 потоков, в логах можем наблюдать что они выполняются последовательно, не превышая лимит
        for(int i = 0; i < 100; ++i) {
            new Thread(() -> {
                try {
                    api.createDocument(new CrptApi.Document(), "TOKEN");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}