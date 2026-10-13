package com.cupcake.ui.screen.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cupcake.ai.LilTiiEvalReport
import com.cupcake.ai.LilTiiEvalResult
import com.cupcake.ui.theme.CupCakeTheme
import kotlin.math.roundToInt

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LilTiiEvalScreen(
    onClose: () -> Unit,
    viewModel: LilTiiEvalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CupCakeTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("LilTii Eval (Debug)") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )

            when (uiState.status) {
                EvalStatus.IDLE -> IdleContent(onRun = { viewModel.runEval() })
                EvalStatus.RUNNING -> RunningContent(
                    current = uiState.currentIteration,
                    total = uiState.totalIterations
                )
                EvalStatus.SUCCESS -> SuccessContent(
                    report = uiState.report,
                    onRunAgain = {
                        viewModel.reset()
                        viewModel.runEval()
                    }
                )
                EvalStatus.ERROR -> ErrorContent(
                    error = uiState.error,
                    onRetry = {
                        viewModel.reset()
                        viewModel.runEval()
                    }
                )
            }
        }
    }
}

@Composable
private fun IdleContent(onRun: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "LilTii 20-Iteration Few-Shot Eval",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Runs 20 scripted prompts against the on-device Llama model and scores output format compliance.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRun) {
            Text("Run 20-iteration eval")
        }
    }
}

@Composable
private fun RunningContent(current: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Running evaluation...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { current.toFloat() / total },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Iteration $current / $total",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SuccessContent(
    report: LilTiiEvalReport?,
    onRunAgain: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Evaluation Complete",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (report != null) {
            item {
                SummaryCard(
                    passCount = report.passCount,
                    failCount = report.failCount,
                    passRate = report.overallPassRate,
                    avgScore = report.avgScore,
                    passed = report.passed
                )
            }

            item {
                Text(
                    text = "Emotion Tag Distribution",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (report.emotionTagDistribution.isEmpty()) {
                item {
                    Text(
                        text = "No emotion tags detected",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val entries = report.emotionTagDistribution.toList()
                items(entries) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "[${entry.first}]", fontSize = 13.sp)
                        Text(
                            text = "${entry.second} (${(entry.second.toDouble() / report.iterations * 100).roundToInt()}%)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Per-Iteration Breakdown",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            items(report.results) { result ->
                IterationCard(result)
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRunAgain, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text("Run Again")
            }
        }
    }
}

@Composable
private fun ErrorContent(error: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Evaluation Failed",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error ?: "Unknown error",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun SummaryCard(
    passCount: Int,
    failCount: Int,
    passRate: Double,
    avgScore: Double,
    passed: Boolean
) {
    val statusColor = if (passed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (passed) "PASSED" else "FAILED",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = "${(passRate * 100).roundToInt()}% pass rate",
                    fontSize = 14.sp,
                    color = statusColor
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatTile(
                    label = "Passed",
                    value = "$passCount",
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Failed",
                    value = "$failCount",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Avg Score",
                    value = "%.1f".format(avgScore),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IterationCard(result: LilTiiEvalResult) {
    val statusColor = if (result.passed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Iteration ${result.iteration}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Score: ${result.score}/6",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        color = statusColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (result.passed) "PASS" else "FAIL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (result.emotionTag != null) {
                Text(
                    text = "Tag: [${result.emotionTag}]",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.action != null) {
                Text(
                    text = "Action: *${result.action}*",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.notes.isNotBlank()) {
                Text(
                    text = result.notes,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
