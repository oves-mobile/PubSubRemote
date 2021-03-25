package org.oves.pubsubremote.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.oves.pubsubremote.AppConstants.Constants;
import org.oves.pubsubremote.BuildConfig;
import org.oves.pubsubremote.R;
import org.oves.pubsubremote.adapters.TopicMessageAdapter;
import org.oves.pubsubremote.models.TopicMessageModel;
import org.oves.pubsubremote.utils.MqttClient;
import org.oves.pubsubremote.utils.TimeAgo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import es.dmoral.toasty.Toasty;
import timber.log.Timber;

import static org.oves.pubsubremote.AppConstants.Constants.MY_PERMISSIONS_REQUEST_READ_PHONE_STATE;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    //Fields:
    private RecyclerView recyclerView;
    private LinearLayoutManager linearLayout;
    private TopicMessageAdapter adapter;
    private List<TopicMessageModel> topicMessageModels;
    private Button syncToRemote;
    private SwitchCompat remoteSwitch, localSwitch;
    private boolean isRemoteConnected, isLocalConnected;
    private MqttConnectOptions mqttConnectOptions;
    private boolean isLocalMQTTConnected;
    private boolean isRemoteMQTTConnected;
    private volatile MqttClient mqttAndroidClientLocal;
    private volatile MqttClient mqttAndroidClientRemote;
    private MqttCallbackExtended mqttCallbackExtendedLocal;
    private MqttCallbackExtended mqttCallbackExtendedRemote;
    private IMqttToken iMqttToken;
    private String clientId;
    private TelephonyManager telephonyManager;
    @SuppressLint("HardwareIds")
    private String imei = "test_device";
    private TopicMessageModel topicMessageModel;
    private String data;
    private String topicData;
    private String messageData;
    private TextView remoteStatus;
    private TextView localStatus;
    private Set<TopicMessageModel> topicModelSet;
    private boolean subscribedToAllTopics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Logger
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
//        checkDeviceIMEI();

        //Init Views
        try {
            initViews();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * Init Views
     */
    private void initViews() throws MqttException {
        recyclerView = findViewById(R.id.recyclerView);
        syncToRemote = findViewById(R.id.syncToRemote);
        remoteSwitch = findViewById(R.id.remoteSwitch);
        localSwitch = findViewById(R.id.localSwitch);
        remoteStatus = findViewById(R.id.remoteStatus);
        localStatus = findViewById(R.id.localStatus);
//        clientId = imei + "-" + Constants.MQTT_CLIENT_ID;
        clientId = Constants.MQTT_CLIENT_ID;

        remoteSwitch.setChecked(false);
        localSwitch.setChecked(false);

        //Set Click Listeners
        syncToRemote.setOnClickListener(this);
        remoteSwitch.setOnClickListener(this);
        localSwitch.setOnClickListener(this);

        //Init ArrayList
        topicMessageModels = new ArrayList<>();
        setUpAdapter();

//        StartLocalMQTTConnection();
//        StartRemoteMQTTConnection();
    }

    /**
     * Setup adapter
     */
    private void setUpAdapter() {
        adapter = new TopicMessageAdapter(topicMessageModels, getApplicationContext());
        linearLayout = new LinearLayoutManager(getApplicationContext());
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(linearLayout);
        recyclerView.setHasFixedSize(true);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View view) {
        //Switch
        switch (view.getId()) {
            case R.id.remoteSwitch:
                checkRemoteSwitch();
                break;

            case R.id.localSwitch:
                checkLocalSwitch();
                break;
            case R.id.syncToRemote:

                if (isRemoteMQTTConnected) {
                    Timber.e("syncToRemote1: %s", isRemoteMQTTConnected);
                    checkTopicModelList();
                } else {
                    Timber.e("Not is Remote Connected");
                }
//                checkRemoteSwitch();
                Toasty.success(getApplicationContext(), " Sync started...", Toast.LENGTH_SHORT).show();

                break;
            default:
                Toasty.success(getApplicationContext(), " Another item clicked...", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    //Check Remote connection
    private void validateRemoteConnection() throws MqttException {
        if (isRemoteMQTTConnected && remoteSwitch.isChecked()) {

            Timber.e("validateRemoteConnection active");
            if (isRemoteMQTTConnected && mqttAndroidClientRemote.isConnected()) {
                checkRemoteSwitch();
//                subscribeToAllTopics();
                Timber.e("validateRemoteConnection: %s", isLocalMQTTConnected);
                Toasty.error(getApplicationContext(), " Successfully connected", Toasty.LENGTH_SHORT).show();
            } else if (!mqttAndroidClientLocal.isConnected() && !isLocalMQTTConnected) {
                checkRemoteSwitch();
                Toasty.error(getApplicationContext(), "Try checking if your Mosquitto on Termux is running", Toasty.LENGTH_SHORT).show();
            } else if (mqttAndroidClientLocal == null) {
                checkRemoteSwitch();
                Toasty.error(getApplicationContext(), "Client is Null", Toasty.LENGTH_SHORT).show();
            }
        } else {
            checkRemoteSwitch();
        }
    }

    //Check Local Connection
    private void validateLocalConnection() {
        if (isLocalMQTTConnected && localSwitch.isChecked()) {

            Timber.e("validateLocalConnection active");
            if (isLocalMQTTConnected && mqttAndroidClientLocal.isConnected()) {
//                checkLocalSwitch();
//                subscribeToAllTopics();
                Timber.e("validateLocalConnection: %s", isLocalMQTTConnected);
                Toasty.error(getApplicationContext(), " Successfully connected", Toasty.LENGTH_SHORT).show();
            } else if (!mqttAndroidClientLocal.isConnected() && !isLocalMQTTConnected) {
//                checkLocalSwitch();
                Toasty.error(getApplicationContext(), "Try checking if your Mosquitto on Termux is running", Toasty.LENGTH_SHORT).show();
            } else if (mqttAndroidClientLocal == null) {
//                checkLocalSwitch();
                Toasty.error(getApplicationContext(), "Client is Null", Toasty.LENGTH_SHORT).show();
            }
        } else {
            Toasty.error(getApplicationContext(), "Client status not known", Toasty.LENGTH_SHORT).show();
//            checkLocalSwitch();
        }
    }

    /**
     * Subscribe to all topics
     */
    private void subscribeToAllTopics() {
        String topic = Constants.DEFAULT_TOPIC;
        int qos = Constants.DEFAULT_QOS_LEVEL;
        if (isLocalMQTTConnected) {
            try {
                iMqttToken = mqttAndroidClientLocal.subscribe(topic, qos);
                iMqttToken.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Timber.e("Topics %s", Arrays.toString(asyncActionToken.getTopics()));
                        Timber.e("Subscription to all topics complete");
                        String serverURI = asyncActionToken.getClient().getServerURI().substring(6);
                        Toasty.success(getApplicationContext(), "Subscribing to All topics on " + serverURI, Toasty.LENGTH_SHORT).show();
                        subscribedToAllTopics = true;

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e("iMqttToken onFailure: ", exception.getMessage());

                    }
                });
            } catch (MqttException mqttException) {
                mqttException.printStackTrace();
            }
        } else {
            Toasty.error(getApplicationContext(), "Seems like you are not connected to the local broker", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Publish from Local by changing serverURI
     */
    private void publishFromLocalMqtt(String topic, MqttMessage message) {
        if (isLocalMQTTConnected) {
            try {
                iMqttToken = mqttAndroidClientLocal.publish(topic, message);
                iMqttToken.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        //Handle OK status
                        Timber.e("Publish from Local MQTT %s", asyncActionToken.getClient().getServerURI() + " Successfully completed");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Timber.e("Publish from Local MQTT %s", asyncActionToken.getClient().getServerURI() + " Failed to publish");
                    }
                });
            } catch (MqttException e) {
                e.printStackTrace();
            }
        } else {
            try {
                StartLocalMQTTConnection();
                checkLocalSwitch();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Start connection to Local MQTT
     */
    private void StartLocalMQTTConnection() throws MqttException {
        mqttAndroidClientLocal = new MqttClient(getApplicationContext(), Constants.LOCAL_MQTT_SERVER, clientId);
        mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setUserName(Constants.MQTT_USER_NAME);
        mqttConnectOptions.setPassword(Constants.MQTT_USER_PASSWORD.toCharArray());
        mqttCallbackExtendedLocal = new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Timber.e("connectComplete Local %s", serverURI);
                Timber.e("StartRemoteMQTTConnection connectComplete isLocalMQTTConnected: %s", isLocalMQTTConnected);
                isLocalMQTTConnected = true;
                subscribeToAllTopics();
                localSwitch.setChecked(true);
                localStatus.setText("Connected");
                Toasty.success(getApplicationContext(), "Local Client Connected", Toasty.LENGTH_SHORT).show();
            }

            @Override
            public void connectionLost(Throwable cause) {
//                Timber.e("connectionLost %s", cause.getLocalizedMessage());
                isLocalMQTTConnected = false;
                Timber.e("StartLocalMQTTConnection connectionLost: %s", isLocalMQTTConnected);
                Toasty.success(getApplicationContext(), "Local Client Disconnected", Toasty.LENGTH_SHORT).show();
                localSwitch.setChecked(false);
                localStatus.setText("Connect");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
//                topicMessageModels.clear();
                Gson json = new Gson();
                byte[] encodedPayload;

                String data = json.toJson(Arrays.toString(message.getPayload()));
                topicMessageModel = new TopicMessageModel(topic, message.toString()); // json.fromJson(data, TopicMessageModel.class);
                topicData = topicMessageModel.getTopic();
                messageData = topicMessageModel.getMessage();

                //Add object to a set
                topicModelSet = new HashSet<>();
                topicModelSet.add(topicMessageModel);

                //Add to List
                int i = topicModelSet.size();
                topicMessageModels.addAll(topicModelSet);

//                for (TopicMessageModel y : topicMessageModels){
//                    checkRemoteSwitch();
//                    if (isRemoteMQTTConnected){
//                        String transmitMessage = y.getMessage();
//                        testPublish(transmitMessage);
//                    } else {
//                        Timber.e("Connect first");
//                    }
//                }


                encodedPayload = data.getBytes(StandardCharsets.UTF_8);
                MqttMessage mqttMessage = new MqttMessage(encodedPayload);
                Timber.e("messageArrived Local topicMessageModel %s", messageData);
//                topicMessageModels.add(topicMessageModel);
                Timber.e("messageArrived Local topicMessageModels %s", topicMessageModels);

                Timber.e("messageArrived Local %s", message);
                String timeAgo = TimeAgo.getTimeAgo(System.currentTimeMillis());
                Timber.e("messageArrived Local TimeAgo %s", timeAgo);

                //Publish data
//                publishFromLocalMqtt(topic, mqttMessage);

//                publishObjectToRemote(topic, message);
                Timber.e("messageArrived Local ServerUrl %s", mqttAndroidClientLocal.getServerURI());

                adapter.notifyDataSetChanged();
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                Timber.e("deliveryComplete %s", token);
            }
        };
        mqttAndroidClientLocal.setCallback(mqttCallbackExtendedLocal);
        mqttAndroidClientLocal.connect(mqttConnectOptions);
    }

    /**
     * Start connection to Remote Host
     */
    private void StartRemoteMQTTConnection() throws MqttException {
        mqttAndroidClientRemote = new MqttClient(getApplicationContext(), Constants.REMOTE_MQTT_SERVER, clientId);
        mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setCleanSession(false);
//        mqttConnectOptions.setUserName(Constants.MQTT_USER_NAME);
//        mqttConnectOptions.setPassword(Constants.MQTT_USER_PASSWORD.toCharArray());
        mqttCallbackExtendedRemote = new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Timber.e("connectComplete Remote %s", serverURI);
                Timber.e("StartRemoteMQTTConnection connectComplete isRemoteMQTTConnected: %s", isRemoteMQTTConnected);
                isRemoteMQTTConnected = true;
                remoteStatus.setText("Connected");
                remoteSwitch.setChecked(true);
                checkTopicModelList();
                Toasty.success(getApplicationContext(), "Remote Client Connected", Toasty.LENGTH_SHORT).show();
            }

            @Override
            public void connectionLost(Throwable cause) {
                Timber.e("StartRemoteMQTTConnection connectionLost isRemoteMQTTConnected: %s", isRemoteMQTTConnected);
                isRemoteMQTTConnected = false;
                remoteStatus.setText("Connect");
                remoteSwitch.setChecked(false);
                Toasty.success(getApplicationContext(), "Remote Client Disconnected", Toasty.LENGTH_SHORT).show();
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                Timber.e("messageArrived Remote doc %s", message.toString());
                //TODO: publish topic and message

//                if (!topicMessageModels.isEmpty()){
//                    Gson json = new Gson();
//                    //Not Empty
//                    Toasty.success(getApplicationContext(), "Data %s " + message, Toast.LENGTH_SHORT ).show();
//                    MqttMessage messageContent = new MqttMessage(message.getPayload());
//                    Timber.e("messageArrived Remote %s", messageContent);
//                    TopicMessageModel topicMessageModel = json.fromJson(message.toString(), TopicMessageModel.class);
//                    data = json.toJson(topicMessageModel);
//
//                    String topicContent = topicMessageModel.getTopic();
//                    String prefix = imei + "-";
//                    Timber.e("messageArrived Remote %s", prefix);
//                    Timber.e("messageArrived Remote Topic %s", topicContent);
//
//                    publishObjectToRemote(topic, message);
//                } else {
//                    //Empty
//                    Toasty.error(getApplicationContext(), "No topics to sync", Toast.LENGTH_SHORT).show();
//                }

//                iMqttToken = mqttAndroidClientRemote.connect(mqttConnectOptions);
                Timber.e("StartRemoteMQTTConnection messageArrived status %s", isRemoteConnected);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

                if (token.isComplete()) {
                    Timber.e("deliveryComplete %s", token.getMessageId());
                }
            }
        };
        mqttAndroidClientRemote.setCallback(mqttCallbackExtendedRemote);
        mqttAndroidClientRemote.connect(mqttConnectOptions);
    }

    /**
     * Check List items
     */
    private void checkTopicModelList() {
        if (!topicMessageModels.isEmpty()) {
//            topicMessageModels.forEach(model -> {
//                testPublish(model.getMessage());
//            });
            prepareDataToPublish();
        } else {
            Timber.e("No items in the topics list");
        }

    }

    /**
     * Publish TopicModel Object to remote
     */
    private void publishObjectToRemote(String topicContent, MqttMessage message1Content) throws MqttException {
        if (isRemoteConnected && mqttAndroidClientRemote.isConnected()) {
            Timber.e("Publish client connected");
            try {
                iMqttToken = mqttAndroidClientRemote.publish(topicContent, message1Content);
                iMqttToken.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Timber.e("Published successfully");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Timber.e("Publish failed due to %s", exception.getMessage());
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (!isRemoteMQTTConnected && !mqttAndroidClientRemote.isConnected()) {
            //Start connection
            StartRemoteMQTTConnection();

            if (isRemoteConnected && mqttAndroidClientRemote.isConnected()) {
                checkRemoteSwitch();
                iMqttToken = mqttAndroidClientRemote.publish(topicContent, message1Content);
                iMqttToken.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Timber.e("Publish client connected");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Timber.e("Publish client onFailure %s", exception.getMessage());
                    }
                });
            } else {
                Timber.e("Publish client NOT connected1");
            }
        }
    }

    /**
     * Test Publish
     */
    private void testPublish() {
        if (mqttAndroidClientRemote != null) {
            //TODO: Handle TopicModels List items instead of dummy data
            prepareDataToPublish();
//            iMqttToken.setActionCallback(new IMqttActionListener() {
//                @Override
//                public void onSuccess(IMqttToken asyncActionToken) {
//                    Timber.e("isRemoteSuccessful: %s", mqttAndroidClientRemote.isConnected());
//                    Timber.e("Published data 7");
//                }
//
//                @Override
//                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
//                    Timber.e("Exception: %s", exception.getMessage());
//                }
//            });
        } else {
            Timber.e("mqttAndroidClientRemote is Null");
        }
    }

    private void prepareDataToPublish() {
        if (!topicMessageModels.isEmpty()) {
            for (int i = 0; i <= topicMessageModels.size() - 1; i++) {
                TopicMessageModel model = topicMessageModels.get(i);
                String topic = model.getTopic();
                String message = model.getMessage();

                try {
                    mqttAndroidClientRemote.publish("_OVES/Android/" + imei, new MqttMessage(message.getBytes()));
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /////////////////////////////////////////////////

    private void checkRemoteSwitch() {
//        try{
//            StartRemoteMQTTConnection();
//        } catch (MqttException e) {
//            e.printStackTrace();
//        }
        StartRemoteProcess();
        if (isRemoteMQTTConnected && mqttAndroidClientRemote != null) {
            if (remoteSwitch.isChecked()) {
                Timber.e("Connected and remote switch checked");
                Timber.e("isRemoteMQTTConnected: %s", isRemoteMQTTConnected);
                Timber.e("mqttAndroidClientRemote.isConnected() : %s", mqttAndroidClientRemote.isConnected());
                remoteSwitch.setChecked(true);
                remoteStatus.setText("Connecting");
//                testPublish("Mqqt");
            } else {
                Timber.e("Connected and remote switch UnChecked");
                Timber.e("isRemoteMQTTConnected else: %s", isRemoteMQTTConnected);
                Timber.e("mqttAndroidClientRemote.isConnected() else : %s", mqttAndroidClientRemote.isConnected());
                EndRemoteProcess();
                remoteSwitch.setChecked(false);
                remoteStatus.setText("Connect");

            }
        } else if (mqttAndroidClientRemote == null) {
            Timber.e("Not Connected");
            Timber.e("mqttAndroidClientRemote: %s", null);
            Timber.e("isRemoteMQTTConnected: %s", isRemoteMQTTConnected);
            Timber.e("mqttAndroidClientRemote.isConnected() : %s", mqttAndroidClientRemote.isConnected());
            remoteSwitch.setChecked(false);
            remoteStatus.setText("Connect");

        } else if (!isRemoteMQTTConnected) {
            Timber.e("Not Connected");
            Timber.e("isRemoteMQTTConnected: %s", isRemoteMQTTConnected);
            Timber.e("mqttAndroidClientRemote.isConnected() : %s", mqttAndroidClientRemote.isConnected());
            remoteSwitch.setChecked(false);
            remoteStatus.setText("Connect");
        }

        ///////////////////////////////////////
//        if (remoteSwitch.isChecked()){
//            //TODO: Create method to handle isChecked
//            StartRemoteProcess();
//            Timber.e("Remote switch Checked isRemoteMQTTConnected1 %s", isRemoteMQTTConnected);
//
//            if (isRemoteMQTTConnected){
//                if (isRemoteMQTTConnected && mqttAndroidClientRemote.isConnected() && mqttAndroidClientRemote != null){
//                    //Connected
//                    Timber.e("Remote switch Checked isRemoteMQTTConnectedCR1 %s", isRemoteMQTTConnected);
//                }
//                else if(!isRemoteMQTTConnected && mqttAndroidClientRemote == null){
//                    //NOt Connected
//                    Timber.e("Remote switch Checked isRemoteMQTTConnectedCR2 %s", isRemoteMQTTConnected);
//                }
//                else if (mqttAndroidClientRemote == null){
//                    //Start process and recheck
//                    Timber.e("Remote switch Checked isRemoteMQTTConnectedCR3 %s", isRemoteMQTTConnected);
//                }
//                else {
//                    Timber.e("Remote switch Checked isRemoteMQTTConnectedCR4 %s", isRemoteMQTTConnected);
//
//                }
//            } else {
//                Timber.e("Remote switch Checked isRemoteMQTTConnectedCR5 %s", isRemoteMQTTConnected);
//                try {
//                    StartRemoteMQTTConnection();
//                } catch (MqttException e) {
//                    e.printStackTrace();
//                }
//            }
//
//            //Check if Local client is null and disconnected
////            if ( isRemoteMQTTConnected ){
////                if ( mqttAndroidClientRemote.isConnected() && mqttAndroidClientRemote != null){
////                    Timber.e("Remote switch Checked isRemoteMQTTConnected2 %s", isRemoteMQTTConnected);
////                    //TODO: Publish messages from the topicMessageModels
////                    Timber.e("Publishing data....");
////                }
////                else if (!isRemoteMQTTConnected && mqttAndroidClientRemote == null){
////                    Timber.e("Remote switch Checked isRemoteMQTTConnected3 %s", isRemoteMQTTConnected);
////                    try {
////                        StartRemoteMQTTConnection();
////                    } catch (MqttException e) {
////                        e.printStackTrace();
////                    }
////                }
////                else {
////                    Timber.e("Remote switch Checked isRemoteMQTTConnected4 %s", isRemoteMQTTConnected);
////                }
////            } else {
////                Timber.e("MQTT Local is not nul or is connected");
////            }
//
//        }
//        else {
//            Timber.e("Remote switch Checked isRemoteMQTTConnected5 %s", isRemoteMQTTConnected);
//            EndRemoteProcess();
//            Timber.e("Remote switch Checked isRemoteMQTTConnected6 %s", isRemoteMQTTConnected);
//            Toasty.success(getApplicationContext(), "checkRemoteSwitch Client Disconnected ", Toasty.LENGTH_SHORT).show();
//        }
    }

    private void checkLocalSwitch() {
        if (localSwitch.isChecked()) {
            Timber.e("Local switch Checked isLocalMQTTConnected");
            StartLocalProcess();
            //Checked
            Timber.e("Local switch Checked isLocalMQTTConnected %s", isLocalMQTTConnected);

            if (isLocalMQTTConnected && mqttAndroidClientLocal.isConnected() && mqttAndroidClientLocal != null) {
                Timber.e("Local switch Checked isLocalMQTTConnected1 %s", isLocalMQTTConnected);
                if (subscribedToAllTopics) {
                    //
                    Timber.e("Already Subscribed");
                } else {
                    Timber.e("Subscribing to All topics");
                    subscribeToAllTopics();
                }
            } else if (!isLocalMQTTConnected && mqttAndroidClientLocal == null) {
                Timber.e("Local switch Checked isLocalMQTTConnected2 %s", isLocalMQTTConnected);
                try {
                    StartLocalMQTTConnection();
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            } else {
                Timber.e("Local switch Checked isLocalMQTTConnected3 %s", isLocalMQTTConnected);
            }

        } else {
            Timber.e("Local switch UnChecked isLocalMQTTConnected %s", isLocalMQTTConnected);
            EndLocalProcess();
            Timber.e("Local switch UnChecked2 isLocalMQTTConnected %s", isLocalMQTTConnected);
            Toasty.success(getApplicationContext(), "Client Disconnected", Toasty.LENGTH_SHORT).show();

        }
    }

    private void EndRemoteProcess() {
        Timber.e("EndRemoteProcess: Remote switch UnChecked isLocalMQTTConnected %s", isRemoteMQTTConnected);

        if (isRemoteMQTTConnected) {
            Timber.e("EndRemoteProcess isRemoteMQTTConnected: isRemoteMQTTConnected %s", isLocalMQTTConnected);
            disconnectRemoteConnection();
            remoteSwitch.setChecked(false);
            remoteStatus.setText("Connect");
        } else {
            Timber.e("EndRemoteProcess: Already Disconnected....");
            Timber.e("EndRemoteProcess else: isRemoteMQTTConnected %s", isLocalMQTTConnected);
        }
    }

    private void EndLocalProcess() {
        Timber.e("EndLocalProcess: Local switch UnChecked isLocalMQTTConnected %s", isLocalMQTTConnected);
        if (isLocalMQTTConnected && mqttAndroidClientLocal.isConnected()) {
            disconnectLocalConnection();
        } else {
            Timber.e("EndLocalProcess else: Local switch UnChecked isLocalMQTTConnected %s", isLocalMQTTConnected);
        }
    }

    private void StartLocalProcess() {
        Timber.e("StartLocalProcess: Local switch isLocalMQTTConnected %s", isLocalMQTTConnected);
//        try {
//            StartLocalMQTTConnection();
//        } catch (MqttException e) {
//            e.printStackTrace();
//        }

        if (isLocalMQTTConnected) {
            Timber.e("StartLocalProcess isLocalMQTTConnected: Local switch UnChecked isLocalMQTTConnectedXX %s", isLocalMQTTConnected);

        } else {
            Timber.e("StartLocalProcess !isLocalMQTTConnected: Local switch isLocalMQTTConnectedYY %s", isLocalMQTTConnected);
            try {
                StartLocalMQTTConnection();
                if (isLocalMQTTConnected && mqttAndroidClientLocal.isConnected() && mqttAndroidClientLocal != null) {
                    StartLocalMQTTConnection();
                } else if (mqttAndroidClientLocal == null) {
                    Timber.e("StartLocalProcess: Local switch isLocalMQTTConnected1 %s", isLocalMQTTConnected);
                    StartLocalMQTTConnection();
                } else if (!isLocalMQTTConnected && !mqttAndroidClientLocal.isConnected() && mqttAndroidClientLocal == null) {
                    Timber.e("StartLocalProcess: Local switch isLocalMQTTConnectedX %s", isLocalMQTTConnected);
                } else if (!isLocalMQTTConnected && !mqttAndroidClientLocal.isConnected()) {
                    Timber.e("StartLocalProcess: Local switch isLocalMQTTConnectedY %s", isLocalMQTTConnected);
                    try {
                        StartLocalMQTTConnection();
                        if (isLocalMQTTConnected) {
                            Timber.e("StartLocalProcess: Local switch UnChecked isLocalMQTTConnectedYZX %s", isLocalMQTTConnected);
//                            subscribeToAllTopics();
                        } else {
                            Timber.e("StartLocalProcess: Local switch UnChecked isLocalMQTTConnectedYZX else %s", isLocalMQTTConnected);
                        }

                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                } else {
                    Timber.e("StartLocalProcess: Local switch isLocalMQTTConnectedZ %s", isLocalMQTTConnected);
                }
            } catch (MqttException e) {
                e.printStackTrace();
            }

        }
    }

    private void StartRemoteProcess() {
        Timber.e("StartRemoteProcess1: Remote switch isRemoteMQTTConnected %s", isRemoteMQTTConnected);
        try {
            StartRemoteMQTTConnection();
        } catch (MqttException e) {
            e.printStackTrace();
        }

        if (isRemoteMQTTConnected) {
            Timber.e("StartRemoteProcess2x: isLocalMQTTConnected: Remote switch UnChecked isLocalMQTTConnectedXX %s", isRemoteMQTTConnected);
//            testPublish("Hello MQTT");
            checkTopicModelList();
        } else {
            Timber.e("StartRemoteProcess3x: !isLocalMQTTConnected: Remote switch isRemoteMQTTConnected %s", isRemoteMQTTConnected);
            try {
                StartRemoteMQTTConnection();
                if (isRemoteMQTTConnected && mqttAndroidClientRemote.isConnected() && mqttAndroidClientRemote != null) {
                    Timber.e("StartRemoteProcess4x:: I got you bug");
                } else if (mqttAndroidClientRemote == null) {
                    Timber.e("StartRemoteProcess5x: Remote switch isRemoteMQTTConnected %s", isRemoteMQTTConnected);
                    StartRemoteMQTTConnection();
                } else if (!mqttAndroidClientRemote.isConnected() && mqttAndroidClientRemote == null) {
                    Timber.e("StartRemoteProcess6x: Remote switch isRemoteMQTTConnected %s", isRemoteMQTTConnected);
                } else if (!mqttAndroidClientRemote.isConnected()) {
                    Timber.e("StartRemoteProcess7x: Remote switch isRemoteMQTTConnected %s", isRemoteMQTTConnected);
                    try {
                        StartRemoteMQTTConnection();
                        if (isRemoteMQTTConnected) {
                            Timber.e("StartRemoteProcess8x: Remote switch UnChecked isRemoteMQTTConnected %s", isRemoteMQTTConnected);
//                            subscribeToAllTopics();
                        } else {
                            Timber.e("StartRemoteProcess9x: Remote switch UnChecked isRemoteMQTTConnected else %s", isRemoteMQTTConnected);
                        }

                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                } else {
                    Timber.e("StartLocalProcess10x: Remote switch isRemoteMQTTConnected %s", isRemoteMQTTConnected);
                }
            } catch (MqttException e) {
                e.printStackTrace();
            }

        }
    }

    ////////////////////////////////////////////////
    @Override
    protected void onResume() {
        super.onResume();
//        startService(new Intent(this, MqttConnectionManagerService.class));
    }

    /**
     * Check device IMEI
     */
    private void checkDeviceIMEI() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                // READ_PHONE_STATE permission has not been granted.
                requestReadPhoneStatePermission();

                return;
            }
            imei = telephonyManager.getDeviceId();

        } else {
            // READ_PHONE_STATE permission has already been granted.
            doPermissionGrantedStuffs();
            imei = telephonyManager.getImei();
            Timber.e("IMEI Number %s", imei);
        }
    }

    /**
     * Alert User
     */
    private void alertAlert(String msg) {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Permission Request")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do somthing here
                    }
                })
                .setIcon(R.drawable.ic_launcher_background)
                .show();
    }

    public void doPermissionGrantedStuffs() {
        //Have an  object of TelephonyManager
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //Get IMEI Number of Phone  //////////////// for this example i only need the IMEI
        @SuppressLint("HardwareIds") String IMEINumber = tm.getDeviceId();

        /************************************************
         * **********************************************
         * This is just an icing on the cake
         * the following are other children of TELEPHONY_SERVICE
         *
         //Get Subscriber ID
         String subscriberID=tm.getDeviceId();

         //Get SIM Serial Number
         String SIMSerialNumber=tm.getSimSerialNumber();

         //Get Network Country ISO Code
         String networkCountryISO=tm.getNetworkCountryIso();

         //Get SIM Country ISO Code
         String SIMCountryISO=tm.getSimCountryIso();

         //Get the device software version
         String softwareVersion=tm.getDeviceSoftwareVersion()

         //Get the Voice mail number
         String voiceMailNumber=tm.getVoiceMailNumber();


         //Get the Phone Type CDMA/GSM/NONE
         int phoneType=tm.getPhoneType();

         switch (phoneType)
         {
         case (TelephonyManager.PHONE_TYPE_CDMA):
         // your code
         break;
         case (TelephonyManager.PHONE_TYPE_GSM)
         // your code
         break;
         case (TelephonyManager.PHONE_TYPE_NONE):
         // your code
         break;
         }

         //Find whether the Phone is in Roaming, returns true if in roaming
         boolean isRoaming=tm.isNetworkRoaming();
         if(isRoaming)
         phoneDetails+="\nIs In Roaming : "+"YES";
         else
         phoneDetails+="\nIs In Roaming : "+"NO";


         //Get the SIM state
         int SIMState=tm.getSimState();
         switch(SIMState)
         {
         case TelephonyManager.SIM_STATE_ABSENT :
         // your code
         break;
         case TelephonyManager.SIM_STATE_NETWORK_LOCKED :
         // your code
         break;
         case TelephonyManager.SIM_STATE_PIN_REQUIRED :
         // your code
         break;
         case TelephonyManager.SIM_STATE_PUK_REQUIRED :
         // your code
         break;
         case TelephonyManager.SIM_STATE_READY :
         // your code
         break;
         case TelephonyManager.SIM_STATE_UNKNOWN :
         // your code
         break;

         }
         */
    }

    /**
     * Requests the READ_PHONE_STATE permission. If the permission has been denied previously, a
     * dialog will prompt the user to grant the permission, otherwise it is requested directly.
     */
    private void requestReadPhoneStatePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.READ_PHONE_STATE)) {
            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // For example if the user has previously denied the permission.
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Permission Request")
                    .setMessage(getString(R.string.permission_read_phone_state_rationale))
                    .setCancelable(false)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            //re-request
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.READ_PHONE_STATE},
                                    MY_PERMISSIONS_REQUEST_READ_PHONE_STATE);
                        }
                    })
                    .setIcon(R.drawable.ic_launcher_background)
                    .show();
        } else {
            // READ_PHONE_STATE permission has not been granted yet. Request it directly.
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE},
                    MY_PERMISSIONS_REQUEST_READ_PHONE_STATE);
        }
    }

    /**
     * Disconnect Local Connection
     */
    private void disconnectLocalConnection() {
        try {
            if (!(mqttAndroidClientLocal == null) && isLocalMQTTConnected && mqttAndroidClientLocal.isConnected()) {
                IMqttToken disconnectLocalMQTTToken = mqttAndroidClientLocal.disconnect();
                disconnectLocalMQTTToken.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        //
                        Timber.e("Local Client DISCONNECTED!");
                        String localClientId = mqttAndroidClientLocal.getClientId();
                        String localClientURI = mqttAndroidClientLocal.getServerURI();

                        Timber.e("Local Client Id is %s", localClientId);
                        Timber.e("Local Client URI is %s", localClientURI);
//                        mqttAndroidClientLocal.unregisterResources();
//                        if (mqttAndroidClientLocal != null){
//                            Timber.e("Nullifying Local Client Instance");
//                            mqttAndroidClientLocal = null;
//                        }
                        Timber.e("disconnectLocalConnection onSuccess: isLocalMQTTConnected %s", isLocalMQTTConnected);
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Timber.e("Local Client FAILED to DISCONNECT! due to %s", exception.getMessage());
                    }
                });
