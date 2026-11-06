package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parser.SampleTickets
import com.example.ui.components.NothingCard
import com.example.ui.components.PerforatedDivider
import com.example.ui.theme.DotoFontFamily
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingWhite
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun AddTicketScreen(
    isExtracting: Boolean,
    extractionMessage: String?,
    onBack: () -> Unit,
    onFileSelected: (Uri) -> Unit,
    onParseRawText: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var customTicketText by remember { mutableStateOf("") }
    var showRawTextInput by remember { mutableStateOf(false) }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onFileSelected(uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("add_ticket_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "IMPORT TICKET",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "PDFBOX-ANDROID & ML KIT OCR // OFFLINE",
                                fontFamily = DotoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isExtracting) {
                    item {
                        NothingCard(
                            borderColor = NothingRed,
                            modifier = Modifier.testTag("extraction_processing_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = NothingRed,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column {
                                    Text(
                                        text = "PROCESSING DOCUMENT...",
                                        fontFamily = DotoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = extractionMessage ?: "Extracting text layer & running OCR...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. Primary File Picker Card (PDF, JPG, PNG)
                item {
                    NothingCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileOpen,
                                contentDescription = null,
                                tint = NothingRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "SELECT FILE FROM DEVICE",
                                    fontFamily = DotoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Supports PDF (text layer or scanned pages via PdfRenderer + OCR), JPG, and PNG images.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NothingRed)
                                .clickable(enabled = !isExtracting) {
                                    documentPickerLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "image/jpeg",
                                            "image/png",
                                            "image/*"
                                        )
                                    )
                                }
                                .testTag("pick_file_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CHOOSE PDF / JPG / PNG",
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = NothingWhite
                            )
                        }
                    }
                }

                // 2. Share Intent Info Card
                item {
                    NothingCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "SHARE FROM ANY APP (ACTION_SEND)",
                                    fontFamily = DotoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Share a downloaded ticket PDF or screenshot from Files, Gmail, or WhatsApp directly to TravelTrack to launch extraction.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 3. Sample Ticket & Raw Text Extraction Bench
                item {
                    NothingCard {
                        Text(
                            text = "TEST EXTRACTION PIPELINE",
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Run the label-based parser on sample IRCTC ERS, redBus/TSRTC, or custom ticket text and inspect the Review screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                onParseRawText(
                                    SampleTickets.SAMPLE_IRCTC_ERS_TEXT,
                                    "sample://irctc_ers_11020"
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("extract_sample_train_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Train,
                                contentDescription = null,
                                tint = NothingRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EXTRACT IRCTC TRAIN ERS (RLWL/28)",
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                onParseRawText(
                                    SampleTickets.SAMPLE_REDBUS_TSRTC_TEXT,
                                    "sample://redbus_tsrtc"
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("extract_sample_bus_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DirectionsBus,
                                contentDescription = null,
                                tint = NothingRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EXTRACT REDBUS / TSRTC BUS TICKET",
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                onParseRawText(
                                    SampleTickets.SAMPLE_UNKNOWN_TICKET_TEXT,
                                    "sample://unknown_ticket"
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("extract_sample_unknown_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EXTRACT UNKNOWN TICKET (MANUAL FILL)",
                                fontFamily = DotoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        PerforatedDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (showRawTextInput) "▼ HIDE CUSTOM TEXT PARSER" else "▶ PASTE RAW TICKET TEXT",
                            fontFamily = DotoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NothingRed,
                            modifier = Modifier
                                .clickable { showRawTextInput = !showRawTextInput }
                                .padding(vertical = 4.dp)
                                .testTag("toggle_raw_text_input")
                        )

                        if (showRawTextInput) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customTicketText,
                                onValueChange = { customTicketText = it },
                                label = {
                                    Text(
                                        "Paste ticket text here",
                                        fontFamily = SpaceGroteskFontFamily
                                    )
                                },
                                minLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_ticket_text_input")
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NothingRed)
                                    .clickable {
                                        if (customTicketText.isNotBlank()) {
                                            onParseRawText(customTicketText, "manual://pasted_text")
                                        }
                                    }
                                    .testTag("run_custom_text_parser_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "PARSE TEXT & REVIEW",
                                    fontFamily = DotoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = NothingWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
