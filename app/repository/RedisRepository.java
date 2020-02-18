package repository;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisSortedSetAsyncCommands;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.lettuce.core.api.sync.RedisStringCommands;
import models.StatItem;
import models.TopStatItem;
import play.Logger;
import utils.StatItemSamples;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@Singleton
public class RedisRepository {

    private static Logger.ALogger logger = Logger.of("RedisRepository");
    private final RedisClient redisClient;

    @Inject
    public RedisRepository(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    public CompletionStage<Boolean> addNewHeroVisited(StatItem statItem) {
        logger.info("hero visited " + statItem.name);
        return addHeroAsLastVisited(statItem).thenCombine(incrHeroInTops(statItem), (aLong, aBoolean) -> {
            return aBoolean && aLong > 0;
        });
    }

    private CompletionStage<Boolean> incrHeroInTops(StatItem statItem) {
        StatefulRedisConnection<String, String> connection = redisClient.connect();

        CompletionStage<Boolean> result = connection
                .async()
                .zincrby("mostVisitedHeroes", 1, statItem.toJson().toString())
                .thenApply(res -> !res.isNaN());

        connection.close();

        return result;
    }


    private CompletionStage<Long> addHeroAsLastVisited(StatItem statItem) {
        StatefulRedisConnection<String, String> connection = redisClient.connect();

        CompletionStage<Long> result = connection
                .async()
                .zadd("lastVisitedHeroes", new Timestamp(new Date().getTime()).getTime(), statItem.toJson().toString());

        connection.close();

        return result;
    }

    public CompletionStage<List<StatItem>> lastHeroesVisited(int count) {
        StatefulRedisConnection<String, String> connection = redisClient.connect();

        CompletionStage<List<StatItem>> result = connection
                .async()
                .zrevrange("lastVisitedHeroes",0, count-1)
                .thenApply(list -> list.stream().map(elem -> StatItem.fromJson(elem)).collect(Collectors.toList()));

        return result;
    }

    public CompletionStage<List<TopStatItem>> topHeroesVisited(int count) {
        StatefulRedisConnection<String, String> connection = redisClient.connect();

        CompletionStage<List<TopStatItem>> result = connection
                .async()
                .zrevrangeWithScores("mostVisitedHeroes", 0,count-1)
                .thenApply(list -> list.stream().map(elem -> {
                    StatItem item = StatItem.fromJson(elem.getValue());
                    return new TopStatItem(item, (long) elem.getScore());
                }).collect(Collectors.toList()));

        return result;
    }
}
