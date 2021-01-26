package com.proton.ecgcard.connector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.proton.ecgcard.connector.callback.DataListener;
import com.proton.ecgcard.connector.data.parse.CardBleDataParse;
import com.proton.ecgcard.connector.data.parse.IBleDataParse;
import com.proton.ecgcard.connector.data.uuid.CardUUID;
import com.proton.ecgcard.connector.data.uuid.IDeviceUUID;
import com.proton.ecgcard.connector.utils.AppUtils;
import com.proton.ecgcard.connector.utils.BleUtils;
import com.proton.encrypt.EncryptHelper;
import com.wms.ble.BleOperatorManager;
import com.wms.ble.bean.ScanResult;
import com.wms.ble.callback.OnConnectListener;
import com.wms.ble.callback.OnReadCharacterListener;
import com.wms.ble.callback.OnScanListener;
import com.wms.ble.callback.OnSubscribeListener;
import com.wms.ble.callback.OnWriteCharacterListener;
import com.wms.ble.operator.IBleOperator;
import com.wms.ble.utils.ScanManager;
import com.wms.logger.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by 王梦思 on 2017/7/7.
 * ble设备管理器
 */
@SuppressLint("StaticFieldLeak")
public class EcgCardManager {
    /**
     * 设备管理器
     */
    private static final Map<String, EcgCardManager> mEcgCardManager = new HashMap<>();
    private static Context mContext;
    private static com.wms.ble.callback.OnScanListener mScanCallback;
    /**
     * 服务：心电
     */
    private String serviceDataEcg;
    /**
     * 服务:设备信息服务
     */
    private String serviceDeviceInfo;
    /**
     * 特征：双手触摸通知（可读可订阅）
     */
    private String characterDoubleTouch;
    /**
     * 特征：心电（可读可订阅）
     */
    private String characterDataEcg;
    /**
     * 特征:电量（可读可订阅）
     */
    private String characterBattery;
    /**
     * 特征:设备版本号（可读）
     */
    private String characterVersion;
    /**
     * 特征:序列号（可读）
     */
    private String characterSerial;
    /**
     * 特征：体脂（可读可订阅）
     */
    private String characterBfr;
    private final IBleOperator mBleOperator;
    /**
     * 接受数据监听器
     */
    private DataListener mDataListener;
    /**
     * 连接监听器
     */
    private OnConnectListener mConnectListener;
    /**
     * 数据解析
     */
    private IBleDataParse dataParse = new CardBleDataParse();
    /**
     * 设备uuid数据提供者
     */
    private IDeviceUUID deviceUUID = new CardUUID();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /**
     * 当前触摸模式
     */
    private int mCurrentTouchMode;
    private final List<Integer> bfrList = new ArrayList<>();
    /**
     * 体制电阻
     */
    private int bfr;
    /**
     * 包序号
     */
    private double mLastPackageNum;
    /**
     * 已经注册了ecg数据通知，防止多次注册
     */
    private boolean hasSubscriptEcgData;
    /**
     * 当前硬件版本
     */
    private String mCurrentHardVersion;
    private final String macAddress;
    private OnScanListener mCheckDeviceIsUpdateModeCallback;

    private EcgCardManager(String macAddress) {
        mBleOperator = BleOperatorManager.getInstance();
        this.macAddress = macAddress;
        initUUID();
    }

    public static void init(Context context) {
        mContext = context.getApplicationContext();
        BleOperatorManager.init(mContext);
        //初始化日志
        Logger.newBuilder()
                .tag("ecgCard_connector")
                .showThreadInfo(false)
                .methodCount(1)
                .methodOffset(5)
                .context(mContext)
                .deleteOnLaunch(true)
                .isDebug(BuildConfig.DEBUG)
                .build();
    }

    public static EcgCardManager getInstance(String macAddress) {
        if (mContext == null) {
            throw new IllegalStateException("You should initialize EcgCardManager before using,You can initialize in your Application class");
        }
        if (!mEcgCardManager.containsKey(macAddress)) {
            mEcgCardManager.put(macAddress, new EcgCardManager(macAddress));
        }
        return mEcgCardManager.get(macAddress);
    }

