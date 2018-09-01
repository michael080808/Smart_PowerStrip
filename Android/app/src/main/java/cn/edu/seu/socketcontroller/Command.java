package cn.edu.seu.socketcontroller;

import android.support.annotation.NonNull;

public enum Command {
    Query_Main("QM"), Query_Full("QF"), Limit("LT"), Alarm("AL"), Relieve("UL"), Off("CL"), On("ST");

    private String command;

    Command(String command) {
        this.command = command;
    }

    String Command(int args) {
        if (this == Limit) {
            return command + String.format("%04d", args);
        } else {
            return command + String.format("%02d", args);
        }
    }
}
