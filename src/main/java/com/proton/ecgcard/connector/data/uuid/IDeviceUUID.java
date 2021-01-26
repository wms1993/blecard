package com.proton.ecgcard.connector.data.uuid;

/**
 * Created by 王梦思 on 2017/8/7.
 */

public interface IDeviceUUID {
    /**
     * ECG心电数据服务uuid
     */
    String getEcgServiceUUID();

    /**
     * 设备信息服务uuid
     */
    String getDeviceInfoServiceUUID();

    /**
     * ECG心电数据Character uuid
     */
    String getCharacterDataEcgUUID();

    /**
     * 体脂Character uuid
     */
    String getCharacterBfrUUID();

    /**
     * 硬件版本Character uuid
     */
    String getCharacterVersionUUID();

    /**
     * 电量uuid
     */
    String getCharacterBatteryUUID();

    /**
     * 双手触摸uuid
     */
    String getCharacterDoubleTouchUUID();

    /**
     * 序列号uuid
     */
    String getCharacterSearialUUID();
}
