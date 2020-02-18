package repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import env.ElasticConfiguration;
import env.MarvelHeroesConfiguration;
import models.PaginatedResults;
import models.SearchedHero;
import play.libs.Json;
import play.libs.ws.WSClient;
import utils.SearchedHeroSamples;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class ElasticRepository {

    private final WSClient wsClient;
    private final ElasticConfiguration elasticConfiguration;

    @Inject
    public ElasticRepository(WSClient wsClient, MarvelHeroesConfiguration configuration) {
        this.wsClient = wsClient;
        this.elasticConfiguration = configuration.elasticConfiguration;
    }

    public CompletionStage<PaginatedResults<SearchedHero>> searchHeroes(String input, int size, int page) {
        /*String bodyQuery = "{\n" +
                "  \"size\": " + size + ",\n" +
                "  \"from\": " + size * (page - 1) + "";

        // Add search with Fuziness feature
        if(!input.equals("*") && input.length() > 0) {
            bodyQuery += ",\n" +
                    "  \"query\": \n" +
                    "  { \n" +
                    "    \"match\": \n" +
                    "    { \n" +
                    "      \"name\": \n" +
                    "      {\n" +
                    "        \"query\": \"" + input + "\",\n" +
                    "        \"fuzziness\": \"2\"\n" +
                    "      }\n" +
                    "    } \n" +
                    "  }";
        }

        bodyQuery += "\n}";*/
        String formattedInput = input.replace(' ', '*');
        String bodyQuery = "{\n" +
                "  \"size\": " + size + ",\n" +
                "  \"from\": "+ size * (page - 1) + ", \n" +
                "  \"query\": {\n" +
                "      \"query_string\" : {\n" +
                "          \"fields\" : [\"name.keyword^5\", \"aliases.keyword^4\", \"secretIdentities.keyword^3\",\"description.keyword^2\",\"partners.keyword\"],\n" +
                "          \"query\" : \"*"+formattedInput+"*\"\n" +
                "      }\n" +
                "  }\n" +
                "}";

        System.out.println(bodyQuery);

        return wsClient.url(elasticConfiguration.uri + "/heroes/_search")
                 .post(Json.parse(bodyQuery))
                 .thenApply(response -> {
                     List<SearchedHero> heroes = new ArrayList<SearchedHero>();

                     JsonNode bodyNode = response.asJson();
                     JsonNode sources = bodyNode.get("hits").get("hits");

                     int total = bodyNode.get("hits").get("total").get("value").asInt();
                     int totalPage = (int) (total / size) + 1;

                     if (sources.isArray()) {
                         for (final JsonNode heroNode : sources) {
                             JsonNode heroBody = heroNode.get("_source");
                             ((ObjectNode)heroBody).put("id", heroNode.get("_id").asText());

                             SearchedHero newHero = SearchedHero.fromJson(heroBody);
                             heroes.add(newHero);
                         }
                     }

                     return new PaginatedResults<>(
                             total,
                             page,
                             totalPage,
                             heroes);
                 });
    }

    public CompletionStage<List<SearchedHero>> suggest(String input) {
        return CompletableFuture.completedFuture(Arrays.asList(SearchedHeroSamples.IronMan(),
                SearchedHeroSamples.MsMarvel(), SearchedHeroSamples.SpiderMan()));
        // TODO
        // return wsClient.url(elasticConfiguration.uri + "...")
        // .post(Json.parse("{ ... }"))
        // .thenApply(response -> {
        // return ...
        // });
    }
}
