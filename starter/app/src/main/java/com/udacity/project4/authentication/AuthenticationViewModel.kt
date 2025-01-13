package com.udacity.project4.authentication

import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.udacity.project4.R

class AuthenticationViewModel : ViewModel() {

    private lateinit var googleSignInClient: GoogleSignInClient

    enum class LoginState {
        LOADING, SUCCESS, ERROR
    }

    private val _googleSignInIntent = MutableLiveData<Intent>()
    val googleSignInIntent: LiveData<Intent> get() = _googleSignInIntent

    val email = MutableLiveData<String>()
    val password = MutableLiveData<String>()

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    fun onLoginClick() {
        val email = email.value.orEmpty()
        val password = password.value.orEmpty()

        if (email.isEmpty() || password.isEmpty()) {
            _loginState.value = LoginState.ERROR
            return
        }

        _loginState.value = LoginState.LOADING
        FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                _loginState.value = if (task.isSuccessful) LoginState.SUCCESS else LoginState.ERROR
            }
    }

    fun onRegisterClick() {
        
    }

    fun onGoogleSignInClick() {
        // Emit the sign-in intent via LiveData
        _googleSignInIntent.value = googleSignInClient.signInIntent
    }

    fun initializeGoogleSignIn(context: Context) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    fun handleGoogleSignInResult(data: Intent?) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            _loginState.value = LoginState.ERROR
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { task ->
                _loginState.value = if (task.isSuccessful) {
                    LoginState.SUCCESS
                } else {
                    LoginState.ERROR
                }
            }
    }
}
