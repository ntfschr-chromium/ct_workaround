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

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = new WebView(this.getApplicationContext());
        setContentView(webView);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);

        final Context context = this;

        if (WebViewVersionChecker.currentWebViewHasCTProblem()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(context.getString(R.string.dialog_message))
                    .setCancelable(false)
                    .setPositiveButton(WebViewVersionChecker.hasPlayStore(context)
                            ? context.getString(R.string.go_to_play_store)
                            : context.getString(R.string.will_update_manually),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    WebViewVersionChecker.invokePlayStoreToUpdateWebView(context);
                                }
                            })
                    .setNegativeButton(context.getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Perform whatever action you may need here
                        }
                    });
            builder.create().show();
        }

        // Continue with whatever your app wanted to do
        webView.loadUrl("https://www.google.com");
    }
}
