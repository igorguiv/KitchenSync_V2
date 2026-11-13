package com.kitchensync.app

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class Repo {
    val db = Firebase.firestore
    fun fridge(code: String): DocumentReference = db.collection("fridges").document(code)

    fun <T : Any> listen(code: String, name: String, cls: Class<T>, onStatus: (String?) -> Unit = {}): Flow<List<T>> = callbackFlow {
        val reg = fridge(code).collection(name).addSnapshotListener { s, e ->
            if (e != null) { onStatus(e.message) }
            if (s != null) {
                if (!s.metadata.isFromCache) onStatus(null)
                trySend(s.documents.mapNotNull { d -> runCatching { d.toObject(cls) }.getOrNull() })
            }
        }
        awaitClose { reg.remove() }
    }

    fun member(code: String, uid: String): Flow<Member?> = callbackFlow {
        val reg = fridge(code).collection("members").document(uid).addSnapshotListener { s, _ ->
            trySend(s?.toObject(Member::class.java))
        }
        awaitClose { reg.remove() }
    }

    suspend fun create(code: String, uid: String, name: String) {
        fridge(code).set(mapOf("owner" to uid)).await()
        fridge(code).collection("members").document(uid).set(Member(uid, name, true)).await()
    }

    /** returns false if fridge doesn't exist */
    suspend fun join(code: String, uid: String, name: String): Boolean {
        if (!fridge(code).get().await().exists()) return false
        fridge(code).collection("members").document(uid).set(Member(uid, name, false)).await()
        return true
    }

    suspend fun setApproved(code: String, uid: String, ok: Boolean) {
        val d = fridge(code).collection("members").document(uid)
        if (ok) d.update("approved", true).await() else d.delete().await()
    }

    suspend fun leave(code: String, uid: String) { fridge(code).collection("members").document(uid).delete().await() }
    suspend fun put(code: String, col: String, id: String, obj: Any) { fridge(code).collection(col).document(id).set(obj).await() }
    suspend fun del(code: String, col: String, id: String) { fridge(code).collection(col).document(id).delete().await() }
    suspend fun rate(code: String, id: String, uid: String, stars: Long) {
        fridge(code).collection("cooked").document(id).update("ratings.$uid", stars).await()
    }
}
