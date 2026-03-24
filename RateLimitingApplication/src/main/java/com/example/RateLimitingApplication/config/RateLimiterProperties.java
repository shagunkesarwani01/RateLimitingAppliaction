package com.example.RateLimitingApplication.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limiter")       // bind properties starting with rate limiter
@Data       // lombok: generates getter and setters
public class RateLimiterProperties {

    private long capacity = 10;     // Burst capacity
    private long refillRate = 5;

    private String apiServerUrl = "http://localhost:8081";
    private int timeout = 5000;

}

/*
Jab hum cloud par deploy karte hain, toh localhost ka matlab waqai mein badal jata hai.

Is apiServerUrl ka main use Routing (Gateway Pattern) ke liye hota hai. Chaliye isko thoda detail mein samajhte hain:

1. The Core Purpose (Routing)
Aap ek Rate Limiter (API Gateway) bana rahe hain. Iska architecture kuch is tarah kaam karta hai:

User request bhejta hai.

Sabse pehle request aapke Rate Limiter ke paas aati hai.

Rate Limiter Redis se check karta hai: "Kya is user ke tokens bache hain?"

Agar tokens bache hain, toh Rate Limiter us request ko aage forward kar deta hai aapke actual backend application ko (jaise ki aapki Spring Boot journal application).

Yeh apiServerUrl wahi destination hai jahan Gateway ko allowed requests bhejni hain.

2. Localhost vs. Cloud: Yeh hardcoded kyu hai?
Aapne jo = "http://localhost:8080" likha hai, yeh sirf ek default (fallback) value hai local development ke liye.

Jab aap apne laptop par testing kar rahe honge, toh aapka Rate Limiter shayad port 8081 par chal raha hoga, aur aapka main backend server 8080 par. Toh locally yeh perfectly kaam karega.

Lekin cloud par, localhost ka matlab hota hai "wahi same server/container". Cloud par actual API kisi doosre server ya URL par host hogi (e.g., [https://my-actual-backend.onrender.com](https://my-actual-backend.onrender.com)).

3. The Magic of Spring Boot @ConfigurationProperties
Aapko cloud par jaane ke baad is Java code ko change ya recompile karne ki zaroorat nahi padegi!

Kyunki aapne @ConfigurationProperties use kiya hai, Spring Boot aapko is variable ko bahar se override karne ki power deta hai. Jab aap apni app ko cloud (jaise AWS, Heroku, ya Render) par host karenge, toh aap wahan bas ek Environment Variable set kar denge:

Bash
RATE_LIMITER_API_SERVER_URL=https://my-actual-backend.onrender.com
Spring Boot automatically us hardcoded localhost:8080 ko ignore kar dega aur is nayi cloud URL ko pick kar lega. Yeh industry standard best practice hai cloud-native applications likhne ki!
 */