package com.bbogush.web_screen;

import java.util.Random;

public class Utils {
    public static String randomString(int len) {
        char [] chars = new char[len];
        String symbols = "0123456789abcdefghijklmnopqrstuvwxyz";

        Random rand = new Random();
        for (int i = 0; i < len; i++) {
            int index = rand.nextInt(len);
            chars[i] = symbols.charAt(index);
        }
        return String.copyValueOf(chars);
    }
}
