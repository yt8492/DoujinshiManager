package com.yt8492.doujinshimanager.ui.register

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.yt8492.doujinshimanager.Constants
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Composable
fun RegisterPage(
    navController: NavController,
    registerViewModel: RegisterViewModel = koinViewModel(),
    pickerViewModel: DoujinshiPickerViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bindingModel by registerViewModel.bindingModel.collectAsStateWithLifecycle()
    val destination by registerViewModel.destination.collectAsStateWithLifecycle()
    val pickResult by pickerViewModel.pickResult.collectAsStateWithLifecycle()
    val isShowDialog by pickerViewModel.isShow.collectAsStateWithLifecycle()
    LaunchedEffect(destination) {
        destination?.let {
            it.navigate(navController)
            registerViewModel.onCompleteNavigation()
        }
    }
    LaunchedEffect(pickResult) {
        pickResult?.let {
            registerViewModel.onPickResult(it)
        }
    }
    BackHandler {
        if (isShowDialog) {
            pickerViewModel.onDismissDialog()
        } else {
            registerViewModel.onBackPress()
        }
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        val resolved = uris.mapNotNull { uri ->
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val dir = File(context.filesDir, Constants.imagesDirectoryName)
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
                        if (read != -1) {
                            it.write(buffer, 0, read)
                        }
                    } while (read != -1)
                }
                file.path
            }
        }
        registerViewModel.onSelectImages(resolved)
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
            registerViewModel.onSelectImages(listOf(takePictureImagePath))
        }
        setTakePictureImagePath(null)
    }
    val onClickTakePicture = remember {
        {
            val dir = File(context.filesDir, Constants.imagesDirectoryName)
            if (!dir.exists()) {
                dir.mkdir()
            }
            val file = File(dir, UUID.randomUUID().toString() + ".jpg")
            val uri = FileProvider.getUriForFile(
                context,
                Constants.fileProviderName,
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
        onInputTitle = registerViewModel::onInputTitle,
        onFocusCircle = registerViewModel::onFocusCircle,
        onInputCircle = registerViewModel::onInputCircle,
        onSelectCircle = registerViewModel::onSelectCircle,
        onDeleteCircle = registerViewModel::onDeleteCircle,
        onFocusAuthor = registerViewModel::onFocusAuthor,
        onInputAuthor = registerViewModel::onInputAuthor,
        onEnterAuthor = registerViewModel::onEnterAuthor,
        onSelectAuthor = registerViewModel::onSelectAuthor,
        onDeleteAuthor = registerViewModel::onDeleteAuthor,
        onFocusTag = registerViewModel::onFocusTag,
        onInputTag = registerViewModel::onInputTag,
        onEnterTag = registerViewModel::onEnterTag,
        onSelectTag = registerViewModel::onSelectTag,
        onDeleteTag = registerViewModel::onDeleteTag,
        onFocusEvent = registerViewModel::onFocusEvent,
        onInputEvent = registerViewModel::onInputEvent,
        onEnterEventName = registerViewModel::onEnterEventName,
        onEnterEventDate = registerViewModel::onEnterEventDate,
        onSelectEvent = registerViewModel::onSelectEvent,
        onDeleteEvent = registerViewModel::onDeleteEvent,
        onSelectPubDate = registerViewModel::onSelectPubDate,
        onDeletePubDate = registerViewModel::onDeletePubDate,
        onClickAddImage = onClickAddImage,
        onClickTakePicture = onClickTakePicture,
        onDeleteImage = registerViewModel::onDeleteImage,
        onClickRegister = registerViewModel::onClickRegister,
        onClickPick = pickerViewModel::showDialog,
        onDismiss = registerViewModel::onDismiss,
        onBackPress = registerViewModel::onBackPress,
    )
    if (isShowDialog) {
        DoujinshiPickerDialog(
            viewModel = pickerViewModel,
        )
    }
}
