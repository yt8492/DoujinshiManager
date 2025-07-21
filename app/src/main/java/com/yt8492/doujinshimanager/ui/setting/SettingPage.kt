package com.yt8492.doujinshimanager.ui.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingPage(
    navController: NavController,
    viewModel: SettingViewModel = koinViewModel(),
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { viewModel.exportData(it) }
    }
    
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importData(it) }
    }
    
    LaunchedEffect(destination) {
        destination?.let {
            it.navigate(navController)
            viewModel.onCompleteNavigation()
        }
    }
    
    SettingTemplate(
        onClickBack = { navController.popBackStack() },
        onClickExportData = {
            Clock.System.now()
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val fileName = "doujinshi_export_$timestamp.zip"
            createDocumentLauncher.launch(fileName)
        },
        onClickImportData = {
            openDocumentLauncher.launch(arrayOf("application/zip"))
        },
    )
    
    // エクスポート中のプログレスダイアログ
    if (isExporting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("エクスポート中") },
            text = {
                CircularProgressIndicator()
            },
            confirmButton = { }
        )
    }
    
    // エクスポート結果のダイアログ
    exportResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearExportResult() },
            title = { 
                Text(if (result.isSuccess) "エクスポート完了" else "エクスポート失敗") 
            },
            text = { 
                Text(
                    if (result.isSuccess) {
                        "データのエクスポートが完了しました。"
                    } else {
                        "エクスポート中にエラーが発生しました: ${result.exceptionOrNull()?.message}"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearExportResult() }) {
                    Text("OK")
                }
            }
        )
    }
    
    // インポート中のプログレスダイアログ
    if (isImporting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("インポート中") },
            text = {
                CircularProgressIndicator()
            },
            confirmButton = { }
        )
    }
    
    // インポート結果のダイアログ
    importResult?.let { result ->
        AlertDialog(
            onDismissRequest = { viewModel.clearImportResult() },
            title = { 
                Text(if (result.isSuccess) "インポート完了" else "インポート失敗") 
            },
            text = { 
                Text(
                    if (result.isSuccess) {
                        "データのインポートが完了しました。"
                    } else {
                        "インポート中にエラーが発生しました: ${result.exceptionOrNull()?.message}"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearImportResult() }) {
                    Text("OK")
                }
            }
        )
    }
}
