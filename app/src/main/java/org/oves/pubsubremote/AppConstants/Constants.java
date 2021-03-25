package org.oves.pubsubremote.AppConstants;

public class Constants {
    /* Base URL*/
    public static final String BASE_URL = "https://users-service.omnivoltaic.com/graphql";
    public static final int MY_PERMISSIONS_REQUEST_READ_PHONE_STATE = 01;
    public static boolean IS_DEBUG = true;
    public static boolean IS_CLEAN_SESSION = false;

    /*Client ID Configuration*/
    public static boolean IS_RANDOM_CLIENT_ID = false;
    public static String MQTT_CLIENT_ID = "pub-Sub-remote-app";

    /*Authentication for MQTT*/
    public static boolean IS_PASSWORD_AUTHENTICATION = false;
    public static String MQTT_USER_NAME = "oves";
    public static String MQTT_USER_PASSWORD = "vG=dDZM@<Y^8ej/U";

    /*Test Data*/
    public static boolean IS_TEST_DATA = false;
    public static boolean IS_CUSTOM_TOPIC = true;
    /*MQTT Configurations*/
    public static String LOCAL_MQTT_SERVER = "tcp://127.0.0.1:1883";
    public static String REMOTE_MQTT_SERVER = "tcp://mqtt-2.omnivoltaic.com:1883";
    public static String DEFAULT_PAYLOAD = "Hello Remote, this is PubSubRemote.";
    public static Integer DEFAULT_QOS_LEVEL = 1;
    public static String DEFAULT_TOPIC = "#";
    public static String IMEI_NUMBER = "";
}
