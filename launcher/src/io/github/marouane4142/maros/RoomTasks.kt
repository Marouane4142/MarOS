package io.github.marouane4142.maros

import android.content.Context
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.*

/** Long operations acknowledge immediately; the paired owner can poll the bounded task result. */
object RoomTasks {
    private data class Task(val created: Long, @Volatile var state: String = "queued", @Volatile var message: String = "En attente")
    private val tasks = ConcurrentHashMap<String, Task>()
    private val executor = ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,ArrayBlockingQueue<Runnable>(6)) { r -> Thread(r,"MarOS-RoomTask").apply { isDaemon=true } }.apply { allowCoreThreadTimeOut(true) }
    @JvmStatic @Synchronized fun start(c: Context, request: JSONObject): JSONObject {
        val action=request.getString("action");require(action in setOf("scene","watch_refresh","photos","cast","drive_backup")) { "Action inconnue" }
        if(action=="scene")require(request.getString("scene") in setOf("cinema","music","game","night"))
        val now=System.currentTimeMillis();tasks.entries.removeIf { now-it.value.created>3600000 }
        if(tasks.size>=16) tasks.entries.filter { it.value.state in setOf("done","error") }.minByOrNull { it.value.created }?.let { tasks.remove(it.key) }
        require(tasks.size<16) { "Trop de tâches ; réessayez plus tard" }
        val id=UUID.randomUUID().toString();val task=Task(now);tasks[id]=task
        try{executor.execute{
            task.state="running";task.message="En cours"
            try{task.message=when(action){"scene"->RoomData.runScene(c.applicationContext,request.getString("scene"));"watch_refresh"->Watchlist(c).refresh();"photos"->ConnectedFeatures.importImmich(c);"drive_backup"->GoogleLink.uploadBackup(c);else->RoomData.testCast()};task.state="done"}
            catch(e:Exception){task.message=e.message?:"Action indisponible";task.state="error"}
        }}catch(e:RejectedExecutionException){tasks.remove(id);throw IllegalStateException("Une opération est déjà en attente")}
        return status(id)
    }
    @JvmStatic fun status(id:String):JSONObject{val task=tasks[id]?:throw IllegalArgumentException("Tâche inconnue ou expirée");return JSONObject().put("id",id).put("state",task.state).put("message",task.message)}
}
