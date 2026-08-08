package com.example.climb.data.social

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class WrongPasswordException : Exception("That current password isn't correct")

class AuthRepository(private val auth: FirebaseAuth) {
    val currentUid: String?
        get() = auth.currentUser?.uid

    /** True only when this account can sign in with a password at all — a Google-only account
     * has no password to change, and Firebase's `updatePassword` would just fail confusingly if
     * called on one, so callers should check this before ever showing a change-password form. */
    val hasPasswordProvider: Boolean
        get() = auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } ?: false

    val currentUserFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        auth.createUserWithEmailAndPassword(email, password).await()
        Unit
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    suspend fun signInWithGoogle(idToken: String): Result<Unit> = runCatching {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }

    /** Firebase requires a fresh reauthentication immediately before a sensitive change like
     * this — a session that's merely still logged in isn't recent enough on its own. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Not signed in")
        val email = user.email ?: throw IllegalStateException("This account has no email to reauthenticate with")
        try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        } catch (e: Exception) {
            throw WrongPasswordException()
        }
        user.updatePassword(newPassword).await()
    }

    fun signOut() = auth.signOut()
}
