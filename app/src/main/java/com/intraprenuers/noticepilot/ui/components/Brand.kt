package com.intraprenuers.noticepilot.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Branding integration point. When Siva's logo is ready:
 *  1. put it in app/src/main/res/drawable/ (e.g. noticepilot_logo.xml or .png)
 *  2. set LOGO_RES = R.drawable.noticepilot_logo  (import com.intraprenuers.noticepilot.R)
 * BrandMark then shows the logo next to the wordmark everywhere. Nothing else needs to change.
 */
object Brand {
    val LOGO_RES: Int? = null
}

/** The NoticePilot wordmark (and logo, once [Brand.LOGO_RES] is set). */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val logo = Brand.LOGO_RES
        if (logo != null) {
            Image(painter = painterResource(logo), contentDescription = null, modifier = Modifier.size(28.dp))
        }
        Text(
            text = "NoticePilot",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = 0.2.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
