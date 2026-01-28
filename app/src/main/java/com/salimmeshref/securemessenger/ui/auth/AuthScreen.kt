package com.salimmeshref.securemessenger.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salimmeshref.securemessenger.R
import com.salimmeshref.securemessenger.ui.components.MyTextFieldComponent
import com.salimmeshref.securemessenger.ui.components.PasswordTextFieldComponent

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onAuthSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            onAuthSuccess()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.onEvent(AuthEvent.ClearError)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (uiState.isSignUpMode) stringResource(R.string.create_account) else stringResource(R.string.welcome_back),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = if (uiState.isSignUpMode) {
                        stringResource(R.string.sign_up_to_get_started)
                    } else {
                        stringResource(R.string.sign_in_to_continue)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                AnimatedVisibility(
                    visible = uiState.isSignUpMode,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Column {
                        MyTextFieldComponent(
                            value = uiState.displayName,
                            onValueChange = { viewModel.onEvent(AuthEvent.DisplayNameChanged(it)) },
                            label = "Display Name",
                            enabled = !uiState.isLoading,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null
                                )
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                MyTextFieldComponent(
                    value = uiState.email,
                    onValueChange = { viewModel.onEvent(AuthEvent.EmailChanged(it)) },
                    label = "Email",
                    enabled = !uiState.isLoading,
                    keyboardType = KeyboardType.Email,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Email,
                            contentDescription = null
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                PasswordTextFieldComponent(
                    value = uiState.password,
                    onValueChange = { viewModel.onEvent(AuthEvent.PasswordChanged(it)) },
                    label = "Password",
                    enabled = !uiState.isLoading,
                    imeAction = if (uiState.isSignUpMode) ImeAction.Next else ImeAction.Done,
                    onImeAction = {
                        if (!uiState.isSignUpMode) {
                            viewModel.onEvent(AuthEvent.Submit)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null
                        )
                    }
                )

                AnimatedVisibility(
                    visible = uiState.isSignUpMode,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        PasswordTextFieldComponent(
                            value = uiState.confirmPassword,
                            onValueChange = { viewModel.onEvent(AuthEvent.ConfirmPasswordChanged(it)) },
                            label = "Confirm Password",
                            enabled = !uiState.isLoading,
                            imeAction = ImeAction.Done,
                            onImeAction = { viewModel.onEvent(AuthEvent.Submit) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.onEvent(AuthEvent.Submit) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (uiState.isSignUpMode) stringResource(R.string.sign_up) else stringResource(R.string.sign_in)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { viewModel.onEvent(AuthEvent.ToggleAuthMode) },
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        text = if (uiState.isSignUpMode) {
                            stringResource(R.string.already_have_an_account_sign_in)
                        } else {
                            stringResource(R.string.don_t_have_an_account_sign_up)
                        }
                    )
                }
            }
        }
    }
}
