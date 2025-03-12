package me.iacn.biliroaming.hook

import android.view.View
import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.utils.*
import java.lang.Math.ceil
import kotlinx.coroutines.*


class AutoLikeHook(classLoader: ClassLoader) : BaseHook(classLoader) {
    private val likedVideos = HashSet<Long>()

    private var timelength = -1L
    private var progress = -1L

    companion object {
        var detail: Pair<Long, Int>? = null
    }

    override fun startHook() {
        if (!sPrefs.getBoolean("auto_like", false)) return

        Log.d("startHook: AutoLike")

        val likeIds = arrayOf(
            "frame_recommend",
            "frame1",
            "frame_like"
        ).map { getId(it) }


        hookPlayViewReplyUnite()


        instance.likeMethod()?.let { likeMethod ->
            instance.sectionClass?.hookAfterAllMethods(likeMethod) { param ->
                val sec = param.thisObject ?: return@hookAfterAllMethods
                GlobalScope.launch(Dispatchers.Default) {
                    if (shouldClickLike()) {
                        withContext(Dispatchers.Main) {
                            val likeView = sec.javaClass.declaredFields.filter {
                                View::class.java.isAssignableFrom(it.type)
                            }.firstNotNullOfOrNull {
                                sec.getObjectFieldOrNullAs<View>(it.name)?.takeIf { v ->
                                    v.id in likeIds
                                }
                            }
                            likeView?.callOnClick()
                        }
                    }
                }
            }
        }
        instance.bindViewMethod()?.let { bindViewMethod ->
            instance.sectionClass?.hookAfterMethod(
                bindViewMethod,
                instance.viewHolderClass,
                instance.continuationClass
            ) { param ->
                GlobalScope.launch(Dispatchers.Default) {
                    if (shouldClickLike()) {
                        withContext(Dispatchers.Main) {
                            val root = param.args[0].callMethodAs<View>(instance.getRootMethod())
                            val likeView = likeIds.firstNotNullOfOrNull { id ->
                                root.findViewById(id)
                            }
                            likeView?.callOnClick()
                        }
                    }
                }
            }
        }
    }


    private fun hookPlayViewReplyUnite() {
        instance.playerMossClass?.hookAfterMethod(
            if (instance.useNewMossFunc) "executePlayViewUnite" else "playViewUnite",
            instance.playViewUniteReqClass
        ) { param ->
            param.result?.let {
                handlePlayViewUniteReply(it)
            }
        }
    }

    private fun handlePlayViewUniteReply(playViewUniteReply: Any) {
        Log.d("hooking: Processing PlayViewUniteReply")
        timelength = playViewUniteReply.callMethod("getVodInfo")?.callMethodAs("getTimelength") ?: -1L
        timelength = ceil(timelength / 1000.0).toLong()
        Log.d("Timelength: $timelength s")
        progress = playViewUniteReply.callMethod("getHistory")?.callMethod("getCurrentVideo")?.callMethodAs("getProgress") ?: -1L
        Log.d("Progress: $progress s")
    }

    private suspend fun shouldClickLike(): Boolean {
        val (aid, like) = detail ?: return false
        if (likedVideos.contains(aid) || like != 0 ) {
            return false
        }
        timeup()
        synchronized(likedVideos) {
            likedVideos.add(aid)
        }
        return true
    }

    private suspend fun timeup() {
        var needTime = ceil(timelength * 0.2).toLong()
        if (progress < needTime) {
            Log.d("needTime:$needTime")
            delay((needTime - progress) * 1000)
        }
    }

}
