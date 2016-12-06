// Copyright 2016 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.example.android_webview.ct_workaround;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.webkit.CookieManager;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.Date;

public class WebViewVersionChecker {

    /** Represents a WebView version. Is comparable. */
    private static class WebViewVersion implements Comparable<WebViewVersion> {

        public WebViewVersion(int major, int minor, int branch, int patch) {
            this(new int[]{major, minor, branch, patch});
        }

        private WebViewVersion(int[] components) {
            mComponents = components;
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof WebViewVersion)) {
                return false;
            }
            WebViewVersion that = (WebViewVersion)obj;
            return Arrays.equals(this.mComponents, that.mComponents);
        }

        public int hashCode() {
            return Arrays.hashCode(mComponents);
        }

        public int compareTo(WebViewVersion that) {
            for (int i = 0; i < 4; i++) {
                int diff = this.mComponents[i] - that.mComponents[i];
                if (diff != 0)
                    return diff;
            }
            return 0;
        }

        private int[] mComponents;
    }

    private static final WebViewVersion MIN_CT_BROKEN = new WebViewVersion(53, 0, 0, 0);
    private static final WebViewVersion FIRST_54_RELEASE = new WebViewVersion(54, 0, 2840, 68);
    private static final WebViewVersion SECOND_54_RELEASE = new WebViewVersion(54, 0, 2840, 85);
    private static final WebViewVersion MIN_CT_FIXED = new WebViewVersion(55, 0, 2883, 54);

    /**
     * Return true if the current version of WebView has the certificate transparency problem
     */
    public static boolean currentWebViewHasCTProblem()
    {
        WebViewVersion ourVersion = getWebViewVersion();
        // If we couldn't get the version we're on a non-updatable OS version.
        if (ourVersion == null)
            return false;

        final Date FIRST_54_EXPIRATION_DATE;
        final Date SECOND_54_EXPIRATION_DATE;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        try {
            FIRST_54_EXPIRATION_DATE = format.parse("2016-12-27");
            SECOND_54_EXPIRATION_DATE = format.parse("2017-01-07");
        } catch (ParseException e) {
            // We should never get here, but we'll return false just in case
            return false;
        }

        Date now = new Date();

        if (ourVersion.compareTo(MIN_CT_BROKEN) < 0) {
            return false;
        } else if (ourVersion.compareTo(FIRST_54_RELEASE) < 0) {
            return true;
        } else if (ourVersion.compareTo(SECOND_54_RELEASE) < 0) {
            return now.after(FIRST_54_EXPIRATION_DATE);
        } else if (ourVersion.compareTo(MIN_CT_FIXED) < 0) {
            return now.after(SECOND_54_EXPIRATION_DATE);
        }
        return false;
    }

    private static PackageInfo getWebViewPackageInfo() {
        try {
            // If we're on K or below, just return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                return null;
            // Force WebView provider to be loaded.
            CookieManager.getInstance();
            Class<?> factory = Class.forName("android.webkit.WebViewFactory");
            Field f = factory.getDeclaredField("sPackageInfo");
            f.setAccessible(true);
            return (PackageInfo) f.get(null);
        } catch (Exception e) {
            // just say we don't know the version.
            return null;
        }
    }

    private static final Pattern VERSION_REGEX =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)$");

    private static WebViewVersion getWebViewVersion() {
        PackageInfo pi = getWebViewPackageInfo();
        if (pi == null)
            return null;

        String versionString = pi.versionName;
        Matcher m = VERSION_REGEX.matcher(versionString);
        if (m.matches()) {
            return new WebViewVersion(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
        } else {
            // Unknown version string format
            return null;
        }
    }

    private static final String[] UPDATABLE_PACKAGES = {
            "com.google.android.webview",
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.canary",
            "com.chrome.dev"
    };
    private static final String PLAY_STORE_NAME = "com.android.vending";

    public static boolean hasPlayStore(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PLAY_STORE_NAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    /**
     * Send an intent that will cause the Play Store to launch to the appropriate location for the
     * current device to update WebView.
     * Returns false if this is not possible on the current device,
     * e.g. because WebView is not updatable, or because the device does not have the Play Store.
     */
    public static boolean invokePlayStoreToUpdateWebView(Context context) {
        if (!hasPlayStore(context))
            return false;
        PackageInfo pi = getWebViewPackageInfo();
        if (pi == null)
            return false;
        String webViewPackageName = pi.packageName;
        for (int i=0; i < UPDATABLE_PACKAGES.length; i++) {
            if (UPDATABLE_PACKAGES[i].equals(webViewPackageName)) {
                try {
                    context.startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=" + webViewPackageName)));
                } catch (ActivityNotFoundException e) {
                    // If we reach here, it must mean that something is preventing us from invoking
                    // the Play store, so we'll assume it's unreachable
                    return false;
                }
                return true;
            }
        }
        return false;
    }
}
