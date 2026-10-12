/*  Copyright (C) 2026 UltimateGadget contributors

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.URLDecoder

/**
 * Signs in to the Amazfit/Zepp or Xiaomi account inside a WebView so the user can complete whatever
 * the vendor asks — password, OTP, captcha, two-factor — on the vendor's own login page, which the
 * previous email/password handshake could not do. Once logged in, the auth tokens are read straight
 * from the session cookies and handed back; the email and password never pass through the app.
 *
 * This is the browser method the official Gadgetbridge pairing guide documents for Zepp (read the
 * `hm-user-login-info` cookie at watchface.zepp.com), extended to Xiaomi (capture the `passToken`
 * set by account.xiaomi.com, which [XiaomiToken] re-uses to fetch the session without a password).
 *
 * FLAG_SECURE keeps the login out of screenshots/recents, and the cookie jar is wiped on entry so
 * each sign-in is explicit and nothing is left behind afterwards.
 */
class UltimateAccountLoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false

    private val provider: String by lazy { intent.getStringExtra(EXTRA_PROVIDER) ?: PROVIDER_ZEPP }

    private val poll = object : Runnable {
        override fun run() {
            if (!tryCapture()) handler.postDelayed(this, 1200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        val hint = TextView(this).apply {
            text = if (provider == PROVIDER_XIAOMI) {
                "Sign in to your Xiaomi (Mi Fitness) account. The key is captured automatically once " +
                    "verification is complete."
            } else {
                "Sign in to your Amazfit/Zepp account. The key is captured automatically once " +
                    "verification is complete."
            }
            setTextColor(Color.WHITE)
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER
        }
        webView = WebView(this)
        root.addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tryCapture()
            }
        }

        // Wipe any previous session so the user signs in explicitly and nothing lingers afterwards.
        cm.removeAllCookies {
            WebStorage.getInstance().deleteAllData()
            webView.loadUrl(startUrl())
            handler.postDelayed(poll, 1500)
        }
    }

    private fun startUrl(): String = when (provider) {
        PROVIDER_XIAOMI -> "https://account.xiaomi.com/pass/serviceLogin?sid=miothealth&_locale=en_US"
        else -> "https://watchface.zepp.com/"
    }

    /** Reads the session cookies; on success, returns the tokens and finishes. */
    private fun tryCapture(): Boolean {
        if (finished) return true
        val result = when (provider) {
            PROVIDER_XIAOMI -> captureXiaomi()
            else -> captureZepp()
        } ?: return false
        finished = true
        handler.removeCallbacks(poll)
        setResult(Activity.RESULT_OK, result)
        finish()
        return true
    }

    private fun captureZepp(): Intent? {
        val cookies = CookieManager.getInstance().getCookie("https://watchface.zepp.com") ?: return null
        val map = parseCookies(cookies)
        // Preferred: the combined login-info cookie with the full token_info object.
        map["hm-user-login-info"]?.let { raw ->
            runCatching {
                val json = JSONObject(URLDecoder.decode(raw, "UTF-8"))
                val tokenInfo = json.optJSONObject("token_info")
                val userId = tokenInfo?.optString("user_id").orEmpty()
                val appToken = tokenInfo?.optString("app_token").orEmpty()
                if (userId.isNotBlank() && appToken.isNotBlank()) return result(userId, appToken)
            }
        }
        // Fallback: the individual cookies.
        val userId = map["userid"].orEmpty()
        val appToken = map["apptoken"].orEmpty()
        if (userId.isNotBlank() && appToken.isNotBlank()) return result(userId, appToken)
        return null
    }

    private fun captureXiaomi(): Intent? {
        val cookies = CookieManager.getInstance().getCookie("https://account.xiaomi.com") ?: return null
        val map = parseCookies(cookies)
        val passToken = map["passToken"].orEmpty()
        val userId = (map["userId"] ?: map["userid"]).orEmpty()
        if (passToken.isNotBlank() && userId.isNotBlank()) return result(userId, passToken)
        return null
    }

    private fun result(userId: String, token: String): Intent =
        Intent().putExtra(EXTRA_USER_ID, userId).putExtra(EXTRA_TOKEN, token)

    private fun parseCookies(header: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (part in header.split(';')) {
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            out[part.substring(0, idx).trim()] = part.substring(idx + 1).trim()
        }
        return out
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val PROVIDER_ZEPP = "zepp"
        const val PROVIDER_XIAOMI = "xiaomi"
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_USER_ID = "user_id"

        /** App token (Zepp) or passToken (Xiaomi), depending on the provider. */
        const val EXTRA_TOKEN = "token"

        fun intent(context: Context, provider: String): Intent =
            Intent(context, UltimateAccountLoginActivity::class.java).putExtra(EXTRA_PROVIDER, provider)
    }
}
