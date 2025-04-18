package com.udacity.project4.authentication

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.AuthCredential
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
    val confirmPassword = MutableLiveData<String>()

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    private val _navigateToSignUp = MutableLiveData<Boolean>()
    val navigateToRegister: LiveData<Boolean> get() = _navigateToSignUp

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
                if (task.isSuccessful) {
                    _loginState.value = LoginState.SUCCESS
                } else {
//  This would be the code to handle the login error and direct the user to FragmentSignup
//  As it is a security issue like mentioned here https://github.com/firebase/firebase-android-sdk/issues/5586
//  I couldn't add any check for unknown emails. Also I think it would be a bad UX.
//                    val exception = task.exception
//                    if (exception is FirebaseAuthInvalidCredentialsException) {
//                        // Redirect to FragmentSignup
//                        _navigateToSignUp.value = true
//                    } else {
//                        _loginState.value = LoginState.ERROR
//                    }
                    _loginState.value = LoginState.ERROR
                }
            }
    }

    fun onSignUpClick() {
        val email = email.value.orEmpty()
        val password = password.value.orEmpty()
        _loginState.value = LoginState.LOADING

        // validation
        if (email.isEmpty() || password.isEmpty() || password != confirmPassword.value || password.isEmpty()) {
            _loginState.value = LoginState.ERROR
            return
        }

        FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _loginState.value = LoginState.SUCCESS
                } else {
                    _loginState.value = LoginState.ERROR
                }
            }
    }

    fun onNavigateToRegister() {
        _navigateToSignUp.value = true
    }

    fun onNavigationHandled() {
        _navigateToSignUp.value = false
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


    fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            // Successfully signed in
            val user = FirebaseAuth.getInstance().currentUser
            val credential =
                GoogleAuthProvider.getCredential(user?.getIdToken(false)?.result?.token, null)
            firebaseAuthWithGoogle(credential)
        } else {
            // Sign in failed
            _loginState.value = LoginState.ERROR
        }
    }

    private fun firebaseAuthWithGoogle(credential: AuthCredential) {
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
