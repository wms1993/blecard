package com.proton.ecgcard.connector.utils;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 王梦思 on 2017/7/5.
 */
public class BleUtils {

    private static final int ECG_START = 16;
    private static final int ECG_DX = 12;

    /**
     * Convert hex string to byte[]
     *
     * @param hexString the hex string
     * @return byte[]
     */
    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    public static String bytes2BinaryString(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : bytes) {
            stringBuilder.append(Integer.toBinaryString((b & 0xFF) + 0x100).substring(1));
        }
        return stringBuilder.toString();
    }

    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }

        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    public static List<Float> getEcgData(String s) {
        List<Float> list = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            list.add(getRate(s, i));
        }
        return list;
    }

    /**
     * 获取包序号
     */
    public static int getPackageNum(byte[] value) {
        int first = getFirstByteInt(bytes2BinaryString(value));
        int second = getSecondByteInt(bytes2BinaryString(value));
        return first + second * 256;
    }

    private static int getFirstByteInt(String dataStr) {
        try {
            String rateStr = dataStr.substring(0, 8);
            return getBinaryIntNum(rateStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static int getSecondByteInt(String dataStr) {
        try {
            String rateStr = dataStr.substring(8, 16);
            return getBinaryIntNum(rateStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private static float getRate(String dataStr, int i) {
        try {
            String rateStr = dataStr.substring(ECG_START + i * ECG_DX, ECG_START + (i + 1) * ECG_DX);
            rateStr = reverseStr(rateStr);
            int tempV = getBinaryIntNum(rateStr);
            return getV(tempV);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0f;
    }

    private static float getV(int data) {
        return (data * 4.3f / 4096f - 1.27f) * 5f;
    }

    private static String reverseStr(String orginalStr) {
        if (TextUtils.isEmpty(orginalStr)) {
            return "";
        }
        char[] orginalCharArray = orginalStr.toCharArray();
        int start = 0;
        int end = orginalStr.length();
        reverse(orginalCharArray, start, end);
        return new String(orginalCharArray);
    }

    private static void reverse(char[] in, int start, int end) {
        int n = (end - start) / 2;
        for (int i = 0; i < n; i++) {
            char t = in[start + i];
            in[start + i] = in[end - i - 1];
            in[end - i - 1] = t;
        }
    }

    private static int getBinaryIntNum(String temp) {
        int a = 0;
        try {
            a = Integer.parseInt(temp + "", 2);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return a;
    }

    public static int byte2int(byte[] res) {
        // 一个byte数据左移24位变成0x??000000，再右移8位变成0x00??0000
        byte[] bt = new byte[4];
        bt[0] = res[0];
        bt[1] = res[1];
        bt[2] = 0;
        bt[3] = 0;
        int targets = (bt[0] & 0xff) | ((bt[1] << 8) & 0xff00) // | 表示安位或
                | ((bt[2] << 24) >>> 8) | (bt[3] << 24);
        return targets;
    }

    /**
     * 解析广播包中的mac地址
     */
    public static String getMacaddressByBroadcast(byte[] scanRecord) {
        if (scanRecord.length < 7) {
            return "";
        }
        byte[] macbytes = new byte[6];
        System.arraycopy(scanRecord, 4, macbytes, 0, macbytes.length);
        return parseBssid2Mac(bytesToHexString(macbytes)).toUpperCase();
    }

    public static String parseBssid2Mac(String bssid) {
        StringBuilder macbuilder = new StringBuilder();
        for (int i = 0; i < bssid.length() / 2; i++) {
            macbuilder.append(bssid, i * 2, i * 2 + 2).append(":");
        }
        macbuilder.delete(macbuilder.length() - 1, macbuilder.length());
        return macbuilder.toString();
    }
}
