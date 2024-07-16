package com.yt8492.doujinshimanager.ui.register

import android.util.Log
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun RegisterPage(
    navController: NavController,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bindingModel by viewModel.bindingModel.collectAsStateWithLifecycle()
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    LaunchedEffect(destination) {
        destination?.let {
            it.navigate(navController)
            viewModel.onCompleteNavigation()
        }
    }
    BackHandler {
        viewModel.onBackPress()
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        val resolved = uris.mapNotNull { uri ->
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val dir = File(context.filesDir, "images")
                if (!dir.exists()) {
                    dir.mkdir()
                }
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(
                    context.contentResolver.getType(uri)
                )
                val file = File(dir, UUID.randomUUID().toString() + "." + extension)
                FileOutputStream(file).use {
                    var read = 0
                    val buffer = ByteArray(8192)
                    do {
                        read = inputStream.read(buffer, 0, 8192)
                        Log.d("hogehoge", "read: $read")
                        if (read != -1) {
                            it.write(buffer, 0, read)
                        }
                    } while (read != -1)
                }
                file.path
            }
        }
        viewModel.onSelectImages(resolved)
    }
    val onClickAddImage = remember {
        {
            coroutineScope.launch {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(
                        mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                    )
                )
            }
            Unit
        }
    }
    RegisterTemplate(
        bindingModel = bindingModel,
        onInputTitle = viewModel::onInputTitle,
        onInputCircle = viewModel::onInputCircle,
        onSelectCircle = viewModel::onSelectCircle,
        onDeleteCircle = viewModel::onDeleteCircle,
        onInputAuthor = viewModel::onInputAuthor,
        onEnterAuthor = viewModel::onEnterAuthor,
        onSelectAuthor = viewModel::onSelectAuthor,
        onDeleteAuthor = viewModel::onDeleteAuthor,
        onInputTag = viewModel::onInputTag,
        onEnterTag = viewModel::onEnterTag,
        onSelectTag = viewModel::onSelectTag,
        onDeleteTag = viewModel::onDeleteTag,
        onInputEvent = viewModel::onInputEvent,
        onEnterEventName = viewModel::onEnterEventName,
        onEnterEventDate = viewModel::onEnterEventDate,
        onSelectEvent = viewModel::onSelectEvent,
        onDeleteEvent = viewModel::onDeleteEvent,
        onSelectPubDate = viewModel::onSelectPubDate,
        onDeletePubDate = viewModel::onDeletePubDate,
        onClickAddImage = onClickAddImage,
        onDeleteImage = viewModel::onDeleteImage,
        onClickRegister = viewModel::onClickRegister,
        onDismiss = viewModel::onDismiss,
        onBackPress = viewModel::onBackPress,
    )
}
