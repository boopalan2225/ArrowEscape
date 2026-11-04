package com.clevercorner.arrowescape.ui.coins

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.economy.CoinRules
import com.clevercorner.arrowescape.economy.RedeemResult
import com.clevercorner.arrowescape.economy.ReferralCode
import com.clevercorner.arrowescape.economy.Wallet
import kotlinx.coroutines.launch

/**
 * Bring a friend, and take their code if they brought you first.
 *
 * An honest limit worth stating out loud: with no server, the only half of a
 * referral this app can *verify* is the half happening on this device. So the
 * player who redeems a code is paid here and now, and the friend who shared
 * it is paid when there is somebody neutral to witness the redeem (see
 * `CoinBank.redeem`). Nothing here pretends otherwise, and no coins are
 * promised that cannot be delivered.
 */
@Composable
fun ReferralCard(
    wallet: Wallet,
    ensureReferralCode: suspend () -> String,
    onRedeem: suspend (String) -> RedeemResult,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Minted on arrival rather than at install: a code nobody has ever
    // looked at does not need to exist.
    var code by remember { mutableStateOf(wallet.referralCode) }
    LaunchedEffect(Unit) { if (code == null) code = ensureReferralCode() }

    var entry by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<RedeemResult?>(null) }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.coins_invite_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.coins_invite_subtitle, CoinRules.REFERRAL),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            CodePlaque(code = code.orEmpty())

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { context.shareInvite(code.orEmpty()) },
                enabled = code != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = stringResource(R.string.coins_invite_share),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(18.dp))
            Divider()
            Spacer(Modifier.height(18.dp))

            if (wallet.hasPaid(CoinRules.REFERRAL_ID)) {
                // One invite bonus per player; saying so beats a button that
                // silently refuses.
                Text(
                    text = stringResource(R.string.coins_redeem_already),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                RedeemRow(
                    entry = entry,
                    onEntryChange = {
                        // Normalising as they type means a code read aloud
                        // with dashes and spaces simply works.
                        entry = ReferralCode.normalise(it).take(ReferralCode.LENGTH)
                        outcome = null
                    },
                    onRedeem = {
                        scope.launch { outcome = onRedeem(entry) }
                    }
                )
            }

            AnimatedVisibility(
                visible = outcome != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                Text(
                    text = outcome.message(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (outcome == RedeemResult.CREDITED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

/** The code itself, spaced out and legible enough to read down a phone line. */
@Composable
private fun CodePlaque(code: String) {
    // A gentle breathing scale: the one thing on this card worth looking at.
    val pulse = remember { Animatable(0.96f) }
    LaunchedEffect(code) {
        if (code.isNotEmpty()) pulse.animateTo(1f, tween(500))
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulse.value)
    ) {
        Text(
            text = code.toCharArray().joinToString(" "),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )
    }
}

@Composable
private fun RedeemRow(
    entry: String,
    onEntryChange: (String) -> Unit,
    onRedeem: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = entry,
            onValueChange = onEntryChange,
            singleLine = true,
            label = { Text(stringResource(R.string.coins_redeem_label)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            // Disabled until the code could plausibly be real: no point
            // telling somebody mid-type that their half-typed code is wrong.
            onClick = onRedeem,
            enabled = entry.length == ReferralCode.LENGTH,
            shape = MaterialTheme.shapes.large
        ) {
            Text(stringResource(R.string.coins_redeem_button), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RedeemResult?.message(): String = when (this) {
    RedeemResult.CREDITED -> stringResource(R.string.coins_redeem_ok, CoinRules.REFERRAL)
    RedeemResult.MALFORMED -> stringResource(R.string.coins_redeem_bad)
    RedeemResult.OWN_CODE -> stringResource(R.string.coins_redeem_own)
    RedeemResult.ALREADY_REDEEMED -> stringResource(R.string.coins_redeem_already)
    null -> ""
}

/**
 * Hands the invite to whatever the player shares with - the system sheet
 * knows their friends far better than this app ever will.
 */
private fun android.content.Context.shareInvite(code: String) {
    val link = "https://play.google.com/store/apps/details?id=$packageName"
    val message = getString(R.string.coins_invite_message, code, link)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    startActivity(Intent.createChooser(send, getString(R.string.coins_invite_share)))
}
