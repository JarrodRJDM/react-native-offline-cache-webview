package im.shimo.react.webview;

import android.content.Context;
import android.os.Build;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import com.facebook.common.logging.FLog;
import com.facebook.react.common.ReactConstants;
import com.facebook.react.common.build.ReactBuildConfig;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.views.webview.ReactWebViewManager;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.webview.WebViewConfig;

public class AdvancedWebViewManager extends ReactWebViewManager {

    private static final String REACT_CLASS = "RNAdvancedWebView";
    private static final String BRIDGE_NAME = "__REACT_WEB_VIEW_BRIDGE";

    private WebViewConfig mWebViewConfig;

    public AdvancedWebViewManager() {
        super();
        mWebViewConfig = new WebViewConfig() {
            public void configWebView(WebView webView) {
            }
        };
    }

    protected static class AdvancedWebView extends ReactWebView {
        private boolean mMessagingEnabled = false;
        private InputMethodManager mInputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        public AdvancedWebView(ThemedReactContext reactContext) {
            super(reactContext);
        }

        private class ReactWebViewBridge {
            ReactWebView mContext;

            ReactWebViewBridge(ReactWebView c) {
                mContext = c;
            }

            @JavascriptInterface
            public void postMessage(String message) {
                mContext.onMessage(message);
            }

            @JavascriptInterface
            public void showKeyboard() {
                requestFocus();
                mInputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
            }
        }

        @Override
        public void setMessagingEnabled(boolean enabled) {
            if (mMessagingEnabled == enabled) {
                return;
            }

            mMessagingEnabled = enabled;
            if (enabled) {
                addJavascriptInterface(new AdvancedWebView.ReactWebViewBridge(this), BRIDGE_NAME);
                linkBridge();
            } else {
                removeJavascriptInterface(BRIDGE_NAME);
            }
        }

        @Override
        public void linkBridge() {
            if (mMessagingEnabled) {
                if (ReactBuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    // See isNative in lodash
                    String testPostMessageNative = "String(window.postMessage) === String(Object.hasOwnProperty).replace('hasOwnProperty', 'postMessage')";
                    evaluateJavascript(testPostMessageNative, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            if (value.equals("true")) {
                                FLog.w(ReactConstants.TAG, "Setting onMessage on a WebView overrides existing values of window.postMessage, but a previous value was defined");
                            }
                        }
                    });
                }

                loadUrl("javascript:" +
                        "(function () {" +
                        "   function isDescendant(parent, child) {" +
                        "     var node = child.parentNode;" +
                        "     while (node) {" +
                        "         if (node == parent) {" +
                        "             return true;" +
                        "         }" +
                        "         node = node.parentNode;" +
                        "     }" +
                        "     return false;" +
                        "   }" +
                        "   window.originalPostMessage = window.postMessage," +
                        "   window.postMessage = function(data) {" +
                                BRIDGE_NAME + ".postMessage(String(data));" +
                        "   };" +
                        "   var focus = HTMLElement.prototype.focus;" +
                        "   HTMLElement.prototype.focus = function() {" +
                        "       focus.call(this);" +
                        "       var selection = document.getSelection();" +
                        "       var anchorNode = selection && selection.anchorNode;" +
                        "       if (anchorNode && isDescendant(this, anchorNode) || this === anchorNode) {" +
                                    BRIDGE_NAME + ".showKeyboard();" + // Show soft input manually, can't show soft input via javascript
                        "       }" +
                        "   };" +
                        "   document.dispatchEvent(new CustomEvent('ReactNativeContextReady'));" +
                        "})()");
            }
        }
    }


    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected WebView createViewInstance(ThemedReactContext reactContext) {
        ReactWebView webView = new AdvancedWebView(reactContext);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        reactContext.addLifecycleEventListener(webView);
        mWebViewConfig.configWebView(webView);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        // Fixes broken full-screen modals/galleries due to body height being 0.
        webView.setLayoutParams(
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        if (ReactBuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        return webView;
    }

    @ReactProp(name = "allowFileAccessFromFileURLs")
    public void setAllowFileAccessFromFileURLs(WebView root, boolean allows) {
        root.getSettings().setAllowFileAccessFromFileURLs(allows);
    }

}
