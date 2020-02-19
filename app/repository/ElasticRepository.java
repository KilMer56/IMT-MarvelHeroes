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
        String bodyQuery = "{\n" +
                "  \"size\": " + size + ",\n" +
                "  \"from\": "+ size * (page - 1) + ", \n" +
                "  \"query\": {\n" +
                "      \"query_string\" : {\n" +
                "          \"fields\" : [\"name.keyword^5\", \"aliases.keyword^4\", \"secretIdentities.keyword^4\",\"description.keyword^3\",\"partners.keyword\"],\n" +
                "          \"query\" : \"*"+input.replaceAll(" ", "*")+"*\"\n" +
                "      }\n" +
                "  }\n" +
                "}";

        return wsClient.url(elasticConfiguration.uri + "/heroes/_search")
                 .post(Json.parse(bodyQuery))
                 .thenApply(response -> {

                     JsonNode bodyNode = response.asJson();
                     JsonNode source = bodyNode.get("hits").get("hits");

                     List<SearchedHero> heroes = parseSource(source);
                     int total = bodyNode.get("hits").get("total").get("value").asInt();
                     int totalPage = (int) (total / size) + 1;

                     return new PaginatedResults<>(
                             total,
                             page,
                             totalPage,
                             heroes);
                 });
    }

    public CompletionStage<List<SearchedHero>> suggest(String input) {
        String bodyQuery = "{\n" +
                "  \"suggest\": {\n" +
                "        \"suggestion\" : {\n" +
                "            \"prefix\" : \""+input+"\", \n" +
                "            \"completion\" : { \n" +
                "                \"field\" : \"suggest\" \n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}";

        return wsClient.url(elasticConfiguration.uri + "/heroes/_search")
                .post(Json.parse(bodyQuery))
                .thenApply(response -> {
                    JsonNode bodyNode = response.asJson();
                    JsonNode suggestions = bodyNode.get("suggest").get("suggestion");

                    List<SearchedHero> heroes = new ArrayList<SearchedHero>();

                    if (suggestions.isArray()) {
                        for (final JsonNode suggest : suggestions) {
                            JsonNode options = suggest.get("options");

                            if (options.isArray()) {
                                for (final JsonNode option : options) {
                                    JsonNode heroBody = option.get("_source");
                                    ((ObjectNode) heroBody).put("id", option.get("_id").asText());

                                    SearchedHero newHero = SearchedHero.fromJson(heroBody);
                                    heroes.add(newHero);
                                }
                            }
                        }
                    }

                    return heroes;
                });
    }

    private List<SearchedHero> parseSource(JsonNode source){
        List<SearchedHero> heroes = new ArrayList<SearchedHero>();

        if (source.isArray()) {
            for (final JsonNode heroNode : source) {
                JsonNode heroBody = heroNode.get("_source");
                ((ObjectNode)heroBody).put("id", heroNode.get("_id").asText());

                SearchedHero newHero = SearchedHero.fromJson(heroBody);
                heroes.add(newHero);
            }
        }

        return heroes;
    }
}
