package com.kuikly.stockchat.android

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.kuikly.stockchat.android.adapter.KRImageAdapter
import com.kuikly.stockchat.android.adapter.KRLogAdapter
import com.kuikly.stockchat.android.adapter.KRRouterAdapter
import com.kuikly.stockchat.android.adapter.KRThreadAdapter
import com.kuikly.stockchat.android.adapter.KRUncaughtExceptionHandlerAdapter
import com.tencent.kuikly.core.render.android.IKuiklyRenderExport
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.css.ktx.toMap
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import com.tencent.kuiklybase.kline.host.registerKuiklyKLineChart
import org.json.JSONObject

/**
 * Kuikly 页面承载容器。每个 Kuikly 页面对应一个 Activity 实例，
 * 由 [KRRouterAdapter] 负责页面间跳转。
 */
class KuiklyRenderActivity : AppCompatActivity(), KuiklyRenderViewBaseDelegatorDelegate {

    private lateinit var containerView: ViewGroup
    private val renderViewDelegator = KuiklyRenderViewBaseDelegator(this)

    private val pageName: String
        get() = intent.getStringExtra(KEY_PAGE_NAME)?.takeIf { it.isNotEmpty() } ?: DEFAULT_PAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kuikly_render)
        setupImmersiveMode()
        containerView = findViewById(R.id.kuikly_container)
        renderViewDelegator.onAttach(containerView, "", pageName, createPageData())
    }

    override fun onResume() {
        super.onResume()
        renderViewDelegator.onResume()
    }

    override fun onPause() {
        super.onPause()
        renderViewDelegator.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        renderViewDelegator.onDetach()
    }

    override fun registerExternalRenderView(kuiklyRenderExport: IKuiklyRenderExport) {
        super.registerExternalRenderView(kuiklyRenderExport)
        kuiklyRenderExport.registerKuiklyKLineChart()
    }

    private fun createPageData(): Map<String, Any> {
        val jsonStr = intent.getStringExtra(KEY_PAGE_DATA)
        val params: MutableMap<String, Any> = if (jsonStr.isNullOrEmpty()) {
            mutableMapOf()
        } else {
            JSONObject(jsonStr).toMap()
        }
        params["platform"] = "android"
        return params
    }

    private fun setupImmersiveMode() {
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    companion object {
        private const val KEY_PAGE_NAME = "pageName"
        private const val KEY_PAGE_DATA = "pageData"
        private const val DEFAULT_PAGE = "StockChat"

        init {
            with(KuiklyRenderAdapterManager) {
                krImageAdapter = KRImageAdapter
                krLogAdapter = KRLogAdapter
                krUncaughtExceptionHandlerAdapter = KRUncaughtExceptionHandlerAdapter
                krRouterAdapter = KRRouterAdapter
                krThreadAdapter = KRThreadAdapter()
            }
        }

        fun start(context: Context, pageName: String, pageData: JSONObject) {
            val starter = Intent(context, KuiklyRenderActivity::class.java)
            starter.putExtra(KEY_PAGE_NAME, pageName)
            starter.putExtra(KEY_PAGE_DATA, pageData.toString())
            context.startActivity(starter)
        }
    }
}
