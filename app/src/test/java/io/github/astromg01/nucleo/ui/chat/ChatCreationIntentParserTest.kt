package io.github.astromg01.nucleo.ui.chat

import io.github.astromg01.nucleo.domain.project.ProjectProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatCreationIntentParserTest {
    @Test
    fun routesAndroidAppAndApkRequestsToAndroidFactory() {
        assertEquals(
            ProjectProfile.ANDROID_APP,
            ChatCreationIntentParser.parse("Crie um APK de controle financeiro")?.profile,
        )
        assertEquals(
            ProjectProfile.ANDROID_APP,
            ChatCreationIntentParser.parse("Quero desenvolver um aplicativo Android")?.profile,
        )
    }

    @Test
    fun routesWebsiteRequestsToWebFactory() {
        assertEquals(
            ProjectProfile.WEB_APP,
            ChatCreationIntentParser.parse("Cria um site para minha comunidade")?.profile,
        )
    }

    @Test
    fun routesPythonRequestsToPythonFactory() {
        assertEquals(
            ProjectProfile.PYTHON,
            ChatCreationIntentParser.parse("Gere um projeto em Python para organizar arquivos")?.profile,
        )
    }

    @Test
    fun routesOtherExplicitProjectRequestsToGenericFactory() {
        assertEquals(
            ProjectProfile.GENERIC,
            ChatCreationIntentParser.parse("Construa um bot para moderar minha comunidade")?.profile,
        )
    }

    @Test
    fun doesNotRouteContentCreationOrCodeAnalysisIntoProjectFactory() {
        assertNull(ChatCreationIntentParser.parse("Crie um roteiro de vídeo sobre Minecraft"))
        assertNull(ChatCreationIntentParser.parse("Crie um post para divulgar meu app"))
        assertNull(ChatCreationIntentParser.parse("Crie material no formato: Post. Tema/objetivo: divulgação do meu app."))
        assertNull(ChatCreationIntentParser.parse("Analise este APK e encontre os problemas"))
        assertNull(ChatCreationIntentParser.parse("Vamos conversar sobre como criar um app"))
        assertNull(ChatCreationIntentParser.parse(" "))
    }
}
