package com.proton.ecgcard.connector.callback;

/**
 * 数据接收监听器
 */
public abstract class DataListener {
    /**
     * 接收心电原始数据
     *
     * @param data 加密后
     */
    public void receiveEcgRawData(byte[] data) {
    }

    /**
     * 接收触摸模式数据
     *
     * @param mode 0 松开 1 触摸
     */
    public void receiveTouchMode(int mode) {
    }

    /**
     * 接受到体脂
     */
    public void receiveBFR(Integer bfr) {
    }

    /**
     * 接受包序号
     */
    public void receivePackageNum(int packageNum) {
    }

    /**
     * 读取电量
     */
    public void receiveBattery(Integer battery) {
    }

    /**
     * 读取序列号
     */
    public void receiveSerial(String serial) {
    }

    /**
     * 读取硬件版本号
     */
    public void receiveHardVersion(String hardVersion) {
    }
}