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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intraprenuers.noticepilot.R

/**
 * Branding integration point. Siva's logo lives in res/drawable-nodpi/noticepilot_logo.png
 * (the launcher icon uses res/drawable-nodpi/ic_launcher_foreground.png from the same artwork).
 * To swap the logo, replace that PNG; set LOGO_RES = null to show the wordmark alone.
 */
object Brand {
    val LOGO_RES: Int? = R.drawable.noticepilot_logo
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
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(7.dp))
            )
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