//                mqttAndroidClientLocal = null;
                Timber.e("disconnectLocalConnection: isLocalMQTTConnected %s", isLocalMQTTConnected);
                Toasty.success(getApplicationContext(), "Client Disconnecting", Toasty.LENGTH_SHORT).show();
            } else {
                Timber.e("disconnectLocalConnection: isLocalMQTTConnectedFr %s", isLocalMQTTConnected);
                Toasty.success(getApplicationContext(), "Local Already Disconnected", Toasty.LENGTH_SHORT).show();
            }
            isLocalMQTTConnected = false;
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * Disconnect Local Connection
     */
    private void disconnectRemoteConnection() {
        try {
            if (!(mqttAndroidClientRemote == null) && isRemoteMQTTConnected & mqttAndroidClientRemote.isConnected()) {
                IMqttToken disconnectRemoteMQTTToken = mqttAndroidClientRemote.disconnect();
                disconnectRemoteMQTTToken.setActionCallback(new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Timber.e("Remote Client DISCONNECTED!");
                        String remoteClientId = mqttAndroidClientRemote.getClientId();
                        String remoteClientURI = mqttAndroidClientRemote.getServerURI();

                        Timber.e("Remote Client Id is %s", remoteClientId);
                        Timber.e("Remote Client URI is %s", remoteClientURI);
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Timber.e("Remote Client FAILED to DISCONNECT! due to %s", exception.getMessage());
                    }
                });
                Toasty.success(getApplicationContext(), "Client Disconnecting", Toasty.LENGTH_SHORT).show();
            } else {
                Timber.e("disconnectLocalConnection: isRemoteMQTTConnected %s", isRemoteMQTTConnected);
                Toasty.success(getApplicationContext(), "Remote Already Disconnected", Toasty.LENGTH_SHORT).show();
            }
            isRemoteMQTTConnected = false;
        } catch (MqttException e) {
            Timber.e("Exception %s", e.getMessage());
        }
    }
}