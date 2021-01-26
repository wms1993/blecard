package com.proton.ecgcard.connector.utils;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.proton.ecgcard.connector.R;
import com.wms.ble.callback.OnConnectListener;
import com.wms.ble.callback.OnSubscribeListener;
import com.wms.ble.callback.OnWriteCharacterListener;
import com.wms.ble.operator.FastBleOperator;
import com.wms.ble.operator.IBleOperator;
import com.wms.ble.utils.BluetoothUtils;
import com.wms.logger.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Created by 王梦思 on 2018-11-06.
 * <p/>
 */
public class CardFirewareUpdateManager {

    /**
     * 重置服务uuid
     */
    private static final String SERVICE_RESET = "f000ffd0-0451-4000-b000-000000000000";
    /**
     * 特征:重置服务特征
     */
    private static final String CHARACTOR_RESET = "f000ffd1-0451-4000-b000-000000000000";
    /**
     * 固件升级服务
     */
    private static final String SERVICE_UPDATE_FIRMWARE = "f000ffc0-0451-4000-b000-000000000000";
    /**
     * 固件升级特征值(可写)
     */
    private static final String CHARACTOR_UPDATE_WRITE = "f000ffc1-0451-4000-b000-000000000000";
    /**
     * 固件升级回调特征值(可订阅)
     */
    private static final String CHARACTOR_UPDATE_CALLBACK = "f000ffc2-0451-4000-b000-000000000000";
    /**
     * 固件升级状态的mac地址
     */
    private static final String UPDATE_MACADDRESS = "0A:D0:AD:0A:D0:AD";

