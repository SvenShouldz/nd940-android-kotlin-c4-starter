package com.udacity.project4.authentication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.udacity.project4.R
import com.udacity.project4.databinding.ActivityAuthenticationBinding
import com.udacity.project4.locationreminders.RemindersActivity

class AuthenticationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityAuthenticationBinding

    // FirebaseUI Sign-In Launcher
    private val signInLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { result ->
        this.onSignInResult(result)
    }

    companion object {
        private const val TAG = "AuthenticationActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = Firebase.auth

        // Check if user is signed in (Firebase Authentication)
        if (auth.currentUser == null) {
            // Not signed in, launch the FirebaseUI sign-in flow
            launchFirebaseUiSignIn()
        } else {
            // User is already signed in, navigate directly to reminders
            navigateToReminders()
        }
    }

    private fun launchFirebaseUiSignIn() {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        // Create and launch sign-in intent
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setLogo(R.drawable.map)
            .setTheme(R.style.AppTheme)
            .setIsSmartLockEnabled(false, true)
            .build()

        signInLauncher.launch(signInIntent)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        if (result.resultCode == Activity.RESULT_OK) {
            // Successfully signed in
            Log.i(
                TAG,
                "Successfully signed in user ${FirebaseAuth.getInstance().currentUser?.displayName}!"
            )
            navigateToReminders()
        } else {
            if (response == null) {
                Log.w(TAG, "Sign in cancelled by user")
            } else {
                Log.w(TAG, "Sign in error", response.error)
                Toast.makeText(this, "Sign in failed: ${response.error?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun navigateToReminders() {
        val intent = Intent(this, RemindersActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

}