package fi.omasaasahko

import android.content.Context
import fi.omasaasahko.domain.AppLanguage

/** The resources decide the language; this keeps code and text in the same language. */
fun AppLanguage.Companion.of(context: Context): AppLanguage = fromTag(context.getString(R.string.language_tag))
