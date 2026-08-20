package com.brouken.player.together;

import java.security.SecureRandom;

/**
 * Anonymous room aliases adapted from LocalSend's adjective-and-fruit names.
 * LocalSend is licensed under Apache-2.0: https://github.com/localsend/localsend
 * The Watch Together adaptation originates in Just+ Player by Oleksandr Zhyzhchenko.
 */
public final class AliasGenerator {
    private static final String[] ADJECTIVES = {
            "Able", "Active", "Adorable", "Agile", "Alert", "Amber", "Amused", "Ancient",
            "Artful", "Balanced", "Bold", "Brave", "Breezy", "Bright", "Brisk", "Calm",
            "Careful", "Cheerful", "Clever", "Cool", "Cosmic", "Crafty", "Curious", "Daring",
            "Dazzling", "Eager", "Elegant", "Energetic", "Fearless", "Friendly", "Golden",
            "Happy", "Helpful", "Honest", "Jolly", "Joyful", "Kind", "Lively", "Lucky",
            "Magic", "Merry", "Mighty", "Nimble", "Noble", "Playful", "Quick", "Quiet",
            "Radiant", "Reliable", "Serene", "Sharp", "Silent", "Smart", "Solar", "Strong",
            "Sunny", "Swift", "Tranquil", "Valiant", "Vibrant", "Warm", "Wise", "Witty"
    };

    private static final String[] FRUITS = {
            "Apple", "Apricot", "Avocado", "Banana", "Blackberry", "Blueberry", "Carrot",
            "Cherry", "Coconut", "Cranberry", "Cucumber", "Date", "Fig", "Grape",
            "Grapefruit", "Guava", "Kiwi", "Lemon", "Lime", "Lychee", "Mango", "Melon",
            "Olive", "Orange", "Papaya", "Peach", "Pear", "Pineapple", "Plum", "Pumpkin",
            "Quince", "Raspberry", "Strawberry", "Tangerine", "Tomato", "Watermelon"
    };

    private static final SecureRandom RANDOM = new SecureRandom();

    private AliasGenerator() {
    }

    public static String random() {
        return ADJECTIVES[RANDOM.nextInt(ADJECTIVES.length)]
                + " " + FRUITS[RANDOM.nextInt(FRUITS.length)];
    }
}
