import com.arny.aiprompts.platform.EncryptedJvmSettings
import io.mockk.*
import java.util.prefs.Preferences
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*
class EncryptedSettingsSafetyTest {
 @Test fun longUnicodeSettingsRoundTripAndFailedWriteRetainsOldGeneration() {
  val values=mutableMapOf<String,String>()
  val delegate=mockk<Preferences>(); var fail=false
  every { delegate.put(any(),any()) } answers {
   if(fail) throw IllegalStateException("Synthetic storage failure")
   val value=secondArg<String>(); assertTrue(value.length<=Preferences.MAX_VALUE_LENGTH)
   values[firstArg()]=value
  }
  every { delegate.get(any(),any()) } answers { values[firstArg<String>()] ?: secondArg<String?>() }
  every { delegate.remove(any()) } answers { values.remove(firstArg<String>()); Unit }
  val settings=EncryptedJvmSettings(delegate,SecretKeySpec(ByteArray(32),"AES"))
  every { delegate.keys() } answers { values.keys.toTypedArray() }
  val content=("a".repeat(1199)+"😀").repeat(20)
  settings.putString("profiles",content); assertEquals(content,settings.getStringOrNull("profiles"))
  assertEquals(setOf("profiles"),settings.keys); assertEquals(1,settings.size)
  fail=true; assertFailsWith<IllegalStateException> { settings.putString("profiles",content+"new") }
  assertEquals(content,settings.getStringOrNull("profiles"))
  fail=false; settings.putString("profiles","short"); assertEquals("short",settings.getStringOrNull("profiles"))
  assertEquals(setOf("profiles"),values.keys)
 }
}
