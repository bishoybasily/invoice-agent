package com.example.invoiceagent;

import lombok.experimental.UtilityClass;

import java.nio.file.Path;

@UtilityClass
public class Samples {

    public static final Path DIR = Path.of(System.getProperty("ops.samples-dir", "../invoice-agent-samples"));

    public static Path file(String name) {
        return DIR.resolve(name);
    }
}
