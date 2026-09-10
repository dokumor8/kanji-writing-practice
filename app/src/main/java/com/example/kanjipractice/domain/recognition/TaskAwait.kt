package com.example.kanjipractice.domain.recognition

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridges a Play Services [Task] to a coroutine.
 *
 * Hand-rolled instead of pulling in kotlinx-coroutines-play-services: it is a
 * dozen lines and the app has exactly one Task-based dependency.
 */
internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { value -> if (cont.isActive) cont.resume(value) }
    addOnFailureListener { error -> if (cont.isActive) cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}
