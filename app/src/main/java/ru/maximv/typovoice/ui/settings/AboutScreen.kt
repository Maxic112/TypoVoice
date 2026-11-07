package ru.maximv.typovoice.ui.settings

import androidx.compose.runtime.Composable
import ru.maximv.typovoice.BuildConfig
import ru.maximv.typovoice.ui.InfoItem
import ru.maximv.typovoice.ui.Note
import ru.maximv.typovoice.ui.SectionScreen
import ru.maximv.typovoice.R
import ru.maximv.typovoice.str

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SectionScreen(str(R.string.about_title), onBack) {
        item {
            InfoItem("TypoVoice", str(R.string.about_version, BuildConfig.VERSION_NAME))
            InfoItem(str(R.string.common_server), str(R.string.about_server))
            Note(
                str(R.string.about_text),
            )
        }
    }
}