    private String filePath;
    private String macaddress;
    /**
     * 固件包文件大小
     */
    private long mFileSize;
    private IBleOperator bleOperator;
    private int buffSize = 16;
    private byte[] mFileBytes;
    private long startTime;
    private Context mContext;
    /**
     * 是否使用重置的mac地址升级
     */
    private boolean isUsingResetMacaddress;
    private boolean hasStop;
    private OnFirewareUpdateListener onFirewareUpdateListener;
    private BroadcastReceiver mBluetoothReceive = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                switch (blueState) {
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Logger.w("固件更新:蓝牙关闭");
                        updateFail(getString(R.string.connector_please_open_bluetooth), UpdateFailType.BLUETOOTH_NOT_OPEN);
                        break;
                }
            }
        }
    };

    public CardFirewareUpdateManager(Context context, String filePath, String macaddress) {
        if (context == null) {
            throw new IllegalArgumentException("context can not be null");
        }
        bleOperator = new FastBleOperator(context);
        this.filePath = filePath;
        this.macaddress = macaddress;
        this.mContext = context;
    }

    private static byte[] toByteArray(String filename) {
        File f = new File(filename);
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) f.length());
        BufferedInputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(f));
            int buf_size = 1024;
            byte[] buffer = new byte[buf_size];
            int len;
            while (-1 != (len = in.read(buffer, 0, buf_size))) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public CardFirewareUpdateManager update() {
        if (TextUtils.isEmpty(filePath) || !new File(filePath).exists()) {
            updateFail(getString(R.string.connector_fireware_not_exist), UpdateFailType.FIREWARE_NOT_EXIST);
            return this;
        }
        if (!BluetoothUtils.isBluetoothOpened()) {
            updateFail(getString(R.string.connector_please_open_bluetooth), UpdateFailType.BLUETOOTH_NOT_OPEN);
            BluetoothUtils.openBluetooth();
            return this;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothReceive, filter);
        mFileBytes = toByteArray(filePath);
        mFileSize = mFileBytes.length;
        connectDevice(macaddress);
        return this;
    }

    private void connectDevice(final String macaddress) {
        if (hasStop) {
            return;
        }
        bleOperator.setConnectListener(new OnConnectListener() {
            @Override
            public void onConnectSuccess() {
                if (onFirewareUpdateListener != null) {
                    onFirewareUpdateListener.onConnectSuccess();
                }
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (macaddress.equals(UPDATE_MACADDRESS)) {
                            uploadData();
                        } else {
                            resetDevice();
                        }
                    }
                }, 200);
            }

            @Override
            public void onConnectFaild() {
                Logger.w("连接失败:", macaddress);
                if (macaddress.equals(UPDATE_MACADDRESS)) {
                    if (onFirewareUpdateListener != null) {
                        onFirewareUpdateListener.onConnectFail();
                    }
                    updateFail(getString(R.string.connector_connect_fail), UpdateFailType.CONNECT_FAIL);
                } else {
                    isUsingResetMacaddress = true;
                    connectDevice(UPDATE_MACADDRESS);
                }
            }

            @Override
            public void onDisconnect(boolean isManual) {
                Logger.w("连接断开:", macaddress);
                if (!isManual) {
                    updateFail(getString(R.string.connector_device_disconnect), UpdateFailType.DISCONNECT);
                }
            }
        });
        bleOperator.connect(macaddress);
    }

    /**
     * 上传固件包
     */
    private void uploadData() {
        bleOperator.subscribeNotification(UPDATE_MACADDRESS, SERVICE_UPDATE_FIRMWARE, CHARACTOR_UPDATE_CALLBACK, new OnSubscribeListener() {

            @Override
            public void onSuccess() {
                Logger.w("订阅成功");
                write(0, null);
            }

            @Override
            public void onFail() {
                Logger.w("订阅失败");
                updateFail(getString(R.string.connector_fireware_write_fail), UpdateFailType.SUBSCRIBE_FAIL);
            }

            @Override
            public void onNotify(String s, byte[] bytes) {
                String countString = BleUtils.bytesToHexString(bytes).substring(0, 4);
                int index = Integer.parseInt(countString.substring(2, 4) + countString.substring(0, 2), 16);
                write(index, bytes);
            }
        });

        startTime = System.currentTimeMillis();
    }

    private void write(final int index, byte[] data) {
        if (hasStop) {
            stopUpdate();
            return;
        }
        final float progress = (float) index * buffSize / mFileSize;
        byte[] temp = new byte[buffSize];
        System.arraycopy(mFileBytes, buffSize * index, temp, 0, buffSize);
        byte[] writeData;
        if (data != null) {
            writeData = new byte[data.length + temp.length];
            System.arraycopy(data, 0, writeData, 0, data.length);
            System.arraycopy(temp, 0, writeData, data.length, temp.length);
        } else {
            writeData = temp;
        }
        bleOperator.writeNoRsp(UPDATE_MACADDRESS, SERVICE_UPDATE_FIRMWARE, data == null ? CHARACTOR_UPDATE_WRITE : CHARACTOR_UPDATE_CALLBACK, writeData, new OnWriteCharacterListener() {
            @Override
            public void onFail() {
                updateFail(getString(R.string.connector_fireware_write_fail), UpdateFailType.FIREWARE_WRITE_FAIL);
            }
        });

        //这个回调不放到写入成功是因为有时候会出现最后一包不回调导致提示升级失败
        if (onFirewareUpdateListener != null) {
            onFirewareUpdateListener.onProgress(progress);
            if (index == mFileSize / buffSize - 1) {
                onFirewareUpdateListener.onSuccess(isUsingResetMacaddress ? UPDATE_MACADDRESS : macaddress);
                Logger.w("升级总耗时:", (System.currentTimeMillis() - startTime));
            }
        }
    }

    /**
     * 重置设备,设备重置成功后,设备重启，蓝牙地址会变成0A:D0:AD:0A:D0:AD
     */
    private void resetDevice() {
        bleOperator.write(macaddress, SERVICE_RESET, CHARACTOR_RESET, BleUtils.hexStringToBytes("01"), new OnWriteCharacterListener() {
            @Override
            public void onSuccess() {
                bleOperator.disConnect(macaddress);
                connectDevice(UPDATE_MACADDRESS);
            }

            @Override
            public void onFail() {
                updateFail(getString(R.string.connector_reset_fail), UpdateFailType.RESET_FAIL);
            }
        });
    }

    public void stopUpdate() {
        hasStop = true;
        try {
            if (mBluetoothReceive != null) {
                mContext.unregisterReceiver(mBluetoothReceive);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (bleOperator != null) {
            bleOperator.disConnect(macaddress);
            bleOperator.disConnect(UPDATE_MACADDRESS);
        }
    }

    private void updateFail(String msg, UpdateFailType type) {
        if (onFirewareUpdateListener != null) {
            onFirewareUpdateListener.onFail(msg, type);
        }
        stopUpdate();
    }

    public void setOnFirewareUpdateListener(OnFirewareUpdateListener onFirewareUpdateListener) {
        this.onFirewareUpdateListener = onFirewareUpdateListener;
    }

    private String getString(int stringRes) {
        return mContext.getString(stringRes);
    }

    public enum UpdateFailType {
        /**
         * 连接中断
         */
        DISCONNECT,
        /**
         * 固件不存在
         */
        FIREWARE_NOT_EXIST,
        /**
         * 蓝牙没有打开
         */
        BLUETOOTH_NOT_OPEN,
        /**
         * 连接失败
         */
        CONNECT_FAIL,
        /**
         * 订阅失败
         */
        SUBSCRIBE_FAIL,
        /**
         * 固件写入失败
         */
        FIREWARE_WRITE_FAIL,
        /**
         * 重置设备失败
         */
        RESET_FAIL,
    }

    public interface OnFirewareUpdateListener {

        void onConnectSuccess();

        void onConnectFail();

        void onSuccess(String macaddress);

        void onFail(String msg, UpdateFailType type);

        void onProgress(float progress);
    }
}
