package com.engine.veloce;

import com.engine.veloce.tui.VeloceCli;
import picocli.CommandLine;

/**
 * Main application entry point for VeloceEngine.
 */
public final class VeloceApplication {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new VeloceCli()).execute(args);
        System.exit(exitCode);
    }
}
