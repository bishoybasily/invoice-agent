package com.example.invoiceagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("ops")
public class OpsProps {

    private Extractor extractor = Extractor.REPLAY;
    private String samplesDir = "samples";
    private double minConfidence = 0.85;

    public enum Extractor { CLAUDE, GEMINI, REPLAY }
}
