package ru.ingvord.trading.telegram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SendMessageTest {



    @Test
    void execute() throws  Exception {
        new SendMessage("","1136291781", "ping").execute();
    }
}