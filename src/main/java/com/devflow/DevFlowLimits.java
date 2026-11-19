package com.devflow;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Safety limits from application.yml (devflow.limits.*). */
@ConfigurationProperties(prefix = "devflow.limits")
public record DevFlowLimits(int maxFilesPerKit, int maxFileChars, int maxVariableLength) {
}
