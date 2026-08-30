package com.cc4c.functional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class TestControllerFunctionalTest extends FunctionalTestSupport {

    @Test
    void imageUploadUsesConfiguredStorageAndKeepsTheResponseContract() throws Exception {
        MockMultipartFile image =
                new MockMultipartFile("file", "test-upload.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2, 3, 4});

        MvcResult result = mockMvc.perform(
                        multipart("/test/uploadImage").file(image).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value("1"))
                .andExpect(jsonPath("$.message").value("success"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String requestUrl = response.path("url").asText();
        String relativePath = requestUrl.substring("http://localhost:5173/test-blog/".length());
        Path storedFile = Path.of(System.getProperty("user.dir"), "target", "functional-files", "blog")
                .resolve(relativePath.replace('/', java.io.File.separatorChar));

        assertTrue(requestUrl.matches("http://localhost:5173/test-blog/img[1-5]/.+test-upload\\.png"));
        assertTrue(Files.isRegularFile(storedFile));
    }
}
