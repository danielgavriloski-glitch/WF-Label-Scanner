package mk.td.booking;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.app.AlertDialog;

public class MainActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setDefaultTextEncodingName("UTF-8");
        web.addJavascriptInterface(new Bridge(), "TDNative");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("Во ред", (d, w) -> result.confirm()).setOnCancelListener(d -> result.confirm()).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("Да", (d, w) -> result.confirm()).setNegativeButton("Не", (d, w) -> result.cancel()).setOnCancelListener(d -> result.cancel()).show();
                return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    public class Bridge {
        @JavascriptInterface public void printContract() {
            runOnUiThread(() -> {
                PrintManager manager = (PrintManager)getSystemService(Context.PRINT_SERVICE);
                manager.print("TD Booking договор", web.createPrintDocumentAdapter("TD Booking договор"), null);
            });
        }
    }
    @Override public void onBackPressed() {
        if(web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
