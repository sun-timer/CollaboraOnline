package org.libreoffice.androidlib.ai;

public final class AiCloudParams {
    public final String endpoint;
    public final String apiKey;
    public final String model;

    public AiCloudParams(String endpoint, String apiKey, String model) {
        this.endpoint = endpoint == null ? "" : endpoint;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model == null ? "" : model;
    }
}
