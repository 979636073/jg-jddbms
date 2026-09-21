package com.jd.spi.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Database import/export command with an explicitly separated credential argument.
 */
public final class DmpCommand {
    private static final String REDACTED_CREDENTIAL = "******";

    private final String executable;
    private final String credentialArgument;
    private final List<String> optionArguments;

    public DmpCommand(String executable, String credentialArgument, List<String> optionArguments) {
        this.executable = executable;
        this.credentialArgument = credentialArgument;
        this.optionArguments = Collections.unmodifiableList(new ArrayList<>(optionArguments));
    }

    public String getExecutable() {
        return executable;
    }

    public List<String> toProcessArguments() {
        List<String> arguments = new ArrayList<>(optionArguments.size() + 2);
        arguments.add(executable);
        arguments.add(credentialArgument);
        arguments.addAll(optionArguments);
        return arguments;
    }

    public String toSafeString() {
        List<String> safeArguments = new ArrayList<>(optionArguments.size() + 2);
        safeArguments.add(executable);
        safeArguments.add(REDACTED_CREDENTIAL);
        safeArguments.addAll(optionArguments);
        return String.join(" ", safeArguments);
    }
}
