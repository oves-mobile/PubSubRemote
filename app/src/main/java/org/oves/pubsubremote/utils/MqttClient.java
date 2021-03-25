package org.oves.pubsubremote.utils;

import android.content.Context;

import org.eclipse.paho.android.service.MqttAndroidClient;

public class MqttClient extends MqttAndroidClient {
    private static MqttClient INSTANCE;
    private final String clientId;
    //Field Variables
    private Context context;
    private String serverUrl;

    //Constructor
    public MqttClient(Context context, String serverURI, String clientId) {
        super(context, serverURI, clientId);
        this.context = context;
        this.serverUrl = serverURI;
        this.clientId = clientId;
    }

    public static MqttClient getINSTANCE(Context context, String serverUrl, String clientId) {
        if (INSTANCE == null) {
            INSTANCE = new MqttClient(context, serverUrl, clientId);
        }
        return INSTANCE;
    }

    //Getters and Setters
    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @Override
    public String getClientId() {
        return clientId;
    }
}
