package com.proton.ecgcard.connector.data.parse;

import com.proton.ecgcard.connector.utils.BleUtils;

import java.util.List;

/**
 * Created by 王梦思 on 2017/8/7.
 * 心电卡数据解析
 */

public class CardBleDataParse implements IBleDataParse {
    /**
     * 解析电量
     */
    @Override
    public int parseBattery(byte[] value) {
        return Integer.valueOf(BleUtils.bytes2BinaryString(value), 2);
    }

    @Override
    public String parseHardVersion(byte[] value) {
        return new String(value);
    }

    @Override
    public String parseSerial(byte[] value) {
        return new String(value);
    }

    /**
     * 解析体脂
     */
    @Override
    public int parseBFRData(byte[] value) {
        return BleUtils.byte2int(value);
    }

    /**
     * 解析双手触摸数据
     */
    @Override
    public int parseDoubleTouchData(byte[] value) {
        return value[0];
    }

    /**
     * 解析ecg数据
     */
    @Override
    public List<Float> parseEcgData(byte[] value) {
        return BleUtils.getEcgData(BleUtils.bytes2BinaryString(value));
    }
}
