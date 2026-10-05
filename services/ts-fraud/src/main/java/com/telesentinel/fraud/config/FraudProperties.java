package com.telesentinel.fraud.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("telesentinel.fraud")
public record FraudProperties(Wangiri wangiri, Irsf irsf, List<String> allowList) {

    public FraudProperties {
        allowList = allowList == null ? List.of()
                : allowList.stream().map(n -> n.startsWith("+") ? n : "+" + n).toList();   // same spelling as ingestion
    }

    public record Wangiri(long shortCallSeconds, long distinctTargets, long windowSeconds) { }

    public record Irsf(List<String> riskyPrefixes, long maxSeconds, long windowSeconds) { }
}
