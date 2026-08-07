package com.morphdrop.app.ui.screens.welcome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.morphdrop.app.R
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardingPage.Welcome,
        OnboardingPage.Convert,
        OnboardingPage.Privacy,
        OnboardingPage.Speed
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar with Skip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (pagerState.currentPage < pages.size - 1) {
                    TextButton(
                        onClick = onFinish,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // Pager for Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) { position ->
                OnboardingPagerItem(page = pages[position])
            }

            // Bottom Navigation Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 32.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page Indicators (Animated Dots)
                Row(
                    modifier = Modifier.height(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { iteration ->
                        val isSelected = pagerState.currentPage == iteration
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 8.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "dot_width"
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary 
                                          else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            label = "dot_color"
                        )
                        
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Primary Action Button
                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Text(
                        text = if (pagerState.currentPage == pages.size - 1) "Get Started" else "Next",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingPagerItem(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LottieAnimationView(
            resId = page.lottieRes,
            modifier = Modifier
                .fillMaxHeight(0.45f) // Take up 45% of height to look good on all screens
                .aspectRatio(1f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        
        // Extra spacer at bottom to push content up slightly for better balance
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun LottieAnimationView(resId: Int, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    if (composition != null) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = modifier
        )
    }
}

sealed class OnboardingPage(
    val title: String,
    val description: String,
    val lottieRes: Int
) {
    data object Welcome : OnboardingPage(
        title = "Welcome to MorphDrop",
        description = "The easiest way to convert files on your phone with elegance and speed.",
        com.morphdrop.app.R.raw.welcome
    )

    data object Convert : OnboardingPage(
        title = "Convert Any File",
        description = "PDF, Word, Excel, Images, and more. Support for all your essential formats.",
        com.morphdrop.app.R.raw.convert
    )

    data object Privacy : OnboardingPage(
        title = "100% Offline & Private",
        description = "Your files never leave your device. Privacy is our priority, no internet required.",
        com.morphdrop.app.R.raw.privacy
    )

    data object Speed : OnboardingPage(
        title = "Beautiful & Fast",
        description = "Material You design that adapts to your style while delivering peak performance.",
        com.morphdrop.app.R.raw.performance
    )
}
