package com.proton.ecgcard.connector.data.uuid;

/**
 * Created by 王梦思 on 2017/8/7.
 * 心电卡uuid
 */

public class CardUUID implements IDeviceUUID {
    /**
     * 服务：心电
     */
    private static final String SERVICE_DATA_ECG = "0000fff6-0000-1000-8000-00805f9b34fb";
    /**
     * 特征：心电（可读可订阅）
     */
    private static final String CHARACTER_DATA_ECG = "0000fff7-0000-1000-8000-00805f9b34fb";
    /**
     * 特征：体脂（可读可订阅）
     */
    private static final String CHARACTER_BFR = "0000fffa-0000-1000-8000-00805f9b34fb";
    /**
     * 特征：双手触摸通知（可读可写可订阅）
     */
    private static final String CHARACTER_DOUBLE_TOUCH = "0000fff8-0000-1000-8000-00805f9b34fb";
    /**
     * 特征:电量（可读可订阅）
     */
    private static final String CHARACTER_BATTERY = "0000fff9-0000-1000-8000-00805f9b34fb";
    /**
     * 服务:设备信息服务
     */
    private static final String SERVICE_DEVICE_INFO = "0000180a-0000-1000-8000-00805f9b34fb";
    /**
     * 特征:设备版本号（可读）
     */
    private static final String CHARACTER_VERSION = "00002a26-0000-1000-8000-00805f9b34fb";
    /**
     * 特征:序列号（可读）
     */
    private static final String CHARACTER_SEARIAL = "00002a25-0000-1000-8000-00805f9b34fb";

    @Override
    public String getEcgServiceUUID() {
        return SERVICE_DATA_ECG;
    }

    @Override
    public String getDeviceInfoServiceUUID() {
        return SERVICE_DEVICE_INFO;
    }

    @Override
    public String getCharacterDataEcgUUID() {
        return CHARACTER_DATA_ECG;
    }

    @Override
    public String getCharacterBfrUUID() {
        return CHARACTER_BFR;
    }

    @Override
    public String getCharacterVersionUUID() {
        return CHARACTER_VERSION;
    }

    @Override
    public String getCharacterBatteryUUID() {
        return CHARACTER_BATTERY;
    }

    @Override
    public String getCharacterDoubleTouchUUID() {
        return CHARACTER_DOUBLE_TOUCH;
    }

    @Override
    public String getCharacterSearialUUID() {
        return CHARACTER_SEARIAL;
    }
}
