package io.github.seungminshin97.japeace.presentation

import androidx.compose.runtime.Composable

@Composable
internal fun ProvideRestNavigation(content: @Composable () -> Unit) = content()

internal fun restPlaceRoute(catalog: List<RestPlace>): RestPlaceRoute = RestPlaceRoute()
