package com.xmentarik.quiz.ui.game

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xmentarik.quiz.R
import com.xmentarik.quiz.data.Question
import com.xmentarik.quiz.online.QuestionReport
import com.xmentarik.quiz.online.ReportReason
import com.xmentarik.quiz.ui.LocalAppLanguage
import com.xmentarik.quiz.ui.ReportStatus
import com.xmentarik.quiz.ui.theme.QuizColors

/**
 * Lets the player say what is wrong with [question]. Sent through Firestore when [canSendOnline],
 * otherwise through the email app to [reportEmail].
 */
@Composable
fun ReportDialog(
    question: Question,
    selectedOption: Int?,
    status: ReportStatus?,
    canSendOnline: Boolean,
    reportEmail: String,
    onSubmit: (ReportReason, String) -> Unit,
    onSentByEmail: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val labels = LocalAppLanguage.current.optionLabels
    var reason by rememberSaveable { mutableStateOf(ReportReason.WRONG_ANSWER) }
    var comment by rememberSaveable { mutableStateOf("") }
    val sending = status == ReportStatus.SENDING
    val reasonLabels = mapOf(
        ReportReason.WRONG_ANSWER to stringResource(R.string.report_wrong_answer),
        ReportReason.AMBIGUOUS to stringResource(R.string.report_ambiguous),
        ReportReason.TYPO_OR_TRANSLATION to stringResource(R.string.report_typo),
        ReportReason.OTHER to stringResource(R.string.report_other),
    )
    val subject = stringResource(R.string.report_email_subject, question.id)

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("🚩 " + stringResource(R.string.report_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Column(Modifier.selectableGroup()) {
                    ReportReason.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == reason,
                                    onClick = { reason = option },
                                    enabled = !sending,
                                    role = Role.RadioButton,
                                ),
                        ) {
                            RadioButton(selected = option == reason, onClick = null, enabled = !sending)
                            Text(reasonLabels.getValue(option), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(QuestionReport.MAX_COMMENT_LENGTH) },
                    label = { Text(stringResource(R.string.report_comment)) },
                    enabled = !sending,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (status == ReportStatus.FAILED) {
                    Text(stringResource(R.string.report_failed), color = QuizColors.Wrong)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    if (canSendOnline) {
                        onSubmit(reason, comment)
                    } else {
                        val body = buildString {
                            appendLine("${question.id}: ${question.text}")
                            question.options.forEachIndexed { i, option ->
                                val marks = (if (i == question.correctIndex) " ✓" else "") +
                                    (if (i == selectedOption) " ←" else "")
                                appendLine("${labels[i]}: $option$marks")
                            }
                            appendLine()
                            appendLine(reasonLabels.getValue(reason))
                            append(QuestionReport.cleanComment(comment))
                        }
                        val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(reportEmail))
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, body)
                        }
                        try {
                            context.startActivity(email)
                            onSentByEmail()
                        } catch (e: ActivityNotFoundException) {
                            onDismiss()
                        }
                    }
                },
            ) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.report_send))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) { Text(stringResource(R.string.cancel)) }
        },
    )
}