    /**
     * 扫描心电贴的设备
     */
    public static void scanDevice(final OnScanListener listener) {
        scanDevice(10000, listener);
    }

    /**
     * 扫描心电卡设备
     */
    public static void scanDevice(int scanTime, final OnScanListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("yout should set a scan listener,or you can not receive data");
        }
        mScanCallback = new MyScanCallback(listener);
        ScanManager.getInstance().scan(mScanCallback, scanTime, "BLE_ECG", "OAD ECG");
    }

    /**
     * 停止搜索
     */
    public static void stopScan() {
        ScanManager.getInstance().stop(mScanCallback);
    }

    /**
     * 检查设备是否是升级模式
     */
    private void checkDeviceIsUpdateMode() {
        mCheckDeviceIsUpdateModeCallback = new MyScanCallback(new OnScanListener() {
            @Override
            public void onDeviceFound(ScanResult result) {
                if (result.getMacaddress().equals(macAddress)) {
                    if ("OAD ECG".equals(result.getName())) {
                        if (mConnectListener != null) {
                            mConnectListener.onDeviceNeedUpdate();
                        }
                    } else {
                        mBleOperator.connect(macAddress);
                    }
                    stopScan();
                }
            }

            @Override
            public void onScanCanceled() {
                mBleOperator.connect(macAddress);
            }

            @Override
            public void onScanStopped() {
                mBleOperator.connect(macAddress);
            }
        });
        scanDevice(5000, mCheckDeviceIsUpdateModeCallback);
    }

    /**
     * 心电卡连接操作
     */
    public void connectEcgCard(final OnConnectListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("connect listener can not be null");
        }
        if (mDataListener == null) {
            throw new IllegalArgumentException("you must set receiverDataLister before you connect" +
                    ",if you do not want receive data,you can use other method");
        }
        this.mConnectListener = listener;
        mBleOperator.setConnectListener(new OnConnectListener() {

            @Override
            public void onConnectSuccess(ScanResult result) {
                //连接成功，订阅数据
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getBattery()
                                .getTouchMode()
                                .subscribeTouchModeNotification()
                                .subscribeEcgDataNotification()
                                .getSerial()
                                .getHardVersion();
                    }
                }, 1000);
                if (mConnectListener != null) {
                    mConnectListener.onConnectSuccess();
                }
            }

            @Override
            public void onConnectFaild() {
                if (mConnectListener != null) {
                    mConnectListener.onConnectFaild();
                }
            }

            @Override
            public void onDisconnect(boolean isManual) {
                if (mConnectListener != null) {
                    mConnectListener.onDisconnect(isManual);
                }
            }
        });
        checkDeviceIsUpdateMode();
    }

    /**
     * 向触摸特征值中写数据
     */
    public void writeTouchMode(byte[] value, OnWriteCharacterListener onWriteCharacterListener) {
        mBleOperator.write(macAddress, serviceDataEcg, characterDoubleTouch, value, onWriteCharacterListener);
    }

    /**
     * 断开连接
     */
    public void disConnect() {
        disConnect(true);
    }

    /**
     * 断开连接
     */
    public void disConnect(boolean isClearListener) {
        clear(isClearListener);
        if (mCheckDeviceIsUpdateModeCallback != null) {
            ScanManager.getInstance().stop(mCheckDeviceIsUpdateModeCallback);
        }
        mBleOperator.disConnect(macAddress);
    }

    /**
     * 清空信息
     */
    public void clear(boolean isClearListener) {
        mLastPackageNum = 0;
        hasSubscriptEcgData = false;
        bfr = 0;
        bfrList.clear();
        if (isClearListener) {
            mConnectListener = null;
            mDataListener = null;
        }
        mCurrentTouchMode = 0;
    }

    /**
     * 订阅通知
     */
    private void subscribeNotification(String uuidService, final String uuid) {
        mBleOperator.subscribeNotification(macAddress, uuidService, uuid, new OnSubscribeListener() {
            @Override
            public void onNotify(String uuid, byte[] value) {
                if (uuid.equalsIgnoreCase(characterDoubleTouch)) {
                    parseDoubleTouchData(value);
                } else if (uuid.equalsIgnoreCase(characterDataEcg)) {
                    parseEcgData(value);
                } else if (uuid.equalsIgnoreCase(characterBfr)) {
                    parseBFRData(value);
                } else if (uuid.equalsIgnoreCase(characterBattery)) {
                    parseBattery(value);
                }
            }

            @Override
            public void onSuccess() {
                Logger.w("订阅通知成功");
            }

            @Override
            public void onFail() {
                Logger.w("订阅通知失败");
                if (uuid.equalsIgnoreCase(characterDataEcg)) {
                    hasSubscriptEcgData = false;
                }
            }
        });
    }

    /**
     * 解析电量
     */
    private void parseBattery(final byte[] value) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mDataListener != null) {
                    Integer battery = dataParse.parseBattery(value);
                    Logger.w("电量:", battery);
                    mDataListener.receiveBattery(battery);
                }
            }
        });
    }

    /**
     * 解析体脂
     */
    private void parseBFRData(final byte[] value) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                bfr = dataParse.parseBFRData(value);
                bfrList.add(bfr);
                if (mDataListener != null) {
                    mDataListener.receiveBFR(bfr);
                }
            }
        });
    }

    /**
     * 解析双手触摸数据
     */
    private void parseDoubleTouchData(final byte[] value) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                mCurrentTouchMode = value[0];
                Logger.w("touch mode = ", value[0]);
                if (mDataListener != null) {
                    mDataListener.receiveTouchMode(dataParse.parseDoubleTouchData(value));
                }
                if (mCurrentTouchMode == 0) {
                    clear(false);
                }
            }
        });
    }

    /**
     * 解析ecg数据
     */
    private void parseEcgData(byte[] value) {
        if (mDataListener == null) {
            return;
        }
        //解析包序号，v1.1.5及其以上才有包序号
        if (!TextUtils.isEmpty(mCurrentHardVersion)
                && AppUtils.compareVersion(mCurrentHardVersion, "V1.1.5") >= 0) {
            int packageNum = BleUtils.getPackageNum(value);
            //上一包序号和当前序号一致则取消解析
            if (mLastPackageNum != 0) {
                if (packageNum == mLastPackageNum) {
                    return;
                }
            }
            mLastPackageNum = packageNum;
            mDataListener.receivePackageNum(packageNum);
        }

        mDataListener.receiveEcgRawData(EncryptHelper.encryptData(dataParse.parseEcgData(value), 500));
    }

    /**
     * 订阅触摸模式通知
     */
    public EcgCardManager subscribeTouchModeNotification() {
        subscribeNotification(serviceDataEcg, characterDoubleTouch);
        return this;
    }

    /**
     * 订阅ecg数据通知
     */
    public EcgCardManager subscribeEcgDataNotification() {
        if (hasSubscriptEcgData) {
            return this;
        }
        hasSubscriptEcgData = true;
        subscribeNotification(serviceDataEcg, characterDataEcg);
        return this;
    }

    /**
     * 订阅体脂通知
     */
    public EcgCardManager subscribeBFRNotification() {
        subscribeNotification(serviceDataEcg, characterBfr);
        return this;
    }

    /**
     * 订阅电量通知
     */
    public EcgCardManager subscribeBatteryNotification() {
        subscribeNotification(serviceDataEcg, characterBattery);
        return this;
    }

    /**
     * 设置数据接受监听器，只能有一个监听器
     */
    public EcgCardManager setDataListener(DataListener dataListener) {
        this.mDataListener = dataListener;
        return this;
    }

    /**
     * 获取体脂率电阻
     */
    public EcgCardManager getBfr() {
        mBleOperator.read(macAddress, serviceDataEcg, characterBfr, new OnReadCharacterListener() {
            @Override
            public void onFail() {
                Logger.w("获取体脂率电阻失败");
            }

            @Override
            public void onSuccess(byte[] data) {
                parseBFRData(data);
            }
        });
        return this;
    }

    /**
     * 获取触摸模式
     */
    public EcgCardManager getTouchMode() {
        mBleOperator.read(macAddress, serviceDataEcg, characterDoubleTouch, new OnReadCharacterListener() {
            @Override
            public void onSuccess(byte[] data) {
                parseDoubleTouchData(data);
            }

            @Override
            public void onFail() {
                Logger.w("获取触摸模式失败");
            }
        });
        return this;
    }

    /**
     * 获取硬件版本
     */
    public EcgCardManager getHardVersion() {
        mBleOperator.read(macAddress, serviceDeviceInfo, characterVersion, new OnReadCharacterListener() {
            @Override
            public void onSuccess(byte[] data) {
                String hardVersion = dataParse.parseHardVersion(data);
                mCurrentHardVersion = hardVersion;
                Logger.w("固件版本:", mCurrentHardVersion);
                if (mDataListener != null) {
                    mDataListener.receiveHardVersion(hardVersion);
                }
            }

            @Override
            public void onFail() {
                Logger.w("获取固件版本失败");
            }
        });
        return this;
    }

    /**
     * 获取电量
     */
    public EcgCardManager getBattery() {
        mBleOperator.read(macAddress, serviceDataEcg, characterBattery, new OnReadCharacterListener() {
            @Override
            public void onSuccess(byte[] data) {
                parseBattery(data);
            }

            @Override
            public void onFail() {
                Logger.w("获取电量失败");
            }
        });
        return this;
    }

    /**
     * 获取硬件序列号
     */
    public EcgCardManager getSerial() {
        mBleOperator.read(macAddress, serviceDeviceInfo, characterSerial, new OnReadCharacterListener() {
            @Override
            public void onSuccess(byte[] data) {
                String sn = dataParse.parseSerial(data);
                Logger.w("序列号:", sn);
                if (mDataListener != null) {
                    mDataListener.receiveSerial(sn);
                }
            }

            @Override
            public void onFail() {
                Logger.w("获取序列号失败");
            }
        });
        return this;
    }

    /**
     * 设置数据解析策略，可动态设置
     */
    public EcgCardManager setDataParseStrategy(IBleDataParse dataParse) {
        if (dataParse == null) {
            throw new IllegalArgumentException("data parse startegy can not be null");
        }
        this.dataParse = dataParse;
        return this;
    }

    /**
     * 设置uuid加载策略，动态提供uuid
     */
    public EcgCardManager setDeviceUUIDStrategy(IDeviceUUID deviceUUID) {
        if (deviceUUID == null) {
            throw new IllegalArgumentException("device uuid startegy can not be null");
        }
        this.deviceUUID = deviceUUID;
        initUUID();
        return this;
    }

    public int getBFR() {
        return bfr;
    }

    public int getCurrentTouchMode() {
        return mCurrentTouchMode;
    }

    public List<Integer> getBfrList() {
        return bfrList;
    }

    /**
     * 初始化uuid
     */
    private void initUUID() {
        serviceDataEcg = deviceUUID.getEcgServiceUUID();
        serviceDeviceInfo = deviceUUID.getDeviceInfoServiceUUID();
        characterBattery = deviceUUID.getCharacterBatteryUUID();
        characterDataEcg = deviceUUID.getCharacterDataEcgUUID();
        characterBfr = deviceUUID.getCharacterBfrUUID();
        characterSerial = deviceUUID.getCharacterSearialUUID();
        characterVersion = deviceUUID.getCharacterVersionUUID();
        characterDoubleTouch = deviceUUID.getCharacterDoubleTouchUUID();
    }

    public EcgCardManager setConnectListener(OnConnectListener mConnectListener) {
        this.mConnectListener = mConnectListener;
        return this;
    }

    public static class MyScanCallback extends com.wms.ble.callback.OnScanListener {
        private final OnScanListener listener;

        MyScanCallback(OnScanListener listener) {
            this.listener = listener;
        }

        @Override
        public void onScanStart() {
            listener.onScanStart();
        }

        @Override
        public void onDeviceFound(ScanResult result) {
            if ("OAD ECG".equals(result.getName())) {
                result.setMacaddress(BleUtils.getMacaddressByBroadcast(result.getScanRecord()));
            }
            listener.onDeviceFound(result);
        }

        @Override
        public void onScanStopped() {
            listener.onScanStopped();
        }

        @Override
        public void onScanCanceled() {
            listener.onScanCanceled();
        }
    }
}
