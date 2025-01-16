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
import com.udacity.project4.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private lateinit var viewModel: AuthenticationViewModel
    private lateinit var binding: FragmentLoginBinding

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
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_login, container, false)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        viewModel.initializeGoogleSignIn(requireContext())
        observeLoginState()
        return binding.root
    }

    private fun observeLoginState() {
        // Observe Navigation State
        viewModel.navigateToRegister.observe(viewLifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                findNavController().navigate(R.id.action_loginFragment_to_signUpFragment)
                viewModel.onNavigationHandled()
            }
        }
        // Observe Google Sign-In Intent
        viewModel.googleSignInIntent.observe(viewLifecycleOwner) { intent ->
            googleSignInLauncher.launch(intent)
        }
        // Observe Login State
        viewModel.loginState.observe(viewLifecycleOwner) { state ->
            when (state) {
                AuthenticationViewModel.LoginState.SUCCESS -> {
                    binding.progressBar.visibility = View.GONE
                    (activity as AuthenticationActivity).navigateToReminders()
                }
                AuthenticationViewModel.LoginState.ERROR -> showErrorMessage()
                AuthenticationViewModel.LoginState.LOADING -> showLoading()
            }
        }
    }

    private fun showErrorMessage() {
        binding.progressBar.visibility = View.GONE
        Toast.makeText(requireContext(), "Login failed. Please try again.", Toast.LENGTH_SHORT)
            .show()
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }
}

