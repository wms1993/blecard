package com.proton.ecgcard.connector.utils;

import android.text.TextUtils;

public class AppUtils {

    public static int compareVersion(String version1, String version2) {
        if (TextUtils.isEmpty(version1) || TextUtils.isEmpty(version2)) {
            return -1;
        }
        if (version1.equalsIgnoreCase(version2)) {
            return 0;
        }
        version1 = version1.replaceAll("V", "").replace(".", "");
        version2 = version2.replaceAll("V", "").replace(".", "");

        int v1 = Integer.parseInt(version1);
        int v2 = Integer.parseInt(version2);
        if (v1 > v2) {
            return 1;
        } else if (v1 == v2) {
            return 0;
        } else {
            return -1;
        }
    }
}
