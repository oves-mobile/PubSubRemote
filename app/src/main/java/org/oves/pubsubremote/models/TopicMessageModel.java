package org.oves.pubsubremote.models;

public class TopicMessageModel {
    //Fields:
    private String topic;
    private String message;

    //Constructors
    public TopicMessageModel(String topic, String message) {
        this.topic = topic;
        this.message = message;
    }

    //Getters & Setters:

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
