package com.breakfree.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakfree.app.ui.FeedbackViewModel
import com.breakfree.app.ui.components.SearchTopAppBar
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    initialScreenshotPath: String? = null,
    onBack: () -> Unit,
    viewModel: FeedbackViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = copyUriToCache(context, it)
            viewModel.onImageSelected(file)
        }
    }

    LaunchedEffect(initialScreenshotPath) {
        if (initialScreenshotPath != null) {
            viewModel.onImageSelected(File(initialScreenshotPath))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is FeedbackViewModel.FeedbackEvent.Success -> {
                    Toast.makeText(context, "Feedback submitted! Thank you.", Toast.LENGTH_LONG).show()
                    onBack()
                }
                is FeedbackViewModel.FeedbackEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            SearchTopAppBar(
                title = "Send Feedback",
                onBack = onBack,
                searchEnabled = false,
                showLogo = false,
                actions = {
                    IconButton(
                        onClick = { viewModel.submit() },
                        enabled = state.feedbackText.isNotBlank() && !state.isSending
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Submit")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "We value your feedback! Let us know how we can improve BreakFree.",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = state.feedbackText,
                onValueChange = { viewModel.onTextChange(it) },
                label = { Text("What's on your mind?") },
                modifier = Modifier.fillMaxWidth().height(200.dp),
                placeholder = { Text("Report a bug, suggest a feature, or just say hi!") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Attachment (Optional)", style = MaterialTheme.typography.titleSmall)
            
            Spacer(modifier = Modifier.height(8.dp))

            if (state.imageFile != null && state.imageFile!!.exists()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val bitmap = remember(state.imageFile) {
                        BitmapFactory.decodeFile(state.imageFile!!.absolutePath)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Selected image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    IconButton(
                        onClick = { viewModel.removeImage() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White)
                    }
                }
            } else {
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Add Screenshot")
                    }
                }
            }

        }
    }
}

private fun copyUriToCache(context: android.content.Context, uri: Uri): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val file = File(context.cacheDir, "feedback_upload_${System.currentTimeMillis()}.png")
    val outputStream = FileOutputStream(file)
    inputStream?.copyTo(outputStream)
    inputStream?.close()
    outputStream.close()
    return file
}
