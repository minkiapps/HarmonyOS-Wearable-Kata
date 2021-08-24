package com.fprieto.wearable.presentation.ui.slice;

import com.fprieto.wearable.ResourceTable;
import com.fprieto.wearable.model.DataMessage;
import com.fprieto.wearable.model.PlayerCommand;
import com.fprieto.wearable.presentation.ui.slice.joke.JokeAbilitySlice;
import com.fprieto.wearable.util.LogUtils;
import com.google.gson.Gson;
import com.huawei.watch.kit.hiwear.HiWear;
import com.huawei.watch.kit.hiwear.p2p.HiWearMessage;
import com.huawei.watch.kit.hiwear.p2p.P2pClient;
import com.huawei.watch.kit.hiwear.p2p.Receiver;
import com.huawei.watch.kit.hiwear.p2p.SendCallback;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.agp.components.Button;
import ohos.agp.components.Component;
import ohos.agp.components.Component.RotationEventListener;
import ohos.agp.components.Image;
import ohos.agp.components.ScrollView;
import ohos.app.dispatcher.TaskDispatcher;
import ohos.media.image.ImageSource;
import ohos.media.image.PixelMap;
import ohos.multimodalinput.event.RotationEvent;

import java.nio.charset.StandardCharsets;

public class MainAbilitySlice extends AbilitySlice {

    private final static String TAG = "MainAbilitySlice";

    private final static String peerPkgName = "com.fprieto.hms.wearable";
    private final static String peerFinger = "CFCC7E8B7AF0C5B2B488190B17B897BB483541B26A7F15065602D716E586FEDA";
    private static final int FACTOR = 3;
    int rotationEventCount = 0;

    private Image lastReceivedImage;
    private ScrollView scrollView;

    private TaskDispatcher uiDispatcher;
    private P2pClient p2pClient;

    private final Receiver receiver = new Receiver() {
        @Override
        public void onReceiveMessage(HiWearMessage message) {
            final int type = message.getType();

            switch (type) {
                case HiWearMessage.MESSAGE_TYPE_DATA:
                    final String text = new String(message.getData());
                    LogUtils.d(TAG, "Received text: " + text);
                    break;
                case HiWearMessage.MESSAGE_TYPE_FILE:
                    LogUtils.d(TAG, "Received file.");
                    final PixelMap pixelMap = ImageSource.create(message.getFile(),
                            new ImageSource.SourceOptions())
                            .createPixelmap(new ImageSource.DecodingOptions());
                    uiDispatcher.syncDispatch(() -> {
                        lastReceivedImage.setPixelMap(pixelMap);
                    });
                    break;
            }
        }
    };

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_main);

        uiDispatcher = getUITaskDispatcher();

        initP2PClient();
        initViews();
    }

    private void initP2PClient() {
        p2pClient = HiWear.getP2pClient(this);
        p2pClient.setPeerPkgName(peerPkgName);
        p2pClient.setPeerFingerPrint(peerFinger);
        try {
            p2pClient.ping(resultCode -> {
                LogUtils.d(TAG, "ping result = " + resultCode);
            });
        } catch (Exception e) {
            LogUtils.d(TAG, e.getMessage());
        }
    }

    private void initViews() {
        scrollView = (ScrollView) findComponentById(ResourceTable.Id_scrollview_main);

        lastReceivedImage = (Image) findComponentById(ResourceTable.Id_image_received_image);
        final Button healthButton = (Button) findComponentById(ResourceTable.Id_button_health);
        healthButton.setClickedListener(component -> {
            present(new HealthAbilitySlice(), new Intent());
        });

        final Button sendMessageButton = (Button) findComponentById(ResourceTable.Id_button_send_message);
        sendMessageButton.setClickedListener(component -> {
            sendPlayerCommand();
            sendMessageToMobile("Hello from SmartWatch!");
        });

        final Button recordButton = (Button) findComponentById(ResourceTable.Id_button_record_audio);
        recordButton.setClickedListener(component -> {
            present(new RecordAudioAbilitySlice(), new Intent());
        });

        final Button remotePlayerButton = (Button) findComponentById(ResourceTable.Id_button_play_remote_video);
        remotePlayerButton.setClickedListener(component -> {
            present(new RemoteVideoPlayerAbilitySlice(), new Intent());
        });

        final Button playAudioButton = (Button) findComponentById(ResourceTable.Id_button_play_audio);
        playAudioButton.setClickedListener(component -> {
            present(new AudioPlayerAbilitySlice(), new Intent());
        });

        final Button locationButton = (Button) findComponentById(ResourceTable.Id_button_location);
        locationButton.setClickedListener(component -> {
            present(new LocationAbilitySlice(), new Intent());
        });

        final Button jokeButton = (Button) findComponentById(ResourceTable.Id_button_joke);
        jokeButton.setClickedListener(component -> {
            present(new JokeAbilitySlice(), new Intent());
        });

        scrollView.setReboundEffect(true);
        scrollView.setVibrationEffectEnabled(true);
        scrollView.setTouchFocusable(true);
        scrollView.requestFocus();
        scrollView.setRotationEventListener(new RotationEventListener() {
            @Override
            public boolean onRotationEvent(Component component, RotationEvent rotationEvent) {
                if (rotationEvent != null) {
                    float rotationValue = rotationEvent.getRotationValue();
                    if (Math.abs(rotationEventCount) == FACTOR) {
                        int y = scrollView.getScrollValue(1) + rotationEventCount / FACTOR + (rotationValue > 0 ? 10 : -10);
                        scrollView.scrollTo(0, y);
                        rotationEventCount = 0;
                    } else {
                        rotationEventCount += rotationValue > 0 ? -1 : 1;
                    }
                    return true;
                }
                return false;
            }
        });
        scrollView.setVibrationEffectEnabled(true);
    }

    private void sendMessageToMobile(String message) {
        try {
            HiWearMessage.Builder builder = new HiWearMessage.Builder();
            builder.setPayload(message.getBytes(StandardCharsets.UTF_8));
            HiWearMessage msgPayload = builder.build();
            SendCallback sendCallback = i -> {
                LogUtils.d(TAG, "Send result - " + i);
            };
            p2pClient.send(msgPayload, sendCallback);
        } catch (Exception e) {
            LogUtils.e(TAG, e.getMessage());
        }
    }

    private void sendPlayerCommand() {
        PlayerCommand playerCommand = new PlayerCommand();
        playerCommand.setCommand("play");
        DataMessage dataMessage = new DataMessage();
        dataMessage.setMessageType("Player-Command");
        dataMessage.setPlayerCommand(playerCommand);

        Gson gson = new Gson();
        String json = gson.toJson(dataMessage);
        try {
            HiWearMessage.Builder builder = new HiWearMessage.Builder();
            builder.setPayload(json.getBytes(StandardCharsets.UTF_8));
            HiWearMessage msgPayload = builder.build();
            SendCallback sendCallback = i -> {
                LogUtils.d(TAG, "Send Command result - " + i);
            };
            p2pClient.send(msgPayload, sendCallback);
        } catch (Exception e) {
            LogUtils.e(TAG, e.getMessage());
        }
    }

    @Override
    public void onActive() {
        super.onActive();
        p2pClient.registerReceiver(receiver);
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        p2pClient.unregisterReceiver(receiver);
    }

    @Override
    public void onForeground(Intent intent) {
        super.onForeground(intent);
    }

    @Override
    protected void onBackground() {
        super.onBackground();
    }
}
