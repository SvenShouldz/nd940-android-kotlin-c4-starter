package com.udacity.project4.authentication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.udacity.project4.R
import com.udacity.project4.databinding.FragmentSignupBinding

class SignUpFragment : Fragment() {

    private lateinit var viewModel: AuthenticationViewModel
    private lateinit var binding: FragmentSignupBinding

    private val googleSignInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract(),
    ) { res ->
        viewModel.onSignInResult(res)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this).get(AuthenticationViewModel::class.java)
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_signup, container, false)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        viewModel.initializeGoogleSignIn(requireContext())
        observeSignUpState()
        return binding.root
    }

    private fun observeSignUpState() {
        // Observe Google Sign-In Intent
        viewModel.googleSignInIntent.observe(viewLifecycleOwner) { intent ->
            googleSignInLauncher.launch(intent)
        }
        // Observe Login State
        viewModel.loginState.observe(viewLifecycleOwner) { state ->
            when (state) {
                AuthenticationViewModel.LoginState.SUCCESS -> navigateToReminders()
                AuthenticationViewModel.LoginState.ERROR -> showErrorMessage()
                AuthenticationViewModel.LoginState.LOADING -> showLoading()
            }
        }
    }

    private fun navigateToReminders() {
        findNavController().navigate(R.id.action_signUpFragment_to_loginFragment)
    }

    private fun showLoading() {
        // Show a loading indicator
    }

    private fun showErrorMessage() {
        Toast.makeText(requireContext(), "Sign up failed. Please try again.", Toast.LENGTH_SHORT)
            .show()
    }

}