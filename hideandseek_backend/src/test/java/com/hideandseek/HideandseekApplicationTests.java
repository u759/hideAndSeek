package com.hideandseek;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.hideandseek.service.ClueService;
import com.opencagedata.jopencage.JOpenCageGeocoder;
import com.opencagedata.jopencage.model.JOpenCageResponse;
import com.opencagedata.jopencage.model.JOpenCageResult;
import com.opencagedata.jopencage.model.JOpenCageReverseRequest;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HideandseekApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void testAIClue() {
        Dotenv dotenv = Dotenv.load();

        JOpenCageGeocoder geocoder = new JOpenCageGeocoder(dotenv.get("OPENCAGE_API_KEY"));

        JOpenCageReverseRequest request =
                new JOpenCageReverseRequest(49.2657688, -123.255632);

        request.setLanguage("en");
        request.setNoAnnotations(false);
        request.setNoDedupe(true);

        StringBuilder result = new StringBuilder();

        JOpenCageResponse geocoded = geocoder.reverse(request);

        for (JOpenCageResult resultItem : geocoded.getResults()) {
            if (resultItem.getFormatted() != null) {
                result.append(resultItem.getFormatted()).append("\n");
            }
        }

        Client client = Client.builder().apiKey(dotenv.get("GEMINI_API_KEY")).build();

        String prompt = String.format(
                "Generate a %s clue for the seekers with hiders located in the general vicinity of the" +
                        " following reverse-geocoded locations: %s. The clue should follow" +
                        " the instructions in this description: %s.",
                "campus-landmark", result, "Get a hint about which major UBC landmark the hiders are near");

        System.out.println(prompt);

//        GenerateContentResponse response =
//                client.models.generateContent(
//                        "gemma-3-27b-it",
//                        prompt,
//                        null);
//        System.out.println(response.text());
    }

}
