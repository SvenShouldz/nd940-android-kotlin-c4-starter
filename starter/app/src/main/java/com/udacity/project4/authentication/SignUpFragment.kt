package com.udacity.project4.authentication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.udacity.project4.R
import com.udacity.project4.databinding.FragmentSignupBinding

class SignUpFragment : Fragment() {

    private lateinit var viewModel: AuthenticationViewModel
    private lateinit var binding: FragmentSignupBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this).get(AuthenticationViewModel::class.java)

        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_signup, container, false)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel

        return binding.root
    }

}