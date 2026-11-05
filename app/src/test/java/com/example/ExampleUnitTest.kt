package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCommandParser_openApp() {
    val cmd = com.myra.assistant.ai.CommandParser.parse("YouTube kholo")
    assertNotNull(cmd)
    assertEquals(com.myra.assistant.model.AppCommand.OPEN_APP, cmd?.type)
    assertEquals("youtube", cmd?.params?.get("app_name"))

    val cmd2 = com.myra.assistant.ai.CommandParser.parse("open WhatsApp please")
    assertNotNull(cmd2)
    assertEquals(com.myra.assistant.model.AppCommand.OPEN_APP, cmd2?.type)
    assertEquals("whatsapp", cmd2?.params?.get("app_name"))
  }

  @Test
  fun testCommandParser_primeContact() {
    val cmd = com.myra.assistant.ai.CommandParser.parse("close friend ko call karo")
    assertNotNull(cmd)
    assertEquals(com.myra.assistant.model.AppCommand.PRIME_CALL, cmd?.type)
    assertEquals("0", cmd?.params?.get("index"))

    val cmd2 = com.myra.assistant.ai.CommandParser.parse("meri jaan ko message karo")
    assertNotNull(cmd2)
    assertEquals(com.myra.assistant.model.AppCommand.PRIME_MSG, cmd2?.type)
    assertEquals("0", cmd2?.params?.get("index"))
  }

  @Test
  fun testCommandParser_hardwareControls() {
    val volUp = com.myra.assistant.ai.CommandParser.parse("volume badhao")
    assertEquals(com.myra.assistant.model.AppCommand.VOLUME_UP, volUp?.type)

    val torchOn = com.myra.assistant.ai.CommandParser.parse("flashlight on")
    assertEquals(com.myra.assistant.model.AppCommand.FLASHLIGHT_ON, torchOn?.type)
  }
}
