package it.mappinando.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val WelcomeForest = Color(0xFF345B46)
private val WelcomeLime = Color(0xFFDBF0A5)

@Composable
internal fun OnboardingScreen(onComplete: (useLocation: Boolean) -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize().testTag("onboarding"), color = Color(0xFFF8F7F3)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val small = maxHeight < 620.dp
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(Modifier.size(36.dp))
                    Text("mappinando", Modifier.padding(start = 9.dp).weight(1f), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    TextButton(onClick = { onComplete(false) }, Modifier.testTag("onboarding-skip")) { Text("Salta") }
                }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("onboarding-pager")) { page ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp), horizontalAlignment = Alignment.Start) {
                        WelcomeIllustration(page, Modifier.fillMaxWidth().height(if (small) 170.dp else 300.dp))
                        Text(listOf("01 / ESPLORA", "02 / RICORDA", "03 / PARTI DA QUI")[page], fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = WelcomeForest)
                        Spacer(Modifier.height(12.dp))
                        Text(listOf("Segui la tua\ncuriosità.", "Un posto.\nMille possibilità.", "Il tuo mondo,\nda dove sei.")[page], fontSize = if (small) 30.sp else 37.sp, lineHeight = if (small) 35.sp else 43.sp, letterSpacing = (-1.5).sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        Text(listOf(
                            "Cerca un indirizzo, un ristorante o un angolo da scoprire. La tua mappa comincia vuota e si riempie solo dei tuoi posti.",
                            "Salva le tue tappe, organizzale per città e categoria. Aggiungi idee e cose da fare: le ritrovi tutte lì.",
                            "Se vuoi, usa la posizione per aprire la mappa vicino a te. Anche approssimativa va bene. Puoi sempre continuare senza."
                        )[page], fontSize = 14.sp, lineHeight = 23.sp, color = Color(0xFF707D72))
                        Spacer(Modifier.height(16.dp))
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 12.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        repeat(3) { index ->
                            val width by animateDpAsState(if (index == pager.currentPage) 27.dp else 7.dp, tween(250), label = "Passo iniziale")
                            Box(Modifier.width(width).height(7.dp).clip(CircleShape).background(if (index == pager.currentPage) WelcomeForest else Color(0xFFDCE2D7)))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = {
                        if (pager.currentPage == 2) onComplete(true)
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = tween(480, easing = FastOutSlowInEasing)) }
                    }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("onboarding-next"), shape = RoundedCornerShape(19.dp)) {
                        AnimatedContent(pager.currentPage == 2, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "Invito iniziale") { last ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (last) "Usa la mia posizione" else "Continua", fontWeight = FontWeight.Bold)
                                Icon(if (last) Icons.Outlined.MyLocation else Icons.Outlined.ArrowForward, null, Modifier.padding(start = 10.dp).size(19.dp))
                            }
                        }
                    }
                    if (pager.currentPage == 2) TextButton(onClick = { onComplete(false) }, modifier = Modifier.height(44.dp).testTag("onboarding-without-location")) {
                        Text("Inizia senza posizione", fontSize = 12.sp)
                    } else Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                        Text("La prossima tappa la scegli tu", fontSize = 12.sp, color = WelcomeForest)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeIllustration(page: Int, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "Atlante vivo")
    val drift by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Segnaposto sospesi")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawCircle(WelcomeLime.copy(alpha = .52f), h * .36f, Offset(w * .51f, h * .51f))
            for (index in 0..3) {
                val y = h * (.27f + index * .16f)
                drawLine(WelcomeForest.copy(alpha = .07f), Offset(w * .08f, y), Offset(w * .92f, y - h * .2f), 11.dp.toPx(), StrokeCap.Round)
            }
            val path = Path().apply {
                moveTo(w * .18f, h * .67f); cubicTo(w * .1f, h * .15f, w * .85f, h * .92f, w * .82f, h * .28f)
            }
            drawPath(path, WelcomeForest.copy(alpha = .5f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx()))))
            drawCircle(WelcomeForest.copy(alpha = .14f), 18.dp.toPx() + drift * 4.dp.toPx(), Offset(w * .82f, h * .28f))
            drawCircle(WelcomeForest, 5.dp.toPx(), Offset(w * .82f, h * .28f))
        }
        AnimatedContent(page, transitionSpec = { (fadeIn(tween(360)) + slideInVertically { it / 4 }) togetherWith (fadeOut(tween(180)) + slideOutVertically { -it / 5 }) }, label = "Illustrazione iniziale") { scene ->
            Box(Modifier.size(200.dp, 160.dp), contentAlignment = Alignment.Center) {
                Surface(Modifier.size(94.dp).graphicsLayer { translationY = drift * 5.dp.toPx(); rotationZ = -8f }, color = WelcomeForest, shape = RoundedCornerShape(30.dp), shadowElevation = 6.dp) {
                    Box(contentAlignment = Alignment.Center) { Icon(listOf(Icons.Outlined.Explore, Icons.Outlined.Bookmarks, Icons.Outlined.MyLocation)[scene], null, Modifier.size(46.dp), tint = WelcomeLime) }
                }
                Surface(Modifier.align(Alignment.TopEnd).graphicsLayer { translationY = -drift * 7.dp.toPx(); rotationZ = 9f }, color = Color.White, shape = RoundedCornerShape(19.dp), shadowElevation = 3.dp) {
                    Icon(listOf(Icons.Outlined.Restaurant, Icons.Outlined.Check, Icons.Outlined.NorthEast)[scene], null, Modifier.padding(14.dp).size(24.dp), tint = WelcomeForest)
                }
                Surface(Modifier.align(Alignment.BottomStart).graphicsLayer { translationY = drift * 8.dp.toPx(); rotationZ = -5f }, color = Color.White, shape = RoundedCornerShape(19.dp), shadowElevation = 3.dp) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, null, Modifier.size(18.dp), tint = WelcomeForest)
                        Text(listOf("Da scoprire", "La tua prossima tappa", "Si parte da te")[scene], Modifier.padding(start = 6.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
