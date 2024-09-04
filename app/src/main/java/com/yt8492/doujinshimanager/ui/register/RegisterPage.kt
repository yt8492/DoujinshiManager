package com.yt8492.doujinshimanager.ui.register

import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
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
    val (takePictureImagePath, setTakePictureImagePath) = remember {
        mutableStateOf<String?>(null)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success && takePictureImagePath != null) {
            viewModel.onSelectImages(listOf(takePictureImagePath))
        }
        setTakePictureImagePath(null)
    }
    val onClickTakePicture = remember {
        {
            val dir = File(context.filesDir, "images")
            if (!dir.exists()) {
                dir.mkdir()
            }
            val file = File(dir, UUID.randomUUID().toString() + ".jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "com.yt8492.doujinshimanager.fileprovider",
                file,
            )
            coroutineScope.launch {
                cameraLauncher.launch(uri)
            }
            setTakePictureImagePath(file.path)
        }
    }
    RegisterTemplate(
        bindingModel = bindingModel,
        onInputTitle = viewModel::onInputTitle,
        onFocusCircle = viewModel::onFocusCircle,
        onInputCircle = viewModel::onInputCircle,
        onSelectCircle = viewModel::onSelectCircle,
        onDeleteCircle = viewModel::onDeleteCircle,
        onFocusAuthor = viewModel::onFocusAuthor,
        onInputAuthor = viewModel::onInputAuthor,
        onEnterAuthor = viewModel::onEnterAuthor,
        onSelectAuthor = viewModel::onSelectAuthor,
        onDeleteAuthor = viewModel::onDeleteAuthor,
        onFocusTag = viewModel::onFocusTag,
        onInputTag = viewModel::onInputTag,
        onEnterTag = viewModel::onEnterTag,
        onSelectTag = viewModel::onSelectTag,
        onDeleteTag = viewModel::onDeleteTag,
        onFocusEvent = viewModel::onFocusEvent,
        onInputEvent = viewModel::onInputEvent,
        onEnterEventName = viewModel::onEnterEventName,
        onEnterEventDate = viewModel::onEnterEventDate,
        onSelectEvent = viewModel::onSelectEvent,
        onDeleteEvent = viewModel::onDeleteEvent,
        onSelectPubDate = viewModel::onSelectPubDate,
        onDeletePubDate = viewModel::onDeletePubDate,
        onClickAddImage = onClickAddImage,
        onClickTakePicture = onClickTakePicture,
        onDeleteImage = viewModel::onDeleteImage,
        onClickRegister = viewModel::onClickRegister,
        onDismiss = viewModel::onDismiss,
        onBackPress = viewModel::onBackPress,
    )
}
