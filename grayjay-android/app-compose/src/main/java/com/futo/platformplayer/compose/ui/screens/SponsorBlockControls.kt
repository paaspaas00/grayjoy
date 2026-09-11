package com.futo.platformplayer.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.futo.platformplayer.compose.R
import com.futo.platformplayer.compose.sponsorblock.SponsorBlockCategory
import com.futo.platformplayer.compose.sponsorblock.SponsorBlockRule

@Composable
internal fun SponsorBlockRuleEditor(
    rule: SponsorBlockRule,
    onRuleChange: (SponsorBlockRule) -> Unit,
    inherited: Boolean = false,
    inheritedRule: SponsorBlockRule? = null,
    onCustomize: (() -> Unit)? = null,
    onUseInherited: (() -> Unit)? = null,
    skipNoticesEnabled: Boolean? = null,
    onSkipNoticesEnabledChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (inherited) {
            Text(
                stringResource(R.string.sponsorblock_using_inherited),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { onCustomize?.invoke() },
                modifier = Modifier.testTag("sponsorblock-customize"),
            ) { Text(stringResource(R.string.sponsorblock_customize)) }
        } else {
            if (inheritedRule != null && onUseInherited != null) {
                TextButton(
                    onClick = onUseInherited,
                    modifier = Modifier.testTag("sponsorblock-use-inherited"),
                ) { Text(stringResource(R.string.sponsorblock_use_inherited)) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRuleChange(rule.copy(enabled = !rule.enabled)) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sponsorblock_enabled), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.sponsorblock_enabled_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onRuleChange(rule.copy(enabled = it)) },
                    modifier = Modifier.testTag("sponsorblock-enabled"),
                )
            }
            if (skipNoticesEnabled != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSkipNoticesEnabledChange(!skipNoticesEnabled)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.sponsorblock_skip_notices),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.sponsorblock_skip_notices_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = skipNoticesEnabled,
                        onCheckedChange = onSkipNoticesEnabledChange,
                        modifier = Modifier.testTag("sponsorblock-skip-notices"),
                    )
                }
            }
            Text(
                stringResource(R.string.sponsorblock_segments_to_skip),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            SponsorBlockCategory.entries.forEach { category ->
                val selected = category in rule.categories
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = rule.enabled) {
                            onRuleChange(rule.copy(categories = rule.categories.toggle(category)))
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selected,
                        enabled = rule.enabled,
                        onCheckedChange = {
                            onRuleChange(rule.copy(categories = rule.categories.toggle(category)))
                        },
                    )
                    Text(stringResource(category.labelRes))
                }
            }
        }
    }
}

private fun Set<SponsorBlockCategory>.toggle(category: SponsorBlockCategory): Set<SponsorBlockCategory> =
    if (category in this) this - category else this + category
