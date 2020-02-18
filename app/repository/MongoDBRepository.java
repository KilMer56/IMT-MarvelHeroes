package repository;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import models.Hero;
import models.ItemCount;
import models.YearAndUniverseStat;
import org.bson.Document;
import play.libs.Json;
import utils.ReactiveStreamsUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Singleton
public class MongoDBRepository {

    private final MongoCollection<Document> heroesCollection;

    @Inject
    public MongoDBRepository(MongoDatabase mongoDatabase) {
        this.heroesCollection = mongoDatabase.getCollection("heroes");
    }

    public CompletionStage<Optional<Hero>> heroById(String heroId) {
        String query = "{\"id\":\""+heroId+"\"}";
        Document document = Document.parse(query);
        return ReactiveStreamsUtils.fromSinglePublisher(heroesCollection.find(document).first())
                .thenApply(result -> Optional.ofNullable(result).map(Document::toJson).map(Hero::fromJson));
    }

    public CompletionStage<List<YearAndUniverseStat>> countByYearAndUniverse() {
        return CompletableFuture.completedFuture(new ArrayList<>());
        // TODO
        //List<Document> pipeline = new ArrayList<>();
        //return ReactiveStreamsUtils.fromMultiPublisher(heroesCollection.aggregate(pipeline))
        //        .thenApply(documents -> {
        //            return documents.stream()
        //                            .map(Document::toJson)
        //                            .map(Json::parse)
        //                            .map(jsonNode -> {
        //                                int year = jsonNode.findPath("_id").findPath("yearAppearance").asInt();
        //                                ArrayNode byUniverseNode = (ArrayNode) jsonNode.findPath("byUniverse");
        //                                Iterator<JsonNode> elements = byUniverseNode.elements();
        //                                Iterable<JsonNode> iterable = () -> elements;
        //                                List<ItemCount> byUniverse = StreamSupport.stream(iterable.spliterator(), false)
        //                                        .map(node -> new ItemCount(node.findPath("universe").asText(), node.findPath("count").asInt()))
        //                                        .collect(Collectors.toList());
        //                                return new YearAndUniverseStat(year, byUniverse);
        //
        //                            })
        //                            .collect(Collectors.toList());
        //        });
    }


    public CompletionStage<List<ItemCount>> topPowers(int top) {
        Document unwind = Document.parse("{ $unwind : \"$powers\" }");
        Document group = Document.parse("{\n" +
                "            $group: {\n" +
                "                _id: \"$powers\",\n" +
                "                count: { $sum: 1 }\n" +
                "            }\n" +
                "        }");

        Document sort = Document.parse("{ $sort : { count : -1 } }");
        Document limit = Document.parse("{ $limit : 5 }");

        List<Document> pipeline = new ArrayList<Document>();
        pipeline.add(unwind);
        pipeline.add(group);
        pipeline.add(sort);
        pipeline.add(limit);

         return ReactiveStreamsUtils.fromMultiPublisher(heroesCollection.aggregate(pipeline))
                 .thenApply(documents -> {
                     return documents.stream()
                             .map(Document::toJson)
                             .map(Json::parse)
                             .map(jsonNode -> {
                                 return new ItemCount(jsonNode.findPath("_id").asText(), jsonNode.findPath("count").asInt());
                             })
                             .collect(Collectors.toList());
                 });
    }

    public CompletionStage<List<ItemCount>> byUniverse() {
        String query = "{ $group: {_id: \"$identity.universe\", count: { $sum: 1 } } }";
        Document doc = Document.parse(query);
        List<Document> pipeline = new ArrayList<Document>();
        pipeline.add(doc);

         return ReactiveStreamsUtils.fromMultiPublisher(heroesCollection.aggregate(pipeline))
                 .thenApply(documents -> {
                     return documents.stream()
                             .map(Document::toJson)
                             .map(Json::parse)
                             .map(jsonNode -> {
                                 return new ItemCount(jsonNode.findPath("_id").asText(), jsonNode.findPath("count").asInt());
                             })
                             .collect(Collectors.toList());
                 });
    }

}
