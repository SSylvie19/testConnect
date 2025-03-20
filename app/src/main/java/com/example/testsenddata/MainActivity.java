package com.example.testsenddata;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private MqttAndroidClient mqttClient;
    // Sử dụng giao thức TCP cho Paho: "tcp://host:port"
    private final String serverUri = "tcp://demo.thingsboard.io:1883";
    private final String deviceToken = "j91lb4oxo2s6iwj4k9br";
    private final String telemetryTopic = "v1/devices/me/telemetry";
    private Button button;
    private boolean isReconnecting = false;
    private MqttConnectOptions mqttConnectOptions;

    private float lat = 21.123456f;
    private float lon = 11.123456f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Tạo client với clientId duy nhất
        String clientId = UUID.randomUUID().toString();
        mqttClient = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);

        // Cấu hình các tùy chọn kết nối
        mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setCleanSession(true);
        // Với ThingsBoard, token thiết bị được dùng làm username
        mqttConnectOptions.setUserName(deviceToken);

        connectToThingsBoard();

        button = findViewById(R.id.button);
        button.setOnClickListener(view -> sendTelemetry(lat++, lon++));
    }

    private void connectToThingsBoard() {
        try {
            mqttClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d("MQTT", "Connected to ThingsBoard");
                }
                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e("MQTT", "Failed to connect to ThingsBoard: " + exception.getMessage());
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void sendTelemetry(double lat, double lon) {
        if (!mqttClient.isConnected()) {
            Log.w("MQTT", "MQTT client not connected. Attempting to reconnect...");
            if (!isReconnecting) {
                reconnect(() -> sendTelemetry(lat, lon));
            }
            return;
        }
        String payload = String.format("{\"latitude\": %.6f, \"longitude\": %.6f}", lat, lon);
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1); // QoS 1: At least once
        try {
            mqttClient.publish(telemetryTopic, message, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d("MQTT", "Telemetry published: " + payload);
                }
                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e("MQTT", "Failed to publish telemetry: " + exception.getMessage());
                    // Nếu gặp lỗi kết nối, thử reconnect và gửi lại telemetry
                    if ((exception.getMessage().contains("Session expired") ||
                            exception.getMessage().contains("not connected")) && !isReconnecting) {
                        reconnect(() -> sendTelemetry(lat, lon));
                    }
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // Định nghĩa interface callback cho reconnect
    private interface ReconnectCallback {
        void onReconnected();
    }

    private void reconnect(ReconnectCallback callback) {
        if (mqttClient.isConnected() || isReconnecting) {
            return;
        }
        isReconnecting = true;
        try {
            mqttClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    isReconnecting = false;
                    Log.d("MQTT", "Reconnected to ThingsBoard");
                    if (callback != null) {
                        callback.onReconnected();
                    }
                }
                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    isReconnecting = false;
                    Log.e("MQTT", "Reconnection failed: " + exception.getMessage());
                }
            });
        } catch (MqttException e) {
            isReconnecting = false;
            e.printStackTrace();
        }
    }
}
