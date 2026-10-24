package mk.wf.labelscanner

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source

/** Shared by administrator and employee screens; existing Terminal attendance remains compatible. */
class WorkPresenceFeed(private val db: FirebaseFirestore) {
    private fun snapshot(employeeId: String, query: QuerySnapshot): WorkPresenceSnapshot {
        val events = query.documents.map { document ->
            PresenceEvent(document.id, document.getString("employeeId") ?: "", document.getString("type") ?: "",
                (document.get("timestamp") as? Timestamp)?.toDate()?.time,
                document.getString("source") ?: "", document.getString("enteredBy") ?: "")
        }
        val pending = query.metadata.hasPendingWrites()
        val unresolved = events.any { it.type in AttendancePresence.types && it.at == null }
        return AttendancePresence.reduce(employeeId, events).copy(
            confirmed = !query.metadata.isFromCache && !pending && !unresolved,
            pending = pending,
            error = if (unresolved && !pending) "Има запис без потврдено време. Провери ја евиденцијата." else null
        )
    }

    fun watch(employeeId: String, update: (WorkPresenceSnapshot) -> Unit): ListenerRegistration {
        var last = WorkPresenceSnapshot()
        // Equality-only query: no new composite Firestore index and no global history limit.
        return db.collection("attendance").whereEqualTo("employeeId", employeeId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { query, error ->
                last = if (error != null || query == null) last.copy(confirmed = false,
                    error = "Статусот не е потврден: ${error?.localizedMessage ?: "нема одговор"}")
                else snapshot(employeeId, query)
                update(last)
            }
    }

    fun readServer(employeeId: String): Task<WorkPresenceSnapshot> = db.collection("attendance")
        .whereEqualTo("employeeId", employeeId).get(Source.SERVER).continueWith { result ->
            if (!result.isSuccessful) throw (result.exception ?: IllegalStateException("Нема серверска потврда за статусот."))
            snapshot(employeeId, checkNotNull(result.result))
        }
}
