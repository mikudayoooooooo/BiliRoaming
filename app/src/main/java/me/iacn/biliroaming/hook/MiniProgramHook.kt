package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.XposedInit
import me.iacn.biliroaming.utils.Log
import me.iacn.biliroaming.utils.bv2av
import me.iacn.biliroaming.utils.hookBeforeAllConstructors
import java.net.HttpURLConnection
import java.net.URL

class MiniProgramHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    override fun startHook() {
        if (!XposedInit.sPrefs.getBoolean("mini_program", false)) return
        Log.d("startHook: mini program")
        instance.routeParamsClass?.hookBeforeAllConstructors { param ->
            val arg = param.args[1] as android.net.Uri
            if (arg.toString() != "action://share/shareto") return@hookBeforeAllConstructors
            val bundle = param.args[2] as android.os.Bundle
            val extra = bundle.getBundle("default_extra_bundle")
            if (extra?.getString("platform") == "COPY") {
                extra.getString("params_content")?.let { url ->
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.instanceFollowRedirects = false
                    conn.connect()
                    if (conn.responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                        val target = URL(conn.getHeaderField("Location"))
                        val bv = target.path.split("/").first { it.startsWith("BV") && it.length == 12 }
                        if (bv.isEmpty()) return@hookBeforeAllConstructors
                        val av = bv2av(bv)
                        val query = target.query.split("&").map {
                            it.split("=")
                        }.filter {
                            it.size == 2
                        }.filter {
                            it[0] == "p"
                        }.joinToString("") {
                            it.joinToString("")
                        }
                        extra.putString("params_content", "https://b23.tv/av${av}/${query}")
                    }

                }
                return@hookBeforeAllConstructors
            }
            if (extra?.getString("params_type") != "type_min_program") return@hookBeforeAllConstructors
            extra.putString("params_type", "type_web")
            if (extra.getString("params_title") == "哔哩哔哩") {
                extra.putString("params_title", extra.getString("params_content"))
                extra.putString("params_content", "由哔哩漫游分享")
            }
            if (extra.getString("params_content")!!.startsWith("已观看")) {
                extra.putString("params_content", "${extra.getString("params_content")}\n由哔哩漫游分享")
            }
        }
    }
}