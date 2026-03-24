package com.example.RateLimitingApplication.service;

import com.example.RateLimitingApplication.config.RateLimiterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

// store token bucket state in redis
// manage tokens per client
// handle token refill based on time
// provide rate limiting logic
@Service
@RequiredArgsConstructor
public class RedisTokenBucketService {

    private final JedisPool jedisPool;
    private final RateLimiterProperties rateLimiterProperties;

    private final String TOKENS_KEY_PREFIX = "rate_limiter:tokens:";
    private static final String LAST_REFILL_KEY_PREFIX = "rate_limiter:last_refill:";

    //Pattern how we store the information
    //rate_limiter:{type}:{clientId}

    //rate_limiter:tokens:192.168.1.100 > Current Token Count (=7)
    //rate_limiter:last_refill:192.168.1.100 > Last Refill Timestamp (="12324342342")

    public boolean isAllowed (String clientId) {

        // First get a connection from the JedisPool
        String tokenKey = TOKENS_KEY_PREFIX + clientId;

        // refill the token based on time that has elapsed
        try (Jedis jedis = jedisPool.getResource()){        // get a connection from the available pool
            refillTokens(clientId, jedis);      // this will calculate elapsed time and then add new tokens acc. to it

            String tokenStr = jedis.get(tokenKey);    //Get current token count
            long currentTokens = (tokenStr != null) ? Long.parseLong(tokenStr) : rateLimiterProperties.getCapacity();

            if (currentTokens <= 0) {
                return false;       // because no token is left, too many requests error 429
            } else {
                // decrease the value of the token
                long decremented = jedis.decr(tokenKey);        // decr() method: ATOMIC operation (multi-thread safe), and prevents Race condition
                return decremented >= 0;
            }
        }
    }

    public long getCapacity(String clientId) {
        return rateLimiterProperties.getCapacity();
    }

    public long getAvailableTokens(String clientId) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;

        try (Jedis jedis = jedisPool.getResource()) {
            refillTokens(clientId, jedis);
            String tokenStr = jedis.get(tokenKey);
            return (tokenStr != null) ? Long.parseLong(tokenStr) : rateLimiterProperties.getCapacity();

        } catch (Exception e) {
            System.out.println(e);
            throw new RuntimeException(e);
        }
    }

    public void refillTokens (String clientId, Jedis jedis) {
        // Purpose: Calculate and add the tokens based on the elapsed time
        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        long now = System.currentTimeMillis();
        String lastRefillStr = jedis.get(lastRefillKey);
        if (lastRefillStr == null) {
            jedis.set(tokenKey, String.valueOf(rateLimiterProperties.getCapacity()));       // fill the bucket completely
            jedis.set(lastRefillKey, String.valueOf(now));      // also note down its current time (now)
            return;
        }

        long lastRefillTime = Long.parseLong(lastRefillStr);
        long elapsedTime = now - lastRefillTime;

        if (elapsedTime <= 0) return;

        long tokensToAdd = (elapsedTime * rateLimiterProperties.getRefillRate()) / 1000;
        if (tokensToAdd <= 0) return;

        String tokenStr = jedis.get(tokenKey);      // returns Value = no. of token left (available)
        long currentTokens = (tokenStr != null) ? Long.parseLong(tokenStr) : rateLimiterProperties.getCapacity();
        long newTokens = Math.min(rateLimiterProperties.getCapacity(), currentTokens + tokensToAdd);        // to prevent overflow of bucket (suppose, excess of tokens generated after 1 hour)

        jedis.set(tokenKey, String.valueOf(newTokens));     // update token count
        jedis.set(lastRefillKey, String.valueOf(now));      // update current time

        // tokensToAdd = (elapsedTime * properties.getRefillRate()) / 1000;

        //elapsedTime is in msec
        //refillRate is tokens per second
        //Convert msec to sec  / 1000

        //ElapsedTime > 2000ms (2sec)
        //RefillRate = 5 tokens per second
        //Cal > (2000 * 5) / 1000 > 10 tokens
    }
}

/*
Yeh variables aapke Token Bucket Rate Limiter algorithm ka "brain" hain!
Redis ek "Key-Value" store hai (jaise ek dictionary jahan har value ka ek naam/key hota hai).
Kyunki aap bohot saare users ko ek saath handle karenge, aapko har user ka data alag-alag track karna hoga.
Yeh dono String variables unhi Redis keys ke prefixes (shuruati hisse) define kar rahe hain.

Redis mein best practice hoti hai ki keys ko colons (:) se separate kiya jaye taaki ek "folder-like" structure ban jaye.

1. TOKENS_KEY_PREFIX = "rate_limiter:tokens:"
Yeh key track karti hai ki ek particular user ke "bucket" mein abhi kitne tokens (requests) baaki hain.
Redis mein key banegi: rate_limiter:tokens:192.168.1.5
Value kya hogi: 10 (agar max capacity 10 hai). Jaise-jaise user requests bhejega, yeh number 9, 8, 7 hota jayega.

2. LAST_REFILL_KEY_PREFIX = "rate_limiter:last_refill:"
Yeh key track karti hai ki pichli baar is user ke bucket mein kis time par (timestamp) naye tokens daale gaye the.
Value kya hogi: Ek lamba number (System time in milliseconds, jaise 1711100000000).
Jab user aagli baar aayega, aap current time se is time ko minus karke
check karenge ki kitna waqt guzar gaya aur kitne naye tokens dene hain.
 */