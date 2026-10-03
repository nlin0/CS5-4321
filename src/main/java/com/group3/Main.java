package com.group3;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Main {
    public static void main(String[] args) throws Exception {
        Cli cli = new Cli(System.out);

        // Optional arguments: script files to run before the prompt starts.
        for (String script : args) {
            cli.execute(".read " + script);
        }

        System.out.println("Type .help for commands.");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        cli.run(in, System.console() != null);
    }
}
