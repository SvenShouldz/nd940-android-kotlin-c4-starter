package com.udacity.project4.authentication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.udacity.project4.R
import com.udacity.project4.databinding.FragmentLoginBinding
import com.udacity.project4.locationreminders.RemindersActivity

class LoginFragment : Fragment() {

    private lateinit var viewModel: AuthenticationViewModel
    private lateinit var binding: FragmentLoginBinding

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.handleGoogleSignInResult(result.data)
        } else {
            showErrorMessage()
        }
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
        // Observe Google Sign-In Intent
        viewModel.googleSignInIntent.observe(viewLifecycleOwner) { intent ->
            googleSignInLauncher.launch(intent)
        }

        viewModel.loginState.observe(viewLifecycleOwner) { state ->
            when (state) {
                AuthenticationViewModel.LoginState.SUCCESS -> navigateToReminders()
                AuthenticationViewModel.LoginState.ERROR -> showErrorMessage()
                AuthenticationViewModel.LoginState.LOADING -> showLoading()
            }
        }
    }

    private fun navigateToReminders() {
        val intent = Intent(activity, RemindersActivity::class.java)
        startActivity(intent)
        activity?.finish()
    }

    private fun showErrorMessage() {
        Toast.makeText(requireContext(), "Login failed. Please try again.", Toast.LENGTH_SHORT)
            .show()
    }

    private fun showLoading() {
        // Show a loading indicator
    }
}

