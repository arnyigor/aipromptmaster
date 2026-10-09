package com.arny.aipromptmaster
import com.arny.aipromptmaster.data.repositories.OpenRouterRepositoryImpl
import com.arny.aipromptmaster.data.api.OpenRouterService
import com.arny.aipromptmaster.domain.models.*
import com.arny.aipromptmaster.domain.repositories.IFileRepository
import com.arny.promptcontract.ChatGenerationConfig
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import org.junit.Test
import kotlin.test.*
class StreamingTerminationTest {
 @Test fun streamKeepsTokensButRejectsMissingCompletionAndErrorFrames() = runBlocking {
  val service=mockk<OpenRouterService>(); val repo=OpenRouterRepositoryImpl(service,Json { ignoreUnknownKeys=true },mockk<IFileRepository>(),Dispatchers.Unconfined)
  val chunk="data:{\"choices\":[{\"delta\":{\"content\":\"Partial\"}}]}\n\n"
  for ((ending,success) in listOf("data:[DONE]\n\n" to true,"" to false,"data:{\"error\":{\"message\":\"failure\"}}\n\n" to false)) {
   coEvery { service.getChatCompletionStream(any(),request=any()) } returns Response.success((": heartbeat\n\n"+chunk+ending).toResponseBody())
   val result=repo.getChatCompletionStream("model",emptyList(),"test-key",emptyList(),null,ChatGenerationConfig()).toList()
   assertTrue(result.any { it is DataResult.Success && it.data.content=="Partial" })
   assertEquals(!success,result.any { it is DataResult.Error })
  }
 }
}

