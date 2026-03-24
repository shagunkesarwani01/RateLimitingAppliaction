package com.example.RateLimitingApplication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import lombok.Data;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Component
@Data
@ConfigurationProperties (prefix = "spring.redis")
public class RedisProperties {

    private String host = "localhost";

    private int port = 6379;
    private int timeout = 2000;

    @Bean
    public JedisPool getJedisPool() {

        // Jedis - Java client library for Redis. Let's Java application communucate with the Redis server.
        //Jedis pool keeps multiple connections ready to reuse.
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);     // max no. of connections in the pool
        poolConfig.setMaxIdle(10);      // max idle connections to keep (to be ready)
        poolConfig.setMinIdle(5);       // min connections that are always ready
        poolConfig.setTestOnBorrow(true);       // tests the connection before giving you pool
        poolConfig.setTestOnReturn(true);       // tests the connection before returning to pool
        return new JedisPool(poolConfig, host, port, timeout);
    }
}

/*
Is poori class ka main maqsad (use) aapki configurations ko "Type-Safe", "Clean", aur "Object-Oriented" banana hai.

Samajh lijiye aapki application.properties file ek bada sa godaam (warehouse) hai jahan database, server, aur security ki saari settings rakhi hain.
Yeh RedisProperties class ek special "box" hai jo us godaam se sirf Redis ka saamaan nikal kar apne andar safely rakh leta hai.
Agar aap yeh class nahi banate, toh aapko kya problem aati?

1. Agar aap yeh class nahi banate, toh aapko apne project mein jahan bhi Redis ka host ya port chahiye hota, wahan bar-bar @Value annotation use karna padta:
// Kisi doosri class mein aapko aise likhna padta:
@Value("${spring.redis.host}")
private String host;

@Value("${spring.redis.port}")
private int port;

2. Is class ke saath (The Enterprise Way)
Jab aap yeh RedisProperties class bana lete hain, toh Spring Boot app start hote hi ek RedisProperties ka object bana kar usme saari values bhar deta hai.

Ab aapko poori app mein kahin bhi Redis ki details chahiye hongi, toh aap bas is object ko bula lenge:
// Aap bas is object ko inject karte hain
@Autowired
private RedisProperties redisProperties;

// Aur directly values use karte hain
String myHost = redisProperties.getHost();
int myPort = redisProperties.getPort();

Connection Pool:
Jab bhi aapka application Redis database se baat karta hai, toh ek connection (rasta) banana padta hai.
Agar har nayi request par hum naya connection banayenge aur phir tod denge, toh app bohot slow ho jayega (kyunki connection establish karne mein time aur memory lagti hai).

Iska solution hai Connection Pool — yaani pehle se hi kuch connections bana kar ek "Pool" (reserve) mein rakh lo. Jab kisi user ko zarurat ho,
pool se connection nikalo, use karo, aur wapas pool mein daal do. Isko aap ek Taxi Stand ki tarah samajh sakte hain.
 */